package com.example

import com.example.model.AudioInputConstants
import com.example.model.AudioInputDevice
import com.example.model.AudioInputType
import com.example.model.InputChannelMode
import com.example.model.TrackModel
import com.example.model.TrackType
import com.example.model.requiresSystemAudioCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemAudioRoutingTest {

    @Test
    fun systemAudioDeviceDescriptorHasExpectedConfiguration() {
        val device = AudioInputDevice(
            id = AudioInputConstants.SYSTEM_AUDIO_ID,
            name = "System Sound (Internal Audio)",
            type = AudioInputType.SYSTEM_AUDIO,
            channelCount = 2,
            nativeDeviceInfo = null
        )
        assertEquals("system_audio_playback", device.id)
        assertEquals(AudioInputType.SYSTEM_AUDIO, device.type)
        assertEquals(2, device.channelCount)
    }

    @Test
    fun requiresSystemAudioCaptureOnlyMatchesArmedAudioTracksRoutedToSystemSound() {
        val micTrack = TrackModel(
            id = "t1",
            name = "Mic Track",
            type = TrackType.AUDIO,
            inputDeviceId = AudioInputConstants.DEFAULT_MIC_ID,
            inputChannelMode = InputChannelMode.LEFT_OR_CH1,
            isArmed = true
        )
        val armedSystemTrack = TrackModel(
            id = "t2",
            name = "System Audio Track",
            type = TrackType.AUDIO,
            inputDeviceId = AudioInputConstants.SYSTEM_AUDIO_ID,
            inputChannelMode = InputChannelMode.STEREO,
            isArmed = true
        )
        val disarmedSystemTrack = armedSystemTrack.copy(id = "t3", isArmed = false)
        val midiTrack = TrackModel(
            id = "t4",
            name = "MIDI Track",
            type = TrackType.MIDI,
            inputDeviceId = AudioInputConstants.SYSTEM_AUDIO_ID,
            isArmed = true
        )

        assertFalse(micTrack.isSystemAudioInput())
        assertTrue(armedSystemTrack.isSystemAudioInput())
        assertFalse(midiTrack.isSystemAudioInput())

        assertFalse(listOf(micTrack, disarmedSystemTrack, midiTrack).requiresSystemAudioCapture())
        assertTrue(listOf(micTrack, armedSystemTrack).requiresSystemAudioCapture())
    }
}
