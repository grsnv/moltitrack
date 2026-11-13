package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.MidiEventType
import com.example.model.RecordedMidiEvent
import com.example.model.TrackModel
import com.example.model.TrackType
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

    private var pendingSeekBytes: Long? = null
    private var activeStopCallback: (() -> Unit)? = null

    fun play(
        tracks: List<TrackModel>,
        startPositionMs: Long = 0L,
        onMidiEventReplay: ((TrackModel, RecordedMidiEvent) -> Unit)? = null,
        onPlaybackStopped: (() -> Unit)? = null
    ) {
        if (isPlaying.get()) {
            stop()
        }

        val playableAudioTracks = tracks.filter {
            it.type == TrackType.AUDIO && it.audioFilePath != null && File(it.audioFilePath).exists()
        }
        val midiTracksWithEvents = tracks.filter { it.type == TrackType.MIDI && it.midiEvents.isNotEmpty() }
        if (playableAudioTracks.isEmpty() && midiTracksWithEvents.isEmpty()) return

        var maxDur = 0L
        for (t in playableAudioTracks) {
            val f = File(t.audioFilePath!!)
            val audioBytes = max(0L, f.length() - 44)
            val dur = (audioBytes * 1000L) / (sampleRate * 2L)
            if (dur > maxDur) maxDur = dur
        }
        for (t in midiTracksWithEvents) {
            val lastMidiMs = MidiTimelineHelper.computeMidiDurationMs(t.midiNotes, t.midiEvents)
            if (lastMidiMs > maxDur) maxDur = lastMidiMs
        }
        _totalDurationMs.value = maxDur

        val sortedMidiByTrack: Map<String, List<RecordedMidiEvent>> =
            midiTracksWithEvents.associate { t -> t.id to t.midiEvents.sortedBy { it.timestampMs } }
        val midiCursorByTrack = mutableMapOf<String, Int>()
        resetMidiCursors(sortedMidiByTrack, midiCursorByTrack, startPositionMs)

        activeStopCallback = onPlaybackStopped
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
                openStreams(playableAudioTracks, fileStreams, startByte)

                var currentByteOffset = startByte
                val chunkSamples = 1024
                val inBytes = ByteArray(chunkSamples * 2)
                val outStereoBytes = ByteArray(chunkSamples * 4)

                while (isActive && isPlaying.get()) {
                    val targetSeek = pendingSeekBytes
                    if (targetSeek != null) {
                        pendingSeekBytes = null
                        currentByteOffset = targetSeek
                        val seekMs = (targetSeek * 1000L) / (sampleRate * 2L)
                        resetMidiCursors(sortedMidiByTrack, midiCursorByTrack, seekMs)
                        openStreams(playableAudioTracks, fileStreams, targetSeek)
                        audioTrack.flush()
                        onPlaybackStopped?.invoke()
                    }

                    val hasSolo = tracks.any { it.isSoloed }

                    val accumLeft = FloatArray(chunkSamples)
                    val accumRight = FloatArray(chunkSamples)
                    val currentTrackMeters = mutableMapOf<String, Float>()
                    var anyDataRead = false

                    for (track in playableAudioTracks) {
                        val stream = fileStreams[track.id] ?: continue
                        val isTrackActive = if (hasSolo) track.isSoloed else !track.isMuted
                        val bytesRead = stream.read(inBytes)

                        if (bytesRead > 0) {
                            anyDataRead = true
                            val samplesRead = bytesRead / 2
                            var trackSumSquare = 0.0

                            val leftGain = if (isTrackActive) track.volume * (1.0f - min(1.0f, max(0.0f, track.pan))) else 0f
                            val rightGain = if (isTrackActive) track.volume * (1.0f + min(1.0f, max(-1.0f, track.pan))) else 0f

                            var b = 0
                            for (s in 0 until samplesRead) {
                                val low = inBytes[b].toInt() and 0xFF
                                val high = inBytes[b + 1].toInt()
                                val sample = ((high shl 8) or low).toShort()
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

                    val currentTimeMs = (currentByteOffset * 1000L) / (sampleRate * 2L)
                    val nextByteOffset = currentByteOffset + chunkSamples * 2L
                    val nextTimeMs = (nextByteOffset * 1000L) / (sampleRate * 2L)

                    // Replay recorded MIDI events directly to each track's MIDI source synth engine
                    if (onMidiEventReplay != null && sortedMidiByTrack.isNotEmpty()) {
                        for (track in midiTracksWithEvents) {
                            val isTrackActive = if (hasSolo) track.isSoloed else !track.isMuted
                            val events = sortedMidiByTrack[track.id] ?: continue
                            var idx = midiCursorByTrack[track.id] ?: 0
                            var midiMeterPeak = 0f
                            while (idx < events.size && events[idx].timestampMs < nextTimeMs) {
                                val evt = events[idx]
                                if (isTrackActive && evt.timestampMs >= currentTimeMs - 30L) {
                                    onMidiEventReplay(track, evt)
                                    val pulse = when (evt.type) {
                                        MidiEventType.NOTE_ON -> (evt.data2 / 127f).coerceIn(0.3f, 1f)
                                        MidiEventType.NOTE_OFF -> 0.1f
                                        else -> 0.6f
                                    }
                                    if (pulse > midiMeterPeak) midiMeterPeak = pulse
                                }
                                idx++
                            }
                            if (midiMeterPeak > 0f) {
                                currentTrackMeters[track.id] = midiMeterPeak
                            }
                            midiCursorByTrack[track.id] = idx
                            if (idx < events.size || nextTimeMs <= maxDur) {
                                anyDataRead = true
                            }
                        }
                    }

                    if (!anyDataRead) {
                        if (isLooping) {
                            currentByteOffset = 0L
                            resetMidiCursors(sortedMidiByTrack, midiCursorByTrack, 0L)
                            openStreams(playableAudioTracks, fileStreams, 0L)
                            onPlaybackStopped?.invoke()
                            continue
                        } else {
                            break
                        }
                    }

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
                    currentByteOffset = nextByteOffset
                    _currentPositionMs.value = nextTimeMs

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
                activeStopCallback?.invoke()
            }
        }
    }

    private fun resetMidiCursors(
        sortedMidiByTrack: Map<String, List<RecordedMidiEvent>>,
        cursors: MutableMap<String, Int>,
        positionMs: Long
    ) {
        for ((trackId, events) in sortedMidiByTrack) {
            val idx = events.indexOfFirst { it.timestampMs >= positionMs }
            cursors[trackId] = if (idx >= 0) idx else events.size
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
        activeStopCallback?.invoke()
    }

    fun pause() {
        isPlaying.set(false)
        _isPlayingFlow.value = false
        playbackJob?.cancel()
        _masterMeterLevel.value = Pair(0f, 0f)
        _trackPlaybackMeters.value = emptyMap()
        activeStopCallback?.invoke()
    }
}
