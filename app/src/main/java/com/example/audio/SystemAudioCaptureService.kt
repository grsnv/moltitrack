package com.example.audio

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service with `mediaProjection` type required by Android 10–16
 * to obtain a MediaProjection handle for AudioPlaybackCapture (internal system sound).
 */
class SystemAudioCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            val callback = onProjectionStopped
            cleanupProjection()
            stopForegroundCompat()
            stopSelf()
            callback?.invoke()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pendingProjection?.complete(null)
                pendingProjection = null
                cleanupProjection()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundWithNotification()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_DATA)
                }

                if (resultCode == Activity.RESULT_OK && projectionData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        cleanupProjection()
                        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        val projection = mgr.getMediaProjection(resultCode, projectionData)
                        if (projection != null) {
                            projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
                            mediaProjection = projection
                            activeProjection = projection
                        }
                        pendingProjection?.complete(projection)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        pendingProjection?.complete(null)
                    }
                } else {
                    pendingProjection?.complete(null)
                }
                pendingProjection = null
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording System Sound")
            .setContentText("MultiTrack is capturing internal device audio")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "System Audio Capture",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Displayed while capturing internal system sound onto a MultiTrack lane"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun cleanupProjection() {
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
        } catch (_: Exception) {}
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        activeProjection = null
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        cleanupProjection()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "multitrack_system_audio_capture"
        private const val NOTIFICATION_ID = 4401
        private const val ACTION_START = "com.example.action.START_SYSTEM_AUDIO_CAPTURE"
        private const val ACTION_STOP = "com.example.action.STOP_SYSTEM_AUDIO_CAPTURE"
        private const val EXTRA_RESULT_CODE = "extra_result_code"
        private const val EXTRA_DATA = "extra_data"

        @Volatile
        private var activeProjection: MediaProjection? = null

        @Volatile
        private var pendingProjection: CompletableDeferred<MediaProjection?>? = null

        @Volatile
        var onProjectionStopped: (() -> Unit)? = null

        suspend fun acquireProjection(
            context: Context,
            resultCode: Int,
            data: Intent
        ): MediaProjection? {
            val deferred = CompletableDeferred<MediaProjection?>()
            pendingProjection?.complete(null)
            pendingProjection = deferred

            val intent = Intent(context, SystemAudioCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            ContextCompat.startForegroundService(context, intent)

            return withTimeoutOrNull(5000L) {
                deferred.await()
            }
        }

        fun stopCapture(context: Context) {
            pendingProjection?.complete(null)
            pendingProjection = null
            try {
                activeProjection?.stop()
            } catch (_: Exception) {}
            activeProjection = null
            try {
                context.stopService(Intent(context, SystemAudioCaptureService::class.java))
            } catch (_: Exception) {}
        }
    }
}
