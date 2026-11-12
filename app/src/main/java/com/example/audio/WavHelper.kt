package com.example.audio

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object WavHelper {

    /**
     * Writes standard 44-byte WAV header for 16-bit PCM audio.
     */
    fun writeWavHeader(
        out: FileOutputStream,
        channels: Int,
        sampleRate: Int,
        totalAudioLen: Long
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2 // 16-bit = 2 bytes per sample

        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put("RIFF".toByteArray())
        buffer.putInt(totalDataLen.toInt())
        buffer.put("WAVE".toByteArray())

        // fmt chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // SubChunk1Size (16 for PCM)
        buffer.putShort(1.toShort()) // AudioFormat (1 for PCM)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort()) // BlockAlign
        buffer.putShort(16.toShort()) // BitsPerSample

        // data chunk
        buffer.put("data".toByteArray())
        buffer.putInt(totalAudioLen.toInt())

        out.write(header, 0, 44)
    }

    /**
     * Updates the RIFF and data chunk sizes in an existing WAV file after recording completes.
     */
    fun updateWavHeader(file: File, channels: Int, sampleRate: Int) {
        val totalAudioLen = file.length() - 44
        if (totalAudioLen < 0) return
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2

        val raf = RandomAccessFile(file, "rw")
        try {
            raf.seek(4) // totalDataLen
            val b4 = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalDataLen.toInt()).array()
            raf.write(b4)

            raf.seek(22) // channels
            val bChan = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(channels.toShort()).array()
            raf.write(bChan)

            raf.seek(24) // sampleRate
            val bRate = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate).array()
            raf.write(bRate)

            raf.seek(28) // byteRate
            val bByteRate = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate).array()
            raf.write(bByteRate)

            raf.seek(32) // blockAlign
            val bAlign = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((channels * 2).toShort()).array()
            raf.write(bAlign)

            raf.seek(40) // totalAudioLen
            val bAudioLen = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalAudioLen.toInt()).array()
            raf.write(bAudioLen)
        } finally {
            raf.close()
        }
    }

    /**
     * Extracts normalized peak values (0.0 to 1.0) from a 16-bit PCM WAV file for UI waveform rendering.
     */
    fun extractWaveform(file: File, targetPoints: Int = 120): List<Float> {
        if (!file.exists() || file.length() <= 44) return emptyList()

        try {
            val totalBytes = file.length() - 44
            val totalSamples = (totalBytes / 2).toInt() // 16-bit PCM
            if (totalSamples <= 0) return emptyList()

            val step = max(1, totalSamples / targetPoints)
            val peaks = FloatArray(targetPoints)

            val fis = FileInputStream(file)
            fis.skip(44) // skip header

            val buffer = ByteArray(4096)
            var sampleIdx = 0
            var currentPeak = 0
            var pointIdx = 0

            var bytesRead = fis.read(buffer)
            while (bytesRead > 0 && pointIdx < targetPoints) {
                var i = 0
                while (i < bytesRead - 1) {
                    val low = buffer[i].toInt() and 0xFF
                    val high = buffer[i + 1].toInt()
                    val sample = ((high shl 8) or low).toShort()
                    val absVal = abs(sample.toInt())
                    if (absVal > currentPeak) {
                        currentPeak = absVal
                    }
                    sampleIdx++
                    if (sampleIdx % step == 0 && pointIdx < targetPoints) {
                        peaks[pointIdx] = (currentPeak / 32767f).coerceIn(0.05f, 1.0f)
                        currentPeak = 0
                        pointIdx++
                    }
                    i += 2
                }
                bytesRead = fis.read(buffer)
            }
            fis.close()

            // Fill any remaining points if file ended early
            while (pointIdx < targetPoints) {
                peaks[pointIdx] = (currentPeak / 32767f).coerceIn(0.05f, 1.0f)
                pointIdx++
            }

            return peaks.toList()
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    /**
     * Mixes down multiple mono/stereo WAV tracks into a single master Stereo 16-bit PCM WAV file.
     * Takes into account track volume faders, mute/solo flags, and stereo panning.
     */
    fun mixTracksToMaster(
        trackFilesWithConfig: List<TrackMixConfig>,
        outputFile: File,
        sampleRate: Int = 44100
    ): Boolean {
        if (trackFilesWithConfig.isEmpty()) return false

        // Check solo: if any track is soloed, only include soloed tracks
        val hasSolo = trackFilesWithConfig.any { it.isSoloed }
        val activeTracks = trackFilesWithConfig.filter {
            if (hasSolo) it.isSoloed else !it.isMuted
        }
        if (activeTracks.isEmpty()) return false

        val streams = mutableListOf<FileInputStream>()
        try {
            for (track in activeTracks) {
                val f = File(track.filePath)
                if (f.exists() && f.length() > 44) {
                    val s = FileInputStream(f)
                    s.skip(44) // skip header
                    streams.add(s)
                } else {
                    return false
                }
            }

            val outStream = FileOutputStream(outputFile)
            writeWavHeader(outStream, 2, sampleRate, 0L) // Start with 0 length, updated later

            val inBuffer = ByteArray(2048)
            val mixedOut = ByteArray(4096) // Stereo 16-bit
            var anyDataLeft = true
            var totalWrittenBytes = 0L

            while (anyDataLeft) {
                anyDataLeft = false
                val sampleCount = 1024 // 1024 mono samples per chunk
                val accumLeft = FloatArray(sampleCount)
                val accumRight = FloatArray(sampleCount)

                for (idx in activeTracks.indices) {
                    val stream = streams[idx]
                    val track = activeTracks[idx]
                    val bytesRead = stream.read(inBuffer)
                    if (bytesRead > 0) {
                        anyDataLeft = true
                        val samplesRead = bytesRead / 2

                        // Pan: -1 (full left) to +1 (full right)
                        // panLeft = cos((pan+1)*PI/4), panRight = sin((pan+1)*PI/4) or linear:
                        val leftGain = track.volume * (1.0f - min(1.0f, max(0.0f, track.pan)))
                        val rightGain = track.volume * (1.0f + min(0.0f, max(-1.0f, track.pan)))

                        var b = 0
                        for (s in 0 until samplesRead) {
                            val low = inBuffer[b].toInt() and 0xFF
                            val high = inBuffer[b + 1].toInt()
                            val sample = ((high shl 8) or low).toShort()
                            accumLeft[s] += sample * leftGain
                            accumRight[s] += sample * rightGain
                            b += 2
                        }
                    }
                }

                if (anyDataLeft) {
                    var outByteIdx = 0
                    for (s in 0 until sampleCount) {
                        // Soft clipping / limiter to prevent harsh overflow
                        val leftClamped = accumLeft[s].coerceIn(-32768f, 32767f).toInt()
                        val rightClamped = accumRight[s].coerceIn(-32768f, 32767f).toInt()

                        mixedOut[outByteIdx++] = (leftClamped and 0xFF).toByte()
                        mixedOut[outByteIdx++] = ((leftClamped shr 8) and 0xFF).toByte()
                        mixedOut[outByteIdx++] = (rightClamped and 0xFF).toByte()
                        mixedOut[outByteIdx++] = ((rightClamped shr 8) and 0xFF).toByte()
                    }
                    outStream.write(mixedOut, 0, outByteIdx)
                    totalWrittenBytes += outByteIdx
                }
            }

            outStream.flush()
            outStream.close()
            for (s in streams) {
                s.close()
            }

            // Update WAV header with actual length
            updateWavHeader(outputFile, 2, sampleRate)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            for (s in streams) {
                try { s.close() } catch (_: Exception) {}
            }
            return false
        }
    }

    /**
     * Generates a sample multi-track audio demonstration WAV file with musical / synthetic tones
     * so that the user can explore mixer controls (faders, pan, solo/mute) even before plugging external hardware!
     */
    fun createSyntheticTrack(
        outputFile: File,
        frequencyHz: Float,
        durationSeconds: Int = 12,
        waveformType: String = "SINE",
        sampleRate: Int = 44100
    ) {
        val totalSamples = sampleRate * durationSeconds
        val totalAudioLen = totalSamples * 2L // 16-bit mono

        val out = FileOutputStream(outputFile)
        writeWavHeader(out, 1, sampleRate, totalAudioLen)

        val buffer = ByteArray(2048)
        var byteIdx = 0
        var phase = 0.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Gentle amplitude envelope (beat pulsing)
            val beatEnvelope = 0.6 + 0.4 * kotlin.math.sin(2.0 * Math.PI * 2.0 * t) // 120 BPM pulse

            val sampleValue = when (waveformType) {
                "SQUARE" -> {
                    val s = kotlin.math.sin(2.0 * Math.PI * frequencyHz * t)
                    if (s > 0) 0.4 else -0.4
                }
                "TRIANGLE" -> {
                    val p = (t * frequencyHz) % 1.0
                    (abs(p - 0.5) * 4.0 - 1.0) * 0.5
                }
                "DRUM_PULSE" -> {
                    val beatTime = t % 0.5 // kick every 0.5s
                    val kickFreq = max(40.0, 160.0 * (1.0 - beatTime * 3.0).coerceAtLeast(0.0))
                    val env = kotlin.math.exp(-beatTime * 8.0)
                    kotlin.math.sin(2.0 * Math.PI * kickFreq * beatTime) * env * 0.8
                }
                else -> { // SINE
                    kotlin.math.sin(2.0 * Math.PI * frequencyHz * t) * 0.5
                }
            }

            val finalSample = (sampleValue * beatEnvelope * 28000).toInt().coerceIn(-32768, 32767)

            buffer[byteIdx++] = (finalSample and 0xFF).toByte()
            buffer[byteIdx++] = ((finalSample shr 8) and 0xFF).toByte()

            if (byteIdx >= buffer.size) {
                out.write(buffer, 0, byteIdx)
                byteIdx = 0
            }
        }

        if (byteIdx > 0) {
            out.write(buffer, 0, byteIdx)
        }

        out.flush()
        out.close()
    }
}

data class TrackMixConfig(
    val filePath: String,
    val volume: Float,
    val pan: Float,
    val isMuted: Boolean,
    val isSoloed: Boolean
)
