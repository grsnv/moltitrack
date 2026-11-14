package com.example.model

import android.media.AudioDeviceInfo
import androidx.compose.ui.graphics.Color
import com.example.audio.MidiCcNames
import com.example.ui.theme.TrackColors
import java.util.UUID

enum class AudioInputType {
    BUILTIN_MIC,
    USB_AUDIO,
    WIRED_HEADSET,
    BLUETOOTH,
    SYSTEM_AUDIO,
    DEFAULT
}

object AudioInputConstants {
    const val DEFAULT_MIC_ID = "default_mic"
    const val SYSTEM_AUDIO_ID = "system_audio_playback"
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

enum class MidiEventType(val shortLabel: String) {
    NOTE_ON("NOTE ON"),
    NOTE_OFF("NOTE OFF"),
    CONTROL_CHANGE("CC"),
    PROGRAM_CHANGE("PC"),
    PITCH_BEND("BEND"),
    CHANNEL_PRESSURE("AT"),
    POLY_PRESSURE("POLY AT")
}

data class MidiNote(
    val id: String = UUID.randomUUID().toString(),
    val noteNumber: Int = 60, // 60 = Middle C (C4)
    val startMs: Long = 0L,
    val durationMs: Long = 500L,
    val velocity: Int = 100, // 1..127
    val channel: Int = 1 // 1..16
) {
    val noteName: String
        get() {
            val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val octave = (noteNumber / 12) - 1
            val name = names[noteNumber.coerceIn(0, 127) % 12]
            return "$name$octave"
        }
}

data class RecordedMidiEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long,
    val channel: Int, // 1..16
    val type: MidiEventType,
    val data1: Int, // Note #, CC #, Program #, Pressure, or 14-bit Pitch Bend (0..16383)
    val data2: Int = 0 // Velocity, CC value, or Poly Pressure value
) {
    fun describe(): String = when (type) {
        MidiEventType.NOTE_ON -> "Note On #${data1} (vel $data2)"
        MidiEventType.NOTE_OFF -> "Note Off #${data1} (vel $data2)"
        MidiEventType.CONTROL_CHANGE -> "${MidiCcNames.labelFor(data1)} (CC $data1) = $data2"
        MidiEventType.PROGRAM_CHANGE -> "Program #$data1"
        MidiEventType.PITCH_BEND -> "Pitch Bend ${data1 - 8192}"
        MidiEventType.CHANNEL_PRESSURE -> "Aftertouch $data1"
        MidiEventType.POLY_PRESSURE -> "Poly AT #$data1 = $data2"
    }

    fun toMidiBytes(channelOverride: Int = 0): ByteArray {
        val effectiveCh = if (channelOverride in 1..16) channelOverride else channel
        val chNibble = (effectiveCh - 1).coerceIn(0, 15)
        return when (type) {
            MidiEventType.NOTE_ON -> byteArrayOf(
                (0x90 or chNibble).toByte(),
                (data1 and 0x7F).toByte(),
                data2.coerceIn(1, 127).toByte()
            )
            MidiEventType.NOTE_OFF -> byteArrayOf(
                (0x80 or chNibble).toByte(),
                (data1 and 0x7F).toByte(),
                (data2 and 0x7F).toByte()
            )
            MidiEventType.CONTROL_CHANGE -> byteArrayOf(
                (0xB0 or chNibble).toByte(),
                (data1 and 0x7F).toByte(),
                (data2 and 0x7F).toByte()
            )
            MidiEventType.PROGRAM_CHANGE -> byteArrayOf(
                (0xC0 or chNibble).toByte(),
                (data1 and 0x7F).toByte()
            )
            MidiEventType.PITCH_BEND -> {
                val clamped = data1.coerceIn(0, 16383)
                val lsb = clamped and 0x7F
                val msb = (clamped shr 7) and 0x7F
                byteArrayOf(
                    (0xE0 or chNibble).toByte(),
                    lsb.toByte(),
                    msb.toByte()
                )
            }
            MidiEventType.CHANNEL_PRESSURE -> byteArrayOf(
                (0xD0 or chNibble).toByte(),
                (data1 and 0x7F).toByte()
            )
            MidiEventType.POLY_PRESSURE -> byteArrayOf(
                (0xA0 or chNibble).toByte(),
                (data1 and 0x7F).toByte(),
                (data2 and 0x7F).toByte()
            )
        }
    }
}

