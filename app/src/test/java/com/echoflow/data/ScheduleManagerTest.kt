package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
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
}
