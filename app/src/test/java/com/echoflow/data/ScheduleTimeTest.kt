package com.echoflow.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleTimeTest {
    private fun epoch(zone: String, year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test fun hourlyUsesElapsedTimeAndKeepsAnchor() {
        val start = epoch("UTC", 2026, 9, 23, 9)
        assertEquals(start + 2 * 3_600_000L,
            ScheduleTime.next(ScheduleTask.HOUR, 2, start, "UTC", start))
        assertEquals(start + 4 * 3_600_000L,
            ScheduleTime.next(ScheduleTask.HOUR, 2, start, "UTC", start + 3 * 3_600_000L))
    }

    @Test fun dailyStaysAtLocalHourAcrossDst() {
        val start = epoch("Europe/Dublin", 2026, 3, 28, 9)
        val next = ScheduleTime.next(ScheduleTask.DAY, 1, start, "Europe/Dublin", start)
        assertEquals(epoch("Europe/Dublin", 2026, 3, 29, 9), next)
    }

    @Test fun monthlyThirtyFirstSkipsFebruary() {
        val start = epoch("Europe/Dublin", 2026, 1, 31, 9)
        assertEquals(epoch("Europe/Dublin", 2026, 3, 31, 9),
            ScheduleTime.next(ScheduleTask.MONTH, 1, start, "Europe/Dublin", start))
    }

    @Test fun everyTwoWeeksDoesNotMoveAfterManualRun() {
        val start = epoch("UTC", 2026, 9, 21, 9)
        assertEquals(epoch("UTC", 2026, 10, 5, 9),
            ScheduleTime.next(ScheduleTask.WEEK, 2, start, "UTC", start + 3_600_000L))
    }

    @Test fun onceHasNoNextAfterItsTime() {
        val start = epoch("UTC", 2026, 9, 23, 9)
        assertNull(ScheduleTime.next(ScheduleTask.ONCE, 1, start, "UTC", start))
    }
}
