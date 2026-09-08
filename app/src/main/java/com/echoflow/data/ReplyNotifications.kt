package com.echoflow.data

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.echoflow.MainActivity
import com.echoflow.R

/**
 * One-shot "it's ready" notifications for the work that takes long enough that the user has
 * probably walked away: Echo Adviser / Fusion / Agents replies, generated images and videos,
 * and finished model downloads. While the app is minimized the work keeps running via
 * [KeepAliveService]; this posts the heads-up when it lands.
 *
 * Plain chat replies deliberately never ping — they finish in seconds, and "your message got
 * a reply" is not news worth a buzz.
 *
 * Tapping carries [EXTRA_OPEN_CHAT] so MainActivity can jump straight to that conversation.
 */
object ReplyNotifications {
    const val EXTRA_OPEN_CHAT = "open_chat_id"
    private const val CHANNEL_ID = "echo_replies"

    /**
     * All of these bundle under one group so a few landing together read as one stack rather
     * than a column. API 24+ auto-bundles at four; grouping explicitly gets us bundling from
     * two and a summary line we write ourselves.
     */
    private const val GROUP_KEY = "com.echoflow.READY"
    private const val SUMMARY_ID = 7000
    private const val REPLY_BASE = 100_000
    private const val DOWNLOAD_BASE = 200_000

    /**
     * The conversation currently on screen, pushed by ChatViewModel. Suppression is per-chat
     * rather than per-app: if you are reading conversation A when B's answer lands, B is still
     * worth a notification — you cannot see it, which is the whole test.
     */
    @Volatile
    var visibleChatId: String? = null

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Replies",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Tells you when a long reply, image, video or download is ready" }
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

    /** Post a "reply / image / video ready" (or "couldn't finish") notification for [chatId]. */
    fun notifyReplyReady(context: Context, chatId: String, title: String, text: String) {
        if (isAppForeground(context) && visibleChatId == chatId) return
        post(context, REPLY_BASE + (chatId.hashCode() and 0xFFFF), chatId, title, text)
    }

    /**
     * Post a "model is ready to use" notification. A download has no conversation, so being
     * anywhere in the app counts as seeing it — the model list shows its own progress.
     */
    fun notifyDownloadReady(context: Context, modelId: String, title: String, text: String) {
        if (isAppForeground(context)) return
        post(context, DOWNLOAD_BASE + (modelId.hashCode() and 0xFFFF), null, title, text)
    }

    private fun post(context: Context, id: Int, chatId: String?, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_KEY)
            .setContentIntent(openIntent(context, chatId))
            .build()

        // notify() throws SecurityException if POST_NOTIFICATIONS was denied — degrade silently.
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            refreshSummary(context)
        }
    }

    private fun openIntent(context: Context, chatId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            if (chatId != null) putExtra(EXTRA_OPEN_CHAT, chatId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            chatId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Rebuild the group summary from what is *actually* on screen rather than from a list we
     * keep ourselves — the user can swipe any one of these away at any moment, and our copy
     * would quietly drift out of date.
     *
     * A lone notification needs no summary, so below two we take it down instead.
     */
    private fun refreshSummary(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val titles = runCatching {
            manager.activeNotifications
                .filter { it.id != SUMMARY_ID && it.notification.group == GROUP_KEY }
                .mapNotNull { it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() }
        }.getOrDefault(emptyList())

        if (titles.size < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }

        val inbox = NotificationCompat.InboxStyle().setBigContentTitle("EchoFlow")
        titles.forEach { inbox.addLine(it) }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("EchoFlow")
            .setContentText("${titles.size} things are ready")
            .setStyle(inbox)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            // The children already buzzed on their way in; the summary appearing behind them
            // must not buzz again.
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context, null))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary) }
    }
}
