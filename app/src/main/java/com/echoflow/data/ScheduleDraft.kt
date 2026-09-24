package com.echoflow.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The editable form of a schedule — what the chat card shows, what model tools patch, and what
 * Save resolves into a [ScheduleTask]. Wall-clock fields only; [resolve] turns them into instants
 * in the schedule's own time zone. Nothing here is trusted until [resolve] validates it.
 */
data class ScheduleDraft(
    val title: String = "",
    val instruction: String = "",
    val unit: String = ScheduleTask.DAY,
    val interval: Int = 1,
    val hour: Int = 9,
    val minute: Int = 0,
    /** [Calendar] day numbers; used by [ScheduleTask.WEEK]. */
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int = 1,
    /** "yyyy-MM-dd" for [ScheduleTask.ONCE]; the clock comes from [hour] and [minute]. */
    val onceDate: String = "",
    /** Inclusive last day ("yyyy-MM-dd"); null repeats until stopped. */
    val endDate: String? = null,
    val modelId: String,
) {
    /** Resolve once for both the preview and the save action. Keep an existing cadence. */
    fun resolve(
        original: ScheduleTask? = null,
        id: String = original?.id ?: UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): ScheduleTask {
        require(title.isNotBlank()) { "Give the schedule a name." }
        require(instruction.isNotBlank()) { "Describe what should happen." }
        require(unit != ScheduleTask.WEEK || weekdays.isNotEmpty()) { "Pick at least one day of the week." }
        val zoneId = original?.zoneId ?: TimeZone.getDefault().id
        val previous = original?.toDraft()
        val sameTiming = previous != null && unit == previous.unit && interval == previous.interval && when (unit) {
            ScheduleTask.HOUR -> true
            ScheduleTask.DAY -> hour == previous.hour && minute == previous.minute
            ScheduleTask.WEEK -> weekdays == previous.weekdays && hour == previous.hour && minute == previous.minute
            ScheduleTask.MONTH -> monthDay == previous.monthDay && hour == previous.hour && minute == previous.minute
            ScheduleTask.ONCE -> onceDate == previous.onceDate && hour == previous.hour && minute == previous.minute
            else -> false
        }
        val endAt = endDate?.let { ScheduleTime.endOfDay(it, zoneId) ?: throw IllegalArgumentException("That end date isn't valid.") }
        val timed = if (sameTiming) original!!.copy(
            title = title.trim(), instruction = instruction.trim(), modelId = modelId,
        ) else toTask(id, now, zoneId)
        val task = timed.copy(endAt = endAt, weekdays = if (unit == ScheduleTask.WEEK) ScheduleDays.mask(weekdays) else 0)
        // Editing never changes whether a schedule runs — except scheduling a finished one-time
        // task again, which is the only thing an edit of it can mean. Restart is explicit.
        val status = when {
            original == null -> ScheduleTask.ACTIVE
            original.status == ScheduleTask.COMPLETED && unit == ScheduleTask.ONCE -> ScheduleTask.ACTIVE
            else -> original.status
        }
        require(unit != ScheduleTask.ONCE || task.anchorAt > now) { "Pick a time in the future." }
        val next = if (status == ScheduleTask.ACTIVE) ScheduleTime.next(task, now) else null
        require(status != ScheduleTask.ACTIVE || next != null) { "The end date comes before the first run." }
        return task.copy(status = status, nextRunAt = next)
    }

    fun toTask(
        id: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
        zoneId: String = TimeZone.getDefault().id,
    ): ScheduleTask {
        val zone = TimeZone.getTimeZone(zoneId)
        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
        }
        val anchor = when (unit) {
            ScheduleTask.HOUR -> {
                // Hourly schedules start on the next wall-clock slot at [minute], not "now + N".
                calendar.timeInMillis = now
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                if (calendar.timeInMillis <= now) calendar.add(Calendar.HOUR_OF_DAY, 1)
                calendar.timeInMillis
            }
            ScheduleTask.DAY -> {
                if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            ScheduleTask.WEEK -> {
                val days = weekdays.ifEmpty { setOf(calendar.get(Calendar.DAY_OF_WEEK)) }
                while (calendar.get(Calendar.DAY_OF_WEEK) !in days || calendar.timeInMillis <= now) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                    calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                }
                calendar.timeInMillis
            }
            ScheduleTask.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                var found: Long? = null
                repeat(13) {
                    if (found == null && monthDay <= calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                        calendar.set(Calendar.DAY_OF_MONTH, monthDay.coerceIn(1, 31))
                        calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                        calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                        if (calendar.timeInMillis > now) found = calendar.timeInMillis
                    }
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.add(Calendar.MONTH, 1)
                }
                found ?: error("Could not find a future month for that date.")
            }
            ScheduleTask.ONCE -> {
                val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                    timeZone = zone
                    isLenient = false
                }
                val day = onceDate.trim().take(10)
                require(day.length == 10) { "Pick a date for a one-time schedule." }
                runCatching { parser.parse("$day ${"%02d:%02d".format(Locale.US, hour, minute)}")?.time }
                    .getOrNull() ?: throw IllegalArgumentException("That date isn't valid.")
            }
            else -> throw IllegalArgumentException("Unsupported repeat: $unit")
        }
        require(anchor > now) { "Pick a time in the future." }
        return ScheduleTask(
            id = id, title = title.trim(), instruction = instruction.trim(), modelId = modelId,
            status = ScheduleTask.ACTIVE, unit = unit, interval = interval.coerceIn(1, 999),
            anchorAt = anchor, zoneId = zone.id, nextRunAt = anchor,
            weekdays = if (unit == ScheduleTask.WEEK) ScheduleDays.mask(weekdays) else 0,
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("instruction", instruction)
        .put("repeat", JSONObject().put("every", interval).put("unit", unit))
        .put("time", "%02d:%02d".format(Locale.US, hour, minute))
        .put("days", JSONArray(ScheduleDays.ORDER.filter { it in weekdays }.map(ScheduleDays::key)))
        .put("day_of_month", monthDay)
        .put("date", onceDate)
        .put("ends", endDate ?: "never")
        .put("model", modelId)

    companion object {
        fun fromJson(json: JSONObject, fallbackModel: String): ScheduleDraft {
            val repeat = json.optJSONObject("repeat")
            val time = json.optString("time").split(':')
            val days = json.optJSONArray("days")
            return ScheduleDraft(
                title = json.optString("title"),
                instruction = json.optString("instruction"),
                unit = repeat?.optString("unit")?.takeIf { it in ScheduleTask.UNITS } ?: ScheduleTask.DAY,
                interval = repeat?.optInt("every", 1)?.coerceIn(1, 999) ?: 1,
                hour = time.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9,
                minute = time.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
                weekdays = (0 until (days?.length() ?: 0)).mapNotNull { ScheduleDays.parse(days!!.optString(it)) }.toSet(),
                monthDay = json.optInt("day_of_month", 1).coerceIn(1, 31),
                onceDate = json.optString("date"),
                endDate = json.optString("ends").takeIf { it.length == 10 },
                modelId = json.optString("model").ifBlank { fallbackModel },
            )
        }
    }
}

fun ScheduleTask.toDraft(): ScheduleDraft {
    val zone = TimeZone.getTimeZone(zoneId)
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = anchorAt }
    val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
    return ScheduleDraft(
        title = title, instruction = instruction, unit = unit, interval = interval,
        hour = calendar.get(Calendar.HOUR_OF_DAY), minute = calendar.get(Calendar.MINUTE),
        weekdays = if (unit != ScheduleTask.WEEK) emptySet()
            else ScheduleDays.days(weekdays).ifEmpty { setOf(calendar.get(Calendar.DAY_OF_WEEK)) },
        monthDay = calendar.get(Calendar.DAY_OF_MONTH),
        onceDate = day.format(Date(anchorAt)),
        endDate = endAt?.let { day.format(Date(it)) },
        modelId = modelId,
    )
}
