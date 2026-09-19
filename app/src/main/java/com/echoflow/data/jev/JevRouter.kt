package com.echoflow.data.jev

import kotlinx.coroutines.CancellationException

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
        val previousAssistant: String?,
        val memoryEnabled: Boolean,
        val memoryLearningEnabled: Boolean,
        val searchAvailable: Boolean,
    )

    suspend fun route(
        input: RouteInput,
        client: JevClient = JevClient(),
    ): JevDecision {
        if (input.apiKey.isBlank() || input.prompt.isBlank()) return JevDecision.fallback()
        val started = System.currentTimeMillis()
        return try {
            val scores = client.classify(
                apiKey = input.apiKey,
                message = input.prompt,
                previousAssistant = input.previousAssistant,
                memoryOn = input.memoryEnabled,
                searchOn = input.searchAvailable,
            )
            val latency = System.currentTimeMillis() - started
            val memoryRecall = input.memoryEnabled &&
                scores.needsMemory >= JevThresholds.ACT
            val webForced = input.searchAvailable &&
                scores.needsWeb >= JevThresholds.ACT
            val saveTriggered = input.memoryLearningEnabled &&
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
                memoryRecalled = memoryRecall,
                webForced = webForced,
                saveTriggered = saveTriggered,
                fallback = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JevDecision.fallback()
        }
    }
}
