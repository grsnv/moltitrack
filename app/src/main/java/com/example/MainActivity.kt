package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.Piano
import com.example.model.TrackModel
import com.example.ui.components.ExportDialog
import com.example.ui.components.InputRoutingDialog
import com.example.ui.components.MasterTransportBar
import com.example.ui.components.MidiPatchSelectorDialog
import com.example.ui.components.MidiRoutingDialog
import com.example.ui.components.PianoRollModal
import com.example.ui.components.TrackLaneCard
import com.example.ui.theme.MultiTrackTheme
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
import com.example.viewmodel.MultiTrackViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiTrackTheme {
                MainStudioScreen()
            }
        }
    }
}

@Composable
fun MainStudioScreen(viewModel: MultiTrackViewModel = viewModel()) {
    val context = LocalContext.current

    // Runtime Permission State
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRecordPermission = granted
        if (granted) {
            viewModel.refreshInputs()
        }
    }

    // ViewModel State
    val session by viewModel.currentSession.collectAsStateWithLifecycle()
    val availableInputs by viewModel.availableInputs.collectAsStateWithLifecycle()
    val connectedUsbDevices by viewModel.connectedUsbDevices.collectAsStateWithLifecycle()
    val connectedMidiDevices by viewModel.connectedMidiDevices.collectAsStateWithLifecycle()
    val lastMidiActivity by viewModel.lastMidiActivity.collectAsStateWithLifecycle()
    val isVirtualMidiBound by viewModel.isVirtualMidiBound.collectAsStateWithLifecycle()
    val virtualExternalSendersCount by viewModel.virtualExternalSendersCount.collectAsStateWithLifecycle()
    val selectedBufferSize by viewModel.selectedBufferSize.collectAsStateWithLifecycle()
    val selectedSampleRate by viewModel.selectedSampleRate.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isLooping by viewModel.isLooping.collectAsStateWithLifecycle()
    val recordDurationMs by viewModel.recordDurationMs.collectAsStateWithLifecycle()
    val playheadPositionMs by viewModel.playheadPositionMs.collectAsStateWithLifecycle()
    val totalDurationMs by viewModel.totalDurationMs.collectAsStateWithLifecycle()
    val masterMeterLevel by viewModel.masterMeterLevel.collectAsStateWithLifecycle()
    val trackMeters by viewModel.trackMeters.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    // Dialog States
    var routingTrackTarget by remember { mutableStateOf<TrackModel?>(null) }
    var midiRoutingTrackTarget by remember { mutableStateOf<TrackModel?>(null) }
    var patchSelectorTrackTarget by remember { mutableStateOf<TrackModel?>(null) }
    var pianoRollTrackTarget by remember { mutableStateOf<TrackModel?>(null) }
    var showAddTrackTypeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }

    val currentTimeMs = if (isRecording) recordDurationMs else playheadPositionMs

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StudioDark,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            MasterTransportBar(
                isRecording = isRecording,
                isPlaying = isPlaying,
                isLooping = isLooping,
                currentTimeMs = currentTimeMs,
                masterMeterLeft = masterMeterLevel.first,
                masterMeterRight = masterMeterLevel.second,
                onRecordToggle = {
                    if (!hasRecordPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                    }
                },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onStop = { viewModel.stopPlayback() },
                onLoopToggle = { viewModel.toggleLoop() },
                onAddTrack = { showAddTrackTypeDialog = true },
                onExportClick = { showExportDialog = true },
                modifier = Modifier.testTag("master_transport_bar")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // STUDIO TOP APP BAR
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = StudioSurface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Logo & Brand
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioCardElevated)
                                    .border(1.dp, StudioCyan, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "MultiTrack Logo",
                                    tint = StudioCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "MultiTrack",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = StudioTextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(StudioCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "STUDIO",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = StudioCyan
                                        )
                                    }
                                }
                                Text(
                                    text = session.title,
                                    fontSize = 11.sp,
                                    color = StudioTextSecondary,
                                    maxLines = 1
                                )
                            }
                        }

                        // Top Action Buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Demo Session Loader Button
                            Button(
                                onClick = { viewModel.loadDemoSession() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StudioCardElevated,
                                    contentColor = StudioYellow
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("load_demo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Demo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // New Session Button
                            IconButton(
                                onClick = { showNewSessionDialog = true },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioCardElevated)
                                    .testTag("new_session_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NoteAdd,
                                    contentDescription = "New Session",
                                    tint = StudioTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware Status Banner (USB / ASIO4ALL / Built-in Mic)
                    val hasUsb = connectedUsbDevices.isNotEmpty()
                    val hardwareTitle = if (hasUsb) {
                        val dev = connectedUsbDevices.first()
                        "${dev.productName} • ASIO/UAC Mode (${selectedBufferSize.samples} smp / ${String.format("%.1f", selectedBufferSize.latencyMs(selectedSampleRate))} ms)"
                    } else {
                        "USB Audio / ASIO4ALL Ready • ${availableInputs.size} Input Source(s) Detected"
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val target = session.tracks.firstOrNull { it.type == com.example.model.TrackType.AUDIO }
                                    ?: session.tracks.firstOrNull()
                                if (target?.type == com.example.model.TrackType.MIDI) {
                                    midiRoutingTrackTarget = target
                                } else {
                                    routingTrackTarget = target
                                }
                            },
                        color = if (hasUsb) StudioGreen.copy(alpha = 0.12f) else StudioCardElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasUsb) StudioGreen.copy(alpha = 0.4f) else StudioBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasUsb) Icons.Default.Usb else Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (hasUsb) StudioGreen else StudioCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = hardwareTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (hasUsb) StudioGreen else StudioTextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Configure ASIO",
                                tint = if (hasUsb) StudioGreen else StudioTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // User Notification Message Toast/Banner
            AnimatedVisibility(
                visible = userMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                userMessage?.let { msg ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = StudioCardElevated,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                color = StudioCyan,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearMessage() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = StudioTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Permission Request Card (if not granted yet)
            if (!hasRecordPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioCardElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioYellow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = StudioYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Microphone & USB Audio Access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = StudioTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "To record multi-track audio from built-in microphones, external headsets, and USB audio interfaces, please grant audio recording permission.",
                            fontSize = 12.sp,
                            color = StudioTextSecondary,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioYellow),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Grant Permission", color = StudioDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Multi-Track Lanes LazyColumn
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = session.tracks,
                    key = { it.id }
                ) { track ->
                    val inputDevice = availableInputs.firstOrNull { it.id == track.inputDeviceId }
                    val meterLevel = trackMeters[track.id] ?: 0f

                    val playheadFrac = if (totalDurationMs > 0) {
                        (playheadPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    } else 0f

                    TrackLaneCard(
                        track = track,
                        inputDevice = inputDevice,
                        connectedMidiDevices = connectedMidiDevices,
                        isRecordingActive = isRecording,
                        playheadProgress = playheadFrac,
                        meterLevel = meterLevel,
                        onToggleArm = { viewModel.toggleTrackArm(track.id) },
                        onToggleMute = { viewModel.toggleTrackMute(track.id) },
                        onToggleSolo = { viewModel.toggleTrackSolo(track.id) },
                        onVolumeChange = { viewModel.setTrackVolume(track.id, it) },
                        onPanChange = { viewModel.setTrackPan(track.id, it) },
                        onSeek = { fraction ->
                            val targetMs = (fraction * totalDurationMs).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        onOpenRouting = { routingTrackTarget = track },
                        onOpenPianoRoll = { pianoRollTrackTarget = track },
                        onOpenMidiRouting = { midiRoutingTrackTarget = track },
                        onOpenPatchSelector = { patchSelectorTrackTarget = track },
                        onRename = { newName -> viewModel.renameTrack(track.id, newName) },
                        onClearAudio = { viewModel.clearTrackAudio(track.id) },
                        onDeleteTrack = { viewModel.deleteTrack(track.id) }
                    )
                }

                // Add Track bottom helper button
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable { showAddTrackTypeDialog = true }
                            .padding(vertical = 12.dp)
                            .testTag("add_track_list_button"),
                        color = StudioCard
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = StudioCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add New Track (Audio / MIDI)",
                                color = StudioCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Input Routing & ASIO Hardware Dialog
    routingTrackTarget?.let { track ->
        val trackMeter = trackMeters[track.id] ?: 0f
        InputRoutingDialog(
            track = track,
            availableInputs = availableInputs,
            usbDevices = connectedUsbDevices,
            selectedBufferSize = selectedBufferSize,
            selectedSampleRate = selectedSampleRate,
            liveMeterLevel = trackMeter,
            onSaveRouting = { devId, channelMode ->
                viewModel.updateTrackRouting(track.id, devId, channelMode)
            },
            onSetBufferSize = { viewModel.setBufferSize(it) },
            onSetSampleRate = { viewModel.setSampleRate(it) },
            onRefreshInputs = { viewModel.refreshInputs() },
            onDismiss = { routingTrackTarget = null }
        )
    }

    // Add Track Type Selection Dialog (Audio vs MIDI)
    if (showAddTrackTypeDialog) {
        AlertDialog(
            onDismissRequest = { showAddTrackTypeDialog = false },
            title = {
                Text(
                    text = "Add Track",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = StudioTextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Option 1: Audio Track
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.addTrack()
                                showAddTrackTypeDialog = false
                            }
                            .padding(12.dp)
                            .testTag("add_audio_track_option"),
                        color = StudioCardElevated
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StudioCyan.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = StudioCyan, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Audio Track", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StudioTextPrimary)
                                Text("Record from USB Audio interface, built-in mic, or headset", fontSize = 11.sp, color = StudioTextMuted)
                            }
                        }
                    }

                    // Option 2: MIDI Synth Track
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, StudioYellow.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.addMidiTrack()
                                showAddTrackTypeDialog = false
                            }
                            .padding(12.dp)
                            .testTag("add_midi_track_option"),
                        color = StudioCardElevated
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StudioYellow.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Piano, contentDescription = null, tint = StudioYellow, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("MIDI Track", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = StudioYellow)
                                Text("Play & record with USB MIDI Keyboard or Touch Keys Synth", fontSize = 11.sp, color = StudioTextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTrackTypeDialog = false }) {
                    Text("Cancel", color = StudioTextSecondary)
                }
            },
            containerColor = StudioSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Piano Roll & Virtual Keyboard Modal for MIDI Track
    pianoRollTrackTarget?.let { trackTarget ->
        val latestTrack = session.tracks.firstOrNull { it.id == trackTarget.id } ?: trackTarget
        PianoRollModal(
            track = latestTrack,
            connectedMidiDevices = connectedMidiDevices,
            isRecording = isRecording,
            currentTimeMs = currentTimeMs,
            onAddNote = { note ->
                viewModel.addMidiNoteToTrack(latestTrack.id, note)
            },
            onRemoveNote = { noteId ->
                viewModel.removeMidiNoteFromTrack(latestTrack.id, noteId)
            },
            onClearNotes = {
                viewModel.clearMidiNotes(latestTrack.id)
            },
            onAuditionNote = { noteNumber ->
                viewModel.auditionMidiNote(latestTrack, noteNumber)
            },
            onOpenPatchSelector = {
                patchSelectorTrackTarget = latestTrack
            },
            onOpenMidiRouting = {
                midiRoutingTrackTarget = latestTrack
            },
            onDismiss = { pianoRollTrackTarget = null }
        )
    }

    // MIDI Input Channel & Inter-App Routing Dialog
    midiRoutingTrackTarget?.let { trackTarget ->
        val latestTrack = session.tracks.firstOrNull { it.id == trackTarget.id } ?: trackTarget
        MidiRoutingDialog(
            track = latestTrack,
            connectedMidiDevices = connectedMidiDevices,
            lastMidiActivity = lastMidiActivity,
            isVirtualMidiBound = isVirtualMidiBound,
            virtualExternalSendersCount = virtualExternalSendersCount,
            onSaveMidiRouting = { inputDeviceId, inputChannel ->
                viewModel.updateMidiTrackRouting(latestTrack.id, inputDeviceId, inputChannel)
            },
            onRefreshMidiDevices = { viewModel.refreshMidiDevices() },
            onOpenPatchSelector = { patchSelectorTrackTarget = latestTrack },
            onDismiss = { midiRoutingTrackTarget = null }
        )
    }

    // All-Patches Synth Sound & Hardware Bank/PC Selector Dialog
    patchSelectorTrackTarget?.let { trackTarget ->
        val latestTrack = session.tracks.firstOrNull { it.id == trackTarget.id } ?: trackTarget
        MidiPatchSelectorDialog(
            track = latestTrack,
            hasConnectedMidiDevice = connectedMidiDevices.isNotEmpty(),
            onSelectPatch = { program, bankMsb, bankLsb, channel ->
                viewModel.setTrackMidiPatch(latestTrack.id, program, bankMsb, bankLsb, channel)
            },
            onAuditionNote = { program, bankMsb, bankLsb, channel, noteNumber ->
                viewModel.auditionPatchNote(
                    program = program,
                    bankMsb = bankMsb,
                    bankLsb = bankLsb,
                    channel = channel,
                    noteNumber = noteNumber
                )
            },
            onDismiss = { patchSelectorTrackTarget = null }
        )
    }

    // Export Master / Stems Dialog
    if (showExportDialog) {
        ExportDialog(
            sessionTitle = session.title,
            tracks = session.tracks,
            onExportMaster = { viewModel.shareMasterMix() },
            onExportStems = { viewModel.shareStems() },
            onDismiss = { showExportDialog = false }
        )
    }

    // New Session Confirmation Dialog
    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("Start New Session?") },
            text = { Text("This will reset the mixer and create a fresh multi-track project.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetSession()
                        showNewSessionDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioCyan)
                ) {
                    Text("New Project", color = StudioDark)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
