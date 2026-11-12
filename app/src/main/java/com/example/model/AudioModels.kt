package com.example.model

import android.media.AudioDeviceInfo
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.TrackColors

enum class AudioInputType {
    BUILTIN_MIC,
    USB_AUDIO,
    WIRED_HEADSET,
    BLUETOOTH,
    DEFAULT
}

enum class InputChannelMode(val label: String, val shortLabel: String) {
    STEREO("Stereo (All Channels)", "Stereo"),
    LEFT_OR_CH1("Channel 1 (Left)", "Ch 1 / L"),
    RIGHT_OR_CH2("Channel 2 (Right)", "Ch 2 / R"),
    MONO_SUM("Mono Sum (L+R)", "Sum");

    companion object {
        fun fromName(name: String): InputChannelMode = entries.firstOrNull { it.name == name } ?: STEREO
    }
}

data class AudioInputDevice(
    val id: String,
    val name: String,
    val type: AudioInputType,
    val channelCount: Int = 2,
    val nativeDeviceInfo: AudioDeviceInfo? = null
)

data class TrackModel(
    val id: String,
    val name: String,
    val colorHex: Long = TrackColors[0].value.toLong(),
    val inputDeviceId: String = "default_mic",
    val inputChannelMode: InputChannelMode = InputChannelMode.LEFT_OR_CH1,
    val isArmed: Boolean = true,
    val volume: Float = 1.0f,
    val pan: Float = 0.0f, // -1.0 (Left) to +1.0 (Right)
    val isMuted: Boolean = false,
    val isSoloed: Boolean = false,
    val audioFilePath: String? = null,
    val waveformPoints: List<Float> = emptyList(),
    val durationMs: Long = 0L,
    val liveMeterLevel: Float = 0.0f // 0f to 1f for reactive VU meters
) {
    val color: Color get() = Color(colorHex.toULong())
}

data class RecordingSession(
    val id: String,
    val title: String,
    val sampleRate: Int = 44100,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<TrackModel> = emptyList(),
    val durationMs: Long = 0L
)
