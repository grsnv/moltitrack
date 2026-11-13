package com.example.audio

sealed class ParsedMidiMessage {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : ParsedMidiMessage()
    data class NoteOff(val channel: Int, val note: Int, val velocity: Int) : ParsedMidiMessage()
    data class ProgramChange(val channel: Int, val program: Int) : ParsedMidiMessage()
    data class ControlChange(val channel: Int, val controller: Int, val value: Int) : ParsedMidiMessage()
}

/**
 * Stateless parser for raw MIDI 1.0 byte streams (handles multi-message packets,
 * running status, 2-byte Program/Channel Pressure messages, SysEx, and Real-Time bytes).
 */
object MidiMessageParser {

    fun parse(msg: ByteArray, offset: Int, count: Int): List<ParsedMidiMessage> {
        if (count <= 0 || offset < 0 || offset + count > msg.size) return emptyList()

        val results = mutableListOf<ParsedMidiMessage>()
        var i = offset
        val end = offset + count
        var runningStatus = 0
        var inSysEx = false

        while (i < end) {
            val b = msg[i].toInt() and 0xFF

            // System Real-Time messages (0xF8..0xFF) are single-byte and do not affect running status
            if (b >= 0xF8) {
                i++
                continue
            }

            if (inSysEx) {
                if (b == 0xF7) {
                    inSysEx = false
                } else if ((b and 0x80) != 0 && b != 0xF0) {
                    // Non-realtime status aborts SysEx
                    inSysEx = false
                    continue
                }
                i++
                continue
            }

            if (b == 0xF0) {
                inSysEx = true
                runningStatus = 0
                i++
                continue
            }

            if ((b and 0x80) != 0) {
                // System Common (0xF1..0xF7) cancels running status
                if (b >= 0xF0) {
                    runningStatus = 0
                    val skip = when (b) {
                        0xF2 -> 3 // Song Position Pointer
                        0xF1, 0xF3 -> 2 // MTC / Song Select
                        else -> 1
                    }
                    i += skip
                    continue
                }
                runningStatus = b
                i++
            }

            val status = runningStatus
            if (status == 0) {
                i++
                continue
            }

            val command = status and 0xF0
            val channel = (status and 0x0F) + 1 // 1..16

            when (command) {
                0xC0 -> { // Program Change (1 data byte)
                    if (i < end) {
                        val program = msg[i].toInt() and 0x7F
                        results.add(ParsedMidiMessage.ProgramChange(channel, program))
                        i++
                    }
                }
                0xD0 -> { // Channel Pressure (1 data byte)
                    if (i < end) i++
                }
                0x80 -> { // Note Off (2 data bytes)
                    if (i + 1 < end) {
                        val note = msg[i].toInt() and 0x7F
                        val vel = msg[i + 1].toInt() and 0x7F
                        results.add(ParsedMidiMessage.NoteOff(channel, note, vel))
                        i += 2
                    } else {
                        i = end
                    }
                }
                0x90 -> { // Note On (2 data bytes; velocity 0 == Note Off)
                    if (i + 1 < end) {
                        val note = msg[i].toInt() and 0x7F
                        val vel = msg[i + 1].toInt() and 0x7F
                        if (vel > 0) {
                            results.add(ParsedMidiMessage.NoteOn(channel, note, vel))
                        } else {
                            results.add(ParsedMidiMessage.NoteOff(channel, note, 0))
                        }
                        i += 2
                    } else {
                        i = end
                    }
                }
                0xB0 -> { // Control Change (2 data bytes)
                    if (i + 1 < end) {
                        val cc = msg[i].toInt() and 0x7F
                        val value = msg[i + 1].toInt() and 0x7F
                        results.add(ParsedMidiMessage.ControlChange(channel, cc, value))
                        i += 2
                    } else {
                        i = end
                    }
                }
                0xA0, 0xE0 -> { // Poly Aftertouch, Pitch Bend (2 data bytes)
                    if (i + 1 < end) i += 2 else i = end
                }
                else -> i++
            }
        }
        return results
    }
}
