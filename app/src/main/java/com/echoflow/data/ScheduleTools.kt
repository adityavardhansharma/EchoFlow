package com.echoflow.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * The state a schedule conversation's tools act on. The chat screen implements it over its live
 * draft, so a model's edit and a tap on the card are the same operation on the same state — the
 * person sees every tool call land in the card as it happens and can keep editing by hand.
 */
interface ScheduleWorkspace {
    /** The unsaved working copy; null until something has been drafted. */
    val draft: ScheduleDraft?
    /** What is actually scheduled; null for a schedule that has never been saved. */
    val saved: ScheduleTask?
    val modelId: String
    fun updateDraft(draft: ScheduleDraft)
    /** Validates and persists the draft. Throws [IllegalArgumentException] with a readable reason. */
    suspend fun save(): ScheduleTask
    fun discard()
    suspend fun setStatus(status: String): ScheduleTask
    suspend fun runNow()
}

/** One executed call: what goes back to the model, and the chip the person sees. */
data class ScheduleToolOutcome(val result: String, val edits: List<String>, val saved: Boolean = false)

/**
 * The schedule tools a model may call, their manual, and their execution.
 *
 * Deliberately small and state-shaped: one tool edits any subset of fields, one commits, one
 * changes run state. A model never has to know the order of operations, and there is no "create"
 * vs "update" split to get wrong — saving a draft of a new schedule creates it. Deleting is not a
 * tool; it stays a deliberate act in the UI.
 */
