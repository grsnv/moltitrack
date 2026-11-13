package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.UsbMidiDeviceItem
import com.example.model.AudioInputDevice
import com.example.model.MidiRoutingConstants
import com.example.model.TrackModel
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioOrange
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioYellow
import java.util.Locale
import kotlin.math.log10

@Composable
fun TrackLaneCard(
    track: TrackModel,
    inputDevice: AudioInputDevice?,
    connectedMidiDevices: List<UsbMidiDeviceItem> = emptyList(),
    isRecordingActive: Boolean,
    playheadProgress: Float,
    meterLevel: Float,
    onToggleArm: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSolo: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPianoRoll: () -> Unit = {},
    onOpenMidiRouting: () -> Unit = {},
    onRename: (String) -> Unit,
    onClearAudio: () -> Unit,
    onDeleteTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember(track.name) { mutableStateOf(track.name) }

    // Pulsing animation for armed state when actively recording
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .testTag("track_card_${track.id}"),
        color = StudioCard,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header Row: Color bar, Title, Input Chip, Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color strip
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 28.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(track.color)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Track Title
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showRenameDialog = true }
                    ) {
                        Text(
                            text = track.name,
                            color = StudioTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Rename Track",
                            tint = StudioTextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Input Device & Channel routing tag OR Raw MIDI parameters + routing tags
                    if (track.type == com.example.model.TrackType.MIDI) {
                        Column(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val paramParts = buildList {
                                add("${track.midiNotes.size}n")
                                add("${track.midiEvents.size} evts")
                                if (track.latestCcValues.isNotEmpty()) add("${track.latestCcValues.size} CC")
                                if (track.latestProgram != null) add("PC#${track.latestProgram}")
                                if (track.latestPitchBend != 8192) add("PB")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StudioYellow.copy(alpha = 0.15f))
                                    .border(1.dp, StudioYellow.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .clickable { onOpenPianoRoll() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("midi_chip_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Piano,
                                    contentDescription = "MIDI Notes and Parameters",
                                    tint = StudioYellow,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "MIDI • ${paramParts.joinToString(" • ")}",
                                    color = StudioYellow,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            val sourceLabel = when (track.inputDeviceId) {
                                MidiRoutingConstants.VIRTUAL_APP_MIDI_ID -> "App MIDI"
                                MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
                                MidiRoutingConstants.LEGACY_USB_MIDI_ID,
                                "",
                                "default_mic" -> "All MIDI"
                                else -> connectedMidiDevices.firstOrNull { it.deviceKey == track.inputDeviceId }?.name ?: "MIDI Port"
                            }
                            val chLabel = if (track.midiInputChannel == 0) "Omni" else "Ch ${track.midiInputChannel}"
                            val outTargetLabel = when (track.midiOutputDeviceId) {
                                MidiRoutingConstants.SOURCE_SYNTH_ID -> "Source Synth"
                                MidiRoutingConstants.NO_MIDI_OUTPUT_ID -> "Off"
                                MidiRoutingConstants.VIRTUAL_APP_MIDI_ID -> "App Out"
                                else -> track.midiOutputChannelLabel
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StudioCardElevated)
                                    .clickable { onOpenMidiRouting() }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("midi_routing_chip_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "MIDI Input & Output Routing",
                                    tint = StudioCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "In: $sourceLabel ($chLabel) • Out: $outTargetLabel",
                                    color = StudioCyan,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(StudioCardElevated)
                                .clickable { onOpenRouting() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("routing_chip_${track.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Input Source",
                                tint = StudioCyan,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val devName = inputDevice?.name ?: "Input Mic"
                            Text(
                                text = "$devName • ${track.inputChannelMode.shortLabel}",
                                color = StudioCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // If MIDI track, show dedicated Keys / Piano Roll shortcut button
                if (track.type == com.example.model.TrackType.MIDI) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StudioYellow.copy(alpha = 0.2f))
                            .border(1.dp, StudioYellow, RoundedCornerShape(6.dp))
                            .clickable { onOpenPianoRoll() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("open_keys_btn_${track.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Piano,
                                contentDescription = null,
                                tint = StudioYellow,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Keys",
                                color = StudioYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Arm / Rec button
                val armColor = if (track.isArmed) {
                    if (isRecordingActive) StudioRed.copy(alpha = pulseAlpha) else StudioRed
                } else {
                    StudioCardElevated
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(armColor)
                        .border(1.dp, if (track.isArmed) StudioRed else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable { onToggleArm() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("arm_button_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (track.isArmed) Color.White else StudioTextMuted)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "REC",
                            color = if (track.isArmed) Color.White else StudioTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Mute button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (track.isMuted) StudioOrange else StudioCardElevated)
                        .border(1.dp, if (track.isMuted) StudioOrange else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable { onToggleMute() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("mute_button_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "M",
                        color = if (track.isMuted) Color.Black else StudioTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Solo button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (track.isSoloed) StudioYellow else StudioCardElevated)
                        .border(1.dp, if (track.isSoloed) StudioYellow else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable { onToggleSolo() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("solo_button_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "S",
                        color = if (track.isSoloed) Color.Black else StudioTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Menu overflow
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Track options",
                            tint = StudioTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (track.type == com.example.model.TrackType.MIDI) {
                            DropdownMenuItem(
                                text = { Text("MIDI Input / Output Routing") },
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenMidiRouting()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Notes & MIDI Parameters") },
                                leadingIcon = { Icon(Icons.Default.Tune, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenPianoRoll()
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Configure Audio Input") },
                                leadingIcon = { Icon(Icons.Default.SettingsInputComponent, null) },
                                onClick = {
                                    showMenu = false
                                    onOpenRouting()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename Track") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        if (track.audioFilePath != null) {
                            DropdownMenuItem(
                                text = { Text("Clear Recorded Audio") },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = {
                                    showMenu = false
                                    onClearAudio()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Track", color = StudioRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = StudioRed) },
                            onClick = {
                                showMenu = false
                                onDeleteTrack()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Waveform Lane
            TrackWaveformView(
                waveformPoints = track.waveformPoints,
                trackColor = track.color,
                playheadProgress = playheadProgress,
                durationMs = track.durationMs,
                isRecordingThisTrack = track.isArmed && isRecordingActive,
                onSeek = onSeek,
                height = 54.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Track Real-time VU meter
            VuMeterBar(
                level = meterLevel,
                height = 5.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Controls Row: Volume & Pan Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Volume slider
                Icon(
                    imageVector = if (track.volume > 0.05f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = StudioTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1.2f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "VOL",
                            color = StudioTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val db = if (track.volume > 0.01f) {
                            val dbVal = 20 * log10(track.volume)
                            String.format(Locale.US, "%.1f dB", dbVal)
                        } else "-inf dB"
                        Text(
                            text = db,
                            color = StudioTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = track.volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioCyan,
                            activeTrackColor = StudioCyan,
                            inactiveTrackColor = StudioCardElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .testTag("vol_slider_${track.id}")
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Pan slider
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PAN",
                            color = StudioTextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val panStr = when {
                            track.pan < -0.05f -> String.format(Locale.US, "L %.0f%%", -track.pan * 100)
                            track.pan > 0.05f -> String.format(Locale.US, "R %.0f%%", track.pan * 100)
                            else -> "CENTER"
                        }
                        Text(
                            text = panStr,
                            color = StudioTextSecondary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Slider(
                        value = track.pan,
                        onValueChange = onPanChange,
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = track.color,
                            activeTrackColor = track.color,
                            inactiveTrackColor = StudioCardElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .testTag("pan_slider_${track.id}")
                    )
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Track") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Track Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(renameInput)
                        showRenameDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
