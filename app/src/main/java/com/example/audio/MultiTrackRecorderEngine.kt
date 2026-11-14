package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import com.example.model.AudioInputConstants
import com.example.model.AudioInputDevice
import com.example.model.InputChannelMode
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
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class MultiTrackRecorderEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val sampleRate = 44100
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO

    private val isRecording = AtomicBoolean(false)
    private var recordingJob: Job? = null

    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _trackMeterLevels = MutableStateFlow<Map<String, Float>>(emptyMap())
    val trackMeterLevels: StateFlow<Map<String, Float>> = _trackMeterLevels.asStateFlow()

    private val activeAudioRecords = mutableListOf<AudioRecord>()
    private val activeStreams = mutableListOf<FileOutputStream>()

    @SuppressLint("MissingPermission")
    fun startRecording(
        tracksToRecord: List<TrackModel>,
        availableDevices: List<AudioInputDevice>,
        sessionDir: File,
        mediaProjection: MediaProjection? = null,
        onRecordingFinished: (Map<String, File>) -> Unit
    ): Boolean {
        if (isRecording.get()) return false
        val armedTracks = tracksToRecord.filter { it.isArmed }
        if (armedTracks.isEmpty()) return false
        val armedAudioTracks = armedTracks.filter { it.type == com.example.model.TrackType.AUDIO }

        sessionDir.mkdirs()
        isRecording.set(true)
        _isRecordingFlow.value = true
        _recordingDurationMs.value = 0L

        recordingJob = scope.launch(Dispatchers.IO) {
            val trackFiles = mutableMapOf<String, File>()
            val trackStreams = mutableMapOf<String, FileOutputStream>()
            val trackChannels = mutableMapOf<String, Int>() // 1 for mono, 2 for stereo
            val sharedMeters = ConcurrentHashMap<String, Float>()

            try {
                // Initialize WAV files for each armed AUDIO track (MIDI tracks capture raw MIDI events directly)
                for (track in armedAudioTracks) {
                    val file = File(sessionDir, "track_${track.id}_${System.currentTimeMillis()}.wav")
                    val out = FileOutputStream(file)
                    val channels = if (track.inputChannelMode == InputChannelMode.STEREO) 2 else 1
                    WavHelper.writeWavHeader(out, channels, sampleRate, 0L)
                    trackFiles[track.id] = file
                    trackStreams[track.id] = out
                    trackChannels[track.id] = channels
                    synchronized(activeStreams) {
                        activeStreams.add(out)
                    }
                }

                // Group armed audio tracks by input device (hardware device or system audio playback)
                val tracksByDevice = armedAudioTracks.groupBy { it.inputDeviceId }
                val deviceJobs = mutableListOf<Job>()
                val startTime = System.currentTimeMillis()

                // Timer ticker
                val timerJob = launch {
                    while (isActive && isRecording.get()) {
                        _recordingDurationMs.value = System.currentTimeMillis() - startTime
                        kotlinx.coroutines.delay(50)
                    }
                }

                for ((devId, devTracks) in tracksByDevice) {
                    if (devId == AudioInputConstants.SYSTEM_AUDIO_ID) {
                        val job = launch {
                            recordFromSystemAudio(
                                mediaProjection = mediaProjection,
                                tracks = devTracks,
                                streams = trackStreams,
                                sharedMeters = sharedMeters
                            )
                        }
                        deviceJobs.add(job)
                    } else {
                        val targetDevice = availableDevices.firstOrNull { it.id == devId }
                        val job = launch {
                            recordFromDevice(
                                device = targetDevice,
                                tracks = devTracks,
                                streams = trackStreams,
                                sharedMeters = sharedMeters
                            )
                        }
                        deviceJobs.add(job)
                    }
                }

                if (deviceJobs.isEmpty()) {
                    // MIDI-only recording: hold until stopRecording() is invoked
                    while (isActive && isRecording.get()) {
                        kotlinx.coroutines.delay(50)
                    }
                } else {
                    deviceJobs.forEach { it.join() }
                }
                timerJob.cancel()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Finalize all WAV headers
                for ((trackId, out) in trackStreams) {
                    try {
                        out.flush()
                        out.close()
                    } catch (_: Exception) {}

                    val file = trackFiles[trackId]
                    val channels = trackChannels[trackId] ?: 1
                    if (file != null && file.exists()) {
                        WavHelper.updateWavHeader(file, channels, sampleRate)
                    }
                }

                synchronized(activeStreams) {
                    activeStreams.clear()
                }
                synchronized(activeAudioRecords) {
                    activeAudioRecords.forEach {
                        try {
                            it.stop()
                            it.release()
                        } catch (_: Exception) {}
                    }
                    activeAudioRecords.clear()
                }

                isRecording.set(false)
                _isRecordingFlow.value = false
                _trackMeterLevels.value = emptyMap()

                scope.launch(Dispatchers.Main) {
                    onRecordingFinished(trackFiles)
                }
            }
        }

        return true
    }

    @SuppressLint("MissingPermission")
    private fun recordFromSystemAudio(
        mediaProjection: MediaProjection?,
        tracks: List<TrackModel>,
        streams: Map<String, FileOutputStream>,
        sharedMeters: ConcurrentHashMap<String, Float>
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        val bufferSize = max(minBufferSize * 2, 4096)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = createSystemAudioRecord(mediaProjection, bufferSize)
            synchronized(activeAudioRecords) {
                activeAudioRecords.add(audioRecord)
            }
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return
            }
            audioRecord.startRecording()
            processAudioRecordLoop(audioRecord, bufferSize, tracks, streams, sharedMeters)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun createSystemAudioRecord(
        mediaProjection: MediaProjection,
        bufferSize: Int
    ): AudioRecord {
        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .excludeUid(Process.myUid())
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun recordFromDevice(
        device: AudioInputDevice?,
        tracks: List<TrackModel>,
        streams: Map<String, FileOutputStream>,
        sharedMeters: ConcurrentHashMap<String, Float>
    ) {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        val bufferSize = max(minBufferSize * 2, 4096)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (device?.nativeDeviceInfo != null) {
                audioRecord.setPreferredDevice(device.nativeDeviceInfo)
            }

            synchronized(activeAudioRecords) {
                activeAudioRecords.add(audioRecord)
            }

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                return
            }

            audioRecord.startRecording()
            processAudioRecordLoop(audioRecord, bufferSize, tracks, streams, sharedMeters)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
        }
    }

    private fun processAudioRecordLoop(
        audioRecord: AudioRecord,
        bufferSize: Int,
        tracks: List<TrackModel>,
        streams: Map<String, FileOutputStream>,
        sharedMeters: ConcurrentHashMap<String, Float>
    ) {
        val rawBuffer = ShortArray(bufferSize / 2)
        val trackMonoBuffers = tracks.associate { it.id to ByteArray(rawBuffer.size) }
        val trackStereoBuffers = tracks.associate { it.id to ByteArray(rawBuffer.size * 2) }

        while (isRecording.get()) {
            val shortsRead = audioRecord.read(rawBuffer, 0, rawBuffer.size)
            if (shortsRead <= 0) continue

            // Total stereo frames read:
            val framesRead = shortsRead / 2

            for (track in tracks) {
                val stream = streams[track.id] ?: continue
                var sumSquare = 0.0
                var peak = 0

                when (track.inputChannelMode) {
                    InputChannelMode.LEFT_OR_CH1 -> {
                        val byteBuf = trackMonoBuffers[track.id] ?: continue
                        var byteIdx = 0
                        for (f in 0 until framesRead) {
                            val leftSample = rawBuffer[f * 2]
                            val absVal = abs(leftSample.toInt())
                            if (absVal > peak) peak = absVal
                            sumSquare += leftSample.toDouble() * leftSample

                            byteBuf[byteIdx++] = (leftSample.toInt() and 0xFF).toByte()
                            byteBuf[byteIdx++] = ((leftSample.toInt() shr 8) and 0xFF).toByte()
                        }
                        stream.write(byteBuf, 0, byteIdx)
                    }

                    InputChannelMode.RIGHT_OR_CH2 -> {
                        val byteBuf = trackMonoBuffers[track.id] ?: continue
                        var byteIdx = 0
                        for (f in 0 until framesRead) {
                            val rightSample = rawBuffer[f * 2 + 1]
                            val absVal = abs(rightSample.toInt())
                            if (absVal > peak) peak = absVal
                            sumSquare += rightSample.toDouble() * rightSample

                            byteBuf[byteIdx++] = (rightSample.toInt() and 0xFF).toByte()
                            byteBuf[byteIdx++] = ((rightSample.toInt() shr 8) and 0xFF).toByte()
                        }
                        stream.write(byteBuf, 0, byteIdx)
                    }

                    InputChannelMode.MONO_SUM -> {
                        val byteBuf = trackMonoBuffers[track.id] ?: continue
                        var byteIdx = 0
                        for (f in 0 until framesRead) {
                            val left = rawBuffer[f * 2].toInt()
                            val right = rawBuffer[f * 2 + 1].toInt()
                            val sumSample = ((left + right) / 2).coerceIn(-32768, 32767).toShort()
                            val absVal = abs(sumSample.toInt())
                            if (absVal > peak) peak = absVal
                            sumSquare += sumSample.toDouble() * sumSample

                            byteBuf[byteIdx++] = (sumSample.toInt() and 0xFF).toByte()
                            byteBuf[byteIdx++] = ((sumSample.toInt() shr 8) and 0xFF).toByte()
                        }
                        stream.write(byteBuf, 0, byteIdx)
                    }

                    InputChannelMode.STEREO -> {
                        val byteBuf = trackStereoBuffers[track.id] ?: continue
                        var byteIdx = 0
                        for (f in 0 until framesRead) {
                            val left = rawBuffer[f * 2]
                            val right = rawBuffer[f * 2 + 1]
                            val maxVal = max(abs(left.toInt()), abs(right.toInt()))
                            if (maxVal > peak) peak = maxVal
                            sumSquare += (left.toDouble() * left + right.toDouble() * right) / 2.0

                            byteBuf[byteIdx++] = (left.toInt() and 0xFF).toByte()
                            byteBuf[byteIdx++] = ((left.toInt() shr 8) and 0xFF).toByte()
                            byteBuf[byteIdx++] = (right.toInt() and 0xFF).toByte()
                            byteBuf[byteIdx++] = ((right.toInt() shr 8) and 0xFF).toByte()
                        }
                        stream.write(byteBuf, 0, byteIdx)
                    }
                }

                // Compute normalized VU meter (0f to 1f) with perceptual curve
                val rms = if (framesRead > 0) sqrt(sumSquare / framesRead) else 0.0
                val normalized = (rms / 9000.0).toFloat().coerceIn(0f, 1f)
                sharedMeters[track.id] = normalized
            }

            _trackMeterLevels.value = sharedMeters.toMap()
        }
    }

    fun stopRecording() {
        if (!isRecording.get()) return
        isRecording.set(false)
        recordingJob?.cancel()
    }
}