object MidiRoutingConstants {
    const val ALL_MIDI_INPUTS_ID = "all_midi"
    const val VIRTUAL_APP_MIDI_ID = "virtual_app_midi"
    const val LEGACY_USB_MIDI_ID = "usb_midi"
    const val SOURCE_SYNTH_ID = "source_synth" // Route playback/keys directly to the selected MIDI source's synth engine
    const val ALL_MIDI_OUTPUTS_ID = "all_midi_out"
    const val NO_MIDI_OUTPUT_ID = "none_midi_out"
    const val CHANNEL_OMNI = 0 // 0 = All / Preserve Original MIDI Channels (1..16)
}

data class MidiActivityEvent(
    val sourceId: String,
    val sourceName: String,
    val channel: Int, // 1..16
    val type: MidiEventType,
    val data1: Int,
    val data2: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun summaryText(): String = when (type) {
        MidiEventType.NOTE_ON -> "Note On #$data1 (vel $data2)"
        MidiEventType.NOTE_OFF -> "Note Off #$data1"
        MidiEventType.CONTROL_CHANGE -> "${MidiCcNames.labelFor(data1)} = $data2"
        MidiEventType.PROGRAM_CHANGE -> "Program #$data1"
        MidiEventType.PITCH_BEND -> "Pitch Bend ${data1 - 8192}"
        MidiEventType.CHANNEL_PRESSURE -> "Aftertouch $data1"
        MidiEventType.POLY_PRESSURE -> "Poly AT #$data1 = $data2"
    }

    fun toRecordedEvent(timestampMs: Long): RecordedMidiEvent = RecordedMidiEvent(
        timestampMs = timestampMs,
        channel = channel,
        type = type,
        data1 = data1,
        data2 = data2
    )
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
    val midiEvents: List<RecordedMidiEvent> = emptyList(),
    val midiInputChannel: Int = MidiRoutingConstants.CHANNEL_OMNI, // 0 = Omni (All), 1..16 = specific input channel
    val midiOutputDeviceId: String = MidiRoutingConstants.SOURCE_SYNTH_ID,
    val midiChannel: Int = MidiRoutingConstants.CHANNEL_OMNI, // 0 = Preserve Input Ch, 1..16 = Force Output Ch
    val latestCcValues: Map<Int, Int> = emptyMap(),
    val latestProgram: Int? = null,
    val latestPitchBend: Int = 8192,
    val latestAftertouch: Int = 0
) {
    val color: Color get() = Color(colorHex.toULong())

    val midiInputChannelLabel: String
        get() = if (midiInputChannel == MidiRoutingConstants.CHANNEL_OMNI) "Omni (All)" else "Ch $midiInputChannel"

    val midiOutputChannelLabel: String
        get() = if (midiChannel == MidiRoutingConstants.CHANNEL_OMNI) "Orig Ch" else "Ch $midiChannel"

    fun resolveMidiOutputTarget(): String {
        return when (midiOutputDeviceId) {
            MidiRoutingConstants.SOURCE_SYNTH_ID -> {
                when (inputDeviceId) {
                    MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
                    MidiRoutingConstants.LEGACY_USB_MIDI_ID,
                    "",
                    "default_mic" -> MidiRoutingConstants.ALL_MIDI_OUTPUTS_ID
                    else -> inputDeviceId
                }
            }
            else -> midiOutputDeviceId
        }
    }

    fun isSystemAudioInput(): Boolean =
        type == TrackType.AUDIO && inputDeviceId == AudioInputConstants.SYSTEM_AUDIO_ID

    fun matchesMidiInput(sourceId: String, channel: Int): Boolean {
        if (type != TrackType.MIDI || !isArmed) return false
        val deviceMatches = when (inputDeviceId) {
            MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
            MidiRoutingConstants.LEGACY_USB_MIDI_ID,
            "",
            "default_mic" -> true
            else -> inputDeviceId == sourceId
        }
        val channelMatches = (midiInputChannel == MidiRoutingConstants.CHANNEL_OMNI) || (midiInputChannel == channel)
        return deviceMatches && channelMatches
    }
}

fun Collection<TrackModel>.requiresSystemAudioCapture(): Boolean =
    any { it.isArmed && it.isSystemAudioInput() }

data class RecordingSession(
    val id: String,
    val title: String,
    val sampleRate: Int = 44100,
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<TrackModel> = emptyList(),
    val durationMs: Long = 0L
)
