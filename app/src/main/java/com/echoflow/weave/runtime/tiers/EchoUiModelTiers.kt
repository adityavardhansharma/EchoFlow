package com.echoflow.weave.runtime.tiers

import com.echoflow.BuildConfig
import com.echoflow.weave.core.prompt.EchoUiTiers
import com.echoflow.weave.core.prompt.IntelligentUiLevel

/**
 * The tier table (05 §10.1): model-id prefix → tier, longest prefix wins. Updated from the eval
 * harness (07 §10); each run's FINDINGS.md under weave-eval/runs lists the measured models. On-device models are
 * always tier 0: live UI is cloud-only in this version (a product decision, 05 §10.1).
 */
object EchoUiModelTiers {
    private val TABLE: Map<String, Int> = mapOf(
        // measured (weave-eval/runs/2026-11-26-tier2-balanced)
        "google/gemini-4-hydrogen" to 3,
        "openai/gpt-6-luna" to 3,
        // flagship cloud families
        "anthropic/" to 3, "claude-" to 3,
        "openai/" to 3, "gpt-" to 3, "o3" to 3, "o4" to 3,
        "google/gemini" to 3, "gemini-" to 3,
        "x-ai/grok" to 3, "grok-" to 3,
        "moonshotai/kimi" to 3,
        "z-ai/glm" to 3,
        // mid-size
        "deepseek/" to 2, "qwen/" to 2, "mistralai/" to 2, "meta-llama/" to 2, "minimax/" to 2,
        "xiaomi/mimo" to 2, "nvidia/" to 2, "cohere/" to 2,
        // small variants
        "openai/gpt-4o-mini" to 1, "openai/gpt-4.1-nano" to 1, "google/gemini-flash-lite" to 2,
        "meta-llama/llama-3.2-1b" to 0, "meta-llama/llama-3.2-3b" to 1,
        "google/gemma" to 1, "qwen/qwen3-4b" to 1, "qwen/qwen3-8b" to 1,
    )

    /**
     * Models measured (07 §10) to do clearly better with schema-constrained Weave-JSON than with
     * source; their tier-≤ 2 prompts use the JSON form (05 §12). None yet: both measured models
     * write source Weave at 100 % parse success.
     */
    private val JSON_FORM: Set<String> = emptySet()

    fun form(modelId: String, tier: Int): com.echoflow.weave.core.prompt.PromptForm =
        if (tier <= 2 && JSON_FORM.any { modelId.lowercase().startsWith(it) }) com.echoflow.weave.core.prompt.PromptForm.JSON
        else com.echoflow.weave.core.prompt.PromptForm.SOURCE

    /** Shipped tier for this build (05 §15). */
    val shippedTier: Int get() = EchoUiTiers.shippedTier(BuildConfig.ECHO_UI_PHASE)

    /**
     * The model's tier: the user override (05 §10.3) when set, else the table, else the unknown-model
     * rule (05 §10.2, assuming a modern cloud model with tools and a large context), lowered by an
     * active auto-downgrade (05 §10.4).
     */
    fun tierFor(modelId: String, isLocal: Boolean, override: Int?, downgraded: Boolean = false): Int {
        if (isLocal) return 0
        if (override != null) return override.coerceIn(0, 4)
        val id = modelId.lowercase()
        val table = TABLE.entries.filter { id.startsWith(it.key) }.maxByOrNull { it.key.length }?.value
            ?: EchoUiTiers.unknownModelTier(toolUse = true, contextTokens = 128_000)
        return (if (downgraded) table - 1 else table).coerceIn(0, 4)
    }

    /** Everything a chat turn needs to decide the Echo UI prompt (05 §11.3). */
    data class TurnPlan(val tier: Int, val level: IntelligentUiLevel, val maxBlocks: Int)

    fun plan(
        level: IntelligentUiLevel,
        modelTier: Int,
        latestUserMessage: String,
        makeInteractive: Boolean = false,
    ): TurnPlan {
        val explicit = EchoUiTiers.isExplicitUiRequest(latestUserMessage)
        val tier = EchoUiTiers.effectiveTier(level, modelTier, explicit, makeInteractive, shippedTier)
        val promptLevel = EchoUiTiers.promptLevel(level, explicit, makeInteractive)
        return TurnPlan(tier, promptLevel, if (tier == 0) 0 else EchoUiTiers.maxBlocks(promptLevel))
    }
}
