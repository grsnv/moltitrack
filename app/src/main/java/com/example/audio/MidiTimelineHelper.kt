package com.example.audio

import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.RecordedMidiEvent
import kotlin.math.max

/**
 * Pure Kotlin helper for computing MIDI timeline duration and visual activity bars
 * from raw recorded MIDI notes & parameter events without generating any synthetic audio files.
 */
object MidiTimelineHelper {

    fun computeMidiDurationMs(
        notes: List<MidiNote>,
        events: List<RecordedMidiEvent>
    ): Long {
        val lastNote = notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        val lastEvent = events.maxOfOrNull { it.timestampMs + 250L } ?: 0L
        return max(lastNote, lastEvent)
    }

    fun extractActivityPoints(
        notes: List<MidiNote>,
        events: List<RecordedMidiEvent>,
        targetPoints: Int = 80
    ): List<Float> {
        if (notes.isEmpty() && events.isEmpty()) return emptyList()
        val totalMs = max(1000L, computeMidiDurationMs(notes, events))
        val bins = FloatArray(targetPoints)

        for (note in notes) {
            val startIdx = ((note.startMs.toFloat() / totalMs) * targetPoints).toInt().coerceIn(0, targetPoints - 1)
            val endIdx = (((note.startMs + note.durationMs).toFloat() / totalMs) * targetPoints).toInt().coerceIn(startIdx, targetPoints - 1)
            val amp = (note.velocity / 127f).coerceIn(0.15f, 1.0f)
            for (i in startIdx..endIdx) {
                if (amp > bins[i]) bins[i] = amp
            }
        }

        for (evt in events) {
            if (evt.type == MidiEventType.NOTE_ON || evt.type == MidiEventType.NOTE_OFF) continue
            val idx = ((evt.timestampMs.toFloat() / totalMs) * targetPoints).toInt().coerceIn(0, targetPoints - 1)
            val amp = when (evt.type) {
                MidiEventType.CONTROL_CHANGE -> (evt.data2 / 127f).coerceIn(0.2f, 0.9f)
                MidiEventType.PITCH_BEND -> 0.55f
                MidiEventType.PROGRAM_CHANGE -> 0.7f
                MidiEventType.CHANNEL_PRESSURE -> (evt.data1 / 127f).coerceIn(0.2f, 0.95f)
                MidiEventType.POLY_PRESSURE -> (evt.data2 / 127f).coerceIn(0.2f, 0.95f)
                else -> 0.3f
            }
            if (amp > bins[idx]) bins[idx] = amp
        }

        return bins.map { it.coerceIn(0.05f, 1.0f) }
    }
}
