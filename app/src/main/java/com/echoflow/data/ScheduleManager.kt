package com.echoflow.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import androidx.work.await

/** Room owns the schedule; WorkManager is a replaceable wake-up mechanism. */
class ScheduleManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.scheduleDao()
    private val work = WorkManager.getInstance(context.applicationContext)

    val tasks: Flow<List<ScheduleTask>> = dao.observeTasks()
    fun runs(taskId: String): Flow<List<ScheduleRun>> = dao.observeRuns(taskId)

    suspend fun save(draft: ScheduleTask): ScheduleTask {
        require(draft.title.isNotBlank() && draft.instruction.isNotBlank() && draft.modelId.isNotBlank())
        require(draft.interval in 1..999)
        require(draft.unit in setOf(ScheduleTask.ONCE, ScheduleTask.HOUR, ScheduleTask.DAY, ScheduleTask.WEEK, ScheduleTask.MONTH))
        val saved = database.withTransaction {
            val old = dao.task(draft.id)
            val now = System.currentTimeMillis()
            val next = if (draft.status == ScheduleTask.ACTIVE) ScheduleTime.next(draft, now) else null
            require(draft.status != ScheduleTask.ACTIVE || next != null) {
                "This one-time schedule is in the past. Choose a future date to restart it."
            }
            draft.copy(
                title = draft.title.trim(), instruction = draft.instruction.trim(),
                nextRunAt = next, revision = (old?.revision ?: -1) + 1,
                createdAt = old?.createdAt ?: now, updatedAt = now,
            ).also { dao.saveTask(it) }
        }
        enqueueNext(saved)
        return saved
    }

    suspend fun setStatus(id: String, status: String) {
        require(status in setOf(ScheduleTask.ACTIVE, ScheduleTask.PAUSED, ScheduleTask.COMPLETED))
        val old = dao.task(id) ?: return
        save(old.copy(status = status))
        if (status == ScheduleTask.COMPLETED) stopCurrentRun(id)
    }

    suspend fun delete(id: String) = dao.deleteTask(id)

    suspend fun runNow(taskId: String) {
        val task = dao.task(taskId) ?: return
        enqueue(task, System.currentTimeMillis(), manual = true)
    }

    suspend fun stopCurrentRun(taskId: String) {
        val current = dao.runningForTask(taskId) ?: return
        database.withTransaction {
            val running = dao.run(current.id) ?: return@withTransaction
            if (running.status != ScheduleRun.RUNNING) return@withTransaction
            val now = System.currentTimeMillis()
            dao.updateRun(running.copy(status = ScheduleRun.CANCELLED,
                finishedAt = now, error = "Stopped by you"))
            val task = dao.task(taskId)
            if (task?.unit == ScheduleTask.ONCE && task.nextRunAt == null && task.status == ScheduleTask.ACTIVE) {
                dao.saveTask(task.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
            }
        }
        work.cancelUniqueWork("schedule:${current.id}").await()
        dao.task(taskId)?.let { enqueueNext(it) }
    }

    /** Repairs work after a crash, reboot, package replacement, or a failed enqueue. */
    suspend fun reconcile() {
        val now = System.currentTimeMillis()
        dao.interruptedRuns().filter { (it.startedAt ?: now) < now - 30 * 60_000L }.forEach {
            dao.updateRun(it.copy(status = ScheduleRun.FAILED, finishedAt = now,
                error = "Interrupted before completion. The run was not replayed to avoid duplicate actions."))
        }
        dao.activeTasks().forEach { task ->
            val next = task.nextRunAt
            when {
                next == null -> {
                    if (dao.runningForTask(task.id) != null) return@forEach
                    if (task.unit == ScheduleTask.ONCE) {
                        dao.saveTask(task.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
                    } else save(task)
                }
                next < now - MAX_LATENESS_MS -> {
                    database.withTransaction {
                        val fresh = dao.task(task.id) ?: return@withTransaction
                        if (fresh.revision != task.revision || fresh.nextRunAt != next) return@withTransaction
                        dao.insertRun(ScheduleRun(occurrenceId(task.id, next, task.revision), task.id,
                            next, ScheduleRun.MISSED, finishedAt = now, error = "Device was unavailable at the scheduled time."))
                        val following = ScheduleTime.next(fresh, now)
                        dao.saveTask(fresh.copy(
                            status = if (following == null) ScheduleTask.COMPLETED else fresh.status,
                            nextRunAt = following, updatedAt = now,
                        ))
                    }
                    dao.task(task.id)?.let { enqueueNext(it) }
                }
                else -> enqueueNext(task)
            }
        }
    }

    /** Claims an occurrence atomically; only one worker may perform a model request. */
    suspend fun claim(taskId: String, at: Long, revision: Long, manual: Boolean, runId: String): ScheduleTask? {
        return database.withTransaction {
            val task = dao.task(taskId) ?: return@withTransaction null
            if (!manual && (task.status != ScheduleTask.ACTIVE || task.revision != revision || task.nextRunAt != at)) {
                return@withTransaction null
            }
            if (!manual && System.currentTimeMillis() - at > MAX_LATENESS_MS) return@withTransaction null
            if (dao.runningForTask(taskId) != null) {
                if (!manual) {
                    dao.insertRun(ScheduleRun(runId, taskId, at, ScheduleRun.MISSED,
                        finishedAt = System.currentTimeMillis(),
                        error = "Previous run was still active."))
                    val next = ScheduleTime.next(task, at)
                    dao.saveTask(task.copy(nextRunAt = next,
                        status = if (next == null) ScheduleTask.COMPLETED else task.status,
                        updatedAt = System.currentTimeMillis()))
                }
                return@withTransaction null
            }
            val old = dao.run(runId)
            if (old != null && old.status != ScheduleRun.PENDING) return@withTransaction null
            if (old == null) dao.insertRun(ScheduleRun(runId, taskId, at, ScheduleRun.PENDING))
            if (dao.claimRun(runId, ScheduleRun.PENDING, ScheduleRun.RUNNING, System.currentTimeMillis()) != 1) {
                return@withTransaction null
            }
            if (!manual) {
                val next = ScheduleTime.next(task, at)
                dao.saveTask(task.copy(nextRunAt = next,
                    // A one-time task remains Active while its only run is in progress.
                    status = task.status,
                    updatedAt = System.currentTimeMillis()))
            }
            task
        }
    }

    suspend fun finish(runId: String, resultChatId: String?, error: String?) {
        database.withTransaction {
            val run = dao.run(runId) ?: return@withTransaction
            if (run.status != ScheduleRun.RUNNING) return@withTransaction
            dao.updateRun(run.copy(
                status = if (error == null) ScheduleRun.SUCCEEDED else ScheduleRun.FAILED,
                finishedAt = System.currentTimeMillis(), resultChatId = resultChatId, error = error,
            ))
            val task = dao.task(run.taskId)
            if (task?.unit == ScheduleTask.ONCE && task.nextRunAt == null && task.status == ScheduleTask.ACTIVE) {
                dao.saveTask(task.copy(status = ScheduleTask.COMPLETED,
                    updatedAt = System.currentTimeMillis()))
            }
        }
        dao.run(runId)?.let { run -> dao.task(run.taskId)?.let {
            runCatching { enqueueNext(it) }
        } }
    }

    /** Persist the answer and history link in the same transaction. */
    suspend fun saveAnswer(runId: String, task: ScheduleTask, answer: String): String {
        val now = System.currentTimeMillis()
        val chatId = UUID.randomUUID().toString()
        database.withTransaction {
            val run = dao.run(runId) ?: error("Run no longer exists")
            require(run.status == ScheduleRun.RUNNING)
            database.chatDao().insertThread(ChatThread(chatId, task.title, now, now))
            database.messageDao().insertMessage(ChatMessage(
                UUID.randomUUID().toString(), chatId, "user", task.instruction, now))
            database.messageDao().insertMessage(ChatMessage(
                UUID.randomUUID().toString(), chatId, "assistant", answer, now + 1))
            dao.updateRun(run.copy(status = ScheduleRun.SUCCEEDED, finishedAt = now,
                resultChatId = chatId))
            val latest = dao.task(task.id)
            if (latest?.unit == ScheduleTask.ONCE && latest.nextRunAt == null && latest.status == ScheduleTask.ACTIVE) {
                dao.saveTask(latest.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
            }
        }
        // The result is already committed. A transient queue failure is repaired on the next
        // app start or boot; it must not turn a successful run into a reported failure.
        dao.task(task.id)?.let { runCatching { enqueueNext(it) } }
        return chatId
    }

    private suspend fun enqueueNext(task: ScheduleTask) {
        if (task.status != ScheduleTask.ACTIVE) return
        val next = task.nextRunAt ?: return
        enqueue(task, next, manual = false)
    }

    private suspend fun enqueue(task: ScheduleTask, at: Long, manual: Boolean) {
        val runId = if (manual) UUID.randomUUID().toString() else occurrenceId(task.id, at, task.revision)
        val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(workDataOf("taskId" to task.id, "scheduledAt" to at,
                "revision" to task.revision, "manual" to manual, "runId" to runId))
            .setInitialDelay(if (manual) 0L else (at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        work.enqueueUniqueWork("schedule:$runId", ExistingWorkPolicy.KEEP, request).await()
        if (!manual && task.modelId.startsWith("local/") && at - System.currentTimeMillis() > 5 * 60_000L) {
            val warm = OneTimeWorkRequestBuilder<ScheduleWarmupWorker>()
                .setInputData(workDataOf("taskId" to task.id, "scheduledAt" to at,
                    "revision" to task.revision))
                .setInitialDelay((at - System.currentTimeMillis() - 5 * 60_000L).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            work.enqueueUniqueWork("schedule-warm:$runId", ExistingWorkPolicy.KEEP, warm).await()
        }
    }

    companion object {
        const val MAX_LATENESS_MS = 60 * 60_000L
        fun occurrenceId(taskId: String, at: Long, revision: Long): String =
            UUID.nameUUIDFromBytes("$taskId:$at:$revision".toByteArray(Charsets.UTF_8)).toString()
    }
}
