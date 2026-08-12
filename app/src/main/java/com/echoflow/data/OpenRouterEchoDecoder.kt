package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Decodes nested Echo Adviser, Fusion, and Agent server responses. */
internal object OpenRouterEchoDecoder {
    private val dynamicAdapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    fun parseMaybeJson(s: String): Any? {
        val t = s.trim()
        if (!(t.startsWith("{") || t.startsWith("["))) return null
        return try { dynamicAdapter.fromJson(t) } catch (e: Exception) { null }
    }

    /** Walks an SSE chunk for an advisor result `{model?, advice}`. Returns (advisorModel, advice). */
    fun scanForAdvisorResult(node: Any?, depth: Int = 0): Pair<String?, String>? {
        if (depth > 8) return null
        when (node) {
            is Map<*, *> -> {
                (node["advice"] as? String)?.takeIf { it.isNotBlank() }?.let { advice ->
                    return (node["model"] as? String) to advice
                }
                (node["content"] as? String)?.let { c ->
                    parseMaybeJson(c)?.let { scanForAdvisorResult(it, depth + 1)?.let { r -> return r } }
                }
                for (v in node.values) scanForAdvisorResult(v, depth + 1)?.let { return it }
            }
            is List<*> -> for (v in node) scanForAdvisorResult(v, depth + 1)?.let { return it }
        }
        return null
    }

    /**
     * Walks an SSE chunk for subagent results `{status, model, task_name, outcome}` (or an
     * `{status:"error", task_name, error}`). Appends every distinct one found to [out]; the
     * caller dedupes by task_name. The shape is documented (unlike advisor/fusion), so this is
     * a straightforward keyed scan rather than a guess.
     */
    fun scanForSubagentResults(node: Any?, depth: Int = 0, out: MutableList<SubagentResult>) {
        if (depth > 8) return
        when (node) {
            is Map<*, *> -> {
                val taskName = (node["task_name"] as? String)?.takeIf { it.isNotBlank() }
                val hasOutcome = node.containsKey("outcome")
                val errorMsg = (node["error"] as? String)?.takeIf { it.isNotBlank() }
                if (taskName != null && (hasOutcome || errorMsg != null)) {
                    out.add(
                        SubagentResult(
                            taskName = taskName,
                            workerModel = (node["model"] as? String).orEmpty(),
                            outcome = (node["outcome"] as? String).orEmpty(),
                            error = errorMsg?.takeIf { (node["status"] as? String) == "error" || !hasOutcome },
                        )
                    )
                }
                (node["content"] as? String)?.let { c -> parseMaybeJson(c)?.let { scanForSubagentResults(it, depth + 1, out) } }
                for (v in node.values) scanForSubagentResults(v, depth + 1, out)
            }
            is List<*> -> for (v in node) scanForSubagentResults(v, depth + 1, out)
        }
    }

    fun isFusionResponseList(list: List<*>?): Boolean =
        list != null && list.isNotEmpty() && list.all {
            val m = it as? Map<*, *> ?: return@all false
            m.containsKey("content") && (m.containsKey("model") || m.containsKey("model_id"))
        }

    private fun looksLikeFusionPayload(node: Map<*, *>): Boolean {
        if (node["analysis"] is Map<*, *>) return true
        if (isFusionResponseList(node["responses"] as? List<*>)) return true
        if (node["failed_models"] is List<*> && (node["status"] as? String) == "error") return true
        // status:ok with only failed_models / empty responses still counts as a located tool result
        if ((node["status"] as? String) == "ok" &&
            (node.containsKey("responses") || node.containsKey("analysis") || node.containsKey("failed_models"))
        ) {
            return true
        }
        return false
    }

    /** Walks a response for a fusion result `{analysis?, responses?, failed_models?, status?}`. */
    fun scanForFusionResult(node: Any?, depth: Int = 0): FusionAnalysis? {
        if (depth > 10) return null
        when (node) {
            is Map<*, *> -> {
                if (looksLikeFusionPayload(node)) {
                    return buildFusionAnalysis(node)
                }
                // Tool message content is often a JSON string; also try "text" / "output" aliases.
                listOf("content", "text", "output", "result", "arguments").forEach { key ->
                    when (val v = node[key]) {
                        is String -> parseMaybeJson(v)?.let { scanForFusionResult(it, depth + 1)?.let { r -> return r } }
                        else -> scanForFusionResult(v, depth + 1)?.let { return it }
                    }
                }
                for ((k, v) in node) {
                    if (k == "content" || k == "text" || k == "output" || k == "result" || k == "arguments") continue
                    scanForFusionResult(v, depth + 1)?.let { return it }
                }
            }
            is List<*> -> for (v in node) scanForFusionResult(v, depth + 1)?.let { return it }
        }
        return null
    }

    fun buildFusionAnalysis(result: Map<*, *>): FusionAnalysis {
        val analysis = result["analysis"] as? Map<*, *>
        fun strList(key: String): List<String> =
            (analysis?.get(key) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val contradictions = (analysis?.get("contradictions") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val topic = m["topic"] as? String ?: return@mapNotNull null
            val stances = (m["stances"] as? List<*>)?.map { stance ->
                when (stance) {
                    is Map<*, *> -> {
                        val model = (stance["model"] as? String).orEmpty()
                        val text = (stance["stance"] as? String) ?: stance["text"] as? String ?: stance.toString()
                        if (model.isNotBlank()) "$model: $text" else text
                    }
                    else -> stance.toString()
                }
            } ?: emptyList()
            FusionContradiction(topic, stances)
        } ?: emptyList()

        val partial = (analysis?.get("partial_coverage") as? List<*>)?.mapNotNull { raw ->
            when (raw) {
                is Map<*, *> -> raw["point"] as? String
                is String -> raw
                else -> null
            }
        } ?: emptyList()

        val insights = (analysis?.get("unique_insights") as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val insight = m["insight"] as? String ?: return@mapNotNull null
            FusionInsight((m["model"] as? String).orEmpty(), insight)
        } ?: emptyList()

        val responses = (result["responses"] as? List<*>)?.mapNotNull { raw ->
            val m = raw as? Map<*, *> ?: return@mapNotNull null
            val content = m["content"] as? String ?: return@mapNotNull null
            val model = (m["model"] as? String) ?: (m["model_id"] as? String).orEmpty()
            FusionResponse(model, content)
        } ?: emptyList()

        val failed = (result["failed_models"] as? List<*>)?.mapNotNull { item ->
            when (item) {
                is String -> item
                is Map<*, *> -> (item["model"] as? String) ?: (item["id"] as? String)
                else -> null
            }
        } ?: emptyList()

        return FusionAnalysis(
            panelName = "",
            judgeModel = null,
            models = responses.map { it.model }.filter { it.isNotBlank() },
            consensus = strList("consensus"),
            contradictions = contradictions,
            partialCoverage = partial,
            uniqueInsights = insights,
            blindSpots = strList("blind_spots"),
            responses = responses,
            failedModels = failed,
            toolResultFound = true,
        )
    }

    /**
     * Executes one streaming chat completion, emitting chunks as they arrive and returning
     * the accumulated turn. Handles plain content/reasoning deltas, OpenRouter server-tool
     * activity (`openrouter:web_search`), client function tool_calls deltas, and
     * url_citation annotations. All parsing is defensive: unknown shapes degrade to plain
     * text streaming, never a crash.
     */
}
