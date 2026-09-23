package com.echoflow.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Model proposals are untrusted drafts. Only the validated editor saves a task. */
data class ScheduleDraft(
    val title: String = "New schedule",
    val instruction: String = "",
    val unit: String = ScheduleTask.DAY,
    val interval: Int = 1,
    val hour: Int = 9,
    val minute: Int = 0,
    val weekday: Int = Calendar.MONDAY,
    val monthDay: Int = 1,
    val onceDate: String = "",
    val modelId: String,
    val needsWeb: Boolean = false,
    val assumption: String = "",
) {
    /** Resolve once for both the preview and the save action. Keep an existing cadence. */
    fun resolve(
        original: ScheduleTask? = null,
        id: String = original?.id ?: UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis(),
    ): ScheduleTask {
        val previous = original?.toDraft()
        val sameTiming = previous != null && unit == previous.unit && interval == previous.interval && when (unit) {
            ScheduleTask.HOUR -> true
            ScheduleTask.DAY -> hour == previous.hour && minute == previous.minute
            ScheduleTask.WEEK -> weekday == previous.weekday && hour == previous.hour && minute == previous.minute
            ScheduleTask.MONTH -> monthDay == previous.monthDay && hour == previous.hour && minute == previous.minute
            ScheduleTask.ONCE -> onceDate == previous.onceDate
            else -> false
        }
        val task = if (sameTiming) original!!.copy(
            title = title.trim(), instruction = instruction.trim(), modelId = modelId, needsWeb = needsWeb,
        ) else toTask(id, now, original?.zoneId ?: TimeZone.getDefault().id)
        val status = if (original?.status == ScheduleTask.COMPLETED && unit == ScheduleTask.ONCE) {
            ScheduleTask.ACTIVE
        } else original?.status ?: ScheduleTask.ACTIVE
        require(unit != ScheduleTask.ONCE || task.anchorAt > now) { "Pick a future time." }
        return task.copy(status = status, nextRunAt = if (status == ScheduleTask.ACTIVE) ScheduleTime.next(task, now) else null)
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
        }
        val anchor = when (unit) {
            ScheduleTask.HOUR -> now + interval * 3_600_000L
            ScheduleTask.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
                calendar.timeInMillis
            }
            ScheduleTask.WEEK -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                while (calendar.get(Calendar.DAY_OF_WEEK) != weekday.coerceIn(1, 7) || calendar.timeInMillis <= now) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                calendar.timeInMillis
            }
            ScheduleTask.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                calendar.set(Calendar.MINUTE, minute.coerceIn(0, 59))
                var found: Long? = null
                repeat(13) {
                    if (monthDay <= calendar.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                        calendar.set(Calendar.DAY_OF_MONTH, monthDay.coerceIn(1, 31))
                        if (calendar.timeInMillis > now && found == null) found = calendar.timeInMillis
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
                parser.parse(onceDate)?.time ?: error("Pick a date and time for a one-time schedule.")
            }
            else -> error("Unsupported recurrence")
        }
        require(anchor > now) { "Pick a future time." }
        return ScheduleTask(
            id = id, title = title.trim(), instruction = instruction.trim(), modelId = modelId,
            status = ScheduleTask.ACTIVE, unit = unit, interval = interval.coerceIn(1, 999),
            anchorAt = anchor, zoneId = zone.id, nextRunAt = anchor, needsWeb = needsWeb,
        )
    }
}

fun ScheduleTask.toDraft(): ScheduleDraft {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = anchorAt }
    return ScheduleDraft(
        title = title, instruction = instruction, unit = unit, interval = interval,
        hour = calendar.get(Calendar.HOUR_OF_DAY), minute = calendar.get(Calendar.MINUTE),
        weekday = calendar.get(Calendar.DAY_OF_WEEK), monthDay = calendar.get(Calendar.DAY_OF_MONTH),
        onceDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone(zoneId)
        }.format(java.util.Date(anchorAt)), modelId = modelId, needsWeb = needsWeb,
    )
}

sealed interface ScheduleProposal {
    data class Question(val text: String) : ScheduleProposal
    data class Ready(val draft: ScheduleDraft) : ScheduleProposal
}

class ScheduleDraftAssistant(private val runner: ScheduleModelRunner) {
    suspend fun respond(modelId: String, transcript: String, questionsAsked: Int): ScheduleProposal {
        val system = """
            You help a user create a scheduled EchoFlow task. Reply with exactly one JSON object.
            Allowed actions: {"action":"ask","question":"..."} or
            {"action":"propose","title":"...","instruction":"...","unit":"once|hour|day|week|month",
            "interval":1,"hour":9,"minute":0,"weekday":2,"month_day":1,
            "once_date":"yyyy-MM-dd HH:mm","needs_web":false,"assumption":"..."}.
            Ask only if the answer materially changes the task. Zero questions is ideal when the
            user's intent is clear. Combine related details in one concise question. The user
            has already answered $questionsAsked clarification questions. At most two are allowed.
            ${if (questionsAsked >= 2) "You MUST propose now. Do not ask anything." else "Ask only when essential."}
            A reminder still uses the selected model at execution, but needs_web must be false.
            Set needs_web only when current external information is essential.
            Do not invent websites, account access, or actions the user has not requested.
            Use reasonable editable defaults for missing schedule details and state them in assumption.
            weekday uses Calendar numbering: Sunday=1, Monday=2, ..., Saturday=7.
            Current local date and time: ${java.util.Date()}.
        """.trimIndent()
        val raw = runner.complete(modelId, transcript, system)
        val jsonText = raw.substringAfter('{', "").let { if (it.isEmpty()) "" else "{" + it.substringBeforeLast('}', "") + "}" }
        val json = runCatching { JSONObject(jsonText) }.getOrNull()
        if (json?.optString("action") == "ask" && questionsAsked < 2) {
            val question = json.optString("question").trim()
            if (question.isNotBlank()) return ScheduleProposal.Question(question)
        }
        return ScheduleProposal.Ready(ScheduleDraft(
            title = json?.optString("title")?.takeIf { it.isNotBlank() } ?: "New schedule",
            instruction = json?.optString("instruction").orEmpty(),
            unit = json?.optString("unit")?.takeIf { it in setOf("once", "hour", "day", "week", "month") }
                ?: ScheduleTask.DAY,
            interval = json?.optInt("interval", 1)?.coerceIn(1, 999) ?: 1,
            hour = json?.optInt("hour", 9)?.coerceIn(0, 23) ?: 9,
            minute = json?.optInt("minute", 0)?.coerceIn(0, 59) ?: 0,
            weekday = json?.optInt("weekday", 2)?.coerceIn(1, 7) ?: 2,
            monthDay = json?.optInt("month_day", 1)?.coerceIn(1, 31) ?: 1,
            onceDate = json?.optString("once_date").orEmpty(),
            modelId = modelId,
            needsWeb = json?.optBoolean("needs_web", false) ?: false,
            assumption = json?.optString("assumption").orEmpty(),
        ))
    }
}
