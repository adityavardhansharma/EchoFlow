package com.echoflow.data

import java.util.Calendar
import java.util.TimeZone

/**
 * Weekly day sets as a bitmask over [Calendar] day numbers (bit `day - 1`, Sunday = 1). A mask
 * keeps the column one integer and makes "same days" a cheap equality check.
 */
object ScheduleDays {
    /** Monday-first display order, independent of locale so tools and UI agree. */
    val ORDER = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
    )
    val WEEKDAYS: Set<Int> = ORDER.take(5).toSet()
    val WEEKEND: Set<Int> = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    val EVERY_DAY: Set<Int> = ORDER.toSet()

    private val keys = mapOf(
        Calendar.SUNDAY to "sun", Calendar.MONDAY to "mon", Calendar.TUESDAY to "tue",
        Calendar.WEDNESDAY to "wed", Calendar.THURSDAY to "thu", Calendar.FRIDAY to "fri",
        Calendar.SATURDAY to "sat",
    )

    fun mask(days: Collection<Int>): Int = days.filter { it in 1..7 }.fold(0) { acc, day -> acc or (1 shl (day - 1)) }
    fun days(mask: Int): Set<Int> = (1..7).filter { mask and (1 shl (it - 1)) != 0 }.toSet()
    fun key(day: Int): String = keys.getValue(day)

    /** Accepts "mon", "Monday", "MO"… so a model's natural spelling still lands on a day. */
    fun parse(value: String): Int? {
        val v = value.trim().lowercase()
        if (v.length < 2) return null
        return keys.entries.firstOrNull { (_, key) -> v.startsWith(key) || key.startsWith(v) }?.key
    }
}

/** Pure recurrence calculations; [anchorAt] never changes on Pause, Resume or Run now. */
object ScheduleTime {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    fun next(task: ScheduleTask, after: Long): Long? = next(
        task.unit, task.interval, task.anchorAt, task.zoneId, after, task.weekdays, task.endAt,
    )

    fun next(
        unit: String,
        interval: Int,
        anchorAt: Long,
        zoneId: String,
        after: Long,
        weekdays: Int = 0,
        endAt: Long? = null,
    ): Long? = unbounded(unit, interval, anchorAt, zoneId, after, weekdays)
        ?.takeIf { endAt == null || it <= endAt }

    private fun unbounded(unit: String, interval: Int, anchorAt: Long, zoneId: String, after: Long, weekdays: Int): Long? {
        require(interval in 1..999)
        if (unit == ScheduleTask.ONCE) return anchorAt.takeIf { it > after }
        if (anchorAt > after) return anchorAt
        if (unit == ScheduleTask.HOUR) {
            val step = interval * HOUR_MS
            return anchorAt + ((after - anchorAt) / step + 1) * step
        }
        if (unit == ScheduleTask.WEEK && weekdays != 0) return nextOnDays(interval, anchorAt, zoneId, after, weekdays)

        val zone = TimeZone.getTimeZone(zoneId)
        val base = Calendar.getInstance(zone).apply { timeInMillis = anchorAt }
        val now = Calendar.getInstance(zone).apply { timeInMillis = after }
        val firstIndex = when (unit) {
            ScheduleTask.DAY -> ((after - anchorAt) / (interval * DAY_MS) - 2).coerceAtLeast(0)
            ScheduleTask.WEEK -> ((after - anchorAt) / (interval * 7L * DAY_MS) - 2).coerceAtLeast(0)
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

    /**
     * "Every N weeks on Mon, Thu": walk local calendar days from [after], keeping the anchor's
     * wall-clock time, and accept a day only in an on-week. Weeks are Monday-based and counted
     * from the anchor's week, so the fortnight never drifts when a run happens manually.
     */
    private fun nextOnDays(interval: Int, anchorAt: Long, zoneId: String, after: Long, weekdays: Int): Long {
        val zone = TimeZone.getTimeZone(zoneId)
        val base = Calendar.getInstance(zone).apply { timeInMillis = anchorAt }
        val anchorWeek = mondayWeek(base)
        val cursor = Calendar.getInstance(zone).apply {
            timeInMillis = after
            set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, base.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(7 * interval + 8) {
            val onWeek = Math.floorMod(mondayWeek(cursor) - anchorWeek, interval.toLong()) == 0L
            val onDay = weekdays and (1 shl (cursor.get(Calendar.DAY_OF_WEEK) - 1)) != 0
            if (onWeek && onDay && cursor.timeInMillis > after) return cursor.timeInMillis
            cursor.add(Calendar.DAY_OF_YEAR, 1)
            cursor.set(Calendar.HOUR_OF_DAY, base.get(Calendar.HOUR_OF_DAY))
            cursor.set(Calendar.MINUTE, base.get(Calendar.MINUTE))
        }
        error("Could not calculate next occurrence")
    }

    /** Monday-based week number of the local date; 1970-01-01 was a Thursday. */
    private fun mondayWeek(calendar: Calendar): Long {
        val localDay = Math.floorDiv(calendar.timeInMillis + calendar.timeZone.getOffset(calendar.timeInMillis), DAY_MS)
        return Math.floorDiv(localDay + 3, 7L)
    }

    fun preview(task: ScheduleTask, count: Int = 3, from: Long = System.currentTimeMillis()): List<Long> {
        val result = mutableListOf<Long>()
        var after = from
        repeat(count) {
            val next = next(task, after) ?: return result
            result += next
            after = next
        }
        return result
    }

    /** End of the local calendar day [date] ("yyyy-MM-dd") in [zoneId], the inclusive end bound. */
    fun endOfDay(date: String, zoneId: String): Long? {
        val parts = date.trim().take(10).split('-').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        return Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply {
            isLenient = false
            clear()
            set(parts[0], parts[1] - 1, parts[2], 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.let { runCatching { it.timeInMillis }.getOrNull() }
    }
}
