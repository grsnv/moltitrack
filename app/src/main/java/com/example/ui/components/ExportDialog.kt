package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TrackModel
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioTextMuted
import com.example.ui.theme.StudioTextPrimary
import com.example.ui.theme.StudioTextSecondary

@Composable
fun ExportDialog(
    sessionTitle: String,
    tracks: List<TrackModel>,
    onExportMaster: () -> Unit,
    onExportStems: () -> Unit,
    onDismiss: () -> Unit
) {
    val recordedTracks = tracks.filter { it.audioFilePath != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    tint = StudioCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export Audio & Stems",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioTextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$sessionTitle • ${recordedTracks.size} recorded track(s)",
                    fontSize = 12.sp,
                    color = StudioTextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Option 1: Master Stereo Mix
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = recordedTracks.isNotEmpty()) {
                            onExportMaster()
                            onDismiss()
                        }
                        .testTag("export_master_button"),
                    color = StudioCardElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = StudioCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Export Master Stereo Mix (WAV)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "Mixes down all tracks with your volume faders, pan, and solo/mute settings into a stereo 16-bit WAV file.",
                                fontSize = 11.sp,
                                color = StudioTextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Individual Track Stems
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .clickable(enabled = recordedTracks.isNotEmpty()) {
                            onExportStems()
                            onDismiss()
                        }
                        .testTag("export_stems_button"),
                    color = StudioCardElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = StudioGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Export Individual Track Stems (WAV)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = StudioTextPrimary
                            )
                            Text(
                                text = "Saves each recorded track as an isolated uncompressed WAV file, ready for importing into desktop DAWs.",
                                fontSize = 11.sp,
                                color = StudioTextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                if (recordedTracks.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No audio recorded yet. Record tracks or load the demo session first.",
                        fontSize = 11.sp,
                        color = StudioTextMuted
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
