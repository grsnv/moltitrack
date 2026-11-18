package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.MidiCcNames
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.RecordedMidiEvent
import com.example.ui.theme.StudioBorder
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
import kotlin.math.max
import kotlin.math.roundToInt

private val BLACK_KEY_PCS = setOf(1, 3, 6, 8, 10)

@Composable
fun PianoRollGridView(
    notes: List<MidiNote>,
    trackColor: Color,
    selectedNoteId: String?,
    onSelectNoteForEdit: (MidiNote) -> Unit,
    modifier: Modifier = Modifier
) {
    val minRaw = notes.minOfOrNull { it.noteNumber } ?: 60
    val maxRaw = notes.maxOfOrNull { it.noteNumber } ?: 72
    val lowPitch = (minRaw - 2).coerceIn(0, 115)
    val highPitch = max(lowPitch + 11, (maxRaw + 2).coerceAtMost(127))
    val pitchCount = (highPitch - lowPitch + 1).coerceAtLeast(12)

    val maxEndMs = max(2500L, (notes.maxOfOrNull { it.startMs + it.durationMs } ?: 2000L) + 500L)
    val pxPerSecDp = 110f
    val gridWidthDp = ((maxEndMs / 1000f) * pxPerSecDp).coerceAtLeast(260f).dp
    val rowHeight = 14.dp
    val gridHeight = rowHeight * pitchCount

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(StudioDark)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .testTag("piano_roll_grid")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardElevated)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PIANO ROLL GRID (TAP A NOTE TO EDIT)",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = StudioTextMuted
            )
            Text(
                text = "${MidiNote.noteNameFor(lowPitch)}–${MidiNote.noteNameFor(highPitch)}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = StudioCyan
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .verticalScroll(verticalScroll)
        ) {
            // Left pitch labels column
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .height(gridHeight)
                    .background(StudioCardElevated)
            ) {
                for (pitch in highPitch downTo lowPitch) {
                    val isBlack = (pitch % 12) in BLACK_KEY_PCS
                    val isC = (pitch % 12) == 0
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(if (isBlack) StudioDark.copy(alpha = 0.7f) else StudioCardElevated)
                            .border(0.5.dp, StudioBorder.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = MidiNote.noteNameFor(pitch),
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isC) FontWeight.Bold else FontWeight.Normal,
                            color = if (isC) StudioYellow else StudioTextMuted
                        )
                    }
                }
            }

            // Scrollable time + pitch grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
            ) {
                Box(
                    modifier = Modifier
                        .width(gridWidthDp)
                        .height(gridHeight)
                ) {
                    // Pitch lane backgrounds
                    Column(modifier = Modifier.fillMaxSize()) {
                        for (pitch in highPitch downTo lowPitch) {
                            val isBlack = (pitch % 12) in BLACK_KEY_PCS
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .background(if (isBlack) Color(0xFF0A0F18) else Color(0xFF121926))
                                    .border(0.5.dp, StudioBorder.copy(alpha = 0.25f))
                            )
                        }
                    }

                    // 500ms vertical beat lines
                    val totalHalfSecs = (maxEndMs / 500L).toInt()
                    for (step in 1..totalHalfSecs) {
                        val xOffset = ((step * 0.5f) * pxPerSecDp).dp
                        Box(
                            modifier = Modifier
                                .offset(x = xOffset)
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(
                                    if (step % 2 == 0) StudioBorder.copy(alpha = 0.65f)
                                    else StudioBorder.copy(alpha = 0.28f)
                                )
                        )
                    }

                    // Note bars
                    notes.forEach { note ->
                        if (note.noteNumber in lowPitch..highPitch) {
                            val rowIdx = highPitch - note.noteNumber
                            val xDp = ((note.startMs / 1000f) * pxPerSecDp).dp
                            val wDp = (((note.durationMs / 1000f) * pxPerSecDp).coerceAtLeast(18f)).dp
                            val yDp = rowHeight * rowIdx + 1.dp
                            val isSelected = note.id == selectedNoteId
                            val velAlpha = (0.45f + (note.velocity / 127f) * 0.55f).coerceIn(0.45f, 1f)

                            Box(
                                modifier = Modifier
                                    .offset(x = xDp, y = yDp)
                                    .width(wDp)
                                    .height(rowHeight - 2.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(trackColor.copy(alpha = velAlpha))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.75.dp,
                                        color = if (isSelected) StudioYellow else Color.White.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clickable { onSelectNoteForEdit(note) }
                                    .padding(horizontal = 3.dp)
                                    .testTag("piano_roll_bar_${note.id}"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (wDp >= 26.dp) {
                                    Text(
                                        text = note.noteName,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMidiNoteDialog(
    initialNote: MidiNote,
    isNewNote: Boolean = false,
    onPreviewNote: (noteNumber: Int, velocity: Int, channel: Int) -> Unit,
    onSave: (MidiNote) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var noteNumber by remember(initialNote.id) { mutableIntStateOf(initialNote.noteNumber.coerceIn(0, 127)) }
    var velocity by remember(initialNote.id) { mutableIntStateOf(initialNote.velocity.coerceIn(1, 127)) }
    var channel by remember(initialNote.id) { mutableIntStateOf(initialNote.channel.coerceIn(1, 16)) }
    var startMsText by remember(initialNote.id) { mutableStateOf(initialNote.startMs.coerceAtLeast(0L).toString()) }
    var durationMsText by remember(initialNote.id) { mutableStateOf(initialNote.durationMs.coerceAtLeast(20L).toString()) }

    val parsedStartMs = (startMsText.toLongOrNull() ?: initialNote.startMs).coerceAtLeast(0L)
    val parsedDurationMs = (durationMsText.toLongOrNull() ?: initialNote.durationMs).coerceAtLeast(20L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isNewNote) "Add MIDI Note" else "Edit MIDI Note",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextPrimary
                )
                OutlinedButton(
                    onClick = { onPreviewNote(noteNumber, velocity, channel) },
                    border = BorderStroke(1.dp, StudioCyan),
                    modifier = Modifier.testTag("preview_edited_note_btn")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Preview note", tint = StudioCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview", fontSize = 11.sp, color = StudioCyan)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Pitch selector
                EditorSectionLabel("PITCH: ${MidiNote.noteNameFor(noteNumber)} (#$noteNumber)")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NudgeChip("-Oct") { noteNumber = (noteNumber - 12).coerceIn(0, 127) }
                    NudgeChip("-1") { noteNumber = (noteNumber - 1).coerceIn(0, 127) }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${MidiNote.noteNameFor(noteNumber)} ($noteNumber)",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = StudioYellow
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    NudgeChip("+1") { noteNumber = (noteNumber + 1).coerceIn(0, 127) }
                    NudgeChip("+Oct") { noteNumber = (noteNumber + 12).coerceIn(0, 127) }
                }
                Slider(
                    value = noteNumber.toFloat(),
                    onValueChange = { noteNumber = it.roundToInt().coerceIn(0, 127) },
                    valueRange = 0f..127f,
                    colors = SliderDefaults.colors(thumbColor = StudioYellow, activeTrackColor = StudioYellow),
                    modifier = Modifier.testTag("edit_note_pitch_slider")
                )

                // Velocity selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorSectionLabel("VELOCITY: $velocity")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NudgeChip("-10") { velocity = (velocity - 10).coerceIn(1, 127) }
                        NudgeChip("+10") { velocity = (velocity + 10).coerceIn(1, 127) }
                    }
                }
                Slider(
                    value = velocity.toFloat(),
                    onValueChange = { velocity = it.roundToInt().coerceIn(1, 127) },
                    valueRange = 1f..127f,
                    colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan),
                    modifier = Modifier.testTag("edit_note_velocity_slider")
                )

                // Start Time & Duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        EditorSectionLabel("START (ms)")
                        OutlinedTextField(
                            value = startMsText,
                            onValueChange = { startMsText = it.filter(Char::isDigit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = editorTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_note_start_input")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NudgeChip("-100") { startMsText = (parsedStartMs - 100L).coerceAtLeast(0L).toString() }
                            NudgeChip("+100") { startMsText = (parsedStartMs + 100L).toString() }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        EditorSectionLabel("DURATION (ms)")
                        OutlinedTextField(
                            value = durationMsText,
                            onValueChange = { durationMsText = it.filter(Char::isDigit) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = editorTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("edit_note_duration_input")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            NudgeChip("-100") { durationMsText = (parsedDurationMs - 100L).coerceAtLeast(20L).toString() }
                            NudgeChip("+100") { durationMsText = (parsedDurationMs + 100L).toString() }
                        }
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(125L, 250L, 500L, 1000L).forEach { preset ->
                        NudgeChip("${preset}ms") { durationMsText = preset.toString() }
                    }
                }

                // Channel selector
                ChannelStepperRow(
                    channel = channel,
                    onChannelChange = { channel = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialNote.copy(
                            noteNumber = noteNumber,
                            startMs = parsedStartMs,
                            durationMs = parsedDurationMs,
                            velocity = velocity,
                            channel = channel
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                modifier = Modifier.testTag("save_note_edit_btn")
            ) {
                Text("Save", color = StudioDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onDelete != null && !isNewNote) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = StudioRed),
                        modifier = Modifier.testTag("delete_note_edit_btn")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete note", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun EditMidiEventDialog(
    initialEvent: RecordedMidiEvent,
    onSave: (RecordedMidiEvent) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var timestampText by remember(initialEvent.id) { mutableStateOf(initialEvent.timestampMs.coerceAtLeast(0L).toString()) }
    var channel by remember(initialEvent.id) { mutableIntStateOf(initialEvent.channel.coerceIn(1, 16)) }
    var data1 by remember(initialEvent.id) { mutableIntStateOf(initialEvent.data1) }
    var data2 by remember(initialEvent.id) { mutableIntStateOf(initialEvent.data2) }

    val parsedTimestamp = (timestampText.toLongOrNull() ?: initialEvent.timestampMs).coerceAtLeast(0L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit ${initialEvent.type.shortLabel} Event",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = StudioTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Timestamp
                EditorSectionLabel("TIMESTAMP (ms) • ${String.format(Locale.US, "%.2fs", parsedTimestamp / 1000.0)}")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = timestampText,
                        onValueChange = { timestampText = it.filter(Char::isDigit) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = editorTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edit_event_timestamp_input")
                    )
                    NudgeChip("-100") { timestampText = (parsedTimestamp - 100L).coerceAtLeast(0L).toString() }
                    NudgeChip("+100") { timestampText = (parsedTimestamp + 100L).toString() }
                }

                // Channel
                ChannelStepperRow(
                    channel = channel,
                    onChannelChange = { channel = it }
                )

                // Type-specific parameters
                when (initialEvent.type) {
                    MidiEventType.CONTROL_CHANGE -> {
                        val safeCc = data1.coerceIn(0, 127)
                        val safeVal = data2.coerceIn(0, 127)
                        EditorSectionLabel("CONTROLLER: ${MidiCcNames.labelFor(safeCc)} (CC #$safeCc)")
                        Slider(
                            value = safeCc.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioYellow, activeTrackColor = StudioYellow),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                        EditorSectionLabel("VALUE: $safeVal")
                        Slider(
                            value = safeVal.toFloat(),
                            onValueChange = { data2 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan),
                            modifier = Modifier.testTag("edit_event_data2_slider")
                        )
                    }

                    MidiEventType.PITCH_BEND -> {
                        val rawBend = data1.coerceIn(0, 16383)
                        val signedBend = rawBend - 8192
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorSectionLabel("PITCH BEND: $signedBend (raw $rawBend)")
                            NudgeChip("Center 0") { data1 = 8192 }
                        }
                        Slider(
                            value = rawBend.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 16383) },
                            valueRange = 0f..16383f,
                            colors = SliderDefaults.colors(thumbColor = StudioGreen, activeTrackColor = StudioGreen),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                    }

                    MidiEventType.PROGRAM_CHANGE -> {
                        val prog = data1.coerceIn(0, 127)
                        EditorSectionLabel("PROGRAM NUMBER: #$prog")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NudgeChip("-1") { data1 = (prog - 1).coerceIn(0, 127) }
                            NudgeChip("+1") { data1 = (prog + 1).coerceIn(0, 127) }
                        }
                        Slider(
                            value = prog.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioYellow, activeTrackColor = StudioYellow),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                    }

                    MidiEventType.CHANNEL_PRESSURE -> {
                        val press = data1.coerceIn(0, 127)
                        EditorSectionLabel("AFTERTOUCH PRESSURE: $press")
                        Slider(
                            value = press.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioGreen, activeTrackColor = StudioGreen),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                    }

                    MidiEventType.POLY_PRESSURE -> {
                        val note = data1.coerceIn(0, 127)
                        val press = data2.coerceIn(0, 127)
                        EditorSectionLabel("NOTE: ${MidiNote.noteNameFor(note)} (#$note)")
                        Slider(
                            value = note.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioYellow, activeTrackColor = StudioYellow),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                        EditorSectionLabel("POLY PRESSURE: $press")
                        Slider(
                            value = press.toFloat(),
                            onValueChange = { data2 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioGreen, activeTrackColor = StudioGreen),
                            modifier = Modifier.testTag("edit_event_data2_slider")
                        )
                    }

                    MidiEventType.NOTE_ON, MidiEventType.NOTE_OFF -> {
                        val note = data1.coerceIn(0, 127)
                        val minVel = if (initialEvent.type == MidiEventType.NOTE_ON) 1 else 0
                        val vel = data2.coerceIn(minVel, 127)
                        EditorSectionLabel("NOTE: ${MidiNote.noteNameFor(note)} (#$note)")
                        Slider(
                            value = note.toFloat(),
                            onValueChange = { data1 = it.roundToInt().coerceIn(0, 127) },
                            valueRange = 0f..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioYellow, activeTrackColor = StudioYellow),
                            modifier = Modifier.testTag("edit_event_data1_slider")
                        )
                        EditorSectionLabel("VELOCITY: $vel")
                        Slider(
                            value = vel.toFloat(),
                            onValueChange = { data2 = it.roundToInt().coerceIn(minVel, 127) },
                            valueRange = minVel.toFloat()..127f,
                            colors = SliderDefaults.colors(thumbColor = StudioCyan, activeTrackColor = StudioCyan),
                            modifier = Modifier.testTag("edit_event_data2_slider")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialEvent.copy(
                            timestampMs = parsedTimestamp,
                            channel = channel,
                            data1 = data1,
                            data2 = data2
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                modifier = Modifier.testTag("save_event_edit_btn")
            ) {
                Text("Save", color = StudioDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = StudioRed),
                    modifier = Modifier.testTag("delete_event_edit_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete event", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = StudioTextMuted
    )
}

@Composable
private fun NudgeChip(label: String, onClick: () -> Unit) {
    Surface(
        color = StudioCardElevated,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = StudioTextPrimary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ChannelStepperRow(
    channel: Int,
    onChannelChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorSectionLabel("MIDI CHANNEL: Ch $channel")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onChannelChange((channel - 1).coerceIn(1, 16)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Decrease MIDI channel", tint = StudioTextPrimary, modifier = Modifier.size(16.dp))
            }
            Text(
                text = "Ch $channel",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = StudioCyan
            )
            IconButton(
                onClick = { onChannelChange((channel + 1).coerceIn(1, 16)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Increase MIDI channel", tint = StudioTextPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun editorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = StudioTextPrimary,
    unfocusedTextColor = StudioTextPrimary,
    focusedBorderColor = StudioCyan,
    unfocusedBorderColor = StudioBorder,
    focusedContainerColor = StudioDark,
    unfocusedContainerColor = StudioDark
)
