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
        val pendingTask = AppDatabase.getDatabase(applicationContext).scheduleDao().task(taskId)
            ?: return Result.success()
        if (!manual && (pendingTask.status != ScheduleTask.ACTIVE ||
                pendingTask.revision != revision || pendingTask.nextRunAt != at)) {
            return Result.success()
        }
        val notificationId = (runId.hashCode() and 0x00ff_ffff) or 0x3400_0000
        try {
            setForeground(ScheduleNotifications.running(applicationContext, notificationId, pendingTask.title))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ScheduleWorker", "Could not start foreground work", e)
            if (runAttemptCount < MAX_FOREGROUND_ATTEMPTS - 1 &&
                (manual || System.currentTimeMillis() - at <= ScheduleManager.MAX_LATENESS_MS)) {
                return Result.retry()
            }
            // Foreground access is required for potentially long model inference. Record a
            // terminal failure only after retries; no model request has started yet.
            val task = manager.claim(taskId, at, revision, manual, runId)
            if (task != null) {
                val reason = "Android could not start background model execution. Try running this task while EchoFlow is open."
                manager.finish(runId, null, reason)
                ScheduleNotifications.finished(applicationContext, notificationId, task.title, reason, null, task.id)
                return Result.failure()
            }
            manager.reconcile()
            return Result.success()
        }
        val task = manager.claim(taskId, at, revision, manual, runId) ?: run {
            manager.reconcile()
            return Result.success()
        }
        var resultChatId: String? = null
        try {
            val runner = ScheduleModelRunner(applicationContext)
            val web = runner.webAccess(task.modelId)
            val prompt = SchedulePrompts.run(SchedulePrompts.RunContext(
                task = task, scheduledAt = at, runNumber = manager.runNumber(task.id), manual = manual,
                previousAnswers = manager.recentAnswers(task),
                web = web, use24h = android.text.format.DateFormat.is24HourFormat(applicationContext),
            ))
            var searches = 0
            val agent = ScheduleAgent(complete = { history ->
                runner.stream(task.modelId, history, prompt, runId,
                    serverWebSearch = web == ScheduleWebAccess.Server,
                    localWaitTimeoutMillis = 5 * 60_000L)
            })
            val reply = agent.run(listOf(ScheduleModelRunner.message("user", task.instruction, runId)), execute = { call ->
                val query = call.arguments.optString("query").trim()
                when {
                    call.name != "web_search" || web !is ScheduleWebAccess.Client ->
                        """{"ok":false,"error":"No such tool in this run. Answer now."}"""
                    query.isBlank() -> """{"ok":false,"error":"Give a query."}"""
                    ++searches > 3 -> """{"ok":false,"error":"Search limit reached. Answer now with what you have."}"""
                    else -> try {
                        org.json.JSONObject().put("ok", true)
                            .put("results", formatSearchResultsForModel(runner.search(web.provider, query).take(6))).toString()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        org.json.JSONObject().put("ok", false).put("error", "Search failed: ${e.message}. Say that current information couldn't be checked.").toString()
                    }
                }
            })
            val answer = reply.text.ifBlank { error("The model returned no answer.") }
            val chatId = manager.saveAnswer(runId, task, answer)
            resultChatId = chatId
            ScheduleNotifications.finished(applicationContext, notificationId, task.title,
                SchedulePrompts.preview(answer), chatId, task.id)
            return Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { manager.finish(runId, resultChatId, "Stopped before completion") }
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: "The scheduled run could not finish."
            manager.finish(runId, resultChatId, reason)
            ScheduleNotifications.finished(applicationContext, notificationId, task.title,
                reason, null, task.id)
            return Result.failure()
        }
    }

    private companion object {
        const val MAX_FOREGROUND_ATTEMPTS = 6
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

/** A periodic repair is needed even when a one-time task has no next occurrence. */
class ScheduleRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ScheduleManager(applicationContext).reconcile()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ScheduleRecovery", "Could not repair scheduled work", e)
        Result.retry()
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
            .setSmallIcon(R.drawable.ic_schedule_mark)
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

    /** Opens the schedule's conversation; MainActivity prefers it over the plain chat extra. */
    const val EXTRA_OPEN_SCHEDULE = "open_schedule_id"

    fun finished(context: Context, id: Int, title: String, text: String, chatId: String?, scheduleId: String? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)
        // WorkManager cancels the foreground ID after doWork returns. Completion must
        // use a different namespace so it survives that cleanup (including failed runs).
        val completionId = (id and 0x00ff_ffff) or 0x3500_0000
        val intent = Intent(context, MainActivity::class.java).apply {
            if (chatId != null) putExtra(ReplyNotifications.EXTRA_OPEN_CHAT, chatId)
            if (scheduleId != null) putExtra(EXTRA_OPEN_SCHEDULE, scheduleId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, completionId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_schedule_mark).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending).setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(completionId, notification) }
    }
}
