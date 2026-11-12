package com.example.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.TrackModel
import java.io.File

object AudioExporter {

    private const val AUTHORITY = "com.aistudio.multitrack.recqvx.fileprovider"

    /**
     * Exports and shares the master mixed stereo WAV file via Android Share sheet.
     */
    fun shareMasterMix(
        context: Context,
        tracks: List<TrackModel>,
        sessionTitle: String
    ): Boolean {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanName = sessionTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val masterFile = File(exportDir, "${cleanName}_MasterMix.wav")

            val trackConfigs = tracks.filter { it.audioFilePath != null && File(it.audioFilePath).exists() }
                .map {
                    TrackMixConfig(
                        filePath = it.audioFilePath!!,
                        volume = it.volume,
                        pan = it.pan,
                        isMuted = it.isMuted,
                        isSoloed = it.isSoloed
                    )
                }

            if (trackConfigs.isEmpty()) return false

            val success = WavHelper.mixTracksToMaster(trackConfigs, masterFile)
            if (!success || !masterFile.exists()) return false

            val uri: Uri = FileProvider.getUriForFile(context, AUTHORITY, masterFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$sessionTitle - Master Mix")
                putExtra(Intent.EXTRA_TEXT, "Master mix exported from MultiTrack Studio")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Master Mix")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Exports and shares all individual track stems as separate WAV files.
     */
    fun shareStems(
        context: Context,
        tracks: List<TrackModel>,
        sessionTitle: String
    ): Boolean {
        try {
            val validTracks = tracks.filter { it.audioFilePath != null && File(it.audioFilePath).exists() }
            if (validTracks.isEmpty()) return false

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanSession = sessionTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val uris = ArrayList<Uri>()

            for (track in validTracks) {
                val cleanTrackName = track.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val stemFile = File(exportDir, "${cleanSession}_${cleanTrackName}_stem.wav")
                File(track.audioFilePath!!).copyTo(stemFile, overwrite = true)
                val uri = FileProvider.getUriForFile(context, AUTHORITY, stemFile)
                uris.add(uri)
            }

            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/wav"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_SUBJECT, "$sessionTitle - Track Stems")
                putExtra(Intent.EXTRA_TEXT, "Multi-track stems (${uris.size} tracks) exported from MultiTrack Studio")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Track Stems")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
