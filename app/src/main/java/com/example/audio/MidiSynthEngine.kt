package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.RecordedMidiEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Neutral MIDI reference monitor & waveform renderer.
 * Uses no premade instrument patches; renders recorded MIDI notes while dynamically
 * honoring recorded Pitch Bend, Volume/Expression CC (CC 7 / CC 11), and Sustain (CC 64).
 */
object MidiSynthEngine {

    const val SAMPLE_RATE = 44100

    fun noteToFreq(note: Int, pitchBendSemitones: Double = 0.0): Double {
        return 440.0 * 2.0.pow((note - 69 + pitchBendSemitones) / 12.0)
    }

    fun renderMidiToWav(
        notes: List<MidiNote>,
        outputFile: File,
        midiEvents: List<RecordedMidiEvent> = emptyList(),
        sampleRate: Int = SAMPLE_RATE,
        minDurationMs: Long = 4000L
    ): Boolean {
        val lastEventMs = midiEvents.maxOfOrNull { it.timestampMs + 300L } ?: 0L
        val lastNoteMs = notes.maxOfOrNull { it.startMs + it.durationMs + 300L } ?: 0L

        if (notes.isEmpty()) {
            val totalMs = max(minDurationMs, lastEventMs)
            val totalSamples = (totalMs * sampleRate / 1000L).toInt()
            val totalBytes = totalSamples * 2L
            val out = FileOutputStream(outputFile)
            WavHelper.writeWavHeader(out, 1, sampleRate, totalBytes)
            out.write(ByteArray(totalBytes.toInt()))
            out.flush()
            out.close()
            return true
        }

        val sortedEvents = midiEvents.sortedBy { it.timestampMs }
        val maxEndMs = max(minDurationMs, max(lastNoteMs, lastEventMs))
        val totalSamples = (maxEndMs * sampleRate / 1000.0).toInt()
        val accum = FloatArray(totalSamples)

        for (note in notes) {
            val extraSustainMs = if (isSustainHeldAt(sortedEvents, note.startMs + note.durationMs)) 600L else 0L
            val effectiveDurationMs = note.durationMs + extraSustainMs
            val startSample = (note.startMs * sampleRate / 1000.0).toInt().coerceIn(0, totalSamples - 1)
            val noteDurationSamples = (effectiveDurationMs * sampleRate / 1000.0).toInt()
            val releaseSamples = (0.18 * sampleRate).toInt()
            val endSample = min(totalSamples, startSample + noteDurationSamples + releaseSamples)

            val bendAtStart = latestPitchBendSemitonesAt(sortedEvents, note.startMs)
            val ccGain = latestGainScaleAt(sortedEvents, note.startMs)
            val freq = noteToFreq(note.noteNumber, bendAtStart)
            val velocityGain = (note.velocity / 127.0).toFloat().coerceIn(0.08f, 1.0f) * ccGain

            for (s in startSample until endSample) {
                val t = (s - startSample).toDouble() / sampleRate
                val noteDurationSec = effectiveDurationMs / 1000.0

                val ampEnvelope = when {
                    t < 0.01 -> t / 0.01
                    t < noteDurationSec -> 0.8 * exp(-(t - 0.01) * 1.2)
                    else -> {
                        val relTime = t - noteDurationSec
                        (0.8 * exp(-relTime * 14.0)).coerceAtLeast(0.0)
                    }
                }

                // Clean neutral monitor tone (fundamental + gentle 2nd harmonic)
                val fundamental = sin(2.0 * PI * freq * t)
                val overtone = 0.2 * sin(4.0 * PI * freq * t)
                val sampleValue = fundamental * 0.8 + overtone
                accum[s] += (sampleValue * ampEnvelope * velocityGain * 20000f).toFloat()
            }
        }

        val totalBytes = totalSamples * 2L
        val out = FileOutputStream(outputFile)
        WavHelper.writeWavHeader(out, 1, sampleRate, totalBytes)

        val buffer = ByteArray(4096)
        var bufIdx = 0

        for (s in 0 until totalSamples) {
            val clamped = accum[s].toInt().coerceIn(-32768, 32767)
            buffer[bufIdx++] = (clamped and 0xFF).toByte()
            buffer[bufIdx++] = ((clamped shr 8) and 0xFF).toByte()

            if (bufIdx >= buffer.size) {
                out.write(buffer, 0, bufIdx)
                bufIdx = 0
            }
        }

        if (bufIdx > 0) {
            out.write(buffer, 0, bufIdx)
        }

        out.flush()
        out.close()
        return true
    }

    private fun latestPitchBendSemitonesAt(sortedEvents: List<RecordedMidiEvent>, timeMs: Long): Double {
        val lastBend = sortedEvents.lastOrNull {
            it.timestampMs <= timeMs && it.type == MidiEventType.PITCH_BEND
        } ?: return 0.0
        return ((lastBend.data1 - 8192) / 8192.0) * 2.0
    }

    private fun latestGainScaleAt(sortedEvents: List<RecordedMidiEvent>, timeMs: Long): Float {
        val vol = sortedEvents.lastOrNull {
            it.timestampMs <= timeMs && it.type == MidiEventType.CONTROL_CHANGE && it.data1 == 7
        }?.data2 ?: 127
        val expr = sortedEvents.lastOrNull {
            it.timestampMs <= timeMs && it.type == MidiEventType.CONTROL_CHANGE && it.data1 == 11
        }?.data2 ?: 127
        return ((vol / 127f) * (expr / 127f)).coerceIn(0.15f, 1.0f)
    }

    private fun isSustainHeldAt(sortedEvents: List<RecordedMidiEvent>, timeMs: Long): Boolean {
        val sustain = sortedEvents.lastOrNull {
            it.timestampMs <= timeMs && it.type == MidiEventType.CONTROL_CHANGE && it.data1 == 64
        } ?: return false
        return sustain.data2 >= 64
    }

    fun playNoteLive(
        noteNumber: Int,
        velocity: Int = 100,
        scope: CoroutineScope,
        durationMs: Long = 350L,
        pitchBend14Bit: Int = 8192
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val bendSemitones = ((pitchBend14Bit - 8192) / 8192.0) * 2.0
                val freq = noteToFreq(noteNumber, bendSemitones)
                val sampleCount = (durationMs * SAMPLE_RATE / 1000L).toInt()
                val buffer = ShortArray(sampleCount)
                val velScale = (velocity / 127.0).coerceIn(0.15, 1.0)

                for (i in 0 until sampleCount) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = (1.0 - (i.toDouble() / sampleCount)).coerceAtLeast(0.0)
                    val wave = 0.8 * sin(2.0 * PI * freq * t) + 0.2 * sin(4.0 * PI * freq * t)
                    buffer[i] = (wave * env * velScale * 20000.0).toInt().coerceIn(-32768, 32767).toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                kotlinx.coroutines.delay(durationMs + 50L)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
