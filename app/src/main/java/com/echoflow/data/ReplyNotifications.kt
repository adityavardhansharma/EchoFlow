package com.echoflow.data

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.echoflow.MainActivity
import com.echoflow.R

/**
 * One-shot "your reply is ready" notifications for long cloud generations (Echo Adviser /
 * Echo Fusion), which can take a while. While the app is minimized the reply keeps streaming
 * via [KeepAliveService]; this posts a heads-up only when it finishes and the app is NOT in
 * the foreground, so the user is pinged like Deep Research but never spammed while watching.
 *
 * Tapping carries [EXTRA_OPEN_CHAT] so MainActivity can jump straight to that conversation.
 */
object ReplyNotifications {
    const val EXTRA_OPEN_CHAT = "open_chat_id"
    private const val CHANNEL_ID = "echo_replies"
    private const val NOTIFICATION_BASE = 7100

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Replies",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tells you when a long Echo reply is ready" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** True only when one of this app's processes is currently in the foreground. */
    fun isAppForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val procs = am.runningAppProcesses ?: return false
        val pkg = context.packageName
        return procs.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.processName == pkg
        }
    }

    /**
     * Post a "reply ready / failed" notification for [chatId]. No-op if the app is foreground
     * (the user is already watching) or if notifications are blocked.
     */
    fun notifyReplyReady(context: Context, chatId: String, title: String, text: String) {
        if (isAppForeground(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_CHAT, chatId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            chatId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        // notify() throws SecurityException if POST_NOTIFICATIONS was denied — degrade silently.
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_BASE + (chatId.hashCode() and 0xFFFF), notification)
        }
    }
}
