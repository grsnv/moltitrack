package com.example.audio

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
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
    private val openInputPorts = mutableListOf<MidiInputPort>()

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
                // and open input port to send Program Change, Bank Select, and Notes to the hardware synth!
                if (info.outputPortCount > 0 || info.inputPortCount > 0) {
                    manager.openDevice(info, { device ->
                        if (device != null) {
                            openDevices.add(device)

                            if (info.outputPortCount > 0) {
                                val outPort = device.openOutputPort(0)
                                if (outPort != null) {
                                    openOutputPorts.add(outPort)
                                    outPort.connect(object : MidiReceiver() {
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

                            if (info.inputPortCount > 0) {
                                val inPort = device.openInputPort(0)
                                if (inPort != null) {
                                    synchronized(openInputPorts) {
                                        openInputPorts.add(inPort)
                                    }
                                }
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

    /**
     * Sends Bank Select MSB (CC 0), Bank Select LSB (CC 32), and Program Change (0xC0)
     * to switch the active patch in the connected hardware synthesizer!
     */
    fun sendProgramChange(channel: Int, program: Int, bankMsb: Int = 0, bankLsb: Int = 0) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msbMsg = byteArrayOf((0xB0 or chan).toByte(), 0x00.toByte(), (bankMsb and 0x7F).toByte())
        val lsbMsg = byteArrayOf((0xB0 or chan).toByte(), 0x20.toByte(), (bankLsb and 0x7F).toByte())
        val pcMsg = byteArrayOf((0xC0 or chan).toByte(), (program and 0x7F).toByte())

        synchronized(openInputPorts) {
            for (port in openInputPorts) {
                try {
                    port.send(msbMsg, 0, msbMsg.size)
                    port.send(lsbMsg, 0, lsbMsg.size)
                    port.send(pcMsg, 0, pcMsg.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Sends MIDI Note On to connected hardware synthesizer.
     */
    fun sendNoteOn(channel: Int, note: Int, velocity: Int = 100) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msg = byteArrayOf(
            (0x90 or chan).toByte(),
            (note and 0x7F).toByte(),
            (velocity.coerceIn(1, 127)).toByte()
        )
        synchronized(openInputPorts) {
            for (port in openInputPorts) {
                try {
                    port.send(msg, 0, msg.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Sends MIDI Note Off to connected hardware synthesizer.
     */
    fun sendNoteOff(channel: Int, note: Int) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msg = byteArrayOf(
            (0x80 or chan).toByte(),
            (note and 0x7F).toByte(),
            0x00.toByte()
        )
        synchronized(openInputPorts) {
            for (port in openInputPorts) {
                try {
                    port.send(msg, 0, msg.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun release() {
        try {
            midiManager?.unregisterDeviceCallback(midiCallback)
        } catch (_: Exception) {}
        for (port in openOutputPorts) {
            try { port.close() } catch (_: Exception) {}
        }
        synchronized(openInputPorts) {
            for (port in openInputPorts) {
                try { port.close() } catch (_: Exception) {}
            }
            openInputPorts.clear()
        }
        for (dev in openDevices) {
            try { dev.close() } catch (_: Exception) {}
        }
    }
}
