package com.example.audio

import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.RecordedMidiEvent
import com.example.model.TrackModel
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure state-transformation helper for editing recorded MIDI notes and parameter events
 * on a [TrackModel] while keeping [TrackModel.midiNotes], [TrackModel.midiEvents],
 * snapshot parameter pills, activity waveforms, and duration synchronized for playback.
 */
object MidiTrackEditor {

    data class MidiSnapshots(
        val latestCcValues: Map<Int, Int>,
        val latestProgram: Int?,
        val latestPitchBend: Int,
        val latestAftertouch: Int
    )

    fun sanitizeNote(note: MidiNote): MidiNote = note.copy(
        noteNumber = note.noteNumber.coerceIn(0, 127),
        startMs = note.startMs.coerceAtLeast(0L),
        durationMs = note.durationMs.coerceAtLeast(20L),
        velocity = note.velocity.coerceIn(1, 127),
        channel = note.channel.coerceIn(1, 16)
    )

    fun sanitizeEvent(event: RecordedMidiEvent): RecordedMidiEvent {
        val safeTimestamp = event.timestampMs.coerceAtLeast(0L)
        val safeChannel = event.channel.coerceIn(1, 16)
        val (safeData1, safeData2) = when (event.type) {
            MidiEventType.NOTE_ON -> event.data1.coerceIn(0, 127) to event.data2.coerceIn(1, 127)
            MidiEventType.NOTE_OFF -> event.data1.coerceIn(0, 127) to event.data2.coerceIn(0, 127)
            MidiEventType.CONTROL_CHANGE -> event.data1.coerceIn(0, 127) to event.data2.coerceIn(0, 127)
            MidiEventType.PROGRAM_CHANGE -> event.data1.coerceIn(0, 127) to 0
            MidiEventType.PITCH_BEND -> event.data1.coerceIn(0, 16383) to 0
            MidiEventType.CHANNEL_PRESSURE -> event.data1.coerceIn(0, 127) to 0
            MidiEventType.POLY_PRESSURE -> event.data1.coerceIn(0, 127) to event.data2.coerceIn(0, 127)
        }
        return event.copy(
            timestampMs = safeTimestamp,
            channel = safeChannel,
            data1 = safeData1,
            data2 = safeData2
        )
    }

    fun recomputeSnapshots(events: List<RecordedMidiEvent>): MidiSnapshots {
        val ccs = linkedMapOf<Int, Int>()
        var program: Int? = null
        var pitchBend = 8192
        var aftertouch = 0

        for (evt in events.sortedBy { it.timestampMs }) {
            when (evt.type) {
                MidiEventType.CONTROL_CHANGE -> ccs[evt.data1] = evt.data2
                MidiEventType.PROGRAM_CHANGE -> program = evt.data1
                MidiEventType.PITCH_BEND -> pitchBend = evt.data1
                MidiEventType.CHANNEL_PRESSURE -> aftertouch = evt.data1
                MidiEventType.POLY_PRESSURE -> aftertouch = evt.data2
                MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> {}
            }
        }
        return MidiSnapshots(
            latestCcValues = ccs,
            latestProgram = program,
            latestPitchBend = pitchBend,
            latestAftertouch = aftertouch
        )
    }

    fun addNote(track: TrackModel, note: MidiNote): TrackModel {
        val clean = sanitizeNote(note)
        val onId = clean.noteOnEventId ?: UUID.randomUUID().toString()
        val offId = clean.noteOffEventId ?: UUID.randomUUID().toString()
        val linkedNote = clean.copy(noteOnEventId = onId, noteOffEventId = offId)

        val noteOnEvt = RecordedMidiEvent(
            id = onId,
            timestampMs = linkedNote.startMs,
            channel = linkedNote.channel,
            type = MidiEventType.NOTE_ON,
            data1 = linkedNote.noteNumber,
            data2 = linkedNote.velocity
        )
        val noteOffEvt = RecordedMidiEvent(
            id = offId,
            timestampMs = linkedNote.startMs + linkedNote.durationMs,
            channel = linkedNote.channel,
            type = MidiEventType.NOTE_OFF,
            data1 = linkedNote.noteNumber,
            data2 = 0
        )

        val sortedNotes = (track.midiNotes + linkedNote)
            .sortedWith(compareBy<MidiNote>({ it.startMs }, { it.noteNumber }))
        val sortedEvents = (track.midiEvents + noteOnEvt + noteOffEvt)
            .sortedBy { it.timestampMs }

        return rebuildTimeline(track, sortedNotes, sortedEvents)
    }

