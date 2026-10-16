package com.echoflow.data.voice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.echoflow.MainActivity
import com.echoflow.R

/**
 * Keeps a voice session alive with the screen off or the app in the background. Android only
 * lets an app keep using the microphone from the background inside a microphone foreground
 * service, so voice mode runs one for exactly as long as a session is open, with an ongoing
 * notification that says so and an End button.
 */
class VoiceModeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            endHandler?.invoke()
            stopSelfNow()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            stopSelfNow()
            return START_NOT_STICKY
        }
        ensureChannel()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val end = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceModeService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("Voice mode is on")
            .setContentText("EchoFlow is listening. Tap to return to the conversation.")
            .setContentIntent(open)
            .addAction(0, "End", end)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        val started = runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
        if (started.isFailure) {
            // Without the mic permission Android 14+ refuses a microphone service. The session
            // still runs while the app is open; it just can't continue in the background.
            Log.w(TAG, "Couldn't start the voice mode service", started.exceptionOrNull())
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stopSelfNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Voice mode", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while a voice conversation is open"
                setShowBadge(false)
            },
        )
    }

    companion object {
        private const val TAG = "VoiceModeService"
        private const val CHANNEL_ID = "voice_mode"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_END = "com.echoflow.voice.END"
        private const val ACTION_STOP = "com.echoflow.voice.STOP"

        /** Set by the live session: what the notification's End button does. */
        @Volatile var endHandler: (() -> Unit)? = null

        /** Called from a user tap, while the app is in the foreground, as Android requires. */
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VoiceModeService::class.java)) }
                .onFailure { Log.w(TAG, "Couldn't start the voice mode service", it) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, VoiceModeService::class.java).setAction(ACTION_STOP)) }
                .onFailure {
                    // The app may already be in the background, where startService is refused.
                    runCatching { context.stopService(Intent(context, VoiceModeService::class.java)) }
                }
        }
    }
}
