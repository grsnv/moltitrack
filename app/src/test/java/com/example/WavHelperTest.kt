package com.example

import com.example.audio.AsioBufferSize
import com.example.audio.TrackMixConfig
import com.example.audio.WavHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testWavHeaderFormat() {
        val testFile = tempFolder.newFile("test_header.wav")
        val out = FileOutputStream(testFile)

        // Write 44.1kHz 16-bit Mono header with 1000 bytes
        WavHelper.writeWavHeader(out, 1, 44100, 1000L)
        out.close()

        assertTrue(testFile.exists())
        assertEquals(44L, testFile.length())

        val bytes = testFile.readBytes()
        assertEquals("RIFF", String(bytes.sliceArray(0..3)))
        assertEquals("WAVE", String(bytes.sliceArray(8..11)))
        assertEquals("fmt ", String(bytes.sliceArray(12..15)))
        assertEquals("data", String(bytes.sliceArray(36..39)))

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buffer.getShort(22).toInt()) // Channels = 1
        assertEquals(44100, buffer.getInt(24)) // SampleRate = 44100
        assertEquals(16, buffer.getShort(34).toInt()) // BitsPerSample = 16
        assertEquals(1000, buffer.getInt(40)) // DataLen = 1000
    }

    @Test
    fun testSyntheticTrackGenerationAndEnvelope() {
        val toneFile = tempFolder.newFile("synth_track.wav")
        WavHelper.createSyntheticTrack(toneFile, 440f, durationSeconds = 1, waveformType = "SINE")

        assertTrue(toneFile.exists())
        assertTrue(toneFile.length() > 44L)

        val waveform = WavHelper.extractWaveform(toneFile, targetPoints = 50)
        assertEquals(50, waveform.size)
        // All points should be normalized between 0.05 and 1.0
        assertTrue(waveform.all { it in 0.05f..1.0f })
    }

    @Test
    fun testAsioLatencyCalculation() {
        // 256 samples @ 44.1kHz = 5.8ms
        val latency256 = AsioBufferSize.BUFFER_256.latencyMs(44100)
        assertTrue(latency256 in 5.7f..5.9f)

        // 128 samples @ 48kHz = 2.66ms
        val latency128 = AsioBufferSize.BUFFER_128.latencyMs(48000)
        assertTrue(latency128 in 2.6f..2.7f)
    }

    @Test
    fun testMidiNoteFrequencyAndRendering() {
        // A4 (note 69) should be 440 Hz
        val a4Freq = com.example.audio.MidiSynthEngine.noteToFreq(69)
        assertEquals(440.0, a4Freq, 0.01)

        // Middle C (note 60) should be ~261.63 Hz
        val c4Freq = com.example.audio.MidiSynthEngine.noteToFreq(60)
        assertEquals(261.63, c4Freq, 0.1)

        val midiNotes = listOf(
            com.example.model.MidiNote(noteNumber = 60, startMs = 0L, durationMs = 500L),
            com.example.model.MidiNote(noteNumber = 64, startMs = 500L, durationMs = 500L)
        )
        val renderedFile = tempFolder.newFile("test_midi_render.wav")
        val success = com.example.audio.MidiSynthEngine.renderMidiToWav(
            notes = midiNotes,
            outputFile = renderedFile,
            minDurationMs = 2000L
        )
        assertTrue(success)
        assertTrue(renderedFile.exists())
        assertTrue(renderedFile.length() > 44L)
    }
}
