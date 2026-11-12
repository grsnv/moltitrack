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

enum class TrackType {
    AUDIO,
    MIDI
}

enum class SynthInstrument(val displayName: String, val category: String) {
    SYNTH_LEAD("Neon Saw Lead", "Lead"),
    ELECTRIC_PIANO("FM Electric Piano", "Keys"),
    BASS_SYNTH("Analog Sub Bass", "Bass"),
    PAD_STRINGS("Lush Warm Pad", "Pad"),
    RETRO_8BIT("Chiptune Square", "Retro")
}

data class MidiNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val noteNumber: Int = 60, // 60 = Middle C (C4)
    val startMs: Long = 0L,
    val durationMs: Long = 500L,
    val velocity: Int = 100 // 1..127
) {
    val noteName: String
        get() {
            val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = (noteNumber / 12) - 1
            val name = names[noteNumber % 12]
            return "$name$octave"
        }
}

data class TrackModel(
    val id: String,
    val name: String,
    val type: TrackType = TrackType.AUDIO,
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
    val liveMeterLevel: Float = 0.0f, // 0f to 1f for reactive VU meters
    val midiNotes: List<MidiNote> = emptyList(),
    val instrument: SynthInstrument = SynthInstrument.SYNTH_LEAD,
    val midiProgram: Int = 0, // 0 to 127 (General MIDI & Hardware Program Change)
    val midiBankMsb: Int = 0, // 0 to 127 (Bank Select CC 0)
    val midiBankLsb: Int = 0, // 0 to 127 (Bank Select CC 32)
    val midiChannel: Int = 1 // 1 to 16
) {
    val color: Color get() = Color(colorHex.toULong())
    val patchInfo: MidiPatchInfo get() = GeneralMidiPatches.findPatch(midiProgram)
}

data class RecordingSession(
    val id: String,
    val title: String,
    val sampleRate: Int = 44100,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<TrackModel> = emptyList(),
    val durationMs: Long = 0L
)
