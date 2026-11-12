package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioTextMuted

@Composable
fun TrackWaveformView(
    waveformPoints: List<Float>,
    trackColor: Color,
    playheadProgress: Float, // 0.0 to 1.0
    durationMs: Long,
    isRecordingThisTrack: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF090D14))
            .pointerInput(waveformPoints, durationMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
            .pointerInput(waveformPoints, durationMs) {
                detectDragGestures { change, _ ->
                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            }
    ) {
        if (waveformPoints.isEmpty() && !isRecordingThisTrack) {
            // Empty state placeholder
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2f
                // Center line
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                // Grid dashes
                val dashSpacing = size.width / 12f
                for (i in 0..12) {
                    val x = i * dashSpacing
                    drawLine(
                        color = Color(0xFF161F2E),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Text(
                text = "Empty track • Arm [REC] & hit Record",
                color = StudioTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerY = canvasHeight / 2f

                // Background subtle grid lines
                val gridSpacing = canvasWidth / 8f
                for (g in 0..8) {
                    val gx = g * gridSpacing
                    drawLine(
                        color = Color(0xFF131B29),
                        start = Offset(gx, 0f),
                        end = Offset(gx, canvasHeight),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Center zero axis
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, centerY),
                    end = Offset(canvasWidth, centerY),
                    strokeWidth = 1.dp.toPx()
                )

                // Waveform bars
                if (waveformPoints.isNotEmpty()) {
                    val barCount = waveformPoints.size
                    val barWidth = (canvasWidth / barCount).coerceAtLeast(1f)
                    val gap = 1.dp.toPx()

                    for (i in waveformPoints.indices) {
                        val peak = waveformPoints[i].coerceIn(0.04f, 1.0f)
                        val barHeight = peak * (canvasHeight * 0.85f)
                        val x = i * (canvasWidth / barCount)
                        val top = centerY - (barHeight / 2f)

                        // Highlight portion before playhead slightly brighter
                        val isPast = (x / canvasWidth) <= playheadProgress
                        val barColor = if (isPast) {
                            trackColor.copy(alpha = 0.95f)
                        } else {
                            trackColor.copy(alpha = 0.55f)
                        }

                        drawRect(
                            color = barColor,
                            topLeft = Offset(x, top),
                            size = Size((barWidth - gap).coerceAtLeast(1f), barHeight)
                        )
                    }
                }

                // Playhead line
                if (playheadProgress in 0f..1f && durationMs > 0) {
                    val playheadX = playheadProgress * canvasWidth

                    // Glow line
                    drawLine(
                        color = StudioRed.copy(alpha = 0.4f),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, canvasHeight),
                        strokeWidth = 4.dp.toPx()
                    )
                    // Sharp playhead line
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, canvasHeight),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Playhead top marker triangle
                    val markerPath = Path().apply {
                        moveTo(playheadX - 5.dp.toPx(), 0f)
                        lineTo(playheadX + 5.dp.toPx(), 0f)
                        lineTo(playheadX, 6.dp.toPx())
                        close()
                    }
                    drawPath(markerPath, color = StudioRed)
                }
            }
        }
    }
}
