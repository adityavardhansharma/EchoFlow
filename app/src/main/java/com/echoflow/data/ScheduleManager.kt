package com.echoflow.data

import android.content.Context
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import androidx.work.await

/** Room owns the schedule; WorkManager is a replaceable wake-up mechanism. */
class ScheduleManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.scheduleDao()
    private val work = WorkManager.getInstance(context.applicationContext)

    val tasks: Flow<List<ScheduleTask>> = dao.observeTasks()
    val runningTaskIds: Flow<List<String>> = dao.observeRunningTaskIds()
    fun runs(taskId: String): Flow<List<ScheduleRun>> = dao.observeRuns(taskId)
    fun task(id: String): Flow<ScheduleTask?> = dao.observeTask(id)
    suspend fun taskNow(id: String): ScheduleTask? = dao.task(id)
    suspend fun taskForThread(threadId: String): ScheduleTask? = dao.taskForThread(threadId)
    private val use24h get() = android.text.format.DateFormat.is24HourFormat(context)

    suspend fun save(draft: ScheduleTask): ScheduleTask {
        require(draft.title.isNotBlank() && draft.instruction.isNotBlank() && draft.modelId.isNotBlank())
        require(draft.interval in 1..999)
        require(draft.unit in setOf(ScheduleTask.ONCE, ScheduleTask.HOUR, ScheduleTask.DAY, ScheduleTask.WEEK, ScheduleTask.MONTH))
        ensureRecovery()
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
                threadId = draft.threadId ?: old?.threadId,
            ).also { task ->
                dao.saveTask(task)
                // The conversation is named after its schedule and follows renames.
                task.threadId?.let { database.chatDao().setTitle(it, task.title) }
            }
        }
        enqueueNext(saved)
        return saved
    }

    suspend fun setStatus(id: String, status: String): ScheduleTask? {
        require(status in setOf(ScheduleTask.ACTIVE, ScheduleTask.PAUSED, ScheduleTask.COMPLETED))
        val old = dao.task(id) ?: return null
        if (old.status == status) return old
        val saved = save(old.copy(status = status))
        val stoppedRun = status == ScheduleTask.COMPLETED && stopCurrentRun(id, postEvent = false)
        when (status) {
            ScheduleTask.PAUSED -> post(saved, "Paused. Nothing runs until you resume.", ScheduleEvent(ScheduleEvent.PAUSED))
            ScheduleTask.COMPLETED -> post(saved,
                if (stoppedRun) "You stopped this run and ended the schedule. Earlier answers stay here."
                else "Ended. Earlier answers stay here.", ScheduleEvent(ScheduleEvent.ENDED))
            else -> post(saved, "Resumed" + (saved.nextRunAt?.let {
                " · next run ${ScheduleText.occurrence(it, saved.zoneId, use24h)}"
            } ?: "") + ".", ScheduleEvent(ScheduleEvent.RESUMED))
        }
        return saved
    }

    /**
     * The conversation a schedule lives in, created on demand: v28 schedules have none, and a
     * person may delete it from the drawer while the schedule keeps running.
     */
    suspend fun ensureThread(taskId: String): String? = database.withTransaction {
        ensureThreadInTransaction(taskId)
    }

    private suspend fun ensureThreadInTransaction(taskId: String): String? {
        val task = dao.task(taskId) ?: return null
        task.threadId?.takeIf { database.chatDao().getThreadById(it) != null }?.let { return it }
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        database.chatDao().insertThread(ChatThread(id, task.title, now, now, scheduleId = task.id))
        dao.saveTask(task.copy(threadId = id))
        return id
    }

    /** Appends a schedule-authored row to its conversation and floats it up the drawer. */
    suspend fun post(task: ScheduleTask, content: String, event: ScheduleEvent, at: Long = System.currentTimeMillis()): String? {
        val threadId = ensureThread(task.id) ?: return null
        database.messageDao().insertMessage(ChatMessage(
            id = UUID.randomUUID().toString(), chatId = threadId, role = "assistant",
            content = content, createdAt = at, scheduleEvent = event.toJson(),
        ))
        database.chatDao().touchUpdatedAt(threadId, at)
        return threadId
    }

    /** Recent run answers (oldest first) so a run can avoid repeating itself. */
    suspend fun recentAnswers(task: ScheduleTask, limit: Int = 2): List<Pair<Long, String>> {
        val threadId = task.threadId ?: return emptyList()
        return dao.recentRunAnswers(threadId, limit).reversed().map { message ->
            (ScheduleEvent.parse(message.scheduleEvent)?.scheduledAt ?: message.createdAt) to message.content
        }
    }

    suspend fun runNumber(taskId: String): Int = dao.succeededRuns(taskId) + 1

    suspend fun delete(id: String) {
        stopCurrentRun(id)
        dao.task(id)?.let { task ->
            task.nextRunAt?.let { at ->
                val runId = occurrenceId(id, at, task.revision)
                work.cancelUniqueWork("schedule:$runId").await()
                work.cancelUniqueWork("schedule-warm:$runId").await()
            }
        }
        dao.unlinkThreads(id)
        dao.deleteTask(id)
    }

    suspend fun runNow(taskId: String) {
        val task = dao.task(taskId) ?: return
        enqueue(task, System.currentTimeMillis(), manual = true)
    }

    suspend fun stopCurrentRun(taskId: String, postEvent: Boolean = true): Boolean {
        val current = dao.runningForTask(taskId) ?: return false
        database.withTransaction {
            val running = dao.run(current.id) ?: return@withTransaction
            if (running.status != ScheduleRun.RUNNING) return@withTransaction
            val now = System.currentTimeMillis()
            dao.updateRun(running.copy(status = ScheduleRun.CANCELLED,
                finishedAt = now, error = "Stopped by you"))
            val task = dao.task(taskId)
            if (task != null && task.nextRunAt == null && task.status == ScheduleTask.ACTIVE) {
                dao.saveTask(task.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
            }
        }
        work.cancelUniqueWork("schedule:${current.id}").await()
        dao.task(taskId)?.let {
            if (postEvent) post(it, "You stopped this run.", ScheduleEvent(ScheduleEvent.RUN_FAILED, runId = current.id, scheduledAt = current.scheduledAt))
            enqueueNext(it)
        }
        return true
    }

    /** Repairs work after a crash, reboot, package replacement, or a failed enqueue. */
    suspend fun reconcile(replaceQueued: Boolean = false) {
        ensureRecovery()
        val now = System.currentTimeMillis()
        val missed = mutableListOf<Pair<ScheduleTask, Long>>()
        dao.interruptedRuns().filter { (it.startedAt ?: now) < now - INTERRUPTED_RUN_GRACE_MS }.forEach {
            val infos = workInfos("schedule:${it.id}")
            if (infos.any { info -> info.state == WorkInfo.State.RUNNING }) return@forEach
            database.withTransaction {
                val run = dao.run(it.id) ?: return@withTransaction
                if (run.status == ScheduleRun.RUNNING) dao.updateRun(run.copy(
                    status = ScheduleRun.FAILED, finishedAt = now,
                    error = "Interrupted before completion. The run was not replayed to avoid duplicate actions.",
                ))
            }
        }
        dao.activeTasks().forEach { task ->
            val next = task.nextRunAt
            when {
                next == null -> {
                    if (dao.runningForTask(task.id) != null) return@forEach
                    if (task.unit == ScheduleTask.ONCE || ScheduleTime.next(task, now) == null) {
                        dao.saveTask(task.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
                    } else save(task)
                }
                next < now - MAX_LATENESS_MS -> {
                    database.withTransaction {
                        val fresh = dao.task(task.id) ?: return@withTransaction
                        if (fresh.revision != task.revision || fresh.nextRunAt != next) return@withTransaction
                        dao.insertRun(ScheduleRun(occurrenceId(task.id, next, task.revision), task.id,
                            next, ScheduleRun.MISSED, finishedAt = now, error = "Device was unavailable at the scheduled time."))
                        missed += fresh to next
                        val following = ScheduleTime.next(fresh, now)
                        dao.saveTask(fresh.copy(
                            status = if (following == null) ScheduleTask.COMPLETED else fresh.status,
                            nextRunAt = following, updatedAt = now,
                        ))
                    }
                    dao.task(task.id)?.let { enqueueNext(it, replaceQueued) }
                }
                else -> enqueueNext(task, replaceQueued)
            }
        }
        missed.forEach { (task, at) ->
            runCatching { post(task, "Missed the ${ScheduleText.occurrence(at, task.zoneId, use24h)} run — the phone wasn't available to run it.",
                ScheduleEvent(ScheduleEvent.MISSED, scheduledAt = at)) }
        }
    }

    /** Claims an occurrence atomically; only one worker may perform a model request. */
    suspend fun claim(taskId: String, at: Long, revision: Long, manual: Boolean, runId: String): ScheduleTask? {
        ensureRecovery()
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
            if (task != null && task.nextRunAt == null && task.status == ScheduleTask.ACTIVE) {
                dao.saveTask(task.copy(status = ScheduleTask.COMPLETED,
                    updatedAt = System.currentTimeMillis()))
            }
        }
        dao.run(runId)?.let { run -> dao.task(run.taskId)?.let {
            if (run.status == ScheduleRun.FAILED) runCatching {
                post(it, "This run didn't finish: ${run.error ?: "unknown error"}",
                    ScheduleEvent(ScheduleEvent.RUN_FAILED, runId = run.id, scheduledAt = run.scheduledAt))
            }
            runCatching { enqueueNext(it) }
        } }
    }

    /**
     * Posts the answer into the schedule's conversation and completes the run in one transaction,
     * so a run is never marked done without its answer (or vice versa).
     */
    suspend fun saveAnswer(runId: String, task: ScheduleTask, answer: String): String {
        val now = System.currentTimeMillis()
        val chatId = database.withTransaction {
            val chatId = ensureThreadInTransaction(task.id) ?: error("The schedule no longer exists")
            val run = dao.run(runId) ?: error("Run no longer exists")
            require(run.status == ScheduleRun.RUNNING)
            database.messageDao().insertMessage(ChatMessage(
                id = UUID.randomUUID().toString(), chatId = chatId, role = "assistant", content = answer,
                createdAt = now, scheduleEvent = ScheduleEvent(ScheduleEvent.RUN, runId = runId,
                    scheduledAt = run.scheduledAt).toJson(),
            ))
            database.chatDao().touchUpdatedAt(chatId, now)
            dao.updateRun(run.copy(status = ScheduleRun.SUCCEEDED, finishedAt = now,
                resultChatId = chatId))
            val latest = dao.task(task.id)
            if (latest != null && latest.nextRunAt == null && latest.status == ScheduleTask.ACTIVE) {
                dao.saveTask(latest.copy(status = ScheduleTask.COMPLETED, updatedAt = now))
            }
            chatId
        }
        // The result is already committed. A transient queue failure is repaired on the next
        // app start or boot; it must not turn a successful run into a reported failure.
        dao.task(task.id)?.let { runCatching { enqueueNext(it) } }
        return chatId
    }

    // Register before committing a schedule or claim so a process death cannot leave Room
    // waiting for an app launch to repair it. KEEP never postpones the existing watchdog.
    private suspend fun ensureRecovery() {
        work.enqueueUniquePeriodicWork(
            RECOVERY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScheduleRecoveryWorker>(15, TimeUnit.MINUTES)
                .setInitialDelay(15, TimeUnit.MINUTES).build(),
        ).await()
    }

    private suspend fun enqueueNext(task: ScheduleTask, replaceQueued: Boolean = false) {
        if (task.status != ScheduleTask.ACTIVE) return
        val next = task.nextRunAt ?: return
        enqueue(task, next, manual = false, replaceQueued = replaceQueued)
    }

    private suspend fun enqueue(task: ScheduleTask, at: Long, manual: Boolean,
        replaceQueued: Boolean = false) {
        val runId = if (manual) UUID.randomUUID().toString() else occurrenceId(task.id, at, task.revision)
        val workName = "schedule:$runId"
        if (replaceQueued && workInfos(workName)
                .any { it.state == WorkInfo.State.RUNNING }) return
        val request = OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(workDataOf("taskId" to task.id, "scheduledAt" to at,
                "revision" to task.revision, "manual" to manual, "runId" to runId))
            .setInitialDelay(if (manual) 0L else (at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        val policy = if (replaceQueued) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        work.enqueueUniqueWork(workName, policy, request).await()
        if (!manual && task.modelId.startsWith("local/") && at - System.currentTimeMillis() > 5 * 60_000L) {
            val warm = OneTimeWorkRequestBuilder<ScheduleWarmupWorker>()
                .setInputData(workDataOf("taskId" to task.id, "scheduledAt" to at,
                    "revision" to task.revision))
                .setInitialDelay((at - System.currentTimeMillis() - 5 * 60_000L).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .build()
            val warmName = "schedule-warm:$runId"
            if (!replaceQueued || workInfos(warmName)
                    .none { it.state == WorkInfo.State.RUNNING }) {
                work.enqueueUniqueWork(warmName, policy, warm).await()
            }
        } else if (replaceQueued) {
            work.cancelUniqueWork("schedule-warm:$runId").await()
        }
    }

    private suspend fun workInfos(name: String): List<WorkInfo> = withContext(Dispatchers.IO) {
        work.getWorkInfosForUniqueWork(name).get()
    }

    companion object {
        internal const val RECOVERY_WORK_NAME = "schedule-recovery"
        internal const val INTERRUPTED_RUN_GRACE_MS = 30 * 60_000L
        const val MAX_LATENESS_MS = 60 * 60_000L
        fun occurrenceId(taskId: String, at: Long, revision: Long): String =
            UUID.nameUUIDFromBytes("$taskId:$at:$revision".toByteArray(Charsets.UTF_8)).toString()
    }
}
