package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.TrackModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MultiTrackPlayerEngine(
    private val scope: CoroutineScope
) {
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val isPlaying = AtomicBoolean(false)
    private var playbackJob: Job? = null

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _masterMeterLevel = MutableStateFlow(Pair(0f, 0f)) // Left, Right (0..1)
    val masterMeterLevel: StateFlow<Pair<Float, Float>> = _masterMeterLevel.asStateFlow()

    private val _trackPlaybackMeters = MutableStateFlow<Map<String, Float>>(emptyMap())
    val trackPlaybackMeters: StateFlow<Map<String, Float>> = _trackPlaybackMeters.asStateFlow()

    var isLooping: Boolean = false

    // Target seek offset in bytes from start of audio (after 44-byte header)
    private var pendingSeekBytes: Long? = null

    fun play(tracks: List<TrackModel>, startPositionMs: Long = 0L) {
        if (isPlaying.get()) {
            stop()
        }

        val playableTracks = tracks.filter { it.audioFilePath != null && File(it.audioFilePath).exists() }
        if (playableTracks.isEmpty()) return

        // Calculate max duration across tracks
        var maxDur = 0L
        for (t in playableTracks) {
            val f = File(t.audioFilePath!!)
            val audioBytes = max(0L, f.length() - 44)
            // 44100 samples/sec, 16-bit = 2 bytes per sample (mono) or 4 bytes (stereo)
            val dur = (audioBytes * 1000L) / (sampleRate * 2L)
            if (dur > maxDur) maxDur = dur
        }
        _totalDurationMs.value = maxDur

        isPlaying.set(true)
        _isPlayingFlow.value = true

        playbackJob = scope.launch(Dispatchers.IO) {
            var audioTrack: AudioTrack? = null
            val fileStreams = mutableMapOf<String, FileInputStream>()

            try {
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    channelConfig,
                    audioFormat
                )
                val bufferSize = max(minBufferSize * 2, 8192)

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack.play()

                val startByte = (startPositionMs * sampleRate * 2L) / 1000L
                openStreams(playableTracks, fileStreams, startByte)

                var currentByteOffset = startByte
                val chunkSamples = 1024
                val inBytes = ByteArray(chunkSamples * 2) // mono 16-bit
                val outStereoBytes = ByteArray(chunkSamples * 4) // stereo 16-bit

                while (isActive && isPlaying.get()) {
                    // Check seek
                    val targetSeek = pendingSeekBytes
                    if (targetSeek != null) {
                        pendingSeekBytes = null
                        currentByteOffset = targetSeek
                        openStreams(playableTracks, fileStreams, targetSeek)
                        audioTrack.flush()
                    }

                    // Check solo condition
                    val hasSolo = playableTracks.any { it.isSoloed }

                    val accumLeft = FloatArray(chunkSamples)
                    val accumRight = FloatArray(chunkSamples)
                    val currentTrackMeters = mutableMapOf<String, Float>()
                    var anyDataRead = false

                    for (track in playableTracks) {
                        val stream = fileStreams[track.id] ?: continue
                        val isTrackActive = if (hasSolo) track.isSoloed else !track.isMuted
                        val bytesRead = stream.read(inBytes)

                        if (bytesRead > 0) {
                            anyDataRead = true
                            val samplesRead = bytesRead / 2
                            var trackPeak = 0
                            var trackSumSquare = 0.0

                            val leftGain = if (isTrackActive) track.volume * (1.0f - min(1.0f, max(0.0f, track.pan))) else 0f
                            val rightGain = if (isTrackActive) track.volume * (1.0f + min(0.0f, max(-1.0f, track.pan))) else 0f

                            var b = 0
                            for (s in 0 until samplesRead) {
                                val low = inBytes[b].toInt() and 0xFF
                                val high = inBytes[b + 1].toInt()
                                val sample = ((high shl 8) or low).toShort()
                                val absVal = abs(sample.toInt())
                                if (absVal > trackPeak) trackPeak = absVal
                                trackSumSquare += sample.toDouble() * sample

                                accumLeft[s] += sample * leftGain
                                accumRight[s] += sample * rightGain
                                b += 2
                            }

                            if (isTrackActive && samplesRead > 0) {
                                val trackRms = sqrt(trackSumSquare / samplesRead)
                                currentTrackMeters[track.id] = (trackRms / 8500.0).toFloat().coerceIn(0f, 1f)
                            } else {
                                currentTrackMeters[track.id] = 0f
                            }
                        }
                    }

                    if (!anyDataRead) {
                        if (isLooping) {
                            currentByteOffset = 0L
                            openStreams(playableTracks, fileStreams, 0L)
                            continue
                        } else {
                            break
                        }
                    }

                    // Write stereo mixed buffer to AudioTrack
                    var outIdx = 0
                    var masterLeftPeak = 0f
                    var masterRightPeak = 0f

                    for (s in 0 until chunkSamples) {
                        val leftSample = accumLeft[s].coerceIn(-32768f, 32767f).toInt()
                        val rightSample = accumRight[s].coerceIn(-32768f, 32767f).toInt()

                        val absL = abs(leftSample)
                        val absR = abs(rightSample)
                        if (absL > masterLeftPeak) masterLeftPeak = absL.toFloat()
                        if (absR > masterRightPeak) masterRightPeak = absR.toFloat()

                        outStereoBytes[outIdx++] = (leftSample and 0xFF).toByte()
                        outStereoBytes[outIdx++] = ((leftSample shr 8) and 0xFF).toByte()
                        outStereoBytes[outIdx++] = (rightSample and 0xFF).toByte()
                        outStereoBytes[outIdx++] = ((rightSample shr 8) and 0xFF).toByte()
                    }

                    audioTrack.write(outStereoBytes, 0, outIdx)
                    currentByteOffset += chunkSamples * 2L
                    _currentPositionMs.value = (currentByteOffset * 1000L) / (sampleRate * 2L)

                    _masterMeterLevel.value = Pair(
                        (masterLeftPeak / 32768f).coerceIn(0f, 1f),
                        (masterRightPeak / 32768f).coerceIn(0f, 1f)
                    )
                    _trackPlaybackMeters.value = currentTrackMeters
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                closeStreams(fileStreams)
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                } catch (_: Exception) {}

                isPlaying.set(false)
                _isPlayingFlow.value = false
                _masterMeterLevel.value = Pair(0f, 0f)
                _trackPlaybackMeters.value = emptyMap()
            }
        }
    }

    private fun openStreams(
        tracks: List<TrackModel>,
        streams: MutableMap<String, FileInputStream>,
        offsetBytes: Long
    ) {
        closeStreams(streams)
        for (track in tracks) {
            val path = track.audioFilePath ?: continue
            val f = File(path)
            if (f.exists() && f.length() > 44) {
                val fis = FileInputStream(f)
                val skipTarget = 44L + min(offsetBytes, max(0L, f.length() - 44))
                fis.skip(skipTarget)
                streams[track.id] = fis
            }
        }
    }

    private fun closeStreams(streams: MutableMap<String, FileInputStream>) {
        for ((_, s) in streams) {
            try { s.close() } catch (_: Exception) {}
        }
        streams.clear()
    }

    fun seekTo(positionMs: Long) {
        val targetByte = (positionMs * sampleRate * 2L) / 1000L
        pendingSeekBytes = targetByte
        _currentPositionMs.value = positionMs
    }

    fun stop() {
        isPlaying.set(false)
        _isPlayingFlow.value = false
        playbackJob?.cancel()
        _currentPositionMs.value = 0L
        _masterMeterLevel.value = Pair(0f, 0f)
        _trackPlaybackMeters.value = emptyMap()
    }

    fun pause() {
        isPlaying.set(false)
        _isPlayingFlow.value = false
        playbackJob?.cancel()
        _masterMeterLevel.value = Pair(0f, 0f)
        _trackPlaybackMeters.value = emptyMap()
    }
}
