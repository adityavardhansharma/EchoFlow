package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleManagerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseField = AppDatabase::class.java.getDeclaredField("INSTANCE").apply {
        isAccessible = true
    }

    @Before fun resetDatabase() {
        // DatabaseUpgradeTest closes the process singleton; each Robolectric test needs its own.
        databaseField.set(null, null)
        context.deleteDatabase("local_chat_database")
    }

    @After fun closeDatabase() {
        (databaseField.get(null) as? AppDatabase)?.close()
        databaseField.set(null, null)
        context.deleteDatabase("local_chat_database")
    }

    @Test fun clockRecoveryReplacesQueuedOccurrence() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val task = manager.save(ScheduleTask(
            id = UUID.randomUUID().toString(), title = "Clock test",
            instruction = "Remind me", modelId = "test/model",
            status = ScheduleTask.ACTIVE, unit = ScheduleTask.DAY, interval = 1,
            anchorAt = System.currentTimeMillis() + 3_600_000L,
            zoneId = "UTC", nextRunAt = null,
        ))
        try {
            val name = "schedule:${ScheduleManager.occurrenceId(task.id, task.nextRunAt!!, task.revision)}"
            val first = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
                .single().id

            manager.reconcile(replaceQueued = true)

            val replacement = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
                .first { !it.state.isFinished }.id
            assertNotEquals(first, replacement)
        } finally {
            manager.delete(task.id)
        }
    }
    private fun oneTime(at: Long) = ScheduleTask(
        id = UUID.randomUUID().toString(), title = "Reminder", instruction = "Plan the day",
        modelId = "test/model", status = ScheduleTask.ACTIVE, unit = ScheduleTask.ONCE,
        interval = 1, anchorAt = at, zoneId = "UTC", nextRunAt = at,
    )

    @Test fun recentInterruptedRunGetsAWatchdogAndEventuallyFinishes() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val dao = AppDatabase.getDatabase(context).scheduleDao()
        val now = System.currentTimeMillis()
        val task = oneTime(now - 1_000).copy(nextRunAt = null)
        dao.saveTask(task)
        val run = ScheduleRun("interrupted", task.id, task.anchorAt, ScheduleRun.RUNNING, startedAt = now)
        dao.insertRun(run)

        manager.reconcile()
        assertEquals(ScheduleRun.RUNNING, dao.run(run.id)?.status)
        val work = WorkManager.getInstance(context)
        val watchdog = work.getWorkInfosForUniqueWork(ScheduleManager.RECOVERY_WORK_NAME).get().single()
        assertFalse(watchdog.state.isFinished)

        // No app launch is needed once the grace period passes: the recovery worker repairs it.
        dao.updateRun(run.copy(startedAt = now - ScheduleManager.INTERRUPTED_RUN_GRACE_MS - 1_000))
        val result = TestListenableWorkerBuilder<ScheduleRecoveryWorker>(context).build().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(ScheduleRun.FAILED, dao.run(run.id)?.status)
        assertEquals(ScheduleTask.COMPLETED, dao.task(task.id)?.status)
        assertEquals(watchdog.id, work.getWorkInfosForUniqueWork(ScheduleManager.RECOVERY_WORK_NAME).get().single().id)
    }

    @Test fun savingRegistersRecoveryBeforeAnyOccurrenceRuns() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        manager.save(oneTime(System.currentTimeMillis() + 3_600_000L))
        val watchdog = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(ScheduleManager.RECOVERY_WORK_NAME).get().single()
        assertFalse(watchdog.state.isFinished)
    }

    @Test fun scheduleAgainPersistsAndEnqueuesCompletedOneTimeTask() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val dao = AppDatabase.getDatabase(context).scheduleDao()
        val now = System.currentTimeMillis()
        val original = oneTime(now - 60_000).copy(status = ScheduleTask.COMPLETED, nextRunAt = null)
        dao.saveTask(original)
        dao.insertRun(ScheduleRun("old-run", original.id, original.anchorAt, ScheduleRun.SUCCEEDED))
        val future = oneTime(now + 3_600_000L).toDraft()
        val saved = manager.save(future.resolve(original))
        assertEquals(ScheduleTask.ACTIVE, saved.status)
        assertNotNull(saved.nextRunAt)
        assertNotNull(dao.run("old-run"))
        val workName = "schedule:${ScheduleManager.occurrenceId(saved.id, saved.nextRunAt!!, saved.revision)}"
        assertFalse(WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get().single().state.isFinished)
    }

    @Test fun theSameOccurrenceCannotBeClaimedTwice() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val dao = AppDatabase.getDatabase(context).scheduleDao()
        val task = oneTime(System.currentTimeMillis() - 1_000)
        dao.saveTask(task)
        val id = ScheduleManager.occurrenceId(task.id, task.anchorAt, task.revision)
        assertNotNull(manager.claim(task.id, task.anchorAt, task.revision, false, id))
        assertNull(manager.claim(task.id, task.anchorAt, task.revision, false, id))
        assertEquals(ScheduleRun.RUNNING, dao.run(id)?.status)
        manager.stopCurrentRun(task.id)
        manager.finish(id, null, "late failure")
        assertEquals(ScheduleRun.CANCELLED, dao.run(id)?.status)
    }

    @Test fun runAnswersPostIntoTheSchedulesConversation() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val database = AppDatabase.getDatabase(context)
        val task = oneTime(System.currentTimeMillis() - 1_000)
        database.scheduleDao().saveTask(task)
        val runId = ScheduleManager.occurrenceId(task.id, task.anchorAt, task.revision)
        assertNotNull(manager.claim(task.id, task.anchorAt, task.revision, false, runId))
        val threadId = manager.saveAnswer(runId, task, "Plan: three things")
        assertEquals(threadId, database.scheduleDao().task(task.id)?.threadId)
        assertEquals(task.id, database.chatDao().getThreadById(threadId)?.scheduleId)
        val answer = database.messageDao().getMessagesForChatSync(threadId).single()
        assertEquals(ScheduleEvent.RUN, ScheduleEvent.parse(answer.scheduleEvent)?.type)
        assertEquals(listOf(task.anchorAt to "Plan: three things"), manager.recentAnswers(database.scheduleDao().task(task.id)!!))

        manager.delete(task.id)
        assertEquals(null, database.chatDao().getThreadById(threadId)?.scheduleId)
    }

    @Test fun finalRecurringRunCompletesAndDoesNotFailRecovery() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val dao = AppDatabase.getDatabase(context).scheduleDao()
        val at = System.currentTimeMillis() - 1_000
        val task = oneTime(at).copy(unit = ScheduleTask.DAY, endAt = at + 1_000)
        dao.saveTask(task)
        val runId = ScheduleManager.occurrenceId(task.id, at, task.revision)
        assertNotNull(manager.claim(task.id, at, task.revision, false, runId))
        assertNull(dao.task(task.id)?.nextRunAt)
        manager.saveAnswer(runId, task, "Done")
        assertEquals(ScheduleTask.COMPLETED, dao.task(task.id)?.status)
        manager.reconcile()
        assertEquals(ScheduleTask.COMPLETED, dao.task(task.id)?.status)
    }

    @Test fun failedFinalRecurringRunAlsoCompletes() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val dao = AppDatabase.getDatabase(context).scheduleDao()
        val at = System.currentTimeMillis() - 1_000
        val task = oneTime(at).copy(unit = ScheduleTask.DAY, endAt = at + 1_000)
        dao.saveTask(task)
        val runId = ScheduleManager.occurrenceId(task.id, at, task.revision)
        assertNotNull(manager.claim(task.id, at, task.revision, false, runId))
        manager.finish(runId, null, "Model failed")
        assertEquals(ScheduleTask.COMPLETED, dao.task(task.id)?.status)
    }

    @Test fun deletedConversationIsRecreatedWhenSavingRunAnswer() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = ScheduleManager(context)
        val database = AppDatabase.getDatabase(context)
        val task = oneTime(System.currentTimeMillis() - 1_000)
        database.scheduleDao().saveTask(task)
        val runId = ScheduleManager.occurrenceId(task.id, task.anchorAt, task.revision)
        assertNotNull(manager.claim(task.id, task.anchorAt, task.revision, false, runId))
        val oldThread = manager.ensureThread(task.id)!!
        database.chatDao().deleteThread(database.chatDao().getThreadById(oldThread)!!)
        val newThread = manager.saveAnswer(runId, task, "Recovered answer")
        assertNotEquals(oldThread, newThread)
        assertEquals("Recovered answer", database.messageDao().getMessagesForChatSync(newThread).single().content)
        assertEquals(ScheduleRun.SUCCEEDED, database.scheduleDao().run(runId)?.status)
    }
}
