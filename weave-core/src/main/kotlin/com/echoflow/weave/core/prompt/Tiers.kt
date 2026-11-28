package com.echoflow.weave.core.prompt

/**
 * Pure tier and level logic from 05 §10–11 (effective tier, prompt level, explicit UI requests,
 * unknown-model tiers). `weave-runtime`'s `EchoUiTiers` and settings wire these to preferences.
 */
object EchoUiTiers {

    /** 05 §11.3. */
    fun effectiveTier(level: IntelligentUiLevel, modelTier: Int, explicitUiRequest: Boolean, makeInteractive: Boolean, shippedTier: Int): Int {
        val cap = when {
            makeInteractive -> 4
            level == IntelligentUiLevel.OFF && explicitUiRequest -> 3
            level == IntelligentUiLevel.OFF -> 0
            level == IntelligentUiLevel.BALANCED -> 3
            else -> 4
        }
        return minOf(cap, modelTier, shippedTier)
    }

    fun promptLevel(level: IntelligentUiLevel, explicitUiRequest: Boolean, makeInteractive: Boolean) = when {
        makeInteractive -> IntelligentUiLevel.BALANCED
        level == IntelligentUiLevel.OFF && explicitUiRequest -> IntelligentUiLevel.BALANCED
        else -> level
    }

    /** Max new blocks rendered per reply (05 §11.4), from the prompt level. */
    fun maxBlocks(promptLevel: IntelligentUiLevel) = when (promptLevel) {
        IntelligentUiLevel.OFF -> 0
        IntelligentUiLevel.BALANCED -> 1
        IntelligentUiLevel.IMMERSIVE -> 3
    }

    /** 05 §11.2: a chat's stored override wins over the global level; null follows the global one. */
    fun chatLevel(global: IntelligentUiLevel, override: String?): IntelligentUiLevel =
        override?.let { IntelligentUiLevel.fromStorage(it) } ?: global

    /**
     * 05 §10.4: adds one block's error count to a model's quality record (the last 10 blocks).
     * Returns the record to store and whether the model drops a tier for 7 days: at least 5 blocks
     * averaging more than 2 errors. A downgrade starts a fresh record.
     */
    fun recordQuality(record: List<Int>, errors: Int): Pair<List<Int>, Boolean> {
        val next = (record + errors).takeLast(10)
        val downgrade = next.size >= 5 && next.average() > 2.0
        return (if (downgrade) emptyList() else next) to downgrade
    }

    /** `BuildConfig.ECHO_UI_PHASE` → shipped tier (05 §15). */
    fun shippedTier(phase: Int) = when (phase) { 1 -> 2; 2 -> 3; else -> 4 }

    private val EXPLICIT = Regex(
        listOf(
            """\b(make|build|create|draw|show|generate|turn|put|give (me|us))\b[^.?!\n]{0,40}\b(calculator|chart|graph|plot|table|quiz|flashcards?|slider|dashboard|tracker|timer|countdown|planner|checklist|kanban|widget|form|wizard|game|simulat\w*|ui)s?\b""",
            """\b(plot|chart|graph|visuali[sz]e|tabulate|simulate)\s+(it|this|that|these|those|my|the|our|how)\b""",
            """\b(as|into)\s+(a |an )?(card|chart|graph|table|ui|calculator|checklist)\b""",
            """\b(put|show|display|list|compare|summari[sz]e|organi[sz]e|arrange|lay\s+out|present|format)\b[^.?!\n]{0,40}\bin\s+(a |an )?(chart|graph|table)\b""",
            """\bmake\s+(it|this|that)\s+interactive\b""",
        ).joinToString("|"),
    )

    /** 05 §11.5. */
    fun isExplicitUiRequest(message: String) = EXPLICIT.containsMatchIn(message.lowercase())

    /** 05 §10.2: tier for a model id that matches no prefix. */
    fun unknownModelTier(toolUse: Boolean, contextTokens: Int) = when {
        toolUse && contextTokens >= 32_000 -> 2
        contextTokens >= 16_000 -> 1
        else -> 0
    }
}
