package com.echoflow.data

import com.echoflow.data.OpenRouterService.FusionRequest

/**
 * Executes one paid fusion panel and, if needed, a completion with tools disabled.
 * Transport and message encoding are supplied by the provider so this protocol can be
 * exercised without Android, credentials, or network requests.
 */
internal class OpenRouterFusionRunner(
    private val postForResponse: (String, Map<String, Any>) -> Map<*, *>,
    private val buildMessagesPayload: (List<ChatMessage>, String?) -> MutableList<Map<String, Any>>,
    private val applyInferenceParams: (MutableMap<String, Any>, InferenceParams?) -> Unit,
) {
    /**
     * Force fusion once, recover a final answer if the outer model stalls after the panel, and
     * surface an honest skip when deliberation never ran. No second fusion request: missing or
     * unparsed payloads must not re-bill the multi-model panel.
     */
    fun run(
        apiKey: String,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        params: InferenceParams?,
    ): FusionRunOutcome {
        // Force the fusion tool once (user selected a panel — do not leave it to chance).
        val response = postForResponse(
            apiKey,
            buildFusionToolRequest(
                model = model,
                history = history,
                systemPrompt = systemPrompt,
                fusion = fusion,
                params = params,
                toolChoice = "required",
            ),
        )
        val parsed = preferredFusionFrom(response)
        // Distinguish "never called fusion" from "called fusion but we couldn't decode the body".
        val fusionInvoked = parsed != null ||
            OpenRouterEchoDecoder.responseMentionsFusionInvocation(response)
        val deliberationSkipped = !fusionInvoked
        val analysis = (parsed ?: FusionAnalysis(
            panelName = fusion.panelName,
            judgeModel = fusion.judge,
            models = fusion.models,
            toolResultFound = false,
            deliberationSkipped = deliberationSkipped,
        )).copy(
            panelName = fusion.panelName,
            judgeModel = fusion.judge ?: parsed?.judgeModel,
            models = fusion.models.ifEmpty { parsed?.models ?: emptyList() },
            toolResultFound = parsed != null,
            // Only a true skip when there is no evidence the tool was requested/returned.
            deliberationSkipped = deliberationSkipped,
        )

        var choice = (response["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        var message = choice?.get("message") as? Map<*, *>
        var answer = (message?.get("content") as? String).orEmpty().trim()
        var reasoning = ((message?.get("reasoning") as? String)
            ?: (message?.get("reasoning_content") as? String)).orEmpty().trim()
        var annotations = message?.get("annotations") as? List<*>

        // Panel detail available but no user-facing answer (common when the model only emitted
        // the tool result, or after a capped second fusion call left content empty).
        // Synthesize with tools disabled so fusion cannot be invoked again.
        if (answer.isBlank() && analysis.hasUsableDetail) {
            val synth = postForResponse(
                apiKey,
                buildFusionAnswerRequest(
                    model = model,
                    history = history,
                    systemPrompt = systemPrompt,
                    fusion = fusion,
                    analysis = analysis,
                    params = params,
                ),
            )
            choice = (synth["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
            message = choice?.get("message") as? Map<*, *>
            answer = (message?.get("content") as? String).orEmpty().trim()
            reasoning = ((message?.get("reasoning") as? String)
                ?: (message?.get("reasoning_content") as? String)).orEmpty().ifBlank { reasoning }
            annotations = message?.get("annotations") as? List<*> ?: annotations
        }

        return FusionRunOutcome(analysis, answer, reasoning, annotations)
    }

    data class FusionRunOutcome(
        val analysis: FusionAnalysis,
        val answer: String,
        val reasoning: String,
        val annotations: List<*>?,
    )

    private fun preferredFusionFrom(response: Map<*, *>): FusionAnalysis? =
        OpenRouterEchoDecoder.selectPreferredFusionResult(
            OpenRouterEchoDecoder.scanForAllFusionResults(response),
        )

    private fun buildFusionToolRequest(
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        params: InferenceParams?,
        toolChoice: Any,
    ): MutableMap<String, Any> {
        val fusionParams = mutableMapOf<String, Any>("analysis_models" to fusion.models)
        fusion.judge?.takeIf { it.isNotBlank() }?.let { fusionParams["model"] = it }
        val fusionPlugin = mutableMapOf<String, Any>(
            "id" to "fusion",
            "analysis_models" to fusion.models,
            "model" to (fusion.judge?.takeIf { it.isNotBlank() } ?: model),
        )
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history, systemPrompt),
            "stream" to false,
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true),
            "tools" to listOf(mapOf("type" to "openrouter:fusion", "parameters" to fusionParams)),
            "tool_choice" to toolChoice,
            // Avoid parallel multi-tool fan-out; fusion is the only tool we attach.
            "parallel_tool_calls" to false,
            "plugins" to listOf(fusionPlugin),
        )
        OpenRouterPayloads.enablePdfPlugin(requestMap, OpenRouterPayloads.historyHasPdf(history))
        applyInferenceParams(requestMap, params)
        return requestMap
    }

    /**
     * Follow-up completion with **no** fusion tool: write one final answer from the panel digest.
     * Prevents a second fusion invocation after `fusion_invocation_capped`.
     */
    private fun buildFusionAnswerRequest(
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        fusion: FusionRequest,
        analysis: FusionAnalysis,
        params: InferenceParams?,
    ): MutableMap<String, Any> {
        val digest = formatFusionDigestForJudge(analysis)
        val synthSystem = buildString {
            append(systemPrompt)
            append("\n\n## Panel result (already ran — do not call tools)\n")
            append("The fusion panel \"${fusion.panelName}\" has finished. Write ONE final user-facing answer from this digest. ")
            append("Do not call tools. Do not reprint the digest or per-model transcripts.\n\n")
            append(digest)
        }
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to buildMessagesPayload(history, synthSystem),
            "stream" to false,
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true),
            "tool_choice" to "none",
        )
        OpenRouterPayloads.enablePdfPlugin(requestMap, OpenRouterPayloads.historyHasPdf(history))
        applyInferenceParams(requestMap, params)
        return requestMap
    }

    private fun formatFusionDigestForJudge(analysis: FusionAnalysis): String = buildString {
        if (analysis.consensus.isNotEmpty()) {
            append("Consensus:\n")
            analysis.consensus.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (analysis.contradictions.isNotEmpty()) {
            append("Disagreements:\n")
            analysis.contradictions.forEach { c ->
                append("- ").append(c.topic)
                if (c.stances.isNotEmpty()) append(": ").append(c.stances.joinToString(" | "))
                append('\n')
            }
            append('\n')
        }
        if (analysis.uniqueInsights.isNotEmpty()) {
            append("Unique insights:\n")
            analysis.uniqueInsights.forEach { i ->
                append("- ")
                if (i.model.isNotBlank()) append(i.model).append(": ")
                append(i.insight).append('\n')
            }
            append('\n')
        }
        if (analysis.blindSpots.isNotEmpty()) {
            append("Blind spots:\n")
            analysis.blindSpots.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (analysis.responses.isNotEmpty()) {
            append("Per-model answers (for your synthesis only — do not paste back):\n")
            analysis.responses.forEach { r ->
                append("### ").append(r.model.ifBlank { "model" }).append('\n')
                append(r.content.take(6000)).append("\n\n")
            }
        }
    }.ifBlank { "(No structured digest fields; use any usable panel signal from context.)" }

}
