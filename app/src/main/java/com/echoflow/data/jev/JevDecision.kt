package com.echoflow.data.jev

import org.json.JSONObject

/**
 * Fixed decision thresholds for the Jev Router (Echo Labs opt-in).
 * Initial operating thresholds; not a claim of calibration on EchoFlow traffic.
 */
object JevThresholds {
    /** At or above: act automatically (pre-recall memory, force web, direct save). */
    const val ACT = 0.7
    /** Retained for historical diagnostics; contextual routing uses the Choice distribution. */
    const val REVIEW = 0.4
    const val MODEL = "jev-latest"
    const val PROMPT_VERSION = "contextual-memory-v2"
    /** Upper bound per classification so a TypeSafe outage never stalls a reply. */
    const val TIMEOUT_MS = 5_000L
}

/**
 * One Jev classification for a chat turn. Persisted as JSON on the assistant
 * message row ([jevJson]) and surfaced only inside Echo Labs > Jev.
 *
 * Action semantics are deliberately unequal and recorded honestly:
 * - [memoryRecalled]: the app attempted `search_memory` itself before answering;
 *   this does not imply that relevant memories were found or the request succeeded.
 * - [webForced]: a strong search-first instruction was appended for the main
 *   model (model-directed — the model still performs the search, so this is a
 *   request, not a completed search).
 * - [saveTriggered]: a `remember_memory` instruction was appended, only when
 *   memory tools are installed for this turn (model-directed, same caveat).
 */
data class JevDecision(
    val modelVersion: String,
    val latencyMs: Long,
    val needsMemory: Double,
    val needsSave: Double,
    val needsWeb: Double,
    val routeChoice: String,
    val routeProbabilities: Map<String, Double>,
    val routeConfidence: Double,
    val memoryRecalled: Boolean,
    val webForced: Boolean,
    val saveTriggered: Boolean,
    val fallback: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val memoryAction: String = "defer",
    val promptVersion: String = "legacy-v1",
) {
    fun toJson(): String = JSONObject()
        .put("modelVersion", modelVersion)
        .put("latencyMs", latencyMs)
        .put("needsMemory", needsMemory)
        .put("needsSave", needsSave)
        .put("needsWeb", needsWeb)
        .put("routeChoice", routeChoice)
        .put("routeProbabilities", JSONObject(routeProbabilities))
        .put("routeConfidence", routeConfidence)
        .put("memoryRecalled", memoryRecalled)
        .put("webForced", webForced)
        .put("saveTriggered", saveTriggered)
        .put("fallback", fallback)
        .put("createdAt", createdAt)
        .put("memoryAction", memoryAction)
        .put("promptVersion", promptVersion)
        .toString()

    companion object {
        fun fallback(reason: String = ""): JevDecision = JevDecision(
            modelVersion = "",
            latencyMs = 0,
            needsMemory = 0.0,
            needsSave = 0.0,
            needsWeb = 0.0,
            routeChoice = "fallback",
            routeProbabilities = emptyMap(),
            routeConfidence = 0.0,
            memoryRecalled = false,
            webForced = false,
            saveTriggered = false,
            fallback = true,
        )

        fun fromJson(raw: String?): JevDecision? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                val probs = mutableMapOf<String, Double>()
                o.optJSONObject("routeProbabilities")?.let { p ->
                    p.keys().forEach { k -> probs[k] = p.optDouble(k, 0.0) }
                }
                JevDecision(
                    modelVersion = o.optString("modelVersion", ""),
                    latencyMs = o.optLong("latencyMs", 0),
                    needsMemory = o.optDouble("needsMemory", 0.0),
                    needsSave = o.optDouble("needsSave", 0.0),
                    needsWeb = o.optDouble("needsWeb", 0.0),
                    routeChoice = o.optString("routeChoice", ""),
                    routeProbabilities = probs,
                    routeConfidence = o.optDouble("routeConfidence", 0.0),
                    memoryRecalled = o.optBoolean("memoryRecalled", false),
                    webForced = o.optBoolean("webForced", false),
                    saveTriggered = o.optBoolean("saveTriggered", false),
                    fallback = o.optBoolean("fallback", false),
                    createdAt = o.optLong("createdAt", 0),
                    memoryAction = o.optString("memoryAction", "defer"),
                    promptVersion = o.optString("promptVersion", "legacy-v1"),
                )
            }.getOrNull()
        }
    }
}
