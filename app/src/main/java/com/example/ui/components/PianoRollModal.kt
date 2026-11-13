package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MidiCcNames
import com.example.audio.UsbMidiDeviceItem
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.TrackModel
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioYellow
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PianoRollModal(
    track: TrackModel,
    connectedMidiDevices: List<UsbMidiDeviceItem>,
    isRecording: Boolean,
    currentTimeMs: Long,
    onAddNote: (MidiNote) -> Unit,
    onRemoveNote: (String) -> Unit,
    onClearNotes: () -> Unit,
    onAuditionNote: (Int) -> Unit,
    onOpenMidiRouting: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOctave by remember { mutableIntStateOf(4) } // Default Octave 4 (Middle C is 60)
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Touch Keys, 1 = Notes, 2 = MIDI Params

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Piano,
                        contentDescription = null,
                        tint = StudioYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = track.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StudioCardElevated)
                                    .clickable { onOpenMidiRouting() }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                    .testTag("piano_roll_routing_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "MIDI Routing",
                                    tint = StudioCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "In: ${if (track.midiInputChannel == 0) "Omni" else "Ch ${track.midiInputChannel}"} • Out: ${track.midiOutputChannelLabel}",
                                    fontSize = 11.sp,
                                    color = StudioCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioTextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp)
            ) {
                // MIDI input / parameter status chip
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (connectedMidiDevices.isNotEmpty()) StudioGreen.copy(alpha = 0.15f) else StudioCardElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (connectedMidiDevices.isNotEmpty()) StudioGreen.copy(alpha = 0.4f) else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMidiRouting() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = if (connectedMidiDevices.isNotEmpty()) StudioGreen else StudioCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val midiStatus = if (connectedMidiDevices.isNotEmpty()) {
                            "MIDI Active: ${connectedMidiDevices.first().name} + Virtual Port"
                        } else {
                            "Raw MIDI Recorder • Captures Notes, CCs, Pitch Bend, PC & Aftertouch"
                        }
                        Text(
                            text = midiStatus,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (connectedMidiDevices.isNotEmpty()) StudioGreen else StudioTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Switcher: Touch Keys vs Notes vs Recorded MIDI Parameters
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioDark,
                    contentColor = StudioYellow,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = StudioYellow
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Keys", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Notes (${track.midiNotes.size})", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Params (${track.midiEvents.size})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        // TAB 0: TOUCH KEYS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OCTAVE: C$selectedOctave",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = StudioTextMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(2, 3, 4, 5).forEach { oct ->
                                    val isOct = oct == selectedOctave
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isOct) StudioYellow else StudioCardElevated)
                                            .clickable { selectedOctave = oct }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "C$oct",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOct) StudioDark else StudioTextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        VirtualTouchKeyboard(
                            baseOctave = selectedOctave,
                            onKeyPress = { noteNumber ->
                                onAuditionNote(noteNumber)
                                val noteTime = if (isRecording) currentTimeMs else 0L
                                val outCh = if (track.midiChannel in 1..16) track.midiChannel else 1
                                onAddNote(
                                    MidiNote(
                                        id = UUID.randomUUID().toString(),
                                        noteNumber = noteNumber,
                                        startMs = noteTime,
                                        durationMs = 500L,
                                        velocity = 100,
                                        channel = outCh
                                    )
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRecording) {
                                "Recording active: incoming Notes, CCs, Pitch Bend, Program Change & Aftertouch are being captured."
                            } else {
                                "Tap keys to preview & send MIDI Note On/Off, or arm REC and play from an external app or controller."
                            },
                            fontSize = 11.sp,
                            color = if (isRecording) StudioRed else StudioTextMuted,
                            lineHeight = 15.sp
                        )
                    }

                    1 -> {
                        // TAB 1: RECORDED MIDI NOTES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${track.midiNotes.size} Recorded Note(s)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = StudioTextSecondary
                            )
                            if (track.midiNotes.isNotEmpty() || track.midiEvents.isNotEmpty()) {
                                TextButton(
                                    onClick = onClearNotes,
                                    colors = ButtonDefaults.textButtonColors(contentColor = StudioRed)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All MIDI", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (track.midiNotes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Piano, null, tint = StudioTextMuted, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("No MIDI notes recorded yet", color = StudioTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Route MIDI from another app or USB controller & press REC", color = StudioTextMuted, fontSize = 11.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                track.midiNotes.sortedBy { it.startMs }.forEach { note ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = StudioCardElevated,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, StudioBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(track.color),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = note.noteName,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "${note.noteName} (#${note.noteNumber}) • Ch ${note.channel} • Vel ${note.velocity}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StudioTextPrimary
                                                    )
                                                    val sec = note.startMs / 1000.0
                                                    Text(
                                                        text = "Start: ${String.format(Locale.US, "%.2f", sec)}s • Dur: ${note.durationMs}ms",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = StudioTextMuted
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { onAuditionNote(note.noteNumber) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, "Preview note", tint = StudioCyan, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { onRemoveNote(note.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, "Delete note", tint = StudioRed, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // TAB 2: LIVE & RECORDED MIDI PARAMETERS (CC, PITCH BEND, PROGRAM CHANGE, AFTERTOUCH, NOTES)
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Live Incoming Parameter Snapshots
                            Text(
                                text = "LIVE RECEIVED PARAMETERS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = StudioTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ParamSnapshotPill(
                                    label = "Pitch Bend",
                                    value = "${track.latestPitchBend - 8192}",
                                    color = StudioCyan
                                )
                                ParamSnapshotPill(
                                    label = "Program",
                                    value = track.latestProgram?.let { "#$it" } ?: "—",
                                    color = StudioYellow
                                )
                                ParamSnapshotPill(
                                    label = "Aftertouch",
                                    value = "${track.latestAftertouch}",
                                    color = StudioGreen
                                )
                                track.latestCcValues.entries.sortedBy { it.key }.forEach { (cc, value) ->
                                    ParamSnapshotPill(
                                        label = MidiCcNames.labelFor(cc),
                                        value = "$value",
                                        color = StudioCyan
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RECORDED EVENT LOG (${track.midiEvents.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = StudioTextMuted
                                )
                                if (track.midiEvents.isNotEmpty()) {
                                    TextButton(
                                        onClick = onClearNotes,
                                        colors = ButtonDefaults.textButtonColors(contentColor = StudioRed)
                                    ) {
                                        Text("Clear Log", fontSize = 11.sp)
                                    }
                                }
                            }

                            if (track.midiEvents.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StudioDark),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No MIDI parameters recorded yet.\nArm REC and move knobs, pitch bend, sustain, or play notes.",
                                        fontSize = 11.sp,
                                        color = StudioTextMuted,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                val orderedEvents = remember(track.midiEvents) {
                                    track.midiEvents.sortedBy { it.timestampMs }
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    items(orderedEvents, key = { it.id }) { evt ->
                                        val badgeColor = when (evt.type) {
                                            MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> StudioYellow
                                            MidiEventType.CONTROL_CHANGE -> StudioCyan
                                            MidiEventType.PITCH_BEND -> StudioGreen
                                            MidiEventType.PROGRAM_CHANGE -> StudioYellow
                                            MidiEventType.CHANNEL_PRESSURE, MidiEventType.POLY_PRESSURE -> StudioGreen
                                        }
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = StudioCard,
                                            shape = RoundedCornerShape(5.dp),
                                            border = BorderStroke(1.dp, StudioBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = String.format(Locale.US, "%.2fs", evt.timestampMs / 1000.0),
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = StudioTextMuted,
                                                        modifier = Modifier.width(44.dp)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(badgeColor.copy(alpha = 0.18f))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            text = evt.type.shortLabel,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = badgeColor
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = evt.describe(),
                                                        fontSize = 11.sp,
                                                        color = StudioTextPrimary
                                                    )
                                                }
                                                Text(
                                                    text = "Ch ${evt.channel}",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = StudioTextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
            ) {
                Text("Done", color = StudioDark, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun ParamSnapshotPill(label: String, value: String, color: Color) {
    Surface(
        color = StudioCardElevated,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = "$label: $value",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun VirtualTouchKeyboard(
    baseOctave: Int,
    onKeyPress: (noteNumber: Int) -> Unit
) {
    val baseNote = (baseOctave + 1) * 12

    val whiteNotes = listOf(
        0 to "C",
        2 to "D",
        4 to "E",
        5 to "F",
        7 to "G",
        9 to "A",
        11 to "B",
        12 to "C"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            whiteNotes.forEach { (offset, name) ->
                val noteNum = baseNote + offset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 0.5.dp)
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(Color(0xFFEEEEEE))
                        .clickable { onKeyPress(noteNum) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "$name$baseOctave",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        val blackKeyOffsets = listOf(
            0.625f to 1,
            1.625f to 3,
            3.625f to 6,
            4.625f to 8,
            5.625f to 10
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val keyWidth = maxWidth / 8f
            val blackKeyWidth = keyWidth * 0.65f
            val blackKeyHeight = maxHeight * 0.6f

            blackKeyOffsets.forEach { (pos, offset) ->
                val noteNum = baseNote + offset
                val leftOffset = keyWidth * pos

                Box(
                    modifier = Modifier
                        .size(width = blackKeyWidth, height = blackKeyHeight)
                        .offset(x = leftOffset)
                        .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .clickable { onKeyPress(noteNum) }
                )
            }
        }
    }
}
