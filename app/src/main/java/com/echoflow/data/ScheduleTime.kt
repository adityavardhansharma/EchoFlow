package com.echoflow.data

import java.util.Calendar
import java.util.TimeZone

/** Pure recurrence calculations; [anchorAt] never changes on Pause, Resume or Run now. */
object ScheduleTime {
    private const val HOUR_MS = 3_600_000L

    fun next(task: ScheduleTask, after: Long): Long? = next(
        task.unit, task.interval, task.anchorAt, task.zoneId, after,
    )

    fun next(unit: String, interval: Int, anchorAt: Long, zoneId: String, after: Long): Long? {
        require(interval in 1..999)
        if (unit == ScheduleTask.ONCE) return anchorAt.takeIf { it > after }
        if (anchorAt > after) return anchorAt
        if (unit == ScheduleTask.HOUR) {
            val step = interval * HOUR_MS
            return anchorAt + ((after - anchorAt) / step + 1) * step
        }

        val zone = TimeZone.getTimeZone(zoneId)
        val base = Calendar.getInstance(zone).apply { timeInMillis = anchorAt }
        val now = Calendar.getInstance(zone).apply { timeInMillis = after }
        val firstIndex = when (unit) {
            ScheduleTask.DAY -> ((after - anchorAt) / (interval * 86_400_000L) - 2).coerceAtLeast(0)
            ScheduleTask.WEEK -> ((after - anchorAt) / (interval * 7L * 86_400_000L) - 2).coerceAtLeast(0)
            ScheduleTask.MONTH -> {
                val months = (now.get(Calendar.YEAR) - base.get(Calendar.YEAR)) * 12 +
                    now.get(Calendar.MONTH) - base.get(Calendar.MONTH)
                (months / interval - 1).toLong().coerceAtLeast(0)
            }
            else -> error("Unsupported recurrence: $unit")
        }
        // A few iterations cover DST shifts and missing days of a month. Monthly dates such as
        // the 31st skip months that lack that date instead of silently moving to the last day.
        for (index in firstIndex..firstIndex + 16) {
            val candidate = base.clone() as Calendar
            val steps = (index * interval).toInt()
            when (unit) {
                ScheduleTask.DAY -> candidate.add(Calendar.DAY_OF_YEAR, steps)
                ScheduleTask.WEEK -> candidate.add(Calendar.WEEK_OF_YEAR, steps)
                ScheduleTask.MONTH -> {
                    candidate.set(Calendar.DAY_OF_MONTH, 1)
                    candidate.add(Calendar.MONTH, steps)
                    if (base.get(Calendar.DAY_OF_MONTH) > candidate.getActualMaximum(Calendar.DAY_OF_MONTH)) continue
                    candidate.set(Calendar.DAY_OF_MONTH, base.get(Calendar.DAY_OF_MONTH))
                }
            }
            if (candidate.timeInMillis > after) return candidate.timeInMillis
        }
        error("Could not calculate next occurrence")
    }

    fun preview(task: ScheduleTask, count: Int = 3): List<Long> {
        val result = mutableListOf<Long>()
        var after = System.currentTimeMillis()
        repeat(count) {
            val next = next(task, after) ?: return result
            result += next
            after = next
        }
        return result
    }
}
