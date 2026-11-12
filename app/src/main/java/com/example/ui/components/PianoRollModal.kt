package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.audio.MidiSynthEngine
import com.example.audio.UsbMidiDeviceItem
import com.example.model.MidiNote
import com.example.model.SynthInstrument
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
import java.util.UUID

@Composable
fun PianoRollModal(
    track: TrackModel,
    connectedMidiDevices: List<UsbMidiDeviceItem>,
    isRecording: Boolean,
    currentTimeMs: Long,
    onAddNote: (MidiNote) -> Unit,
    onRemoveNote: (String) -> Unit,
    onClearNotes: () -> Unit,
    onChangeInstrument: (SynthInstrument) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedOctave by remember { mutableIntStateOf(4) } // Default Octave 4 (Middle C is 60)
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Touch Keys, 1 = Piano Roll Grid
    var showInstrumentMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioCardElevated)
                                .clickable { showInstrumentMenu = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Synth: ${track.instrument.displayName}",
                                fontSize = 11.sp,
                                color = StudioYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = StudioYellow,
                                modifier = Modifier.size(12.dp).padding(start = 2.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showInstrumentMenu,
                            onDismissRequest = { showInstrumentMenu = false }
                        ) {
                            SynthInstrument.entries.forEach { inst ->
                                DropdownMenuItem(
                                    text = { Text("${inst.displayName} (${inst.category})") },
                                    onClick = {
                                        onChangeInstrument(inst)
                                        showInstrumentMenu = false
                                    }
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
                    .height(420.dp)
            ) {
                // USB MIDI status chip
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (connectedMidiDevices.isNotEmpty()) StudioGreen.copy(alpha = 0.15f) else StudioCardElevated,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (connectedMidiDevices.isNotEmpty()) StudioGreen.copy(alpha = 0.4f) else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            "USB MIDI Connected: ${connectedMidiDevices.first().name}"
                        } else {
                            "USB MIDI Keyboard: Ready (Plug in any USB MIDI controller via OTG)"
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

                // Tab Switcher: Keys vs Piano Roll Grid
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
                        text = { Text("Touch Keyboard", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Piano Roll Grid (${track.midiNotes.size})", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // TAB 0: VIRTUAL TOUCH PIANO KEYBOARD
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

                    // Virtual Keyboard
                    VirtualTouchKeyboard(
                        baseOctave = selectedOctave,
                        onKeyPress = { noteNumber ->
                            // Play live synth sound
                            MidiSynthEngine.playNoteLive(noteNumber, track.instrument, scope)

                            // Add note to track
                            val noteTime = if (isRecording) currentTimeMs else 0L
                            onAddNote(
                                MidiNote(
                                    id = UUID.randomUUID().toString(),
                                    noteNumber = noteNumber,
                                    startMs = noteTime,
                                    durationMs = 500L,
                                    velocity = 100
                                )
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isRecording) "🔴 Recording active: keys tapped will be recorded at current time!" else "Tap keys to test sounds & compose. Start Recording to capture timed notes.",
                        fontSize = 11.sp,
                        color = if (isRecording) StudioRed else StudioTextMuted,
                        lineHeight = 15.sp
                    )

                } else {
                    // TAB 1: PIANO ROLL GRID VIEW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${track.midiNotes.size} MIDI Note(s)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextSecondary
                        )
                        if (track.midiNotes.isNotEmpty()) {
                            TextButton(
                                onClick = onClearNotes,
                                colors = ButtonDefaults.textButtonColors(contentColor = StudioRed)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", fontSize = 11.sp)
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
                                Icon(
                                    imageVector = Icons.Default.Piano,
                                    contentDescription = null,
                                    tint = StudioTextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No MIDI notes recorded yet",
                                    color = StudioTextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Play keys in 'Touch Keyboard' or plug in a USB MIDI controller",
                                    color = StudioTextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        // List of recorded MIDI notes with play & delete buttons
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
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
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
                                                    text = "Note: ${note.noteName} (MIDI #${note.noteNumber})",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StudioTextPrimary
                                                )
                                                val sec = note.startMs / 1000.0
                                                Text(
                                                    text = "Time: ${String.format("%.2f", sec)}s • Dur: ${note.durationMs}ms",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = StudioTextMuted
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    MidiSynthEngine.playNoteLive(note.noteNumber, track.instrument, scope)
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Preview note",
                                                    tint = StudioCyan,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveNote(note.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete note",
                                                    tint = StudioRed,
                                                    modifier = Modifier.size(16.dp)
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
fun VirtualTouchKeyboard(
    baseOctave: Int,
    onKeyPress: (noteNumber: Int) -> Unit
) {
    // 1 Octave of keys: C, C#, D, D#, E, F, F#, G, G#, A, A#, B + High C
    val baseNote = (baseOctave + 1) * 12 // C4 is note 60

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
        // White Keys Layer
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            whiteNotes.forEachIndexed { index, (offset, name) ->
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

        // Black Keys Layer (C#, D#, F#, G#, A#)
        val blackKeyOffsets = listOf(
            0.625f to 1,  // C#
            1.625f to 3,  // D#
            3.625f to 6,  // F#
            4.625f to 8,  // G#
            5.625f to 10  // A#
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
