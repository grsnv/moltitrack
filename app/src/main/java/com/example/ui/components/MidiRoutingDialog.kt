package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.UsbMidiDeviceItem
import com.example.model.MidiActivityEvent
import com.example.model.MidiRoutingConstants
import com.example.model.TrackModel
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioDark
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary
import com.example.ui.theme.StudioYellow

@Composable
fun MidiRoutingDialog(
    track: TrackModel,
    connectedMidiDevices: List<UsbMidiDeviceItem>,
    lastMidiActivity: MidiActivityEvent?,
    isVirtualMidiBound: Boolean,
    virtualExternalSendersCount: Int,
    onSaveMidiRouting: (inputDeviceId: String, inputChannel: Int) -> Unit,
    onRefreshMidiDevices: () -> Unit,
    onOpenPatchSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    val initialSource = remember(track.id, track.inputDeviceId) {
        when (track.inputDeviceId) {
            "", "default_mic", MidiRoutingConstants.LEGACY_USB_MIDI_ID -> MidiRoutingConstants.ALL_MIDI_INPUTS_ID
            else -> track.inputDeviceId
        }
    }
    var selectedSourceId by remember(track.id) { mutableStateOf(initialSource) }
    var selectedInputChannel by remember(track.id) { mutableIntStateOf(track.midiInputChannel) }

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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(track.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "MIDI Input & App Routing",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = track.name,
                            fontSize = 11.sp,
                            color = StudioYellow
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshMidiDevices,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("refresh_midi_ports_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan MIDI Devices & Apps",
                        tint = StudioCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Live Inter-App & Hardware MIDI Activity Monitor
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
                    border = BorderStroke(
                        1.dp,
                        if (lastMidiActivity != null || virtualExternalSendersCount > 0) StudioGreen.copy(alpha = 0.5f) else StudioBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "INTER-APP & MIDI MONITOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = StudioGreen
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (virtualExternalSendersCount > 0) StudioGreen.copy(alpha = 0.2f)
                                        else StudioCyan.copy(alpha = 0.15f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (virtualExternalSendersCount > 0) "APP LINKED"
                                    else if (isVirtualMidiBound) "VIRTUAL PORT LIVE"
                                    else "PORT READY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (virtualExternalSendersCount > 0) StudioGreen else StudioCyan
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val monitorText = lastMidiActivity?.let { evt ->
                            if (evt.programChange != null) {
                                "Last Rx: Ch ${evt.channel} • PC #${evt.programChange} (${evt.sourceName})"
                            } else {
                                val kind = if (evt.isNoteOn) "Note On" else "Note Off"
                                "Last Rx: $kind • Ch ${evt.channel} • Note #${evt.noteNumber} (vel ${evt.velocity}) from ${evt.sourceName}"
                            }
                        } ?: "Select 'MultiTrack Virtual MIDI' in other synth/sequencer apps to route MIDI notes here, or pick an external app's MIDI port below."
                        Text(
                            text = monitorText,
                            fontSize = 11.sp,
                            fontFamily = if (lastMidiActivity != null) FontFamily.Monospace else FontFamily.Default,
                            color = if (lastMidiActivity != null) StudioCyan else StudioTextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 1: MIDI INPUT SOURCE
                Text(
                    text = "MIDI INPUT SOURCE (APPS & HARDWARE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = StudioTextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Option 1: All MIDI Inputs
                MidiSourceCard(
                    title = "All MIDI Inputs (Omni Sources)",
                    subtitle = "Listen to Virtual Inter-App MIDI, USB controllers, and BLE MIDI simultaneously",
                    badge = "OMNI",
                    badgeColor = StudioCyan,
                    icon = Icons.Default.SettingsInputComponent,
                    isSelected = selectedSourceId == MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
                    testTag = "midi_source_all",
                    onClick = { selectedSourceId = MidiRoutingConstants.ALL_MIDI_INPUTS_ID }
                )

                // Option 2: Dedicated MultiTrack Virtual MIDI Port (Receive from Other Apps)
                MidiSourceCard(
                    title = "MultiTrack Virtual MIDI (From Other Apps)",
                    subtitle = "Receive notes routed into MultiTrack's exported Android MIDI input service",
                    badge = "INTER-APP",
                    badgeColor = StudioGreen,
                    icon = Icons.Default.SwapHoriz,
                    isSelected = selectedSourceId == MidiRoutingConstants.VIRTUAL_APP_MIDI_ID,
                    testTag = "midi_source_virtual_app",
                    onClick = { selectedSourceId = MidiRoutingConstants.VIRTUAL_APP_MIDI_ID }
                )

                // Option 3: Discovered USB / BLE / External App Virtual Output Ports
                if (connectedMidiDevices.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        color = StudioCard,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioBorder)
                    ) {
                        Text(
                            text = "No external USB MIDI controllers or third-party app virtual ports detected right now. Tap refresh after launching another MIDI app or connecting USB OTG.",
                            fontSize = 11.sp,
                            color = StudioTextMuted,
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    connectedMidiDevices.forEach { dev ->
                        val badgeColor = when {
                            dev.isVirtual -> StudioGreen
                            dev.isBluetooth -> StudioCyan
                            else -> StudioYellow
                        }
                        val icon = when {
                            dev.isVirtual -> Icons.Default.Piano
                            dev.isBluetooth -> Icons.Default.Bluetooth
                            else -> Icons.Default.Usb
                        }
                        MidiSourceCard(
                            title = dev.name,
                            subtitle = "${dev.manufacturer} • Out Ports: ${dev.outputPortCount} • In Ports: ${dev.inputPortCount}",
                            badge = dev.typeBadge,
                            badgeColor = badgeColor,
                            icon = icon,
                            isSelected = selectedSourceId == dev.deviceKey,
                            testTag = "midi_source_${dev.deviceKey}",
                            onClick = { selectedSourceId = dev.deviceKey }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: MIDI INPUT CHANNEL FILTER (OMNI OR 1..16)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MIDI INPUT CHANNEL FILTER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Text(
                        text = if (selectedInputChannel == 0) "Omni (Ch 1–16)" else "Channel $selectedInputChannel Only",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioYellow
                    )
                }
                Text(
                    text = "Assign different MIDI channels to separate tracks to split multi-timbral sequencers or controllers.",
                    fontSize = 10.sp,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )

                // Omni Button
                val isOmni = selectedInputChannel == MidiRoutingConstants.CHANNEL_OMNI
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (isOmni) StudioYellow else StudioBorder,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedInputChannel = MidiRoutingConstants.CHANNEL_OMNI }
                        .testTag("midi_in_ch_0"),
                    color = if (isOmni) StudioYellow.copy(alpha = 0.18f) else StudioCard
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OMNI (All Channels 1–16)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOmni) StudioYellow else StudioTextPrimary
                        )
                        if (isOmni) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected Omni",
                                tint = StudioYellow,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Channels 1..16 in a 4x4 grid
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 1..4) {
                            val ch = row * 4 + col
                            val isSelected = selectedInputChannel == ch
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) StudioYellow else StudioCard)
                                    .border(
                                        1.dp,
                                        if (isSelected) StudioYellow else StudioBorder,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedInputChannel = ch }
                                    .testTag("midi_in_ch_$ch"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Ch $ch",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) StudioDark else StudioTextPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Shortcut to Output Patch & Out Channel Selector
                OutlinedButton(
                    onClick = {
                        onSaveMidiRouting(selectedSourceId, selectedInputChannel)
                        onDismiss()
                        onOpenPatchSelector()
                    },
                    border = BorderStroke(1.dp, StudioYellow.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("midi_routing_open_patches_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = StudioYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Patch #${track.midiProgram + 1}: ${track.patchInfo.name} (Out Ch ${track.midiChannel})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioYellow
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveMidiRouting(selectedSourceId, selectedInputChannel)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan),
                modifier = Modifier.testTag("apply_midi_routing_btn")
            ) {
                Text("Apply MIDI Routing", color = StudioDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun MidiSourceCard(
    title: String,
    subtitle: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (isSelected) StudioCyan else StudioBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = if (isSelected) StudioCardElevated else StudioCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) StudioCyan else badgeColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = StudioTextPrimary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = StudioTextMuted,
                    lineHeight = 13.sp
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = StudioCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
