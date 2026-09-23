package com.echoflow.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleDaoTest {
    @Test fun updatingSchedulePreservesRunHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = database.scheduleDao()
            val task = ScheduleTask(
                id = "schedule", title = "Initial", instruction = "Remind me",
                modelId = "test/model", status = ScheduleTask.ACTIVE,
                unit = ScheduleTask.DAY, interval = 1,
                anchorAt = 1_000_000L, zoneId = "UTC", nextRunAt = 1_000_000L,
            )
            dao.saveTask(task)
            dao.insertRun(ScheduleRun("run", task.id, task.anchorAt, ScheduleRun.SUCCEEDED))

            dao.saveTask(task.copy(title = "Renamed", nextRunAt = 2_000_000L))

            assertEquals("Renamed", dao.task(task.id)?.title)
            assertNotNull(dao.run("run"))
        } finally {
            database.close()
        }
    }
}
