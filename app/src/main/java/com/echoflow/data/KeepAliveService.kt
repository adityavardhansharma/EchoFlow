package com.echoflow.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.echoflow.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal dataSync foreground service that keeps the process unfrozen while a model
 * download or a streaming reply is in flight. Without it, Android freezes cached apps
 * shortly after they leave the foreground and their sockets die mid-transfer.
 *
 * Usage is refcounted: every [acquire] must be paired with one [release]; the service
 * stops itself when the last piece of work finishes. Acquire is always called from the
 * foreground (a user tap), which is what the FGS start restriction requires.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background activity",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Keeps downloads and replies running while the app is minimized" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Working in the background"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("EchoFlow")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 41
        internal const val ACTION_STOP = "com.echoflow.keepalive.STOP"
        internal const val EXTRA_TEXT = "text"

        private val holders = AtomicInteger(0)

        fun acquire(context: Context, text: String) {
            holders.incrementAndGet()
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeepAliveService::class.java).putExtra(EXTRA_TEXT, text),
                )
            }
        }

        fun release(context: Context) {
            if (holders.decrementAndGet() <= 0) {
                holders.set(0)
                runCatching {
                    context.startService(
                        Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP),
                    )
                }
            }
        }
    }
}
