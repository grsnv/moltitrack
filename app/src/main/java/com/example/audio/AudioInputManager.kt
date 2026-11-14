package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.model.AudioInputConstants
import com.example.model.AudioInputDevice
import com.example.model.AudioInputType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioInputManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _availableInputs = MutableStateFlow<List<AudioInputDevice>>(emptyList())
    val availableInputs: StateFlow<List<AudioInputDevice>> = _availableInputs.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshInputs()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshInputs()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshInputs()
    }

    fun refreshInputs() {
        val list = mutableListOf<AudioInputDevice>()

        // Always include default input option
        list.add(
            AudioInputDevice(
                id = AudioInputConstants.DEFAULT_MIC_ID,
                name = "Default Android Mic",
                type = AudioInputType.DEFAULT,
                channelCount = 2,
                nativeDeviceInfo = null
            )
        )

        // Internal System Sound capture (AudioPlaybackCapture API, Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(
                AudioInputDevice(
                    id = AudioInputConstants.SYSTEM_AUDIO_ID,
                    name = "System Sound (Internal Audio)",
                    type = AudioInputType.SYSTEM_AUDIO,
                    channelCount = 2,
                    nativeDeviceInfo = null
                )
            )
        }

        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            var usbCount = 0
            var builtInCount = 0

            for (dev in devices) {
                val (type, defaultName) = when (dev.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> {
                        builtInCount++
                        AudioInputType.BUILTIN_MIC to "Built-in Microphone $builtInCount"
                    }
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> {
                        usbCount++
                        val devName = if (dev.productName.isNotBlank()) dev.productName.toString() else "USB Audio Device $usbCount"
                        AudioInputType.USB_AUDIO to devName
                    }
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                        AudioInputType.WIRED_HEADSET to "External Wired Mic / Headset"
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> {
                        val btName = if (dev.productName.isNotBlank()) dev.productName.toString() else "Bluetooth Headset Mic"
                        AudioInputType.BLUETOOTH to btName
                    }
                    else -> continue
                }

                val displayName = if (dev.productName.isNotBlank()) dev.productName.toString() else defaultName
                // Channel count heuristic from channel counts or default to 2
                val maxChannels = dev.channelCounts.maxOrNull() ?: 2

                list.add(
                    AudioInputDevice(
                        id = "dev_${dev.id}",
                        name = displayName,
                        type = type,
                        channelCount = maxChannels,
                        nativeDeviceInfo = dev
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _availableInputs.value = list
    }

    fun findDevice(id: String): AudioInputDevice? {
        return _availableInputs.value.firstOrNull { it.id == id }
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
    }
}
