package com.example

import com.example.audio.MidiMessageParser
import com.example.audio.MidiSynthEngine
import com.example.audio.ParsedMidiMessage
import com.example.audio.VirtualMidiRouter
import com.example.model.GeneralMidiPatches
import com.example.model.MidiActivityEvent
import com.example.model.MidiNote
import com.example.model.MidiRoutingConstants
import com.example.model.TrackModel
import com.example.model.TrackType
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
    fun testMidiMessageParserChannelsAndMultiMessages() {
        // Packet: Program Change on Ch 3 (0xC2, 40), Note On Ch 10 (0x99, 36, 110), Note Off Ch 10 (0x99, 36, 0)
        val packet = byteArrayOf(
            0xC2.toByte(), 40.toByte(),
            0xF8.toByte(), // Real-time clock interspersed
            0x99.toByte(), 36.toByte(), 110.toByte(),
            0x99.toByte(), 36.toByte(), 0.toByte()
        )

        val parsed = MidiMessageParser.parse(packet, 0, packet.size)
        assertEquals(3, parsed.size)
        assertEquals(ParsedMidiMessage.ProgramChange(channel = 3, program = 40), parsed[0])
        assertEquals(ParsedMidiMessage.NoteOn(channel = 10, note = 36, velocity = 110), parsed[1])
        assertEquals(ParsedMidiMessage.NoteOff(channel = 10, note = 36, velocity = 0), parsed[2])
    }

    @Test
    fun testTrackMatchesMidiInputSourceAndChannel() {
        val omniTrack = TrackModel(
            id = "t1",
            name = "Omni Synth",
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
    fun testVirtualMidiRouterDispatchesEvents() {
        val received = mutableListOf<MidiActivityEvent>()
        val listener: (MidiActivityEvent) -> Unit = { received.add(it) }
        VirtualMidiRouter.addListener(listener)
        try {
            val bytes = byteArrayOf(0x91.toByte(), 60.toByte(), 100.toByte()) // Ch 2 Note On C4
            VirtualMidiRouter.dispatchBytes(bytes, 0, bytes.size)
            assertEquals(1, received.size)
            assertEquals(MidiRoutingConstants.VIRTUAL_APP_MIDI_ID, received[0].sourceId)
            assertEquals(2, received[0].channel)
            assertEquals(60, received[0].noteNumber)
            assertTrue(received[0].isNoteOn)
        } finally {
            VirtualMidiRouter.removeListener(listener)
        }
    }

    @Test
    fun testGeneralMidi128PatchesAndRender() {
        assertEquals(128, GeneralMidiPatches.allPatches.size)
        assertEquals("Acoustic Grand Piano", GeneralMidiPatches.findPatch(0).name)
        assertEquals("Lead 2 (Sawtooth)", GeneralMidiPatches.findPatch(81).name)

        val outFile = tempFolder.newFile("gm_patch_render.wav")
        val ok = MidiSynthEngine.renderMidiToWav(
            notes = listOf(MidiNote(noteNumber = 60, startMs = 0L, durationMs = 300L)),
            instrument = GeneralMidiPatches.findPatch(4).synthFamily,
            outputFile = outFile,
            minDurationMs = 1000L,
            midiProgram = 4
        )
        assertTrue(ok)
        assertTrue(outFile.length() > 44L)
    }
}