    fun updateNote(track: TrackModel, updatedNote: MidiNote): TrackModel {
        val oldNote = track.midiNotes.firstOrNull { it.id == updatedNote.id }
            ?: return addNote(track, updatedNote)

        val clean = sanitizeNote(updatedNote)
        val mutableEvents = track.midiEvents.toMutableList()

        val onIdx = findPairedNoteOnIndex(mutableEvents, oldNote)
        val onId = if (onIdx >= 0) {
            val existing = mutableEvents[onIdx]
            mutableEvents[onIdx] = existing.copy(
                timestampMs = clean.startMs,
                channel = clean.channel,
                type = MidiEventType.NOTE_ON,
                data1 = clean.noteNumber,
                data2 = clean.velocity
            )
            existing.id
        } else {
            val newId = clean.noteOnEventId ?: UUID.randomUUID().toString()
            mutableEvents.add(
                RecordedMidiEvent(
                    id = newId,
                    timestampMs = clean.startMs,
                    channel = clean.channel,
                    type = MidiEventType.NOTE_ON,
                    data1 = clean.noteNumber,
                    data2 = clean.velocity
                )
            )
            newId
        }

        val offIdx = findPairedNoteOffIndex(mutableEvents, oldNote)
        val offTimestamp = clean.startMs + clean.durationMs
        val offId = if (offIdx >= 0) {
            val existing = mutableEvents[offIdx]
            mutableEvents[offIdx] = existing.copy(
                timestampMs = offTimestamp,
                channel = clean.channel,
                type = MidiEventType.NOTE_OFF,
                data1 = clean.noteNumber,
                data2 = 0
            )
            existing.id
        } else {
            val newId = clean.noteOffEventId ?: UUID.randomUUID().toString()
            mutableEvents.add(
                RecordedMidiEvent(
                    id = newId,
                    timestampMs = offTimestamp,
                    channel = clean.channel,
                    type = MidiEventType.NOTE_OFF,
                    data1 = clean.noteNumber,
                    data2 = 0
                )
            )
            newId
        }

        val linkedUpdated = clean.copy(noteOnEventId = onId, noteOffEventId = offId)
        val sortedNotes = track.midiNotes
            .map { if (it.id == linkedUpdated.id) linkedUpdated else it }
            .sortedWith(compareBy<MidiNote>({ it.startMs }, { it.noteNumber }))
        val sortedEvents = mutableEvents.sortedBy { it.timestampMs }

        return rebuildTimeline(track, sortedNotes, sortedEvents)
    }

    fun removeNote(track: TrackModel, noteId: String): TrackModel {
        val oldNote = track.midiNotes.firstOrNull { it.id == noteId } ?: return track
        val mutableEvents = track.midiEvents.toMutableList()

        val onIdx = findPairedNoteOnIndex(mutableEvents, oldNote)
        if (onIdx >= 0) {
            mutableEvents.removeAt(onIdx)
        }
        val offIdx = findPairedNoteOffIndex(mutableEvents, oldNote)
        if (offIdx >= 0) {
            mutableEvents.removeAt(offIdx)
        }

        val remainingNotes = track.midiNotes.filterNot { it.id == noteId }
        val sortedEvents = mutableEvents.sortedBy { it.timestampMs }
        return rebuildTimeline(track, remainingNotes, sortedEvents)
    }

