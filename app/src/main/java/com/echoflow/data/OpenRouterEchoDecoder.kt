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
        val status = node["status"] as? String
        val failureReason = node["failure_reason"] as? String
        // A second fusion call in the same turn is rejected — never treat that as the panel result.
        if (status == "error" && failureReason == "fusion_invocation_capped") return false

        if (node["analysis"] is Map<*, *>) return true
        if (isFusionResponseList(node["responses"] as? List<*>)) return true
        if (node["failed_models"] is List<*> && status == "error") return true
        // Explicit hard failures from the fusion tool (all panels failed, credits, etc.).
        if (status == "error" && failureReason != null && failureReason != "fusion_invocation_capped") {
            return true
        }
        // status:ok with only failed_models / empty responses still counts as a located tool result
        if (status == "ok" &&
            (node.containsKey("responses") || node.containsKey("analysis") || node.containsKey("failed_models"))
        ) {
            return true
        }
        return false
    }

    /** Walks a response for the first fusion result (prefer [selectPreferredFusionResult] for multi-hit). */
    fun scanForFusionResult(node: Any?, depth: Int = 0): FusionAnalysis? =
        selectPreferredFusionResult(scanForAllFusionResults(node, depth))

    /**
     * Collects every fusion tool payload in the response tree. A single turn can contain a
     * successful first result and a later `fusion_invocation_capped` error if the model
     * re-invokes the tool — callers should prefer the successful payload.
     */
    fun scanForAllFusionResults(node: Any?, depth: Int = 0, out: MutableList<FusionAnalysis> = mutableListOf()): List<FusionAnalysis> {
        if (depth > 10) return out
        when (node) {
            is Map<*, *> -> {
                if (looksLikeFusionPayload(node)) {
                    out.add(buildFusionAnalysis(node))
                }
                listOf("content", "text", "output", "result", "arguments").forEach { key ->
                    when (val v = node[key]) {
                        is String -> parseMaybeJson(v)?.let { scanForAllFusionResults(it, depth + 1, out) }
                        else -> scanForAllFusionResults(v, depth + 1, out)
                    }
                }
                for ((k, v) in node) {
                    if (k == "content" || k == "text" || k == "output" || k == "result" || k == "arguments") continue
                    scanForAllFusionResults(v, depth + 1, out)
                }
            }
            is List<*> -> for (v in node) scanForAllFusionResults(v, depth + 1, out)
        }
        return out
    }

    /** Prefer a usable panel/analysis payload over empty or hard-failure shells. */
    fun selectPreferredFusionResult(results: List<FusionAnalysis>): FusionAnalysis? {
        if (results.isEmpty()) return null
        results.firstOrNull { it.hasUsableDetail }?.let { return it }
        results.firstOrNull { it.toolResultFound && !it.isHardFailure }?.let { return it }
        return results.firstOrNull()
    }

    /**
     * True when the response tree shows the outer model requested fusion (tool call name /
     * type contains "fusion"), even if we cannot decode the tool result body. Used to avoid
     * labeling an unparsed success as "panel did not run".
     */
    fun responseMentionsFusionInvocation(node: Any?, depth: Int = 0): Boolean {
        if (depth > 10) return false
        when (node) {
            is Map<*, *> -> {
                val type = (node["type"] as? String).orEmpty()
                val name = (node["name"] as? String)
                    ?: ((node["function"] as? Map<*, *>)?.get("name") as? String).orEmpty()
                if (type.contains("fusion", ignoreCase = true) || name.contains("fusion", ignoreCase = true)) {
                    return true
                }
                // tool_calls arrays often sit under message
                (node["tool_calls"] as? List<*>)?.let { list ->
                    if (list.any { responseMentionsFusionInvocation(it, depth + 1) }) return true
                }
                for (v in node.values) {
                    if (responseMentionsFusionInvocation(v, depth + 1)) return true
                }
            }
            is List<*> -> for (v in node) {
                if (responseMentionsFusionInvocation(v, depth + 1)) return true
            }
            is String -> if (node.contains("fusion", ignoreCase = true) &&
                (node.contains("openrouter", ignoreCase = true) || node.contains("tool", ignoreCase = true))
            ) {
                // Avoid matching random prose; require tool-ish context.
                if (node.contains("openrouter:fusion") ||
                    node.contains("openrouter_fusion") ||
                    node.contains("\"fusion\"")
                ) {
                    return true
                }
            }
        }
        return false
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

        // When the tool returns a hard error with no per-model list, still mark as found so UI
        // can show failure — except fusion_invocation_capped, which is filtered earlier.
        val status = result["status"] as? String
        val failureReason = result["failure_reason"] as? String
        val hardError = status == "error" && failureReason != null && failureReason != "fusion_invocation_capped"

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
            failedModels = when {
                failed.isNotEmpty() -> failed
                hardError -> listOf(failureReason ?: "error")
                else -> emptyList()
            },
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
