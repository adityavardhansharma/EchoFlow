package com.echoflow.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a row in a schedule conversation records beyond plain chat — persisted in
 * [ChatMessage.scheduleEvent]. The row's `content` always carries a readable sentence too, so the
 * transcript still makes sense to a model (and to a person reading an export) without this.
 */
data class ScheduleEvent(
    val type: String,
    /** Short labels: the edits a reply made ("Weekdays at 8:00 AM"), or a lifecycle detail. */
    val items: List<String> = emptyList(),
    val runId: String? = null,
    val scheduledAt: Long? = null,
) {
    fun toJson(): String = JSONObject().put("type", type).apply {
        if (items.isNotEmpty()) put("items", JSONArray(items))
        runId?.let { put("runId", it) }
        scheduledAt?.let { put("scheduledAt", it) }
    }.toString()

    /** Rows that are the schedule speaking for itself rather than a conversational reply. */
    val isLifecycle: Boolean get() = type != EDITS && type != RUN

    companion object {
        /** A run's answer. */
        const val RUN = "run"
        /** A model reply that changed or saved the schedule; [items] lists what changed. */
        const val EDITS = "edits"
        const val CREATED = "created"
        const val SAVED = "saved"
        const val PAUSED = "paused"
        const val RESUMED = "resumed"
        const val ENDED = "ended"
        const val RUN_FAILED = "run_failed"
        const val MISSED = "missed"
        const val RUN_STARTED = "run_started"

        fun parse(json: String?): ScheduleEvent? {
            if (json.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(json)
                val items = o.optJSONArray("items")
                ScheduleEvent(
                    type = o.getString("type"),
                    items = (0 until (items?.length() ?: 0)).map { items!!.getString(it) },
                    runId = o.optString("runId").ifBlank { null },
                    scheduledAt = if (o.has("scheduledAt")) o.getLong("scheduledAt") else null,
                )
            }.getOrNull()
        }
    }
}
