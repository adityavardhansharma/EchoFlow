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
            (it as? Map<*, *>)?.containsKey("content") == true && (it as? Map<*, *>)?.containsKey("model") == true
        }
    
    /** Walks an SSE chunk for a fusion result `{analysis?, responses?, failed_models?}`. */
    fun scanForFusionResult(node: Any?, depth: Int = 0): FusionAnalysis? {
        if (depth > 8) return null
        when (node) {
            is Map<*, *> -> {
                val analysisObj = node["analysis"] as? Map<*, *>
                val responses = node["responses"] as? List<*>
                if (analysisObj != null || isFusionResponseList(responses)) {
                    return buildFusionAnalysis(node)
                }
                (node["content"] as? String)?.let { c ->
                    parseMaybeJson(c)?.let { scanForFusionResult(it, depth + 1)?.let { r -> return r } }
                }
                for (v in node.values) scanForFusionResult(v, depth + 1)?.let { return it }
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
            val stances = (m["stances"] as? List<*>)?.map { it.toString() } ?: emptyList()
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
            FusionResponse((m["model"] as? String).orEmpty(), content)
        } ?: emptyList()
    
        val failed = (result["failed_models"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    
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