    fun updateMidiEvent(track: TrackModel, updatedEvent: RecordedMidiEvent): TrackModel {
        val clean = sanitizeEvent(updatedEvent)
        val mutableEvents = track.midiEvents.toMutableList()
        val existingIdx = mutableEvents.indexOfFirst { it.id == clean.id }
        if (existingIdx >= 0) {
            mutableEvents[existingIdx] = clean
        } else {
            mutableEvents.add(clean)
        }

        var updatedNotes = track.midiNotes
        if (clean.type == MidiEventType.NOTE_ON) {
            updatedNotes = updatedNotes.map { note ->
                if (note.noteOnEventId == clean.id) {
                    val existingEnd = note.startMs + note.durationMs
                    val newEnd = max(clean.timestampMs + 20L, existingEnd)
                    val newDuration = newEnd - clean.timestampMs
                    // Keep paired NOTE_OFF aligned in pitch, channel, and valid end time
                    note.noteOffEventId?.let { offId ->
                        val offIdx = mutableEvents.indexOfFirst { it.id == offId }
                        if (offIdx >= 0) {
                            mutableEvents[offIdx] = mutableEvents[offIdx].copy(
                                timestampMs = newEnd,
                                channel = clean.channel,
                                data1 = clean.data1
                            )
                        }
                    }
                    note.copy(
                        noteNumber = clean.data1,
                        startMs = clean.timestampMs,
                        durationMs = newDuration,
                        velocity = clean.data2,
                        channel = clean.channel
                    )
                } else {
                    note
                }
            }
        } else if (clean.type == MidiEventType.NOTE_OFF) {
            updatedNotes = updatedNotes.map { note ->
                if (note.noteOffEventId == clean.id) {
                    val safeEnd = max(note.startMs + 20L, clean.timestampMs)
                    if (safeEnd != clean.timestampMs) {
                        val idx = mutableEvents.indexOfFirst { it.id == clean.id }
                        if (idx >= 0) {
                            mutableEvents[idx] = mutableEvents[idx].copy(timestampMs = safeEnd)
                        }
                    }
                    note.copy(
                        durationMs = safeEnd - note.startMs
                    )
                } else {
                    note
                }
            }
        }

        val sortedNotes = updatedNotes.sortedWith(compareBy<MidiNote>({ it.startMs }, { it.noteNumber }))
        val sortedEvents = mutableEvents.sortedBy { it.timestampMs }
        return rebuildTimeline(track, sortedNotes, sortedEvents, recomputeParamSnapshots = true)
    }

    fun removeMidiEvent(track: TrackModel, eventId: String): TrackModel {
        val existing = track.midiEvents.firstOrNull { it.id == eventId } ?: return track
        val mutableEvents = track.midiEvents.filterNot { it.id == eventId }.toMutableList()

        val updatedNotes = mutableListOf<MidiNote>()
        val offIdsToRemove = mutableSetOf<String>()
        for (note in track.midiNotes) {
            when {
                note.noteOnEventId == existing.id -> {
                    note.noteOffEventId?.let { offIdsToRemove.add(it) }
                }
                note.noteOffEventId == existing.id -> {
                    updatedNotes.add(note.copy(noteOffEventId = null))
                }
                else -> updatedNotes.add(note)
            }
        }
        if (offIdsToRemove.isNotEmpty()) {
            mutableEvents.removeAll { it.id in offIdsToRemove }
        }

        val sortedEvents = mutableEvents.sortedBy { it.timestampMs }
        return rebuildTimeline(track, updatedNotes, sortedEvents, recomputeParamSnapshots = true)
    }

    private fun findPairedNoteOnIndex(events: List<RecordedMidiEvent>, note: MidiNote): Int {
        note.noteOnEventId?.let { id ->
            val byId = events.indexOfFirst { it.id == id }
            if (byId >= 0) return byId
        }
        return events.indexOfFirst {
            it.type == MidiEventType.NOTE_ON &&
                it.channel == note.channel &&
                it.data1 == note.noteNumber &&
                it.timestampMs == note.startMs
        }
    }

    private fun findPairedNoteOffIndex(events: List<RecordedMidiEvent>, note: MidiNote): Int {
        note.noteOffEventId?.let { id ->
            val byId = events.indexOfFirst { it.id == id }
            if (byId >= 0) return byId
        }
        val expectedEnd = note.startMs + note.durationMs
        var bestIdx = -1
        var bestDiff = Long.MAX_VALUE
        events.forEachIndexed { idx, evt ->
            if (evt.type == MidiEventType.NOTE_OFF &&
                evt.channel == note.channel &&
                evt.data1 == note.noteNumber &&
                evt.timestampMs >= note.startMs
            ) {
                val diff = abs(evt.timestampMs - expectedEnd)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestIdx = idx
                }
            }
        }
        return bestIdx
    }

    private fun rebuildTimeline(
        track: TrackModel,
        notes: List<MidiNote>,
        events: List<RecordedMidiEvent>,
        recomputeParamSnapshots: Boolean = false
    ): TrackModel {
        val activity = MidiTimelineHelper.extractActivityPoints(notes, events)
        val duration = MidiTimelineHelper.computeMidiDurationMs(notes, events)
        val base = track.copy(
            audioFilePath = null,
            midiNotes = notes,
            midiEvents = events,
            waveformPoints = activity,
            durationMs = duration
        )
        return if (recomputeParamSnapshots) {
            val snaps = recomputeSnapshots(events)
            base.copy(
                latestCcValues = snaps.latestCcValues,
                latestProgram = snaps.latestProgram,
                latestPitchBend = snaps.latestPitchBend,
                latestAftertouch = snaps.latestAftertouch
            )
        } else {
            base
        }
    }
}
