package com.echoflow.data

import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTextTest {
    private val en = Locale.US
    private fun draft(unit: String, interval: Int = 1, days: Set<Int> = emptySet()) = ScheduleDraft(
        title = "t", instruction = "i", unit = unit, interval = interval, hour = 8, minute = 0,
        weekdays = days, monthDay = 15, modelId = "m",
    )

    @Test fun describesCommonCadences() {
        assertEquals("Every day at 8:00 AM", ScheduleText.cadence(draft(ScheduleTask.DAY), false, en))
        assertEquals("Weekdays at 08:00", ScheduleText.cadence(draft(ScheduleTask.WEEK, days = ScheduleDays.WEEKDAYS), true, en))
        assertEquals("Every Monday at 8:00 AM", ScheduleText.cadence(draft(ScheduleTask.WEEK, days = setOf(Calendar.MONDAY)), false, en))
        assertEquals("Every 2 weeks · Mon, Thu at 8:00 AM",
            ScheduleText.cadence(draft(ScheduleTask.WEEK, 2, setOf(Calendar.THURSDAY, Calendar.MONDAY)), false, en))
        assertEquals("Monthly · the 15th at 8:00 AM", ScheduleText.cadence(draft(ScheduleTask.MONTH), false, en))
        assertEquals("Every 3 hours", ScheduleText.cadence(draft(ScheduleTask.HOUR, 3), false, en))
    }

    @Test fun relativeTimesReadNaturally() {
        val now = 1_000_000_000_000L
        assertEquals("in 45 min", ScheduleText.relative(now + 45 * 60_000L, now, "UTC"))
        assertEquals("in 2 h 5 min", ScheduleText.relative(now + 125 * 60_000L, now, "UTC"))
        assertEquals("in 3 days", ScheduleText.relative(now + 3 * 86_400_000L, now, "UTC"))
    }

    @Test fun ordinals() {
        assertEquals("1st", ScheduleText.ordinal(1, en))
        assertEquals("12th", ScheduleText.ordinal(12, en))
        assertEquals("23rd", ScheduleText.ordinal(23, en))
    }
}
