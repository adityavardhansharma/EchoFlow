package com.echoflow.data

import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class ScheduleDraftTest {
    private fun epoch(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private val now = epoch("2026-09-23T12:00:00Z")

    private fun task(unit: String, anchor: Long, interval: Int = 1) = ScheduleTask(
        id = "task", title = "Reminder", instruction = "Plan the day", modelId = "test/model",
        status = ScheduleTask.ACTIVE, unit = unit, interval = interval,
        anchorAt = anchor, zoneId = "UTC", nextRunAt = null,
    )

    @Test fun unchangedFortnightlyTimingPreservesTheOriginalWeek() {
        val original = task(ScheduleTask.WEEK, epoch("2026-09-21T09:00:00Z"), 2)
        val resolved = original.toDraft().copy(title = "Renamed").resolve(original, now = now)
        assertEquals(original.anchorAt, resolved.anchorAt)
        assertEquals(epoch("2026-10-05T09:00:00Z"), resolved.nextRunAt)
        assertEquals("Renamed", resolved.title)
    }

    @Test fun unchangedHourlyTimingDoesNotRestartTheInterval() {
        val original = task(ScheduleTask.HOUR, epoch("2026-09-23T09:00:00Z"), 2)
        val resolved = original.toDraft().resolve(original, now = now)
        assertEquals(original.anchorAt, resolved.anchorAt)
        assertEquals(epoch("2026-09-23T13:00:00Z"), resolved.nextRunAt)
    }

    @Test fun timingEditsKeepTheSchedulesTimezoneAfterTravel() {
        val defaultZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val original = task(ScheduleTask.DAY, epoch("2026-09-23T09:00:00Z"))
            val resolved = original.toDraft().copy(hour = 14).resolve(original, now = now)
            assertEquals("UTC", resolved.zoneId)
            assertEquals(epoch("2026-09-23T14:00:00Z"), resolved.nextRunAt)
        } finally {
            TimeZone.setDefault(defaultZone)
        }
    }

    @Test fun completedOneTimeTaskBecomesActiveWhenScheduledAgain() {
        val original = task(ScheduleTask.ONCE, epoch("2026-09-22T09:00:00Z"))
            .copy(status = ScheduleTask.COMPLETED)
        val resolved = original.toDraft().copy(onceDate = "2026-09-24 09:00")
            .resolve(original, now = now)
        assertEquals(original.id, resolved.id)
        assertEquals(ScheduleTask.ACTIVE, resolved.status)
        assertEquals(epoch("2026-09-24T09:00:00Z"), resolved.nextRunAt)
    }

    @Test fun completedOneTimeTaskStillRequiresAFutureDate() {
        val original = task(ScheduleTask.ONCE, epoch("2026-09-22T09:00:00Z"))
            .copy(status = ScheduleTask.COMPLETED)
        assertThrows(IllegalArgumentException::class.java) {
            original.toDraft().resolve(original, now = now)
        }
    }

    @Test fun editingPausedAndCompletedRecurringTasksDoesNotActivateThem() {
        for (status in listOf(ScheduleTask.PAUSED, ScheduleTask.COMPLETED)) {
            val original = task(ScheduleTask.DAY, epoch("2026-09-22T09:00:00Z")).copy(status = status)
            val resolved = original.toDraft().copy(hour = 14).resolve(original, now = now)
            assertEquals(status, resolved.status)
            assertNull(resolved.nextRunAt)
        }
    }
}
