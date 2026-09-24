package com.echoflow.data

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One voice for describing a schedule — the home list, the chat card, notifications and the
 * tool results a model reads all say "Weekdays at 8:00 AM" the same way. Pure so it is testable
 * and so a model never sees wording the UI would not show.
 */
object ScheduleText {
    fun time(hour: Int, minute: Int, use24h: Boolean, locale: Locale = Locale.getDefault()): String =
        if (use24h) "%02d:%02d".format(locale, hour, minute)
        else {
            val period = DateFormatSymbols(locale).amPmStrings[if (hour >= 12) 1 else 0]
            "%d:%02d %s".format(locale, (hour + 11) % 12 + 1, minute, period)
        }

    /** "Weekdays", "Every 2 weeks · Mon, Thu", "Monthly · 15th" — the cadence without the clock. */
    fun repeat(draft: ScheduleDraft, locale: Locale = Locale.getDefault()): String = when (draft.unit) {
        ScheduleTask.ONCE -> "Once" + (dateLabel(draft.onceDate, locale)?.let { " · $it" } ?: "")
        ScheduleTask.HOUR -> if (draft.interval == 1) "Every hour" else "Every ${draft.interval} hours"
        ScheduleTask.DAY -> if (draft.interval == 1) "Every day" else "Every ${draft.interval} days"
        ScheduleTask.WEEK -> {
            val days = draft.weekdays
            val named = when {
                days == ScheduleDays.EVERY_DAY -> "every day"
                days == ScheduleDays.WEEKDAYS -> "weekdays"
                days == ScheduleDays.WEEKEND -> "weekends"
                days.size == 1 -> longDay(days.first(), locale)
                else -> ScheduleDays.ORDER.filter { it in days }.joinToString(", ") { shortDay(it, locale) }
            }
            when {
                draft.interval > 1 -> "Every ${draft.interval} weeks · ${named.replaceFirstChar { it.titlecase(locale) }}"
                days.size == 1 -> "Every $named"
                else -> named.replaceFirstChar { it.titlecase(locale) }
            }
        }
        ScheduleTask.MONTH -> (if (draft.interval == 1) "Monthly" else "Every ${draft.interval} months") +
            " · the ${ordinal(draft.monthDay, locale)}"
        else -> draft.unit
    }

    /** The full sentence: "Weekdays at 8:00 AM", "Every hour at :15", "Once · Thu, Oct 1 at 9:00 AM". */
    fun cadence(draft: ScheduleDraft, use24h: Boolean, locale: Locale = Locale.getDefault()): String =
        if (draft.unit == ScheduleTask.HOUR) {
            repeat(draft, locale) + if (draft.minute == 0) "" else " at :%02d".format(locale, draft.minute)
        } else "${repeat(draft, locale)} at ${time(draft.hour, draft.minute, use24h, locale)}"

    fun cadence(task: ScheduleTask, use24h: Boolean, locale: Locale = Locale.getDefault()): String =
        cadence(task.toDraft(), use24h, locale)

    /** "until Oct 22" (the year only when it isn't this year). */
    fun until(endDate: String?, locale: Locale = Locale.getDefault(), now: Long = System.currentTimeMillis()): String? =
        endDate?.let { dateLabel(it, locale, withWeekday = false, now = now) }?.let { "until $it" }

    /** "Thu, Sep 24 · 8:00 AM" in the schedule's own zone. */
    fun occurrence(at: Long, zoneId: String, use24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = at }
        val day = SimpleDateFormat("EEE, MMM d", locale).apply { timeZone = calendar.timeZone }.format(Date(at))
        return "$day · ${time(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), use24h, locale)}"
    }

    /** "in 45 min", "in 2 h 10 min", "tomorrow", "in 3 days", or "now". */
    fun relative(at: Long, now: Long = System.currentTimeMillis(), zoneId: String = TimeZone.getDefault().id): String {
        val delta = at - now
        if (delta < 60_000L) return "now"
        val minutes = delta / 60_000L
        if (minutes < 60) return "in $minutes min"
        val hours = minutes / 60
        if (hours < 12) return if (minutes % 60 == 0L) "in $hours h" else "in $hours h ${minutes % 60} min"
        val zone = TimeZone.getTimeZone(zoneId)
        val days = localDay(at, zone) - localDay(now, zone)
        return when (days) {
            0L -> "today"
            1L -> "tomorrow"
            else -> "in $days days"
        }
    }

    /**
     * When the next run is, short enough for a title bar: "in 40 min", "tomorrow · 8:00 AM",
     * "Fri, Sep 25 · 8:00 AM" — never the relative word and the full date together.
     */
    fun upcoming(at: Long, zoneId: String, use24h: Boolean, now: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
        val soon = relative(at, now, zoneId)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = at }
        val clock = time(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), use24h, locale)
        return when {
            soon == "today" || soon == "tomorrow" -> "$soon · $clock"
            soon == "now" || (soon.startsWith("in ") && !soon.endsWith("days")) -> soon
            else -> occurrence(at, zoneId, use24h, locale)
        }
    }

    fun shortDay(day: Int, locale: Locale = Locale.getDefault()): String = DateFormatSymbols(locale).shortWeekdays[day]
    fun longDay(day: Int, locale: Locale = Locale.getDefault()): String = DateFormatSymbols(locale).weekdays[day]
    /** One-letter labels for the day toggles; narrow forms collide (T/T, S/S) but read correctly in order. */
    fun narrowDay(day: Int, locale: Locale = Locale.getDefault()): String = shortDay(day, locale).take(1).uppercase(locale)

    fun ordinal(n: Int, locale: Locale = Locale.getDefault()): String {
        if (locale.language != "en") return n.toString()
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    private fun dateLabel(date: String, locale: Locale, withWeekday: Boolean = true, now: Long = System.currentTimeMillis()): String? {
        val parsed = runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date.take(10))
        }.getOrNull() ?: return null
        val sameYear = Calendar.getInstance().apply { time = parsed }.get(Calendar.YEAR) ==
            Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val pattern = buildString {
            if (withWeekday) append("EEE, ")
            append("MMM d")
            if (!sameYear) append(", yyyy")
        }
        return SimpleDateFormat(pattern, locale).format(parsed)
    }

    private fun localDay(at: Long, zone: TimeZone): Long = Math.floorDiv(at + zone.getOffset(at), 86_400_000L)
}
