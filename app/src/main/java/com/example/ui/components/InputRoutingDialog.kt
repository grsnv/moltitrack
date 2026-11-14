package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import com.example.audio.AsioBufferSize
import com.example.audio.UsbAudioDeviceInfo
import com.example.model.AudioInputConstants
import com.example.model.AudioInputDevice
import com.example.model.AudioInputType
import com.example.model.InputChannelMode
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

@Composable
fun InputRoutingDialog(
    track: TrackModel,
    availableInputs: List<AudioInputDevice>,
    usbDevices: List<UsbAudioDeviceInfo>,
    selectedBufferSize: AsioBufferSize,
    selectedSampleRate: Int,
    liveMeterLevel: Float,
    onSaveRouting: (deviceId: String, channelMode: InputChannelMode) -> Unit,
    onSetBufferSize: (AsioBufferSize) -> Unit,
    onSetSampleRate: (Int) -> Unit,
    onRefreshInputs: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDevId by remember { mutableStateOf(track.inputDeviceId) }
    var selectedChannel by remember { mutableStateOf(track.inputChannelMode) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Routing, 1 = ASIO / USB Hardware

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(track.color)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Input Routing: ${track.name}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioTextPrimary
                    )
                }
                IconButton(
                    onClick = onRefreshInputs,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh devices",
                        tint = StudioCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Tab switcher: Input Assign vs ASIO / USB Config
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioDark,
                    contentColor = StudioCyan,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = StudioCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Track Input", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("ASIO / USB Panel", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // TAB 0: TRACK INPUT & CHANNEL ASSIGNMENT
                    Text(
                        text = "SELECT HARDWARE INPUT DEVICE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (availableInputs.isEmpty()) {
                        Text(
                            text = "No audio input devices detected",
                            color = StudioTextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        availableInputs.forEach { dev ->
                            val isSelected = dev.id == selectedDevId
                            val isUsb = dev.type == AudioInputType.USB_AUDIO
                            val isSystemAudio = dev.type == AudioInputType.SYSTEM_AUDIO

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
                                    .clickable { selectedDevId = dev.id }
                                    .testTag("device_item_${dev.id}"),
                                color = if (isSelected) StudioCardElevated else StudioCard
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (dev.type) {
                                            AudioInputType.USB_AUDIO -> Icons.Default.Usb
                                            AudioInputType.SYSTEM_AUDIO -> Icons.Default.GraphicEq
                                            AudioInputType.WIRED_HEADSET,
                                            AudioInputType.BLUETOOTH -> Icons.Default.Headphones
                                            else -> Icons.Default.Mic
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isUsb -> StudioGreen
                                            isSystemAudio -> StudioYellow
                                            isSelected -> StudioCyan
                                            else -> StudioTextSecondary
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dev.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = StudioTextPrimary
                                            )
                                            if (isUsb) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(StudioGreen.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "ASIO / UAC",
                                                        color = StudioGreen,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else if (isSystemAudio) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(StudioYellow.copy(alpha = 0.2f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "INTERNAL",
                                                        color = StudioYellow,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = when (dev.type) {
                                                AudioInputType.USB_AUDIO -> "USB Audio Class Device (Direct HW Stream)"
                                                AudioInputType.SYSTEM_AUDIO -> "Records playback audio from other apps on this device"
                                                AudioInputType.BUILTIN_MIC -> "On-device built-in microphone"
                                                AudioInputType.WIRED_HEADSET -> "3.5mm TRRS external headset / mic"
                                                AudioInputType.BLUETOOTH -> "Bluetooth SCO wireless mic"
                                                AudioInputType.DEFAULT -> "System Default Mic"
                                            },
                                            fontSize = 11.sp,
                                            color = StudioTextMuted
                                        )
                                    }
                                    if (isSelected) {
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

                        if (selectedDevId == AudioInputConstants.SYSTEM_AUDIO_ID) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = StudioYellow.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioYellow.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "When you press REC, Android will prompt to authorize internal audio capture. MultiTrack's own playback is automatically excluded to prevent feedback.",
                                    fontSize = 11.sp,
                                    color = StudioYellow,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "CHANNEL ROUTING / ISOLATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Text(
                        text = "Separate stereo or multi-input USB interfaces into isolated tracks:",
                        fontSize = 11.sp,
                        color = StudioTextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    InputChannelMode.entries.forEach { mode ->
                        val isChanSelected = mode == selectedChannel
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (isChanSelected) StudioYellow else StudioBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedChannel = mode }
                                .testTag("channel_item_${mode.name}"),
                            color = if (isChanSelected) StudioCardElevated else StudioCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isChanSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChanSelected) StudioYellow else StudioTextPrimary
                                    )
                                    val desc = when (mode) {
                                        InputChannelMode.LEFT_OR_CH1 -> "Captures Input 1 / Mic 1 / Left channel as dedicated mono track"
                                        InputChannelMode.RIGHT_OR_CH2 -> "Captures Input 2 / Mic 2 / Right channel as dedicated mono track"
                                        InputChannelMode.STEREO -> "Captures interleaved Stereo (both channels in phase)"
                                        InputChannelMode.MONO_SUM -> "Mixes and sums Left + Right channels into one mono track"
                                    }
                                    Text(
                                        text = desc,
                                        fontSize = 10.sp,
                                        color = StudioTextMuted
                                    )
                                }
                                if (isChanSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = StudioYellow,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Input Meter Preview
                    Text(
                        text = "LIVE INPUT METER PREVIEW",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    VuMeterBar(level = liveMeterLevel, height = 7.dp)
                    Text(
                        text = if (selectedDevId == AudioInputConstants.SYSTEM_AUDIO_ID) {
                            "System audio meter activates once recording starts after Android capture permission is granted"
                        } else {
                            "Speak into your input to test signal before recording"
                        },
                        fontSize = 10.sp,
                        color = StudioTextMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                } else {
                    // TAB 1: ASIO4ALL & USB HARDWARE CONTROL PANEL
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioCardElevated)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = StudioGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ASIO4ALL / USB AUDIO CLASS (UAC)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "USB Audio interfaces that use ASIO4ALL on Windows (Focusrite, Behringer, PreSonus, Zoom, Rode) operate as class-compliant USB Audio devices on Android. Use this panel to configure hardware buffer size and sample rates:",
                                fontSize = 11.sp,
                                color = StudioTextSecondary,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Buffer Size Selection (ASIO Latency)
                    Text(
                        text = "ASIO BUFFER SIZE (LATENCY)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    AsioBufferSize.entries.forEach { buf ->
                        val isCurrent = buf == selectedBufferSize
                        val latency = buf.latencyMs(selectedSampleRate)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, if (isCurrent) StudioGreen else StudioBorder, RoundedCornerShape(6.dp))
                                .clickable { onSetBufferSize(buf) },
                            color = if (isCurrent) StudioCardElevated else StudioCard
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = buf.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) StudioGreen else StudioTextPrimary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f ms", latency),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isCurrent) StudioGreen else StudioTextMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sample Rate Selection
                    Text(
                        text = "SAMPLE RATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(44100 to "44.1 kHz", 48000 to "48.0 kHz", 96000 to "96.0 kHz").forEach { (rate, label) ->
                            val isRate = rate == selectedSampleRate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isRate) StudioCyan else StudioCard)
                                    .border(1.dp, if (isRate) StudioCyan else StudioBorder, RoundedCornerShape(6.dp))
                                    .clickable { onSetSampleRate(rate) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRate) StudioDark else StudioTextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Connected USB Hardware inspection
                    Text(
                        text = "USB OTG HARDWARE STATUS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = StudioTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    if (usbDevices.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = StudioCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "No direct USB OTG hardware device detected right now",
                                    fontSize = 12.sp,
                                    color = StudioTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Plug in your USB audio interface with a USB-C or OTG adapter. If prompted by Android, grant USB device access.",
                                    fontSize = 11.sp,
                                    color = StudioTextMuted
                                )
                            }
                        }
                    } else {
                        usbDevices.forEach { usb ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                color = StudioCard,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StudioGreen)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Usb,
                                            contentDescription = null,
                                            tint = StudioGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = usb.productName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = StudioTextPrimary
                                        )
                                    }
                                    Text(
                                        text = "Manufacturer: ${usb.manufacturerName} • ID: ${usb.vendorProductHex}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = StudioTextSecondary
                                    )
                                    Text(
                                        text = "UAC Multi-channel: ~${usb.estimatedChannels} channels supported",
                                        fontSize = 11.sp,
                                        color = StudioGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveRouting(selectedDevId, selectedChannel)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
            ) {
                Text("Apply Routing", color = StudioDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = StudioTextSecondary)
            }
        },
        containerColor = StudioSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
