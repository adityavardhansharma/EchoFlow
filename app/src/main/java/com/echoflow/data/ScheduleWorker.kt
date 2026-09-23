package com.echoflow.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.echoflow.MainActivity
import com.echoflow.R
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class ScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.failure()
        val at = inputData.getLong("scheduledAt", 0)
        val revision = inputData.getLong("revision", -1)
        val manual = inputData.getBoolean("manual", false)
        val runId = inputData.getString("runId") ?: return Result.failure()
        val manager = ScheduleManager(applicationContext)
        if (!manual && at > System.currentTimeMillis()) return Result.retry()
        manager.reconcile()
        val task = manager.claim(taskId, at, revision, manual, runId) ?: run {
            manager.reconcile()
            return Result.success()
        }
        val notificationId = (runId.hashCode() and 0x00ff_ffff) or 0x3400_0000
        var resultChatId: String? = null
        try {
            try {
                setForeground(ScheduleNotifications.running(applicationContext, notificationId, task.title))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Android may deny foreground promotion from the background. Ordinary work can
                // still complete; the run history records any later model failure.
                Log.w("ScheduleWorker", "Continuing without foreground service", e)
            }
            val answer = ScheduleModelRunner(applicationContext).complete(
                modelId = task.modelId,
                userText = task.instruction,
                systemPrompt = "You are completing a user-approved scheduled task. Follow its instruction. " +
                    "For a reminder, write a concise useful reminder. State uncertainty honestly. " +
                    "Do not claim to have checked external information unless results are provided. " +
                    "Current scheduled occurrence: ${java.util.Date(at)}.",
                runId = runId,
                searchQuery = if (task.needsWeb) task.instruction else null,
            )
            val chatId = manager.saveAnswer(runId, task, answer)
            resultChatId = chatId
            ScheduleNotifications.finished(applicationContext, notificationId, task.title,
                answer.take(160), chatId)
            return Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { manager.finish(runId, resultChatId, "Stopped before completion") }
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: "The scheduled run could not finish."
            manager.finish(runId, resultChatId, reason)
            ScheduleNotifications.finished(applicationContext, notificationId, task.title,
                reason, null)
            return Result.failure()
        }
    }
}

class ScheduleWarmupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId") ?: return Result.success()
        val at = inputData.getLong("scheduledAt", 0)
        val revision = inputData.getLong("revision", -1)
        val task = AppDatabase.getDatabase(applicationContext).scheduleDao().task(taskId)
            ?: return Result.success()
        val until = at - System.currentTimeMillis()
        if (task.status != ScheduleTask.ACTIVE || task.revision != revision ||
            task.nextRunAt != at || until !in 1..6 * 60_000L || !task.modelId.startsWith("local/")) {
            return Result.success()
        }
        return try {
            ScheduleModelRunner(applicationContext).prewarm(task.modelId)
            Result.success()
        } catch (_: Exception) {
            // Warm-up is optional. The due worker performs the normal load and reports errors.
            Result.success()
        }
    }
}

internal object ScheduleNotifications {
    private const val CHANNEL = "echo_schedules"

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Schedules", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    fun running(context: Context, id: Int, title: String): ForegroundInfo {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText("Scheduled task is running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(id, notification)
    }

    fun finished(context: Context, id: Int, title: String, text: String, chatId: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            if (chatId != null) putExtra(ReplyNotifications.EXTRA_OPEN_CHAT, chatId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.logo).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
