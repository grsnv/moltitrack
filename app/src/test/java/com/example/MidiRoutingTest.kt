package com.example

import com.example.audio.MidiMessageParser
import com.example.audio.MidiTimelineHelper
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
import org.junit.Test

class MidiRoutingTest {

    @Test
    fun testMidiMessageParserAllChannelParameters() {
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
        assertArrayEquals(byteArrayOf(0xB8.toByte(), 1.toByte(), 64.toByte()), ccEvt.toMidiBytes(channelOverride = 9))

        val bendEvt = RecordedMidiEvent(timestampMs = 200L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 9216)
        assertArrayEquals(byteArrayOf(0xE0.toByte(), 0.toByte(), 72.toByte()), bendEvt.toMidiBytes())

        val atEvt = RecordedMidiEvent(timestampMs = 300L, channel = 2, type = MidiEventType.CHANNEL_PRESSURE, data1 = 95)
        assertArrayEquals(byteArrayOf(0xD1.toByte(), 95.toByte()), atEvt.toMidiBytes())
    }

    @Test
    fun testTrackMatchesMidiInputAndResolvesSourceSynthOutput() {
        val omniTrack = TrackModel(
            id = "t1",
            name = "Omni MIDI",
            type = TrackType.MIDI,
            inputDeviceId = MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
            midiInputChannel = MidiRoutingConstants.CHANNEL_OMNI,
            midiOutputDeviceId = MidiRoutingConstants.SOURCE_SYNTH_ID,
            isArmed = true
        )
        assertTrue(omniTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 1))
        assertTrue(omniTrack.matchesMidiInput("midi_dev_7", 16))
        assertEquals(MidiRoutingConstants.ALL_MIDI_OUTPUTS_ID, omniTrack.resolveMidiOutputTarget())

        val specificSourceTrack = TrackModel(
            id = "t2",
            name = "HW Synth Ch 5",
            type = TrackType.MIDI,
            inputDeviceId = "midi_dev_7",
            midiInputChannel = 5,
            midiOutputDeviceId = MidiRoutingConstants.SOURCE_SYNTH_ID,
            isArmed = true
        )
        assertTrue(specificSourceTrack.matchesMidiInput("midi_dev_7", 5))
        assertFalse(specificSourceTrack.matchesMidiInput("midi_dev_7", 4))
        assertFalse(specificSourceTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 5))
        assertEquals("midi_dev_7", specificSourceTrack.resolveMidiOutputTarget())

        val disarmedTrack = specificSourceTrack.copy(isArmed = false)
        assertFalse(disarmedTrack.matchesMidiInput("midi_dev_7", 5))

        val audioTrack = TrackModel(id = "a1", name = "Audio", type = TrackType.AUDIO, isArmed = true)
        assertFalse(audioTrack.matchesMidiInput(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, 1))
    }

    @Test
    fun testVirtualMidiRouterDispatchesAllParametersAndOutHook() {
        val received = mutableListOf<MidiActivityEvent>()
        val outSent = mutableListOf<ByteArray>()
        val listener: (MidiActivityEvent) -> Unit = { received.add(it) }
        VirtualMidiRouter.addListener(listener)
        VirtualMidiRouter.setVirtualOutSender { outSent.add(it) }
        try {
            val bytes = byteArrayOf(
                0xB1.toByte(), 1.toByte(), 99.toByte(),
                0xE1.toByte(), 0.toByte(), 64.toByte(),
                0x91.toByte(), 60.toByte(), 100.toByte()
            )
            VirtualMidiRouter.dispatchBytes(bytes, 0, bytes.size)
            assertEquals(3, received.size)
            assertEquals(MidiEventType.CONTROL_CHANGE, received[0].type)
            assertEquals(MidiEventType.PITCH_BEND, received[1].type)
            assertEquals(MidiEventType.NOTE_ON, received[2].type)

            VirtualMidiRouter.sendToVirtualOut(byteArrayOf(0x90.toByte(), 60.toByte(), 100.toByte()))
            assertEquals(1, outSent.size)
        } finally {
            VirtualMidiRouter.setVirtualOutSender(null)
            VirtualMidiRouter.removeListener(listener)
        }
    }

    @Test
    fun testMidiTimelineHelperComputesDurationAndActivity() {
        val notes = listOf(MidiNote(noteNumber = 60, startMs = 0L, durationMs = 400L, velocity = 100))
        val events = listOf(
            RecordedMidiEvent(timestampMs = 1500L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 9216)
        )
        assertEquals(1750L, MidiTimelineHelper.computeMidiDurationMs(notes, events))
        val points = MidiTimelineHelper.extractActivityPoints(notes, events, targetPoints = 32)
        assertEquals(32, points.size)
        assertTrue(points.all { it in 0.05f..1.0f })
    }
}
