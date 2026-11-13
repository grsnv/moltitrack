package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExporter
import com.example.audio.AudioInputManager
import com.example.audio.MidiSynthEngine
import com.example.audio.MultiTrackPlayerEngine
import com.example.audio.MultiTrackRecorderEngine
import com.example.audio.UsbAudioInterfaceManager
import com.example.audio.WavHelper
import com.example.model.AudioInputDevice
import com.example.model.InputChannelMode
import com.example.model.MidiActivityEvent
import com.example.model.MidiEventType
import com.example.model.MidiNote
import com.example.model.MidiRoutingConstants
import com.example.model.RecordedMidiEvent
import com.example.model.RecordingSession
import com.example.model.TrackModel
import com.example.model.TrackType
import com.example.ui.theme.TrackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class MultiTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val inputManager = AudioInputManager(application)
    val availableInputs: StateFlow<List<AudioInputDevice>> = inputManager.availableInputs

    private val usbInterfaceManager = UsbAudioInterfaceManager(application)
    val connectedUsbDevices = usbInterfaceManager.connectedUsbAudio
    val selectedBufferSize = usbInterfaceManager.selectedBufferSize
    val selectedSampleRate = usbInterfaceManager.selectedSampleRate

    private val midiManager = com.example.audio.AndroidMidiManager(application) { event ->
        handleExternalMidiEvent(event)
    }
    val connectedMidiDevices = midiManager.connectedMidiDevices
    val lastMidiActivity = midiManager.lastMidiActivity
    val isVirtualMidiBound = midiManager.isVirtualServiceBound
    val virtualExternalSendersCount = midiManager.virtualExternalSendersCount

    private val recorderEngine = MultiTrackRecorderEngine(application, viewModelScope)
    private val playerEngine = MultiTrackPlayerEngine(viewModelScope)

    val isRecording: StateFlow<Boolean> = recorderEngine.isRecordingFlow
    val isPlaying: StateFlow<Boolean> = playerEngine.isPlayingFlow
    val recordDurationMs: StateFlow<Long> = recorderEngine.recordingDurationMs
    val playheadPositionMs: StateFlow<Long> = playerEngine.currentPositionMs
    val totalDurationMs: StateFlow<Long> = playerEngine.totalDurationMs
    val masterMeterLevel: StateFlow<Pair<Float, Float>> = playerEngine.masterMeterLevel

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _currentSession = MutableStateFlow(createDefaultSession())
    val currentSession: StateFlow<RecordingSession> = _currentSession.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Tracks open Note On IDs while recording so Note Off sets accurate note duration: (trackId, channel, noteNumber) -> noteId
    private val activeRecordingNotes = ConcurrentHashMap<Triple<String, Int, Int>, String>()

    val trackMeters: StateFlow<Map<String, Float>> = combine(
        recorderEngine.trackMeterLevels,
        playerEngine.trackPlaybackMeters,
        isRecording,
        isPlaying
    ) { recMeters, playMeters, rec, play ->
        when {
            rec -> recMeters
            play -> playMeters
            else -> emptyMap()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        File(getApplication<Application>().filesDir, "sessions").apply { mkdirs() }
    }

    private fun createDefaultSession(): RecordingSession {
        val tracks = listOf(
            TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 1 - Mic L / In 1",
                colorHex = TrackColors[0].value.toLong(),
                inputDeviceId = "default_mic",
                inputChannelMode = InputChannelMode.LEFT_OR_CH1,
                isArmed = true,
                volume = 1.0f,
                pan = -0.3f
            ),
            TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 2 - Mic R / In 2",
                colorHex = TrackColors[1].value.toLong(),
                inputDeviceId = "default_mic",
                inputChannelMode = InputChannelMode.RIGHT_OR_CH2,
                isArmed = true,
                volume = 1.0f,
                pan = 0.3f
            )
        )
        return RecordingSession(
            id = UUID.randomUUID().toString(),
            title = "New MultiTrack Session",
            tracks = tracks
        )
    }

    fun startRecording(): Boolean {
        if (isPlaying.value) {
            playerEngine.stop()
        }

        val session = _currentSession.value
        val armedTracks = session.tracks.filter { it.isArmed }
        if (armedTracks.isEmpty()) {
            _userMessage.value = "Please arm at least one track [REC] to record."
            return false
        }

        activeRecordingNotes.clear()
        val sessionDir = File(getApplication<Application>().filesDir, "sessions/${session.id}")
        val started = recorderEngine.startRecording(
            tracksToRecord = session.tracks,
            availableDevices = availableInputs.value,
            sessionDir = sessionDir
        ) { recordedFiles ->
            onRecordingComplete(recordedFiles)
        }

        if (started) {
            _userMessage.value = "Recording started on ${armedTracks.size} track(s)"
        }
        return started
    }

    fun stopRecording() {
        activeRecordingNotes.clear()
        recorderEngine.stopRecording()
    }

    private fun onRecordingComplete(recordedFiles: Map<String, File>) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = File(getApplication<Application>().filesDir, "sessions/${_currentSession.value.id}").apply { mkdirs() }
            val updatedTracks = _currentSession.value.tracks.map { track ->
                val audioFile = recordedFiles[track.id]
                if (audioFile != null && audioFile.exists()) {
                    val waveform = WavHelper.extractWaveform(audioFile)
                    val duration = (max(0L, audioFile.length() - 44) * 1000L) / (44100 * 2)
                    track.copy(
                        audioFilePath = audioFile.absolutePath,
                        waveformPoints = waveform,
                        durationMs = duration
                    )
                } else if (track.type == TrackType.MIDI && track.isArmed) {
                    val midiFile = track.audioFilePath?.let { File(it) } ?: File(sessionDir, "midi_${track.id}.wav")
                    MidiSynthEngine.renderMidiToWav(
                        notes = track.midiNotes,
                        outputFile = midiFile,
                        midiEvents = track.midiEvents
                    )
                    val waveform = WavHelper.extractWaveform(midiFile)
                    val duration = (max(0L, midiFile.length() - 44) * 1000L) / (44100 * 2)
                    track.copy(
                        audioFilePath = midiFile.absolutePath,
                        waveformPoints = waveform,
                        durationMs = duration
                    )
                } else {
                    track
                }
            }

            val maxDur = updatedTracks.maxOfOrNull { it.durationMs } ?: 0L
            _currentSession.value = _currentSession.value.copy(
                tracks = updatedTracks,
                durationMs = maxDur
            )
            _userMessage.value = "Recording saved (${updatedTracks.count { it.isArmed }} armed track(s))"
        }
    }

    fun togglePlayPause() {
        if (isRecording.value) return

        if (isPlaying.value) {
            playerEngine.pause()
        } else {
            val tracks = _currentSession.value.tracks
            val hasContent = tracks.any {
                it.audioFilePath != null || it.midiEvents.isNotEmpty() || it.midiNotes.isNotEmpty()
            }
            if (!hasContent) {
                _userMessage.value = "No audio or MIDI recorded yet. Tap Record or load Demo Session!"
                return
            }
            playerEngine.play(tracks, playheadPositionMs.value) { track, event ->
                midiManager.sendRecordedMidiEvent(
                    targetDeviceKey = track.midiOutputDeviceId,
                    outputChannelOverride = track.midiChannel,
                    event = event
                )
            }
        }
    }

    fun stopPlayback() {
        playerEngine.stop()
    }

    fun seekTo(positionMs: Long) {
        playerEngine.seekTo(positionMs)
    }

    fun toggleLoop() {
        val next = !_isLooping.value
        _isLooping.value = next
        playerEngine.isLooping = next
    }

    fun addTrack() {
        val currentTracks = _currentSession.value.tracks
        val nextIndex = currentTracks.size
        val color = TrackColors[nextIndex % TrackColors.size]
        val channelMode = if (nextIndex % 2 == 0) InputChannelMode.LEFT_OR_CH1 else InputChannelMode.RIGHT_OR_CH2
        val newTrack = TrackModel(
            id = UUID.randomUUID().toString(),
            name = "Track ${nextIndex + 1} - Input ${nextIndex + 1}",
            colorHex = color.value.toLong(),
            inputDeviceId = availableInputs.value.firstOrNull()?.id ?: "default_mic",
            inputChannelMode = channelMode,
            isArmed = true
        )
        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks + newTrack
        )
        _userMessage.value = "Added ${newTrack.name}"
    }

    fun addMidiTrack() {
        val currentTracks = _currentSession.value.tracks
        val nextIndex = currentTracks.size
        val color = TrackColors[nextIndex % TrackColors.size]
        val sessionDir = File(getApplication<Application>().filesDir, "sessions/${_currentSession.value.id}").apply { mkdirs() }
        val midiFile = File(sessionDir, "midi_${System.currentTimeMillis()}.wav")

        val newTrack = TrackModel(
            id = UUID.randomUUID().toString(),
            name = "MIDI Track ${nextIndex + 1}",
            type = TrackType.MIDI,
            colorHex = color.value.toLong(),
            inputDeviceId = MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
            midiInputChannel = MidiRoutingConstants.CHANNEL_OMNI,
            midiOutputDeviceId = MidiRoutingConstants.ALL_MIDI_OUTPUTS_ID,
            midiChannel = MidiRoutingConstants.CHANNEL_OMNI,
            isArmed = true,
            audioFilePath = midiFile.absolutePath
        )
        MidiSynthEngine.renderMidiToWav(emptyList(), midiFile)

        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks + newTrack
        )
        _userMessage.value = "Added raw MIDI Track: ${newTrack.name}. Ready to record all MIDI parameters!"
    }

    fun addMidiNoteToTrack(trackId: String, note: MidiNote) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return
        val newNotes = target.midiNotes + note
        val noteOnEvt = RecordedMidiEvent(
            timestampMs = note.startMs,
            channel = note.channel,
            type = MidiEventType.NOTE_ON,
            data1 = note.noteNumber,
            data2 = note.velocity
        )
        val noteOffEvt = RecordedMidiEvent(
            timestampMs = note.startMs + note.durationMs,
            channel = note.channel,
            type = MidiEventType.NOTE_OFF,
            data1 = note.noteNumber,
            data2 = 0
        )
        val newEvents = target.midiEvents + noteOnEvt + noteOffEvt
        renderMidiTrackInternal(target, newNotes, newEvents)
    }

    fun removeMidiNoteFromTrack(trackId: String, noteId: String) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return
        val newNotes = target.midiNotes.filter { it.id != noteId }
        renderMidiTrackInternal(target, newNotes, target.midiEvents)
    }

    fun clearMidiNotes(trackId: String) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return

        val clearedTrack = target.copy(
            midiNotes = emptyList(),
            midiEvents = emptyList(),
            latestCcValues = emptyMap(),
            latestProgram = null,
            latestPitchBend = 8192,
            latestAftertouch = 0
        )
        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks.map { if (it.id == trackId) clearedTrack else it }
        )
        renderMidiTrackInternal(clearedTrack, emptyList(), emptyList())
        _userMessage.value = "Cleared all recorded MIDI notes & parameters"
    }

    private fun renderMidiTrackInternal(
        track: TrackModel,
        notes: List<MidiNote>,
        events: List<RecordedMidiEvent>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = File(getApplication<Application>().filesDir, "sessions/${_currentSession.value.id}").apply { mkdirs() }
            val file = track.audioFilePath?.let { File(it) } ?: File(sessionDir, "midi_${track.id}.wav")

            MidiSynthEngine.renderMidiToWav(
                notes = notes,
                outputFile = file,
                midiEvents = events
            )
            val waveform = WavHelper.extractWaveform(file)
            val duration = (max(0L, file.length() - 44) * 1000L) / (44100 * 2)

            _currentSession.value = _currentSession.value.copy(
                tracks = _currentSession.value.tracks.map {
                    if (it.id == track.id) {
                        it.copy(
                            audioFilePath = file.absolutePath,
                            midiNotes = notes,
                            midiEvents = events,
                            waveformPoints = waveform,
                            durationMs = duration
                        )
                    } else it
                }
            )
        }
    }

    fun auditionMidiNote(track: TrackModel, noteNumber: Int, velocity: Int = 100) {
        val outChannel = if (track.midiChannel in 1..16) track.midiChannel else 1
        midiManager.sendNoteOn(
            channel = outChannel,
            note = noteNumber,
            velocity = velocity,
            targetDeviceKey = track.midiOutputDeviceId
        )
        MidiSynthEngine.playNoteLive(
            noteNumber = noteNumber,
            velocity = velocity,
            scope = viewModelScope,
            durationMs = 380L,
            pitchBend14Bit = track.latestPitchBend
        )
        viewModelScope.launch {
            delay(350)
            midiManager.sendNoteOff(
                channel = outChannel,
                note = noteNumber,
                targetDeviceKey = track.midiOutputDeviceId
            )
        }
    }

    private fun handleExternalMidiEvent(event: MidiActivityEvent) {
        val currentTracks = _currentSession.value.tracks
        val matchingTracks = currentTracks.filter {
            it.matchesMidiInput(event.sourceId, event.channel)
        }
        if (matchingTracks.isEmpty()) return

        val recordingNow = isRecording.value
        val captureTimeMs = recordDurationMs.value
        val matchedIds = matchingTracks.map { it.id }.toSet()

        val updatedTracks = currentTracks.map { track ->
            if (track.id !in matchedIds) return@map track

            // Update live incoming parameter state regardless of recording status
            var nextCcs = track.latestCcValues
            var nextProg = track.latestProgram
            var nextBend = track.latestPitchBend
            var nextAt = track.latestAftertouch

            when (event.type) {
                MidiEventType.CONTROL_CHANGE -> {
                    nextCcs = track.latestCcValues + (event.data1 to event.data2)
                }
                MidiEventType.PROGRAM_CHANGE -> {
                    nextProg = event.data1
                }
                MidiEventType.PITCH_BEND -> {
                    nextBend = event.data1
                }
                MidiEventType.CHANNEL_PRESSURE, MidiEventType.POLY_PRESSURE -> {
                    nextAt = if (event.type == MidiEventType.CHANNEL_PRESSURE) event.data1 else event.data2
                }
                else -> {}
            }

            // MIDI Thru to output port if configured (avoid echoing directly back to same external device)
            if (track.midiOutputDeviceId != MidiRoutingConstants.NO_MIDI_OUTPUT_ID &&
                track.midiOutputDeviceId != event.sourceId
            ) {
                midiManager.sendRecordedMidiEvent(
                    targetDeviceKey = track.midiOutputDeviceId,
                    outputChannelOverride = track.midiChannel,
                    event = event.toRecordedEvent(0L)
                )
            }

            // Audition live reference monitor tone on Note On
            if (event.type == MidiEventType.NOTE_ON) {
                MidiSynthEngine.playNoteLive(
                    noteNumber = event.data1,
                    velocity = event.data2,
                    scope = viewModelScope,
                    pitchBend14Bit = nextBend
                )
            }

            if (!recordingNow) {
                track.copy(
                    latestCcValues = nextCcs,
                    latestProgram = nextProg,
                    latestPitchBend = nextBend,
                    latestAftertouch = nextAt
                )
            } else {
                val recordedEvt = event.toRecordedEvent(captureTimeMs)
                val updatedEvents = track.midiEvents + recordedEvt

                val updatedNotes = when (event.type) {
                    MidiEventType.NOTE_ON -> {
                        val note = MidiNote(
                            id = UUID.randomUUID().toString(),
                            noteNumber = event.data1,
                            startMs = captureTimeMs,
                            durationMs = 300L,
                            velocity = event.data2.coerceIn(1, 127),
                            channel = event.channel
                        )
                        activeRecordingNotes[Triple(track.id, event.channel, event.data1)] = note.id
                        track.midiNotes + note
                    }
                    MidiEventType.NOTE_OFF -> {
                        val openNoteId = activeRecordingNotes.remove(Triple(track.id, event.channel, event.data1))
                        if (openNoteId != null) {
                            track.midiNotes.map { existing ->
                                if (existing.id == openNoteId) {
                                    existing.copy(durationMs = max(60L, captureTimeMs - existing.startMs))
                                } else existing
                            }
                        } else {
                            track.midiNotes
                        }
                    }
                    else -> track.midiNotes
                }

                track.copy(
                    midiNotes = updatedNotes,
                    midiEvents = updatedEvents,
                    latestCcValues = nextCcs,
                    latestProgram = nextProg,
                    latestPitchBend = nextBend,
                    latestAftertouch = nextAt
                )
            }
        }

        _currentSession.value = _currentSession.value.copy(tracks = updatedTracks)
    }

    fun updateMidiTrackRouting(
        trackId: String,
        inputDeviceId: String,
        inputChannel: Int,
        outputDeviceId: String,
        outputChannel: Int
    ) {
        val clampedInCh = inputChannel.coerceIn(0, 16)
        val clampedOutCh = outputChannel.coerceIn(0, 16)
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) {
                    it.copy(
                        inputDeviceId = inputDeviceId,
                        midiInputChannel = clampedInCh,
                        midiOutputDeviceId = outputDeviceId,
                        midiChannel = clampedOutCh
                    )
                } else it
            }
        )
        val inLabel = if (clampedInCh == 0) "Omni" else "Ch $clampedInCh"
        val outLabel = if (clampedOutCh == 0) "Orig Ch" else "Ch $clampedOutCh"
        _userMessage.value = "Updated MIDI routing (In: $inLabel • Out: $outLabel)"
    }

    fun refreshMidiDevices() {
        midiManager.scanMidiDevices()
        _userMessage.value = "Scanned USB, BLE & Inter-App MIDI ports"
    }

    fun deleteTrack(trackId: String) {
        val currentTracks = _currentSession.value.tracks
        if (currentTracks.size <= 1) {
            _userMessage.value = "Session must have at least one track"
            return
        }
        val target = currentTracks.firstOrNull { it.id == trackId }
        target?.audioFilePath?.let { File(it).delete() }

        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks.filter { it.id != trackId }
        )
    }

    fun toggleTrackArm(trackId: String) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(isArmed = !it.isArmed) else it
            }
        )
    }

    fun toggleTrackMute(trackId: String) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
            }
        )
    }

    fun toggleTrackSolo(trackId: String) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(isSoloed = !it.isSoloed) else it
            }
        )
    }

    fun setTrackVolume(trackId: String, volume: Float) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(volume = volume.coerceIn(0f, 1.5f)) else it
            }
        )
    }

    fun setTrackPan(trackId: String, pan: Float) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(pan = pan.coerceIn(-1f, 1f)) else it
            }
        )
    }

    fun updateTrackRouting(trackId: String, deviceId: String, channelMode: InputChannelMode) {
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(inputDeviceId = deviceId, inputChannelMode = channelMode) else it
            }
        )
        _userMessage.value = "Updated track input routing"
    }

    fun renameTrack(trackId: String, newName: String) {
        if (newName.isBlank()) return
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(name = newName.trim()) else it
            }
        )
    }

    fun clearTrackAudio(trackId: String) {
        val target = _currentSession.value.tracks.firstOrNull { it.id == trackId }
        target?.audioFilePath?.let { File(it).delete() }
        _currentSession.value = _currentSession.value.copy(
            tracks = _currentSession.value.tracks.map {
                if (it.id == trackId) it.copy(audioFilePath = null, waveformPoints = emptyList(), durationMs = 0L) else it
            }
        )
    }

    fun resetSession() {
        if (isPlaying.value) playerEngine.stop()
        if (isRecording.value) recorderEngine.stopRecording()
        _currentSession.value = createDefaultSession()
        _userMessage.value = "Created new empty session"
    }

    fun loadDemoSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPlaying.value) playerEngine.stop()
            if (isRecording.value) recorderEngine.stopRecording()

            val demoDir = File(getApplication<Application>().filesDir, "demo_session").apply { mkdirs() }
            val track1File = File(demoDir, "demo_track_1_drums.wav")
            val track2File = File(demoDir, "demo_track_2_bass.wav")
            val track3File = File(demoDir, "demo_track_3_midi.wav")

            WavHelper.createSyntheticTrack(track1File, 120f, 10, "DRUM_PULSE")
            WavHelper.createSyntheticTrack(track2File, 110f, 10, "TRIANGLE")

            val t1 = TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 1 - Rhythm Pulse",
                colorHex = TrackColors[0].value.toLong(),
                inputDeviceId = "default_mic",
                inputChannelMode = InputChannelMode.LEFT_OR_CH1,
                volume = 1.0f,
                pan = -0.2f,
                audioFilePath = track1File.absolutePath,
                waveformPoints = WavHelper.extractWaveform(track1File),
                durationMs = 10000L
            )
            val t2 = TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 2 - Sub Bass",
                colorHex = TrackColors[1].value.toLong(),
                inputDeviceId = "default_mic",
                inputChannelMode = InputChannelMode.RIGHT_OR_CH2,
                volume = 0.9f,
                pan = 0.2f,
                audioFilePath = track2File.absolutePath,
                waveformPoints = WavHelper.extractWaveform(track2File),
                durationMs = 10000L
            )

            val demoMidiNotes = listOf(
                MidiNote(noteNumber = 60, startMs = 0L, durationMs = 450L, velocity = 105, channel = 1),
                MidiNote(noteNumber = 64, startMs = 500L, durationMs = 450L, velocity = 98, channel = 1),
                MidiNote(noteNumber = 67, startMs = 1000L, durationMs = 450L, velocity = 112, channel = 1),
                MidiNote(noteNumber = 71, startMs = 1500L, durationMs = 450L, velocity = 104, channel = 1),
                MidiNote(noteNumber = 72, startMs = 2000L, durationMs = 800L, velocity = 120, channel = 1)
            )
            val demoMidiEvents = listOf(
                RecordedMidiEvent(timestampMs = 0L, channel = 1, type = MidiEventType.CONTROL_CHANGE, data1 = 1, data2 = 42),
                RecordedMidiEvent(timestampMs = 0L, channel = 1, type = MidiEventType.NOTE_ON, data1 = 60, data2 = 105),
                RecordedMidiEvent(timestampMs = 450L, channel = 1, type = MidiEventType.NOTE_OFF, data1 = 60, data2 = 0),
                RecordedMidiEvent(timestampMs = 500L, channel = 1, type = MidiEventType.NOTE_ON, data1 = 64, data2 = 98),
                RecordedMidiEvent(timestampMs = 950L, channel = 1, type = MidiEventType.NOTE_OFF, data1 = 64, data2 = 0),
                RecordedMidiEvent(timestampMs = 1000L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 9200, data2 = 0),
                RecordedMidiEvent(timestampMs = 1000L, channel = 1, type = MidiEventType.NOTE_ON, data1 = 67, data2 = 112),
                RecordedMidiEvent(timestampMs = 1450L, channel = 1, type = MidiEventType.NOTE_OFF, data1 = 67, data2 = 0),
                RecordedMidiEvent(timestampMs = 1500L, channel = 1, type = MidiEventType.PITCH_BEND, data1 = 8192, data2 = 0),
                RecordedMidiEvent(timestampMs = 1500L, channel = 1, type = MidiEventType.NOTE_ON, data1 = 71, data2 = 104),
                RecordedMidiEvent(timestampMs = 1950L, channel = 1, type = MidiEventType.NOTE_OFF, data1 = 71, data2 = 0),
                RecordedMidiEvent(timestampMs = 2000L, channel = 1, type = MidiEventType.CONTROL_CHANGE, data1 = 64, data2 = 127),
                RecordedMidiEvent(timestampMs = 2000L, channel = 1, type = MidiEventType.NOTE_ON, data1 = 72, data2 = 120),
                RecordedMidiEvent(timestampMs = 2800L, channel = 1, type = MidiEventType.NOTE_OFF, data1 = 72, data2 = 0),
                RecordedMidiEvent(timestampMs = 2900L, channel = 1, type = MidiEventType.CONTROL_CHANGE, data1 = 64, data2 = 0)
            )
            MidiSynthEngine.renderMidiToWav(
                notes = demoMidiNotes,
                outputFile = track3File,
                midiEvents = demoMidiEvents,
                minDurationMs = 10000L
            )

            val t3 = TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 3 - Raw MIDI",
                type = TrackType.MIDI,
                colorHex = TrackColors[2].value.toLong(),
                inputDeviceId = MidiRoutingConstants.ALL_MIDI_INPUTS_ID,
                midiInputChannel = MidiRoutingConstants.CHANNEL_OMNI,
                midiOutputDeviceId = MidiRoutingConstants.ALL_MIDI_OUTPUTS_ID,
                midiChannel = MidiRoutingConstants.CHANNEL_OMNI,
                midiNotes = demoMidiNotes,
                midiEvents = demoMidiEvents,
                latestCcValues = mapOf(1 to 42, 64 to 0),
                volume = 0.9f,
                pan = 0.0f,
                audioFilePath = track3File.absolutePath,
                waveformPoints = WavHelper.extractWaveform(track3File),
                durationMs = 10000L
            )

            _currentSession.value = RecordingSession(
                id = UUID.randomUUID().toString(),
                title = "Demo MultiTrack Project",
                tracks = listOf(t1, t2, t3),
                durationMs = 10000L
            )
            _userMessage.value = "Loaded Demo Session! Track 3 contains raw MIDI Notes + CC/Pitch Bend parameters."
        }
    }

    fun shareMasterMix() {
        val success = AudioExporter.shareMasterMix(
            getApplication(),
            _currentSession.value.tracks,
            _currentSession.value.title
        )
        if (!success) {
            _userMessage.value = "Failed to export master mix. Ensure tracks have recorded audio."
        }
    }

    fun shareStems() {
        val success = AudioExporter.shareStems(
            getApplication(),
            _currentSession.value.tracks,
            _currentSession.value.title
        )
        if (!success) {
            _userMessage.value = "Failed to export stems. Ensure tracks have recorded audio."
        }
    }

    fun refreshInputs() {
        inputManager.refreshInputs()
        _userMessage.value = "Refreshed audio input devices"
    }

    fun clearMessage() {
        _userMessage.value = null
    }

    fun setBufferSize(size: com.example.audio.AsioBufferSize) {
        usbInterfaceManager.setBufferSize(size)
        _userMessage.value = "ASIO Buffer set to ${size.label}"
    }

    fun setSampleRate(rate: Int) {
        usbInterfaceManager.setSampleRate(rate)
        _userMessage.value = "Sample rate set to ${rate / 1000} kHz"
    }

    override fun onCleared() {
        super.onCleared()
        inputManager.release()
        usbInterfaceManager.release()
        midiManager.release()
        playerEngine.stop()
        recorderEngine.stopRecording()
    }
}
