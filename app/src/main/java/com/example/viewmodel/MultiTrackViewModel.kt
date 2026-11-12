package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioExporter
import com.example.audio.AudioInputManager
import com.example.audio.MultiTrackPlayerEngine
import com.example.audio.MultiTrackRecorderEngine
import com.example.audio.WavHelper
import com.example.model.AudioInputDevice
import com.example.model.InputChannelMode
import com.example.model.RecordingSession
import com.example.model.TrackModel
import com.example.ui.theme.TrackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.max

class MultiTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val inputManager = AudioInputManager(application)
    val availableInputs: StateFlow<List<AudioInputDevice>> = inputManager.availableInputs

    private val usbInterfaceManager = com.example.audio.UsbAudioInterfaceManager(application)
    val connectedUsbDevices = usbInterfaceManager.connectedUsbAudio
    val selectedBufferSize = usbInterfaceManager.selectedBufferSize
    val selectedSampleRate = usbInterfaceManager.selectedSampleRate

    private val midiManager = com.example.audio.AndroidMidiManager(application) { noteNumber, velocity, isNoteOn ->
        if (isNoteOn) {
            handleExternalMidiNote(noteNumber, velocity)
        }
    }
    val connectedMidiDevices = midiManager.connectedMidiDevices

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

    // Combined live meter levels per track (from recording or playback)
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
        // Ensure recordings directory exists
        val recDir = File(getApplication<Application>().filesDir, "sessions").apply { mkdirs() }
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

        val sessionDir = File(getApplication<Application>().filesDir, "sessions/${session.id}")
        val started = recorderEngine.startRecording(
            tracksToRecord = session.tracks,
            availableDevices = availableInputs.value,
            sessionDir = sessionDir
        ) { recordedFiles ->
            onRecordingComplete(recordedFiles)
        }

        if (started) {
            _userMessage.value = "Recording started on ${armedTracks.size} separate track(s)"
        }
        return started
    }

    fun stopRecording() {
        recorderEngine.stopRecording()
    }

    private fun onRecordingComplete(recordedFiles: Map<String, File>) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedTracks = _currentSession.value.tracks.map { track ->
                val file = recordedFiles[track.id]
                if (file != null && file.exists()) {
                    val waveform = WavHelper.extractWaveform(file)
                    val duration = (max(0L, file.length() - 44) * 1000L) / (44100 * 2)
                    track.copy(
                        audioFilePath = file.absolutePath,
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
            _userMessage.value = "Recording saved: ${recordedFiles.size} tracks captured"
        }
    }

    fun togglePlayPause() {
        if (isRecording.value) return

        if (isPlaying.value) {
            playerEngine.pause()
        } else {
            val hasAudio = _currentSession.value.tracks.any { it.audioFilePath != null }
            if (!hasAudio) {
                _userMessage.value = "No audio recorded yet. Tap Record or load Demo Session!"
                return
            }
            playerEngine.play(_currentSession.value.tracks, playheadPositionMs.value)
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
            type = com.example.model.TrackType.MIDI,
            colorHex = color.value.toLong(),
            inputDeviceId = "usb_midi",
            instrument = com.example.model.SynthInstrument.SYNTH_LEAD,
            isArmed = true,
            audioFilePath = midiFile.absolutePath
        )
        // Render initial silent WAV
        com.example.audio.MidiSynthEngine.renderMidiToWav(emptyList(), newTrack.instrument, midiFile)

        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks + newTrack
        )
        _userMessage.value = "Added MIDI Track: ${newTrack.name}. Tap Keys / Piano Roll to compose!"
    }

    fun addMidiNoteToTrack(trackId: String, note: com.example.model.MidiNote) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return
        val newNotes = target.midiNotes + note

        renderMidiTrackInternal(target, newNotes)
    }

    fun removeMidiNoteFromTrack(trackId: String, noteId: String) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return
        val newNotes = target.midiNotes.filter { it.id != noteId }

        renderMidiTrackInternal(target, newNotes)
    }

    fun clearMidiNotes(trackId: String) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return

        renderMidiTrackInternal(target, emptyList())
    }

    fun changeTrackInstrument(trackId: String, instrument: com.example.model.SynthInstrument) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return

        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks.map { if (it.id == trackId) it.copy(instrument = instrument) else it }
        )
        renderMidiTrackInternal(target.copy(instrument = instrument), target.midiNotes)
    }

    private fun renderMidiTrackInternal(track: TrackModel, notes: List<com.example.model.MidiNote>) {
        viewModelScope.launch(Dispatchers.IO) {
            val sessionDir = File(getApplication<Application>().filesDir, "sessions/${_currentSession.value.id}").apply { mkdirs() }
            val file = track.audioFilePath?.let { File(it) } ?: File(sessionDir, "midi_${track.id}.wav")

            com.example.audio.MidiSynthEngine.renderMidiToWav(
                notes = notes,
                instrument = track.instrument,
                outputFile = file,
                midiProgram = track.midiProgram
            )
            val waveform = WavHelper.extractWaveform(file)
            val duration = (max(0L, file.length() - 44) * 1000L) / (44100 * 2)

            _currentSession.value = _currentSession.value.copy(
                tracks = _currentSession.value.tracks.map {
                    if (it.id == track.id) {
                        it.copy(
                            audioFilePath = file.absolutePath,
                            midiNotes = notes,
                            waveformPoints = waveform,
                            durationMs = duration
                        )
                    } else it
                }
            )
        }
    }

    fun setTrackMidiPatch(trackId: String, program: Int, bankMsb: Int, bankLsb: Int, channel: Int) {
        val currentTracks = _currentSession.value.tracks
        val target = currentTracks.firstOrNull { it.id == trackId } ?: return
        val patch = com.example.model.GeneralMidiPatches.findPatch(program)

        // Send MIDI Program Change + Bank Select to connected USB hardware
        midiManager.sendProgramChange(channel, program, bankMsb, bankLsb)

        val updatedTrack = target.copy(
            midiProgram = program,
            midiBankMsb = bankMsb,
            midiBankLsb = bankLsb,
            midiChannel = channel,
            instrument = patch.synthFamily
        )

        _currentSession.value = _currentSession.value.copy(
            tracks = currentTracks.map { if (it.id == trackId) updatedTrack else it }
        )
        renderMidiTrackInternal(updatedTrack, updatedTrack.midiNotes)
        _userMessage.value = "Selected Patch [${program + 1}]: ${patch.name} (Sent PC #$program to device)"
    }

    fun auditionMidiNote(track: TrackModel, noteNumber: Int) {
        midiManager.sendNoteOn(track.midiChannel, noteNumber, 100)
        com.example.audio.MidiSynthEngine.playNoteLive(
            noteNumber = noteNumber,
            instrument = track.instrument,
            scope = viewModelScope,
            durationMs = 450L,
            midiProgram = track.midiProgram
        )
        viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            midiManager.sendNoteOff(track.midiChannel, noteNumber)
        }
    }

    private fun handleExternalMidiNote(noteNumber: Int, velocity: Int) {
        val armedMidiTracks = _currentSession.value.tracks.filter { it.type == com.example.model.TrackType.MIDI && it.isArmed }
        if (armedMidiTracks.isEmpty()) return

        for (track in armedMidiTracks) {
            com.example.audio.MidiSynthEngine.playNoteLive(
                noteNumber = noteNumber,
                instrument = track.instrument,
                scope = viewModelScope,
                midiProgram = track.midiProgram
            )
            if (isRecording.value) {
                val newNote = com.example.model.MidiNote(
                    id = UUID.randomUUID().toString(),
                    noteNumber = noteNumber,
                    startMs = recordDurationMs.value,
                    durationMs = 400L,
                    velocity = velocity
                )
                addMidiNoteToTrack(track.id, newNote)
            }
        }
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

    /**
     * Loads a multi-track demo session with 3 isolated tracks (Drums/Beat, Bass Synth, Lead Arp)
     * so that the user can test the multi-track studio instantly!
     */
    fun loadDemoSession() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isPlaying.value) playerEngine.stop()
            if (isRecording.value) recorderEngine.stopRecording()

            val demoDir = File(getApplication<Application>().filesDir, "demo_session").apply { mkdirs() }
            val track1File = File(demoDir, "demo_track_1_drums.wav")
            val track2File = File(demoDir, "demo_track_2_bass.wav")
            val track3File = File(demoDir, "demo_track_3_lead.wav")

            WavHelper.createSyntheticTrack(track1File, 120f, 10, "DRUM_PULSE")
            WavHelper.createSyntheticTrack(track2File, 110f, 10, "TRIANGLE")
            WavHelper.createSyntheticTrack(track3File, 440f, 10, "SINE")

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
            // Demo MIDI Melodic Notes (C major 7 arpeggio riff)
            val demoMidiNotes = listOf(
                com.example.model.MidiNote(noteNumber = 60, startMs = 0L, durationMs = 450L),   // C4
                com.example.model.MidiNote(noteNumber = 64, startMs = 500L, durationMs = 450L), // E4
                com.example.model.MidiNote(noteNumber = 67, startMs = 1000L, durationMs = 450L), // G4
                com.example.model.MidiNote(noteNumber = 71, startMs = 1500L, durationMs = 450L), // B4
                com.example.model.MidiNote(noteNumber = 72, startMs = 2000L, durationMs = 800L), // C5
                com.example.model.MidiNote(noteNumber = 69, startMs = 3000L, durationMs = 450L), // A4
                com.example.model.MidiNote(noteNumber = 67, startMs = 3500L, durationMs = 450L), // G4
                com.example.model.MidiNote(noteNumber = 65, startMs = 4000L, durationMs = 450L), // F4
                com.example.model.MidiNote(noteNumber = 64, startMs = 4500L, durationMs = 900L)  // E4
            )
            com.example.audio.MidiSynthEngine.renderMidiToWav(
                demoMidiNotes,
                com.example.model.SynthInstrument.SYNTH_LEAD,
                track3File,
                minDurationMs = 10000L
            )

            val t3 = TrackModel(
                id = UUID.randomUUID().toString(),
                name = "Track 3 - Synth Lead (MIDI)",
                type = com.example.model.TrackType.MIDI,
                colorHex = TrackColors[2].value.toLong(),
                inputDeviceId = "usb_midi",
                instrument = com.example.model.SynthInstrument.SYNTH_LEAD,
                midiNotes = demoMidiNotes,
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
            _userMessage.value = "Loaded Demo Session! Track 3 is a MIDI Track — tap Keys to edit notes or play!"
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
