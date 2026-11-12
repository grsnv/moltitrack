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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.Locale

@Composable
fun MasterTransportBar(
    isRecording: Boolean,
    isPlaying: Boolean,
    isLooping: Boolean,
    currentTimeMs: Long,
    masterMeterLeft: Float,
    masterMeterRight: Float,
    onRecordToggle: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onLoopToggle: () -> Unit,
    onAddTrack: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_tally")
    val recPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tally_scale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .navigationBarsPadding(),
        color = StudioSurface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LCD Digital Timecode display
                val minutes = (currentTimeMs / 1000) / 60
                val seconds = (currentTimeMs / 1000) % 60
                val centis = (currentTimeMs % 1000) / 10
                val timeStr = String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(StudioDark)
                        .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StudioRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = timeStr,
                            color = if (isRecording) StudioRed else StudioCyan,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }

                // Transport Central Buttons: Stop, Record, Play, Loop
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Loop Button
                    IconButton(
                        onClick = onLoopToggle,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isLooping) StudioCyan.copy(alpha = 0.2f) else StudioCard)
                            .testTag("loop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Toggle Loop",
                            tint = if (isLooping) StudioCyan else StudioTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Stop Button (Rewind)
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(StudioCardElevated)
                            .border(1.dp, StudioBorder, CircleShape)
                            .testTag("stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop & Rewind",
                            tint = StudioTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Big Record Button
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) StudioRed else StudioCardElevated)
                            .border(2.dp, StudioRed, CircleShape)
                            .clickable { onRecordToggle() }
                            .testTag("record_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isRecording) 20.dp else 24.dp)
                                .clip(if (isRecording) RoundedCornerShape(4.dp) else CircleShape)
                                .background(if (isRecording) Color.White else StudioRed)
                        )
                    }

                    // Play / Pause Button
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) StudioGreen else StudioCyan)
                            .testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = StudioDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Master Stereo VU meters & Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VerticalMasterVuMeter(
                        leftLevel = masterMeterLeft,
                        rightLevel = masterMeterRight,
                        width = 12.dp,
                        height = 36.dp
                    )

                    // Export / Share
                    IconButton(
                        onClick = onExportClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioCardElevated)
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .testTag("share_export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Export & Share Stems",
                            tint = StudioCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Add Track Button
                    IconButton(
                        onClick = onAddTrack,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioCyan)
                            .testTag("add_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Track",
                            tint = StudioDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
