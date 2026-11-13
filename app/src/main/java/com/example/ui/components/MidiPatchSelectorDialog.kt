package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.model.GeneralMidiPatches
import com.example.model.MidiPatchInfo
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

@Composable
fun MidiPatchSelectorDialog(
    track: TrackModel,
    hasConnectedMidiDevice: Boolean,
    onSelectPatch: (program: Int, bankMsb: Int, bankLsb: Int, channel: Int) -> Unit,
    onAuditionNote: (program: Int, bankMsb: Int, bankLsb: Int, channel: Int, note: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = GM Sound Library, 1 = Direct Hardware Bank/Program
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var selectedProgram by remember { mutableIntStateOf(track.midiProgram) }
    var bankMsb by remember { mutableIntStateOf(track.midiBankMsb) }
    var bankLsb by remember { mutableIntStateOf(track.midiBankLsb) }
    var midiChannel by remember { mutableIntStateOf(track.midiChannel) }

    val filteredPatches = remember(selectedCategory, searchQuery) {
        GeneralMidiPatches.allPatches.filter { patch ->
            val matchesCategory = selectedCategory == "All" || patch.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || patch.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

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
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = StudioYellow,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Synth Sound & Hardware Patches",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = "${track.name} • Active: ${GeneralMidiPatches.findPatch(selectedProgram).name}",
                            fontSize = 11.sp,
                            color = StudioYellow
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = StudioTextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                // Tab Row: GM Sound Library vs Direct Hardware Bank/Program
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
                        text = { Text("128 GM Library", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Hardware Bank / CC", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTab == 0) {
                    // TAB 0: 128 GENERAL MIDI PATCH BROWSER
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search patches (e.g. Rhodes, Organ, Saw, Slap)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = StudioTextMuted, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, null, tint = StudioTextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StudioYellow,
                            unfocusedBorderColor = StudioBorder,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal Category Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val categories = listOf("All") + GeneralMidiPatches.categories
                        categories.forEach { cat ->
                            val isCat = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isCat) StudioYellow else StudioCardElevated)
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = if (isCat) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCat) StudioDark else StudioTextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Patch List
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredPatches, key = { it.programNumber }) { patch ->
                            val isSelected = patch.programNumber == selectedProgram
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (isSelected) StudioYellow else StudioBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedProgram = patch.programNumber
                                        onAuditionNote(patch.programNumber, bankMsb, bankLsb, midiChannel, 60)
                                    }
                                    .testTag("patch_item_${patch.programNumber}"),
                                color = if (isSelected) StudioCardElevated else StudioCard
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = String.format("%03d", patch.programNumber + 1),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) StudioYellow else StudioTextMuted,
                                            modifier = Modifier.width(32.dp)
                                        )
                                        Column {
                                            Text(
                                                text = patch.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) StudioYellow else StudioTextPrimary
                                            )
                                            Text(
                                                text = "${patch.category} • Synth: ${patch.synthFamily.displayName}",
                                                fontSize = 10.sp,
                                                color = StudioTextMuted
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = StudioYellow,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        IconButton(
                                            onClick = {
                                                selectedProgram = patch.programNumber
                                                onAuditionNote(patch.programNumber, bankMsb, bankLsb, midiChannel, 60)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Audition",
                                                tint = StudioCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // TAB 1: DIRECT HARDWARE BANK & PROGRAM CHANGE DIALS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioCardElevated)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Usb,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DIRECT HARDWARE SYNTH CONTROL",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Bank Select (CC 0 / CC 32) and Program Change (0-127) to recall any proprietary sound patch or user bank in your connected hardware synthesizer.",
                                fontSize = 11.sp,
                                color = StudioTextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Program Change Number Slider (0 - 127)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "PROGRAM CHANGE (PC)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StudioTextMuted
                        )
                        Text(
                            text = "PC #$selectedProgram (${selectedProgram + 1})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StudioYellow
                        )
                    }
                    Slider(
                        value = selectedProgram.toFloat(),
                        onValueChange = { selectedProgram = it.toInt() },
                        valueRange = 0f..127f,
                        steps = 127,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioYellow,
                            activeTrackColor = StudioYellow,
                            inactiveTrackColor = StudioCardElevated
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bank Select MSB (CC 0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "BANK SELECT MSB (CC 0)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StudioTextMuted
                        )
                        Text(
                            text = "Bank: $bankMsb",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = StudioCyan
                        )
                    }
                    Slider(
                        value = bankMsb.toFloat(),
                        onValueChange = { bankMsb = it.toInt() },
                        valueRange = 0f..127f,
                        steps = 127,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioCyan,
                            activeTrackColor = StudioCyan,
                            inactiveTrackColor = StudioCardElevated
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // MIDI Out Channel (1 - 16)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MIDI CHANNEL (1-16)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StudioTextMuted
                        )
                        Text(
                            text = "Ch $midiChannel",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = StudioGreen
                        )
                    }
                    Slider(
                        value = midiChannel.toFloat(),
                        onValueChange = { midiChannel = it.toInt() },
                        valueRange = 1f..16f,
                        steps = 15,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioGreen,
                            activeTrackColor = StudioGreen,
                            inactiveTrackColor = StudioCardElevated
                        ),
                        modifier = Modifier.height(28.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Audition Note Button
                    Button(
                        onClick = { onAuditionNote(selectedProgram, bankMsb, bankLsb, midiChannel, 60) },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCardElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Audition Note on Hardware", color = StudioCyan, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectPatch(selectedProgram, bankMsb, bankLsb, midiChannel)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioYellow)
            ) {
                Text("Apply Patch", color = StudioDark, fontWeight = FontWeight.Bold)
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
