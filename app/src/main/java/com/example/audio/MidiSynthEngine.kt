package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.MidiNote
import com.example.model.SynthInstrument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

object MidiSynthEngine {

    const val SAMPLE_RATE = 44100

    /**
     * Converts a MIDI note number (0-127) to frequency in Hertz.
     * Note 69 is A4 (440 Hz).
     */
    fun noteToFreq(note: Int): Double {
        return 440.0 * 2.0.pow((note - 69) / 12.0)
    }

    /**
     * Renders a sequence of MIDI notes into a 16-bit PCM Mono WAV file.
     * Calculates duration based on the latest note end time.
     */
    fun renderMidiToWav(
        notes: List<MidiNote>,
        instrument: SynthInstrument,
        outputFile: File,
        sampleRate: Int = SAMPLE_RATE,
        minDurationMs: Long = 4000L,
        midiProgram: Int = 0
    ): Boolean {
        val effectiveInstrument = if (midiProgram > 0) {
            com.example.model.GeneralMidiPatches.findPatch(midiProgram).synthFamily
        } else instrument
        if (notes.isEmpty()) {
            // Render a short silent track with minimum duration
            val totalSamples = (minDurationMs * sampleRate / 1000L).toInt()
            val totalBytes = totalSamples * 2L
            val out = FileOutputStream(outputFile)
            WavHelper.writeWavHeader(out, 1, sampleRate, totalBytes)
            out.write(ByteArray(totalBytes.toInt()))
            out.flush()
            out.close()
            return true
        }

        val maxEndMs = max(minDurationMs, notes.maxOf { it.startMs + it.durationMs + 300L })
        val totalSamples = (maxEndMs * sampleRate / 1000.0).toInt()
        val accum = FloatArray(totalSamples)

        for (note in notes) {
            val startSample = (note.startMs * sampleRate / 1000.0).toInt().coerceIn(0, totalSamples - 1)
            val noteDurationSamples = (note.durationMs * sampleRate / 1000.0).toInt()
            val releaseSamples = (0.25 * sampleRate).toInt() // 250ms release tail
            val endSample = min(totalSamples, startSample + noteDurationSamples + releaseSamples)

            val freq = noteToFreq(note.noteNumber)
            val velocityGain = (note.velocity / 127.0).toFloat().coerceIn(0.1f, 1.0f)

            for (s in startSample until endSample) {
                val t = (s - startSample).toDouble() / sampleRate
                val noteDurationSec = note.durationMs / 1000.0

                // Envelope calculation (ADSR)
                val ampEnvelope = when {
                    t < 0.02 -> (t / 0.02) // 20ms attack
                    t < noteDurationSec -> {
                        // Sustain
                        when (effectiveInstrument) {
                            SynthInstrument.ELECTRIC_PIANO,
                            SynthInstrument.BASS_SYNTH -> exp(-(t - 0.02) * 2.5) // decaying sustain
                            else -> 0.8
                        }
                    }
                    else -> {
                        // Release
                        val relTime = t - noteDurationSec
                        (0.8 * exp(-relTime * 12.0)).coerceAtLeast(0.0)
                    }
                }

                val sampleValue = generateInstrumentWave(effectiveInstrument, freq, t)
                accum[s] += (sampleValue * ampEnvelope * velocityGain * 24000f).toFloat()
            }
        }

        // Write to WAV file
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

    private fun generateInstrumentWave(instrument: SynthInstrument, freq: Double, t: Double): Double {
        return when (instrument) {
            SynthInstrument.SYNTH_LEAD -> {
                // Bright Sawtooth with detuned sub-oscillator
                val saw1 = 2.0 * ((t * freq) % 1.0) - 1.0
                val saw2 = 2.0 * ((t * (freq * 1.006)) % 1.0) - 1.0
                val sub = sin(2.0 * PI * (freq * 0.5) * t)
                (saw1 * 0.45 + saw2 * 0.35 + sub * 0.2)
            }
            SynthInstrument.ELECTRIC_PIANO -> {
                // FM synthesis (carrier + modulator) for classic Rhodes / DX7 bell timbre
                val modFreq = freq * 2.0
                val modIndex = 2.0 * exp(-t * 3.0)
                val modulator = modIndex * sin(2.0 * PI * modFreq * t)
                sin(2.0 * PI * freq * t + modulator) * 0.75
            }
            SynthInstrument.BASS_SYNTH -> {
                // Analog Sub-Bass with exponential punch
                val p = (t * freq) % 1.0
                val tri = (abs(p - 0.5) * 4.0 - 1.0)
                val subSine = sin(2.0 * PI * (freq * 0.5) * t)
                (tri * 0.4 + subSine * 0.6)
            }
            SynthInstrument.PAD_STRINGS -> {
                // Lush chorus sine/triangle pad
                val sine1 = sin(2.0 * PI * freq * t)
                val sine2 = sin(2.0 * PI * (freq * 1.004) * t)
                val sine3 = sin(2.0 * PI * (freq * 0.996) * t)
                (sine1 + sine2 + sine3) / 3.0 * 0.7
            }
            SynthInstrument.RETRO_8BIT -> {
                // Chiptune 50% square wave with slight vibrato
                val vibrato = 1.0 + 0.01 * sin(2.0 * PI * 6.0 * t)
                val p = (t * freq * vibrato) % 1.0
                if (p < 0.5) 0.5 else -0.5
            }
        }
    }

    /**
     * Live preview note trigger for touch keyboard feedback.
     */
    fun playNoteLive(
        noteNumber: Int,
        instrument: SynthInstrument,
        scope: CoroutineScope,
        durationMs: Long = 400L,
        midiProgram: Int = 0
    ) {
        val effectiveInstrument = if (midiProgram > 0) {
            com.example.model.GeneralMidiPatches.findPatch(midiProgram).synthFamily
        } else instrument

        scope.launch(Dispatchers.IO) {
            try {
                val freq = noteToFreq(noteNumber)
                val sampleCount = (durationMs * SAMPLE_RATE / 1000L).toInt()
                val buffer = ShortArray(sampleCount)

                for (i in 0 until sampleCount) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = (1.0 - (i.toDouble() / sampleCount)).coerceAtLeast(0.0)
                    val wave = generateInstrumentWave(effectiveInstrument, freq, t)
                    buffer[i] = (wave * env * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
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
