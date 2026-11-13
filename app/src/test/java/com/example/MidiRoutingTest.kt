package com.example

import com.example.audio.MidiMessageParser
import com.example.audio.MidiSynthEngine
import com.example.audio.ParsedMidiMessage
import com.example.audio.VirtualMidiRouter
import com.example.model.MidiActivityEvent
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.MidiRoutingConstants
import com.example.model.RecordedMidiEvent
import com.example.model.TrackModel
import com.example.model.TrackType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MidiRoutingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMidiMessageParserAllChannelParameters() {
        // Multi-message stream:
        // 1. CC #74 = 96 on Ch 2 (0xB1, 74, 96)
        // 2. Program Change #12 on Ch 3 (0xC2, 12)
        // 3. Pitch Bend center+1024 -> 9216 = (72 << 7) | 0 -> lsb=0, msb=72 on Ch 4 (0xE3, 0, 72)
        // 4. Channel Aftertouch 88 on Ch 5 (0xD4, 88)
        // 5. Poly Key Pressure note 60 = 77 on Ch 6 (0xA5, 60, 77)
        // 6. Real-time Clock (0xF8)
        // 7. Note On note 36 vel 110 on Ch 10 (0x99, 36, 110)
        // 8. Note Off note 36 on Ch 10 via vel 0 (0x99, 36, 0)
        val packet = byteArrayOf(
            0xB1.toByte(), 74.toByte(), 96.toByte(),
            0xC2.toByte(), 12.toByte(),
            0xE3.toByte(), 0.toByte(), 72.toByte(),
            0xD4.toByte(), 88.toByte(),
            0xA5.toByte(), 60.toByte(), 77.toByte(),
            0xF8.toByte(),
            0x99.toByte(), 36.toByte(), 110.toByte(),
            0x99.toByte(), 36.toByte(), 0.toByte()
        )

        val parsed = MidiMessageParser.parse(packet, 0, packet.size)
        assertEquals(7, parsed.size)
        assertEquals(ParsedMidiMessage.ControlChange(channel = 2, controller = 74, value = 96), parsed[0])
        assertEquals(ParsedMidiMessage.ProgramChange(channel = 3, program = 12), parsed[1])
        assertEquals(ParsedMidiMessage.PitchBend(channel = 4, value14Bit = 9216), parsed[2])
        assertEquals(ParsedMidiMessage.ChannelPressure(channel = 5, pressure = 88), parsed[3])
        assertEquals(ParsedMidiMessage.PolyPressure(channel = 6, note = 60, pressure = 77), parsed[4])
        assertEquals(ParsedMidiMessage.NoteOn(channel = 10, note = 36, velocity = 110), parsed[5])
        assertEquals(ParsedMidiMessage.NoteOff(channel = 10, note = 36, velocity = 0), parsed[6])
    }

    @Test
    fun testRecordedMidiEventRoundTripBytes() {
        val ccEvt = RecordedMidiEvent(timestampMs = 100L, channel = 4, type = MidiEventType.CONTROL_CHANGE, data1 = 1, data2 = 64)
        assertArrayEquals(byteArrayOf(0xB3.toByte(), 1.toByte(), 64.toByte()), ccEvt.toMidiBytes())
        // Override output channel to Ch 9
        assertArrayEquals(byteArrayOf(0xB8.toByte(), 1.toByte(), 64.toByte()), ccEvt.toMidiBytes(channelOverride = 9))

        val bendEvt = RecordedMidiEvent(timestampMs = 200L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 9216)
        assertArrayEquals(byteArrayOf(0xE0.toByte(), 0.toByte(), 72.toByte()), bendEvt.toMidiBytes())

        val atEvt = RecordedMidiEvent(timestampMs = 300L, channel = 2, type = MidiEventType.CHANNEL_PRESSURE, data1 = 95)
        assertArrayEquals(byteArrayOf(0xD1.toByte(), 95.toByte()), atEvt.toMidiBytes())
    }

    @Test
    fun testTrackMatchesMidiInputSourceAndChannel() {
        val omniTrack = TrackModel(
            id = "t1",
            name = "Omni MIDI",
            type = TrackType.MIDI,
            inputDeviceId = MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
            midiInputChannel = MidiRoutingConstants.CHANNEL_OMNI,
            isArmed = true
        )
        assertTrue(omniTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 1))
        assertTrue(omniTrack.matchesMidiInput("midi_dev_7", 16))

        val ch5VirtualTrack = TrackModel(
            id = "t2",
            name = "App Ch 5",
            type = TrackType.MIDI,
            inputDeviceId = MidiRoutingConstants.VIRTUAL_APP_MIDI_ID,
            midiInputChannel = 5,
            isArmed = true
        )
        assertTrue(ch5VirtualTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 5))
        assertFalse(ch5VirtualTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 4))
        assertFalse(ch5VirtualTrack.matchesMidiInput("midi_dev_7", 5))

        val disarmedTrack = ch5VirtualTrack.copy(isArmed = false)
        assertFalse(disarmedTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 5))

        val audioTrack = TrackModel(id = "a1", name = "Audio", type = TrackType.AUDIO, isArmed = true)
        assertFalse(audioTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 1))
    }

    @Test
    fun testVirtualMidiRouterDispatchesAllParameters() {
        val received = mutableListOf<MidiActivityEvent>()
        val listener: (MidiActivityEvent) -> Unit = { received.add(it) }
        VirtualMidiRouter.addListener(listener)
        try {
            val bytes = byteArrayOf(
                0xB1.toByte(), 1.toByte(), 99.toByte(),   // Ch 2 Mod Wheel = 99
                0xE1.toByte(), 0.toByte(), 64.toByte(),   // Ch 2 Pitch Bend center (8192)
                0x91.toByte(), 60.toByte(), 100.toByte()  // Ch 2 Note On C4
            )
            VirtualMidiRouter.dispatchBytes(bytes, 0, bytes.size)
            assertEquals(3, received.size)
            assertEquals(MidiEventType.CONTROL_CHANGE, received[0].type)
            assertEquals(1, received[0].data1)
            assertEquals(99, received[0].data2)
            assertEquals(MidiEventType.PITCH_BEND, received[1].type)
            assertEquals(8192, received[1].data1)
            assertEquals(MidiEventType.NOTE_ON, received[2].type)
            assertEquals(60, received[2].data1)
            assertEquals(2, received[2].channel)
        } finally {
            VirtualMidiRouter.removeListener(listener)
        }
    }

    @Test
    fun testNeutralMidiReferenceRenderWithEvents() {
        val outFile = tempFolder.newFile("raw_midi_render.wav")
        val events = listOf(
            RecordedMidiEvent(timestampMs = 0L, channel = 1, type = MidiEventType.CONTROL_CHANGE, data1 = 7, data2 = 100),
            RecordedMidiEvent(timestampMs = 0L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 9216)
        )
        val ok = MidiSynthEngine.renderMidiToWav(
            notes = listOf(MidiNote(noteNumber = 60, startMs = 0L, durationMs = 300L)),
            outputFile = outFile,
            midiEvents = events,
            minDurationMs = 1000L
        )
        assertTrue(ok)
        assertTrue(outFile.length() > 44L)
    }
}