class ScheduleTools(
    private val workspace: ScheduleWorkspace,
    private val use24h: Boolean = false,
    private val zoneId: () -> String = { workspace.saved?.zoneId ?: TimeZone.getDefault().id },
    private val now: () -> Long = System::currentTimeMillis,
    private val locale: Locale = Locale.getDefault(),
) {
    fun handles(name: String) = name in NAMES

    suspend fun execute(call: ScheduleToolProtocol.Call): ScheduleToolOutcome = try {
        when (call.name) {
            UPDATE -> update(call.arguments)
            SAVE -> save()
            DISCARD -> discard()
            SET_STATUS -> setStatus(call.arguments)
            RUN_NOW -> runNow()
            else -> failure(call.name, "Unknown tool. Available: ${NAMES.joinToString()}.")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        failure(call.name, e.message ?: "The tool failed.")
    }

    private fun update(args: JSONObject): ScheduleToolOutcome {
        if (args.has("__invalid")) return failure(UPDATE, "Arguments must be one JSON object.")
        val existed = workspace.draft != null || workspace.saved != null
        val before = workspace.draft ?: workspace.saved?.toDraft() ?: ScheduleDraft(modelId = workspace.modelId)
        val patch = ScheduleDraftPatch.apply(before, args, zoneId(), now())
        if (patch.errors.isNotEmpty() && patch.draft == before) {
            return failure(UPDATE, patch.errors.joinToString(" "))
        }
        workspace.updateDraft(patch.draft)
        val edits = editLabels(before.takeIf { existed }, patch.draft)
        return ScheduleToolOutcome(describe(UPDATE, patch.draft).apply {
            if (patch.errors.isNotEmpty()) put("rejected", JSONArray(patch.errors))
            if (patch.ignored.isNotEmpty()) put("ignored_fields", JSONArray(patch.ignored))
        }.toString(), edits)
    }

    private suspend fun save(): ScheduleToolOutcome {
        val created = workspace.saved == null
        val task = workspace.save()
        val draft = task.toDraft()
        return ScheduleToolOutcome(JSONObject()
            .put("ok", true)
            .put("status", if (created) "created" else "saved")
            .put("summary", summary(draft))
            .put("state", task.status)
            .put("next_runs", JSONArray(ScheduleTime.preview(task, 3, now()).map { ScheduleText.occurrence(it, task.zoneId, use24h, locale) }))
            .toString(), listOf(if (created) "Created schedule" else "Saved changes"), saved = true)
    }

    private fun discard(): ScheduleToolOutcome {
        workspace.discard()
        return ScheduleToolOutcome(JSONObject().put("ok", true)
            .put("summary", workspace.saved?.let { summary(it.toDraft()) } ?: "No schedule yet").toString(),
            listOf("Discarded changes"))
    }

    private suspend fun setStatus(args: JSONObject): ScheduleToolOutcome {
        if (workspace.saved == null) return failure(SET_STATUS, "Nothing is scheduled yet. Save the schedule first.")
        val status = when (args.optString("status").lowercase()) {
            "active", "resume", "resumed", "restart" -> ScheduleTask.ACTIVE
            "paused", "pause" -> ScheduleTask.PAUSED
            "ended", "end", "stop", "stopped", "completed" -> ScheduleTask.COMPLETED
            else -> return failure(SET_STATUS, "status must be one of: active, paused, ended.")
        }
        val task = workspace.setStatus(status)
        val label = when (status) { ScheduleTask.ACTIVE -> "Resumed"; ScheduleTask.PAUSED -> "Paused"; else -> "Ended" }
        return ScheduleToolOutcome(JSONObject().put("ok", true).put("state", label.lowercase())
            .put("next_run", task.nextRunAt?.let { ScheduleText.occurrence(it, task.zoneId, use24h, locale) } ?: JSONObject.NULL)
            .toString(), listOf(label))
    }

    private suspend fun runNow(): ScheduleToolOutcome {
        if (workspace.saved == null) return failure(RUN_NOW, "Save the schedule before running it.")
        workspace.runNow()
        return ScheduleToolOutcome(JSONObject().put("ok", true)
            .put("note", "The run started in the background. Its answer will appear in this conversation when it finishes; do not write the answer yourself.")
            .toString(), listOf("Started a run"))
    }

    private fun describe(tool: String, draft: ScheduleDraft): JSONObject {
        val result = JSONObject().put("ok", true).put("draft", draft.toJson()).put("summary", summary(draft))
        val resolved = runCatching { draft.resolve(workspace.saved, now = now()) }
        resolved.onSuccess { task ->
            result.put("next_runs", JSONArray(ScheduleTime.preview(task, 3, now()).map { ScheduleText.occurrence(it, task.zoneId, use24h, locale) }))
        }.onFailure { result.put("problem", it.message ?: "Incomplete") }
        result.put("unsaved_changes", !draft.sameAs(workspace.saved?.toDraft()))
        if (tool == UPDATE) result.put("next_step", if (resolved.isSuccess)
            "Draft updated. Save when the user has confirmed (or clearly asked for it)." else "Fix the problem before saving.")
        return result
    }

    private fun summary(draft: ScheduleDraft): String = buildString {
        append(ScheduleText.cadence(draft, use24h, locale))
        ScheduleText.until(draft.endDate, locale, now())?.let { append(" · ").append(it) }
    }

    /** Human chips for what changed, e.g. "Weekdays at 8:00 AM", "Until Oct 22". */
    private fun editLabels(before: ScheduleDraft?, after: ScheduleDraft): List<String> {
        if (before == null) return listOf("Drafted · ${ScheduleText.cadence(after, use24h, locale)}")
        return buildList {
            if (before.title != after.title) add("Named “${after.title}”")
            if (before.instruction != after.instruction) add("Updated the task")
            val timing = { d: ScheduleDraft -> ScheduleText.cadence(d, use24h, locale) }
            if (timing(before) != timing(after)) add(timing(after))
            if (before.endDate != after.endDate) add(ScheduleText.until(after.endDate, locale, now())?.replaceFirstChar { it.uppercase() } ?: "No end date")
        }
    }

    private fun failure(tool: String, message: String) =
        ScheduleToolOutcome(JSONObject().put("ok", false).put("tool", tool).put("error", message).toString(), emptyList())

    companion object {
        const val UPDATE = "update_schedule"
        const val SAVE = "save_schedule"
        const val DISCARD = "discard_changes"
        const val SET_STATUS = "set_schedule_status"
        const val RUN_NOW = "run_schedule_now"
        val NAMES = setOf(UPDATE, SAVE, DISCARD, SET_STATUS, RUN_NOW)

        /** The manual a conversation model reads. Kept next to the executor so they cannot drift. */
        val MANUAL = """
            TOOLS
            Call a tool by writing exactly: <tool name="TOOL_NAME">{json arguments}</tool>
            You may call several tools in one reply. The person never sees tool blocks; they see the
            schedule card update live. After your calls, EchoFlow replies with <tool_result> blocks and
            you continue. Never invent a result — wait for it.

            update_schedule — change any subset of the draft. Only include fields you are changing.
              title: short name, max 6 words ("Morning brief")
              instruction: what to do at each run, written as a complete, self-contained brief to a
                future assistant who cannot see this conversation (topic, scope, format, length, tone)
              repeat: {"every": 1, "unit": "once" | "hour" | "day" | "week" | "month"}
              time: "HH:mm", 24-hour local time ("07:30", "18:00"); for hourly, the minute is used
              days: ["mon","tue","wed","thu","fri","sat","sun"] — weekly only; setting days implies weekly
              day_of_month: 1-31 — monthly only; months without that day are skipped
              date: "yyyy-MM-dd" — one-time only; setting date implies once
              ends: "never" | "yyyy-MM-dd" (last day, inclusive) | {"after": {"amount": 4, "unit": "day"|"week"|"month"|"year"}}
            save_schedule — {} — commit the draft. This creates the schedule or saves changes to it.
            discard_changes — {} — throw away unsaved changes.
            set_schedule_status — {"status": "active" | "paused" | "ended"} — pause, resume or end a saved schedule.
            run_schedule_now — {} — run the saved schedule once right away (the answer arrives later in this chat).
        """.trimIndent()
    }
}

/**
 * Applies a model's (or any JSON) partial edit to a draft. Forgiving about spelling — "daily",
 * "8:30 pm", "Monday" all land — but strict about meaning: a field that cannot be understood is
 * left unchanged and reported back rather than guessed, while the understood fields still apply.
 */
object ScheduleDraftPatch {
    data class Result(val draft: ScheduleDraft, val errors: List<String>, val ignored: List<String>)

    private val known = setOf("title", "instruction", "repeat", "time", "days", "day_of_month", "date", "ends")

    fun apply(start: ScheduleDraft, args: JSONObject, zoneId: String, now: Long): Result {
        var draft = start
        val errors = mutableListOf<String>()
        val ignored = args.keys().asSequence().filter { it !in known }.toList()

        if (args.has("title")) {
            val title = args.optString("title").trim()
            if (title.isBlank() || title.length > 80) errors += "title must be 1-80 characters."
            else draft = draft.copy(title = title)
        }
        if (args.has("instruction")) {
            val instruction = args.optString("instruction").trim()
            if (instruction.isBlank() || instruction.length > 4000) errors += "instruction must be 1-4000 characters."
            else draft = draft.copy(instruction = instruction)
        }
        var unitSet = false
        if (args.has("repeat")) {
            val parsed = parseRepeat(args.opt("repeat"))
            if (parsed == null) errors += "repeat must look like {\"every\": 1, \"unit\": \"day\"}."
            else {
                val (every, unit, days) = parsed
                draft = draft.copy(unit = unit, interval = every, weekdays = days ?: draft.weekdays)
                unitSet = true
            }
        }
        if (args.has("time")) {
            val clock = parseClock(args.optString("time"))
            if (clock == null) errors += "time must be \"HH:mm\" in 24-hour form."
            else draft = draft.copy(hour = clock.first, minute = clock.second)
        }
        if (args.has("days")) {
            val raw = args.optJSONArray("days")
            val days = raw?.let { array -> (0 until array.length()).map { ScheduleDays.parse(array.optString(it)) } }
            if (days == null || days.isEmpty() || days.any { it == null }) errors += "days must be a list like [\"mon\", \"fri\"]."
            else {
                draft = draft.copy(weekdays = days.filterNotNull().toSet())
                if (!unitSet || draft.unit != ScheduleTask.WEEK) draft = draft.copy(unit = ScheduleTask.WEEK)
            }
        }
        if (args.has("day_of_month")) {
            val day = args.optInt("day_of_month", -1)
            if (day !in 1..31) errors += "day_of_month must be 1-31."
            else draft = draft.copy(monthDay = day, unit = if (unitSet) draft.unit else ScheduleTask.MONTH)
        }
        if (args.has("date")) {
            val date = args.optString("date").trim()
            if (!isDate(date)) errors += "date must be \"yyyy-MM-dd\"."
            else draft = draft.copy(onceDate = date, unit = if (unitSet) draft.unit else ScheduleTask.ONCE)
        }
        if (args.has("ends")) {
            when (val end = parseEnd(args.opt("ends"), zoneId, now)) {
                null -> errors += "ends must be \"never\", \"yyyy-MM-dd\" or {\"after\": {\"amount\": 4, \"unit\": \"week\"}}."
                "" -> draft = draft.copy(endDate = null)
                else -> draft = draft.copy(endDate = end)
            }
        }
        // A weekly schedule with no days would be unsavable; default to today's weekday.
        if (draft.unit == ScheduleTask.WEEK && draft.weekdays.isEmpty()) {
            draft = draft.copy(weekdays = setOf(Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)))
        }
        if (draft.unit == ScheduleTask.ONCE && draft.onceDate.isBlank()) {
            draft = draft.copy(onceDate = isoDay(now, zoneId, 0))
        }
        return Result(draft, errors, ignored)
    }

    private fun parseRepeat(value: Any?): Triple<Int, String, Set<Int>?>? = when (value) {
        is JSONObject -> {
            val unit = normalizeUnit(value.optString("unit"))
            val every = value.optInt("every", 1)
            if (unit == null || every !in 1..999) null else Triple(every, unit, null)
        }
        is String -> when (value.trim().lowercase()) {
            "weekdays" -> Triple(1, ScheduleTask.WEEK, ScheduleDays.WEEKDAYS)
            "weekends" -> Triple(1, ScheduleTask.WEEK, ScheduleDays.WEEKEND)
            else -> normalizeUnit(value)?.let { Triple(1, it, null) }
        }
        else -> null
    }

    private fun normalizeUnit(value: String): String? = when (value.trim().lowercase().removeSuffix("s")) {
        "once", "one-time", "one time", "onetime" -> ScheduleTask.ONCE
        "hour", "hourly" -> ScheduleTask.HOUR
        "day", "daily" -> ScheduleTask.DAY
        "week", "weekly" -> ScheduleTask.WEEK
        "month", "monthly" -> ScheduleTask.MONTH
        else -> null
    }

    /** "08:00", "8:30", "8:30 pm", "8pm", "20h15". */
    fun parseClock(value: String): Pair<Int, Int>? {
        val match = Regex("""^\s*(\d{1,2})(?:[:.h](\d{2}))?\s*([ap]\.?m\.?)?\s*$""", RegexOption.IGNORE_CASE)
            .find(value) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val period = match.groupValues[3].lowercase().replace(".", "")
        if (period.isNotEmpty()) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (period == "pm") 12 else 0
        }
        return (hour to minute).takeIf { hour in 0..23 && minute in 0..59 }
    }

    /** "" for never, a "yyyy-MM-dd" last day, or null when the value is not understood. */
    private fun parseEnd(value: Any?, zoneId: String, now: Long): String? = when (value) {
        null, JSONObject.NULL -> ""
        is String -> when {
            value.trim().lowercase() in setOf("never", "none", "") -> ""
            isDate(value.trim()) -> value.trim()
            else -> null
        }
        is JSONObject -> {
            val after = value.optJSONObject("after") ?: value
            if (value.has("on")) value.optString("on").takeIf(::isDate)
            else {
                val amount = after.optInt("amount", -1)
                val field = when (normalizeUnit(after.optString("unit")) ?: after.optString("unit").lowercase().removeSuffix("s")) {
                    ScheduleTask.DAY -> Calendar.DAY_OF_YEAR
                    ScheduleTask.WEEK -> Calendar.WEEK_OF_YEAR
                    ScheduleTask.MONTH -> Calendar.MONTH
                    "year", "yearly" -> Calendar.YEAR
                    else -> null
                }
                if (field == null || amount !in 1..1000) null else isoDay(now, zoneId, 0, field, amount)
            }
        }
        else -> null
    }

    private fun isDate(value: String): Boolean = Regex("""\d{4}-\d{2}-\d{2}""").matches(value) &&
        ScheduleTime.endOfDay(value, "UTC") != null

    /** "In 4 weeks" ends the day before the same weekday four weeks out: 28 days, inclusive of today. */
    private fun isoDay(now: Long, zoneId: String, plusDays: Int, field: Int = Calendar.DAY_OF_YEAR, amount: Int = plusDays): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(zoneId)).apply { timeInMillis = now }
        if (amount != 0) {
            calendar.add(field, amount)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        return "%04d-%02d-%02d".format(Locale.US, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }
}
