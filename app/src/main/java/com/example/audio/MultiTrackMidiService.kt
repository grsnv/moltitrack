package com.example.audio

import android.media.midi.MidiDeviceService
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiReceiver
import com.example.model.MidiActivityEvent
import com.example.model.MidiEventType
import com.example.model.MidiRoutingConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Shared in-process bus connecting Android's exported MidiDeviceService
 * (which receives MIDI routed from external Android synthesizer/sequencer apps)
 * to MultiTrack's AndroidMidiManager and ViewModel.
 */
object VirtualMidiRouter {

    private val listeners = CopyOnWriteArraySet<(MidiActivityEvent) -> Unit>()

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _connectedExternalSenders = MutableStateFlow(0)
    val connectedExternalSenders: StateFlow<Int> = _connectedExternalSenders.asStateFlow()

    fun addListener(listener: (MidiActivityEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (MidiActivityEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun updateServiceStatus(bound: Boolean, inputOpenCount: Int = 0) {
        _isServiceBound.value = bound
        _connectedExternalSenders.value = inputOpenCount
    }

    fun dispatchBytes(
        msg: ByteArray,
        offset: Int,
        count: Int,
        sourceId: String = MidiRoutingConstants.VIRTUAL_APP_MIDI_ID,
        sourceName: String = "MultiTrack Virtual MIDI (Inter-App)"
    ) {
        val parsed = MidiMessageParser.parse(msg, offset, count)
        for (item in parsed) {
            val event = item.toMidiActivityEvent(sourceId = sourceId, sourceName = sourceName)
            for (listener in listeners) {
                listener(event)
            }
        }
    }
}

fun ParsedMidiMessage.toMidiActivityEvent(sourceId: String, sourceName: String): MidiActivityEvent {
    return when (this) {
        is ParsedMidiMessage.NoteOn -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.NOTE_ON,
            data1 = note,
            data2 = velocity
        )
        is ParsedMidiMessage.NoteOff -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.NOTE_OFF,
            data1 = note,
            data2 = velocity
        )
        is ParsedMidiMessage.ControlChange -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.CONTROL_CHANGE,
            data1 = controller,
            data2 = value
        )
        is ParsedMidiMessage.ProgramChange -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.PROGRAM_CHANGE,
            data1 = program,
            data2 = 0
        )
        is ParsedMidiMessage.PitchBend -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.PITCH_BEND,
            data1 = value14Bit,
            data2 = 0
        )
        is ParsedMidiMessage.ChannelPressure -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.CHANNEL_PRESSURE,
            data1 = pressure,
            data2 = 0
        )
        is ParsedMidiMessage.PolyPressure -> MidiActivityEvent(
            sourceId = sourceId,
            sourceName = sourceName,
            channel = channel,
            type = MidiEventType.POLY_PRESSURE,
            data1 = note,
            data2 = pressure
        )
    }
}

/**
 * Android MidiDeviceService allowing other MIDI apps (sequencers, controllers, arpeggiators)
 * to discover "MultiTrack Virtual MIDI" and route MIDI channels, notes, CCs, pitch bend,
 * aftertouch, and program changes directly into MultiTrack tracks.
 */
class MultiTrackMidiService : MidiDeviceService() {

    private val inputReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            if (msg == null || count <= 0) return

            try {
                for (outReceiver in outputPortReceivers) {
                    outReceiver?.send(msg, offset, count, timestamp)
                }
            } catch (_: Exception) {
            }

            VirtualMidiRouter.dispatchBytes(
                msg = msg,
                offset = offset,
                count = count,
                sourceId = MidiRoutingConstants.VIRTUAL_APP_MIDI_ID,
                sourceName = "MultiTrack Virtual MIDI (Inter-App)"
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        VirtualMidiRouter.updateServiceStatus(bound = true)
    }

    override fun onGetInputPortReceivers(): Array<MidiReceiver> {
        return arrayOf(inputReceiver)
    }

    override fun onDeviceStatusChanged(status: MidiDeviceStatus?) {
        super.onDeviceStatusChanged(status)
        if (status != null) {
            val openCount = if (status.isInputPortOpen(0)) 1 else 0
            VirtualMidiRouter.updateServiceStatus(bound = true, inputOpenCount = openCount)
        }
    }

    override fun onDestroy() {
        VirtualMidiRouter.updateServiceStatus(bound = false, inputOpenCount = 0)
        super.onDestroy()
    }
}
