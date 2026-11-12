package com.example.audio

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbMidiDeviceItem(
    val id: Int,
    val name: String,
    val manufacturer: String,
    val inputPortCount: Int,
    val outputPortCount: Int
)

class AndroidMidiManager(
    private val context: Context,
    private val onMidiNoteReceived: (noteNumber: Int, velocity: Int, isNoteOn: Boolean) -> Unit
) {
    private val midiManager: MidiManager? =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private val _connectedMidiDevices = MutableStateFlow<List<UsbMidiDeviceItem>>(emptyList())
    val connectedMidiDevices: StateFlow<List<UsbMidiDeviceItem>> = _connectedMidiDevices.asStateFlow()

    private val openDevices = mutableListOf<MidiDevice>()
    private val openOutputPorts = mutableListOf<MidiOutputPort>()

    private val midiCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo?) {
            scanMidiDevices()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo?) {
            scanMidiDevices()
        }
    }

    init {
        midiManager?.registerDeviceCallback(midiCallback, Handler(Looper.getMainLooper()))
        scanMidiDevices()
    }

    fun scanMidiDevices() {
        val manager = midiManager ?: return
        val list = mutableListOf<UsbMidiDeviceItem>()

        try {
            val devices = manager.devices
            for (info in devices) {
                val props = info.properties
                val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
                    ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                    ?: "USB MIDI Device #${info.id}"
                val mfg = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Generic MIDI"

                list.add(
                    UsbMidiDeviceItem(
                        id = info.id,
                        name = name,
                        manufacturer = mfg,
                        inputPortCount = info.inputPortCount,
                        outputPortCount = info.outputPortCount
                    )
                )

                // Open device output port to listen to Note On / Note Off
                if (info.outputPortCount > 0) {
                    manager.openDevice(info, { device ->
                        if (device != null) {
                            openDevices.add(device)
                            val port = device.openOutputPort(0)
                            if (port != null) {
                                openOutputPorts.add(port)
                                port.connect(object : MidiReceiver() {
                                    override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
                                        if (msg == null || count < 3) return
                                        val status = msg[offset].toInt() and 0xFF
                                        val note = msg[offset + 1].toInt() and 0xFF
                                        val velocity = msg[offset + 2].toInt() and 0xFF

                                        val command = status and 0xF0
                                        if (command == 0x90 && velocity > 0) {
                                            // Note On
                                            onMidiNoteReceived(note, velocity, true)
                                        } else if (command == 0x80 || (command == 0x90 && velocity == 0)) {
                                            // Note Off
                                            onMidiNoteReceived(note, velocity, false)
                                        }
                                    }
                                })
                            }
                        }
                    }, Handler(Looper.getMainLooper()))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _connectedMidiDevices.value = list
    }

    fun release() {
        try {
            midiManager?.unregisterDeviceCallback(midiCallback)
        } catch (_: Exception) {}
        for (port in openOutputPorts) {
            try { port.close() } catch (_: Exception) {}
        }
        for (dev in openDevices) {
            try { dev.close() } catch (_: Exception) {}
        }
    }
}
