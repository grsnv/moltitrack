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
import androidx.core.content.ContextCompat
import com.example.model.MidiActivityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class UsbMidiDeviceItem(
    val id: Int,
    val name: String,
    val manufacturer: String,
    val inputPortCount: Int,
    val outputPortCount: Int,
    val deviceType: Int = MidiDeviceInfo.TYPE_USB,
    val deviceKey: String = "midi_dev_$id"
) {
    val isVirtual: Boolean get() = deviceType == MidiDeviceInfo.TYPE_VIRTUAL
    val isBluetooth: Boolean get() = deviceType == MidiDeviceInfo.TYPE_BLUETOOTH
    val typeBadge: String
        get() = when (deviceType) {
            MidiDeviceInfo.TYPE_VIRTUAL -> "APP MIDI"
            MidiDeviceInfo.TYPE_BLUETOOTH -> "BLE MIDI"
            else -> "USB MIDI"
        }
}

class AndroidMidiManager(
    private val context: Context,
    private val onMidiEventReceived: (MidiActivityEvent) -> Unit
) {
    private val midiManager: MidiManager? =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectedMidiDevices = MutableStateFlow<List<UsbMidiDeviceItem>>(emptyList())
    val connectedMidiDevices: StateFlow<List<UsbMidiDeviceItem>> = _connectedMidiDevices.asStateFlow()

    private val _lastMidiActivity = MutableStateFlow<MidiActivityEvent?>(null)
    val lastMidiActivity: StateFlow<MidiActivityEvent?> = _lastMidiActivity.asStateFlow()

    val isVirtualServiceBound: StateFlow<Boolean> = VirtualMidiRouter.isServiceBound
    val virtualExternalSendersCount: StateFlow<Int> = VirtualMidiRouter.connectedExternalSenders

    private val openDevices = ConcurrentHashMap<Int, MidiDevice>()
    private val openOutputPorts = ConcurrentHashMap<Int, MutableList<MidiOutputPort>>()
    private val openInputPorts = ConcurrentHashMap<Int, MutableList<MidiInputPort>>()
    private val pendingOpenIds = ConcurrentHashMap.newKeySet<Int>()

    private val virtualRouterListener: (MidiActivityEvent) -> Unit = { event ->
        _lastMidiActivity.value = event
        onMidiEventReceived(event)
    }

    private val midiCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo?) {
            scanMidiDevices()
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo?) {
            if (device != null) {
                closeDeviceById(device.id)
            }
            scanMidiDevices()
        }
    }

    init {
        VirtualMidiRouter.addListener(virtualRouterListener)
        registerMidiCallback()
        scanMidiDevices()
    }

    private fun registerMidiCallback() {
        val manager = midiManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.registerDeviceCallback(
                    MidiManager.TRANSPORT_MIDI_BYTE_STREAM,
                    ContextCompat.getMainExecutor(context),
                    midiCallback
                )
            } else {
                @Suppress("DEPRECATION")
                manager.registerDeviceCallback(midiCallback, mainHandler)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isOwnVirtualDevice(info: MidiDeviceInfo): Boolean {
        val props = info.properties
        val product = props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
        return product == "MultiTrack DAW" || name == "MultiTrack Virtual MIDI"
    }

    fun scanMidiDevices() {
        val manager = midiManager ?: return
        val list = mutableListOf<UsbMidiDeviceItem>()

        try {
            val devices: Collection<MidiDeviceInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    manager.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
                } else {
                    @Suppress("DEPRECATION")
                    manager.devices.toList()
                }

            for (info in devices) {
                if (isOwnVirtualDevice(info)) continue

                val props = info.properties
                val fallbackPrefix = when (info.type) {
                    MidiDeviceInfo.TYPE_VIRTUAL -> "App MIDI Port"
                    MidiDeviceInfo.TYPE_BLUETOOTH -> "BLE MIDI"
                    else -> "USB MIDI Device"
                }
                val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
                    ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
                    ?: "$fallbackPrefix #${info.id}"
                val mfg = props.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER)
                    ?: if (info.type == MidiDeviceInfo.TYPE_VIRTUAL) "External App" else "Generic MIDI"

                val deviceKey = "midi_dev_${info.id}"

                list.add(
                    UsbMidiDeviceItem(
                        id = info.id,
                        name = name,
                        manufacturer = mfg,
                        inputPortCount = info.inputPortCount,
                        outputPortCount = info.outputPortCount,
                        deviceType = info.type,
                        deviceKey = deviceKey
                    )
                )

                val alreadyOpen = openDevices.containsKey(info.id) || pendingOpenIds.contains(info.id)
                if (!alreadyOpen && (info.outputPortCount > 0 || info.inputPortCount > 0)) {
                    pendingOpenIds.add(info.id)
                    manager.openDevice(info, { device ->
                        pendingOpenIds.remove(info.id)
                        if (device != null) {
                            openDevices[info.id] = device

                            if (info.outputPortCount > 0) {
                                val outList = mutableListOf<MidiOutputPort>()
                                for (portIdx in 0 until info.outputPortCount) {
                                    val outPort = device.openOutputPort(portIdx)
                                    if (outPort != null) {
                                        outList.add(outPort)
                                        outPort.connect(object : MidiReceiver() {
                                            override fun onSend(
                                                msg: ByteArray?,
                                                offset: Int,
                                                count: Int,
                                                timestamp: Long
                                            ) {
                                                if (msg == null || count <= 0) return
                                                val parsed = MidiMessageParser.parse(msg, offset, count)
                                                for (item in parsed) {
                                                    val event = when (item) {
                                                        is ParsedMidiMessage.NoteOn -> MidiActivityEvent(
                                                            sourceId = deviceKey,
                                                            sourceName = name,
                                                            channel = item.channel,
                                                            noteNumber = item.note,
                                                            velocity = item.velocity,
                                                            isNoteOn = true
                                                        )
                                                        is ParsedMidiMessage.NoteOff -> MidiActivityEvent(
                                                            sourceId = deviceKey,
                                                            sourceName = name,
                                                            channel = item.channel,
                                                            noteNumber = item.note,
                                                            velocity = item.velocity,
                                                            isNoteOn = false
                                                        )
                                                        is ParsedMidiMessage.ProgramChange -> MidiActivityEvent(
                                                            sourceId = deviceKey,
                                                            sourceName = name,
                                                            channel = item.channel,
                                                            noteNumber = -1,
                                                            velocity = 0,
                                                            isNoteOn = false,
                                                            programChange = item.program
                                                        )
                                                        is ParsedMidiMessage.ControlChange -> null
                                                    }
                                                    if (event != null) {
                                                        _lastMidiActivity.value = event
                                                        onMidiEventReceived(event)
                                                    }
                                                }
                                            }
                                        })
                                    }
                                }
                                if (outList.isNotEmpty()) {
                                    openOutputPorts[info.id] = outList
                                }
                            }

                            if (info.inputPortCount > 0) {
                                val inPort = device.openInputPort(0)
                                if (inPort != null) {
                                    openInputPorts[info.id] = mutableListOf(inPort)
                                }
                            }
                        }
                    }, mainHandler)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _connectedMidiDevices.value = list
    }

    private fun closeDeviceById(deviceId: Int) {
        pendingOpenIds.remove(deviceId)
        openOutputPorts.remove(deviceId)?.forEach { port ->
            try { port.close() } catch (_: Exception) {}
        }
        openInputPorts.remove(deviceId)?.forEach { port ->
            try { port.close() } catch (_: Exception) {}
        }
        openDevices.remove(deviceId)?.let { dev ->
            try { dev.close() } catch (_: Exception) {}
        }
    }

    /**
     * Sends Bank Select MSB (CC 0), Bank Select LSB (CC 32), and Program Change (0xC0)
     * to switch the active patch in the connected hardware/app synthesizer.
     */
    fun sendProgramChange(channel: Int, program: Int, bankMsb: Int = 0, bankLsb: Int = 0) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msbMsg = byteArrayOf((0xB0 or chan).toByte(), 0x00.toByte(), (bankMsb and 0x7F).toByte())
        val lsbMsg = byteArrayOf((0xB0 or chan).toByte(), 0x20.toByte(), (bankLsb and 0x7F).toByte())
        val pcMsg = byteArrayOf((0xC0 or chan).toByte(), (program and 0x7F).toByte())

        for (ports in openInputPorts.values) {
            for (port in ports) {
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
     * Sends MIDI Note On to connected hardware/virtual synthesizer input ports.
     */
    fun sendNoteOn(channel: Int, note: Int, velocity: Int = 100) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msg = byteArrayOf(
            (0x90 or chan).toByte(),
            (note and 0x7F).toByte(),
            (velocity.coerceIn(1, 127)).toByte()
        )
        for (ports in openInputPorts.values) {
            for (port in ports) {
                try {
                    port.send(msg, 0, msg.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Sends MIDI Note Off to connected hardware/virtual synthesizer input ports.
     */
    fun sendNoteOff(channel: Int, note: Int) {
        val chan = (channel - 1).coerceIn(0, 15)
        val msg = byteArrayOf(
            (0x80 or chan).toByte(),
            (note and 0x7F).toByte(),
            0x00.toByte()
        )
        for (ports in openInputPorts.values) {
            for (port in ports) {
                try {
                    port.send(msg, 0, msg.size)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun release() {
        VirtualMidiRouter.removeListener(virtualRouterListener)
        try {
            midiManager?.unregisterDeviceCallback(midiCallback)
        } catch (_: Exception) {}
        val ids = openDevices.keys.toList()
        for (id in ids) {
            closeDeviceById(id)
        }
    }
}
