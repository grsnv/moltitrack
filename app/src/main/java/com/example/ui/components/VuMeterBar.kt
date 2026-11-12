package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioRed
import com.example.ui.theme.StudioYellow

@Composable
fun VuMeterBar(
    level: Float, // 0.0 to 1.0
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    segmentCount: Int = 16
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 800f),
        label = "vu_level"
    )

    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .clip(RoundedCornerShape(height / 2))
            .background(Color(0xFF0D131F))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            val activeSegments = (animatedLevel * segmentCount).toInt()
            for (i in 0 until segmentCount) {
                val isActive = i < activeSegments
                val segmentColor = when {
                    i >= (segmentCount * 0.85f).toInt() -> if (isActive) StudioRed else Color(0x33FF3B30)
                    i >= (segmentCount * 0.65f).toInt() -> if (isActive) StudioYellow else Color(0x33FBBF24)
                    else -> if (isActive) StudioGreen else Color(0x3334D399)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(segmentColor)
                )

                if (i < segmentCount - 1) {
                    Spacer(modifier = Modifier.width(1.dp).background(Color(0xFF0D131F)))
                }
            }
        }
    }
}

@Composable
fun VerticalMasterVuMeter(
    leftLevel: Float,
    rightLevel: Float,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
    height: Dp = 48.dp
) {
    Row(modifier = modifier) {
        VerticalVuBar(level = leftLevel, width = width / 2, height = height)
        Spacer(modifier = Modifier.width(2.dp))
        VerticalVuBar(level = rightLevel, width = width / 2, height = height)
    }
}

@Composable
private fun VerticalVuBar(
    level: Float,
    width: Dp,
    height: Dp,
    segments: Int = 12
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 700f),
        label = "v_vu"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0D131F))
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth().fillMaxHeight()
        ) {
            val activeCount = (animatedLevel * segments).toInt()
            for (i in (segments - 1) downTo 0) {
                val isActive = i < activeCount
                val color = when {
                    i >= (segments * 0.85f).toInt() -> if (isActive) StudioRed else Color(0x22FF3B30)
                    i >= (segments * 0.65f).toInt() -> if (isActive) StudioYellow else Color(0x22FBBF24)
                    else -> if (isActive) StudioGreen else Color(0x2234D399)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(color)
                )
                if (i > 0) {
                    Spacer(modifier = Modifier.height(1.dp).background(Color(0xFF0D131F)))
                }
            }
        }
    }
}
