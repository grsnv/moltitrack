package com.example

import com.example.audio.MidiTrackEditor
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.RecordedMidiEvent
import com.example.model.TrackModel
import com.example.model.TrackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiEditorTest {

    private fun emptyMidiTrack(): TrackModel = TrackModel(
        id = "midi_track_1",
        name = "Test MIDI",
        type = TrackType.MIDI
    )

    @Test
    fun testAddUpdateAndRemoveLinkedNoteKeepsEventsInSync() {
        var track = emptyMidiTrack()
        val note = MidiNote(
            id = "n1",
            noteNumber = 60,
            startMs = 200L,
            durationMs = 400L,
            velocity = 95,
            channel = 1
        )
        track = MidiTrackEditor.addNote(track, note)

        assertEquals(1, track.midiNotes.size)
        assertEquals(2, track.midiEvents.size)
        val savedNote = track.midiNotes.first()
        assertEquals(850L, track.durationMs) // 600ms note end + 250ms event tail

        // Edit pitch, start, duration, velocity, and channel (with out-of-bounds clamping check)
        val editedInput = savedNote.copy(
            noteNumber = 135, // clamps to 127
            startMs = 500L,
            durationMs = 750L,
            velocity = 200, // clamps to 127
            channel = 4
        )
        track = MidiTrackEditor.updateNote(track, editedInput)

        assertEquals(1, track.midiNotes.size)
        val updated = track.midiNotes.first()
        assertEquals(127, updated.noteNumber)
        assertEquals(500L, updated.startMs)
        assertEquals(750L, updated.durationMs)
        assertEquals(127, updated.velocity)
        assertEquals(4, updated.channel)
        assertEquals(1500L, track.durationMs) // 1250ms note end + 250ms event tail

        val noteOn = track.midiEvents.first { it.type == MidiEventType.NOTE_ON }
        val noteOff = track.midiEvents.first { it.type == MidiEventType.NOTE_OFF }
        assertEquals(500L, noteOn.timestampMs)
        assertEquals(127, noteOn.data1)
        assertEquals(127, noteOn.data2)
        assertEquals(4, noteOn.channel)
        assertEquals(1250L, noteOff.timestampMs)
        assertEquals(127, noteOff.data1)
        assertEquals(4, noteOff.channel)

        // Remove the note -> both NOTE_ON and NOTE_OFF must also be removed
        track = MidiTrackEditor.removeNote(track, updated.id)
        assertTrue(track.midiNotes.isEmpty())
        assertTrue(track.midiEvents.isEmpty())
        assertEquals(0L, track.durationMs)
    }

    @Test
    fun testUpdateAndRemoveLegacyUnlinkedNoteMatchesByTimestampPitchChannel() {
        val ccEvt = RecordedMidiEvent(
            id = "cc1",
            timestampMs = 100L,
            channel = 2,
            type = MidiEventType.CONTROL_CHANGE,
            data1 = 64,
            data2 = 127
        )
        val onEvt = RecordedMidiEvent(
            id = "on_legacy",
            timestampMs = 250L,
            channel = 2,
            type = MidiEventType.NOTE_ON,
            data1 = 64,
            data2 = 80
        )
        val offEvt = RecordedMidiEvent(
            id = "off_legacy",
            timestampMs = 650L,
            channel = 2,
            type = MidiEventType.NOTE_OFF,
            data1 = 64,
            data2 = 0
        )
        val legacyNote = MidiNote(
            id = "legacy_n",
            noteNumber = 64,
            startMs = 250L,
            durationMs = 400L,
            velocity = 80,
            channel = 2
        )
        var track = emptyMidiTrack().copy(
            midiNotes = listOf(legacyNote),
            midiEvents = listOf(ccEvt, onEvt, offEvt)
        )

        track = MidiTrackEditor.updateNote(
            track,
            legacyNote.copy(noteNumber = 67, startMs = 300L, durationMs = 500L, velocity = 110, channel = 3)
        )
        val updatedOn = track.midiEvents.first { it.id == "on_legacy" }
        val updatedOff = track.midiEvents.first { it.id == "off_legacy" }
        assertEquals(67, updatedOn.data1)
        assertEquals(300L, updatedOn.timestampMs)
        assertEquals(110, updatedOn.data2)
        assertEquals(3, updatedOn.channel)
        assertEquals(67, updatedOff.data1)
        assertEquals(800L, updatedOff.timestampMs)
        assertEquals(3, updatedOff.channel)

        // Remove note leaves CC event intact
        track = MidiTrackEditor.removeNote(track, "legacy_n")
        assertTrue(track.midiNotes.isEmpty())
        assertEquals(listOf("cc1"), track.midiEvents.map { it.id })
    }

    @Test
    fun testUpdateAndRemoveParameterEventsRecomputesSnapshotsAndLinkedNotes() {
        var track = emptyMidiTrack()
        track = MidiTrackEditor.addNote(
            track,
            MidiNote(id = "n1", noteNumber = 60, startMs = 100L, durationMs = 400L, velocity = 90, channel = 1)
        )
        val ccEvt = RecordedMidiEvent(id = "cc1", timestampMs = 200L, channel = 1, type = MidiEventType.CONTROL_CHANGE, data1 = 1, data2 = 50)
        val bendEvt = RecordedMidiEvent(id = "pb1", timestampMs = 300L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 10000)
        val progEvt = RecordedMidiEvent(id = "pc1", timestampMs = 350L, channel = 1, type = MidiEventType.PROGRAM_CHANGE, data1 = 18)
        val atEvt = RecordedMidiEvent(id = "at1", timestampMs = 400L, channel = 1, type = MidiEventType.CHANNEL_PRESSURE, data1 = 64)

        track = MidiTrackEditor.updateMidiEvent(track, ccEvt)
        track = MidiTrackEditor.updateMidiEvent(track, bendEvt)
        track = MidiTrackEditor.updateMidiEvent(track, progEvt)
        track = MidiTrackEditor.updateMidiEvent(track, atEvt)

        assertEquals(50, track.latestCcValues[1])
        assertEquals(10000, track.latestPitchBend)
        assertEquals(18, track.latestProgram)
        assertEquals(64, track.latestAftertouch)

        // Edit CC controller & value and Pitch Bend
        track = MidiTrackEditor.updateMidiEvent(track, ccEvt.copy(data1 = 74, data2 = 120))
        track = MidiTrackEditor.updateMidiEvent(track, bendEvt.copy(data1 = 18000)) // clamps to 16383
        assertNull(track.latestCcValues[1])
        assertEquals(120, track.latestCcValues[74])
        assertEquals(16383, track.latestPitchBend)

        // Edit NOTE_ON event directly -> linked MidiNote updates too
        val linkedNote = track.midiNotes.first()
        val noteOnEvt = track.midiEvents.first { it.id == linkedNote.noteOnEventId }
        track = MidiTrackEditor.updateMidiEvent(
            track,
            noteOnEvt.copy(timestampMs = 150L, data1 = 72, data2 = 121, channel = 5)
        )
        val syncedNote = track.midiNotes.first()
        assertEquals(72, syncedNote.noteNumber)
        assertEquals(150L, syncedNote.startMs)
        assertEquals(350L, syncedNote.durationMs) // end was 500ms, start moved to 150ms
        assertEquals(121, syncedNote.velocity)
        assertEquals(5, syncedNote.channel)

        // Remove Pitch Bend & Program Change -> snapshots revert to defaults
        track = MidiTrackEditor.removeMidiEvent(track, "pb1")
        track = MidiTrackEditor.removeMidiEvent(track, "pc1")
        assertEquals(8192, track.latestPitchBend)
        assertNull(track.latestProgram)
    }
}
