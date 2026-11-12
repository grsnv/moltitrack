package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class UsbAudioDeviceInfo(
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val hasAudioClass: Boolean,
    val estimatedChannels: Int,
    val isAsio4AllCompatible: Boolean = true
) {
    val vendorProductHex: String
        get() = String.format(Locale.US, "%04X:%04X", vendorId, productId)
}

enum class AsioBufferSize(val samples: Int, val label: String) {
    BUFFER_64(64, "64 samples (Ultra-Low)"),
    BUFFER_128(128, "128 samples (Low Latency)"),
    BUFFER_256(256, "256 samples (Studio Default)"),
    BUFFER_512(512, "512 samples (High Stability)"),
    BUFFER_1024(1024, "1024 samples (Relaxed)");

    fun latencyMs(sampleRate: Int): Float {
        return (samples * 1000f) / sampleRate
    }
}

class UsbAudioInterfaceManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _connectedUsbAudio = MutableStateFlow<List<UsbAudioDeviceInfo>>(emptyList())
    val connectedUsbAudio: StateFlow<List<UsbAudioDeviceInfo>> = _connectedUsbAudio.asStateFlow()

    private val _selectedBufferSize = MutableStateFlow(AsioBufferSize.BUFFER_256)
    val selectedBufferSize: StateFlow<AsioBufferSize> = _selectedBufferSize.asStateFlow()

    private val _selectedSampleRate = MutableStateFlow(44100)
    val selectedSampleRate: StateFlow<Int> = _selectedSampleRate.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED ||
                action == UsbManager.ACTION_USB_DEVICE_DETACHED
            ) {
                scanUsbDevices()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        scanUsbDevices()
    }

    fun scanUsbDevices() {
        val manager = usbManager ?: return
        val list = mutableListOf<UsbAudioDeviceInfo>()

        for ((_, device) in manager.deviceList) {
            var isAudio = device.deviceClass == UsbConstants.USB_CLASS_AUDIO
            var channelEstimate = 2

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                    isAudio = true
                    // Many 4-channel interfaces report multiple endpoints
                    if (iface.endpointCount >= 2) {
                        channelEstimate = 4
                    }
                }
            }

            if (isAudio) {
                val prodName = device.productName?.takeIf { it.isNotBlank() } ?: "USB Audio Interface"
                val mfgName = device.manufacturerName?.takeIf { it.isNotBlank() } ?: "Generic UAC"
                list.add(
                    UsbAudioDeviceInfo(
                        deviceName = device.deviceName,
                        productName = prodName,
                        manufacturerName = mfgName,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        hasAudioClass = true,
                        estimatedChannels = channelEstimate,
                        isAsio4AllCompatible = true
                    )
                )
            }
        }

        _connectedUsbAudio.value = list
    }

    fun setBufferSize(size: AsioBufferSize) {
        _selectedBufferSize.value = size
    }

    fun setSampleRate(rate: Int) {
        _selectedSampleRate.value = rate
    }

    fun release() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
