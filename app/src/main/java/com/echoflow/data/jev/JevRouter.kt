package com.echoflow.data.jev

import com.echoflow.data.memory.MemoryPrivacy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Backend router: one Jev call per turn, fixed thresholds, cloud chats only.
 *
 * Cloud-only rule lives here so callers cannot accidentally route local chats:
 * on-device models, Ollama, and OpenAI-compatible endpoints (often LAN) never
 * send prompts to TypeSafe. Direct cloud brands + OpenRouter are eligible.
 */
object JevRouter {
    /** Direct cloud brands eligible for Jev. Null = OpenRouter cloud. */
    private val cloudProviders = setOf(
        "openai", "claude", "gemini", "cerebras", "sarvam", "xai",
    )

    fun isCloudChat(isLocal: Boolean, customProvider: String?): Boolean {
        if (isLocal) return false
        if (customProvider == null) return true // OpenRouter cloud
        return customProvider in cloudProviders
    }

    data class RouteInput(
        val apiKey: String,
        val prompt: String,
        val memoryEnabled: Boolean,
        val memoryLearningEnabled: Boolean,
        val searchAvailable: Boolean,
        val recentTurns: List<Pair<String, String>> = emptyList(),
    )

    data class RecallPlan(val prefetch: Boolean, val allowTool: Boolean, val action: String)

    /** One policy for prefetch, tool exposure and diagnostics; a force request cannot enable disabled memory. */
    fun recallPlan(decision: JevDecision?, memoryEnabled: Boolean, forceMemory: Boolean, legacyRecall: Boolean): RecallPlan = when {
        !memoryEnabled -> RecallPlan(false, false, "disabled")
        forceMemory -> RecallPlan(true, true, "forced_recall")
        decision == null || decision.fallback -> RecallPlan(legacyRecall, true, "legacy")
        decision.memoryAction == "skip" -> RecallPlan(false, false, "skip")
        decision.memoryAction == "recall" -> RecallPlan(true, true, "recall")
        else -> RecallPlan(false, true, "defer")
    }

    /** Preserve referents from both speakers for the memory backend's query rewriter. */
    fun recallQuery(message: String, recentTurns: List<Pair<String, String>>): String =
        "Missing personal history relevant to the latest user request: " + MemoryPrivacy.redact(message).take(1000) +
            recentTurns.filter { it.first == "user" || it.first == "assistant" }.takeLast(4)
                .joinToString(separator = "", prefix = "\nRecent conversation (context only):") { (role, content) ->
                    "\n$role: " + MemoryPrivacy.redact(content).take(500)
                }

    suspend fun route(
        input: RouteInput,
        client: JevClient = JevClient(),
    ): JevDecision {
        if (input.apiKey.isBlank() || input.prompt.isBlank()) return JevDecision.fallback()
        val started = System.currentTimeMillis()
        return try {
            // Bounded: a TypeSafe outage must never stall the reply. Normal Jev
            // calls land in 70-500ms; past the timeout we use legacy behaviour.
            val scores = withTimeoutOrNull(JevThresholds.TIMEOUT_MS) {
                client.classify(
                    apiKey = input.apiKey,
                    message = input.prompt,
                    memoryOn = input.memoryEnabled,
                    searchOn = input.searchAvailable,
                    recentTurns = input.recentTurns,
                )
            } ?: return JevDecision.fallback()
            val latency = System.currentTimeMillis() - started
            // One judgment, not agreement between overlapping classifiers. Uncertainty
            // delegates to the answering model; it never activates legacy keyword recall.
            val memoryAction = if (scores.routeProbabilities.getValue(scores.routeChoice) >= JevThresholds.ACT)
                scores.routeChoice else "defer"
            val webForced = input.searchAvailable &&
                scores.needsWeb >= JevThresholds.ACT
            // The save instruction orders a remember_memory call, so it is only
            // raised when memory tools are installed for this turn (memoryEnabled)
            // — never when learning is on but recall (and the tool) is off.
            val saveTriggered = input.memoryEnabled && input.memoryLearningEnabled &&
                scores.needsSave >= JevThresholds.ACT
            JevDecision(
                modelVersion = scores.modelVersion,
                latencyMs = latency,
                needsMemory = scores.needsMemory,
                needsSave = scores.needsSave,
                needsWeb = scores.needsWeb,
                routeChoice = scores.routeChoice,
                routeProbabilities = scores.routeProbabilities,
                routeConfidence = scores.routeConfidence,
                memoryRecalled = false, // Updated by the caller after the recall attempt.
                webForced = webForced,
                saveTriggered = saveTriggered,
                fallback = false,
                memoryAction = memoryAction,
                promptVersion = JevThresholds.PROMPT_VERSION,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JevDecision.fallback()
        }
    }
}
