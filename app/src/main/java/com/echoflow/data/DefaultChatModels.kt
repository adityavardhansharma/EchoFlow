package com.echoflow.data

/**
 * Built-in OpenRouter chat models EchoFlow ships with out of the box.
 *
 * [BUILT_IN] entries always appear in the in-chat picker even before Room seeding finishes.
 * [SHIPPED] is written into `custom_models` once so Settings → Models shows them too.
 */
object DefaultChatModels {
    const val DEFAULT_MODEL_ID = "openai/gpt-5.6-luna"
    const val DEFAULT_MODEL_NAME = "GPT 5.6 Luna"

    /** OpenRouter's free-model router, presented in EchoFlow as a first-party model. */
    const val ECHO_LUMEN_MODEL_ID = "openrouter/free"
    const val ECHO_LUMEN_MODEL_NAME = "Echo Lumen"

    /** Previous built-in default; preserved for upgrades that never persisted a selection. */
    const val LEGACY_DEFAULT_MODEL_ID = "google/gemini-2.0-flash"
    const val LEGACY_DEFAULT_MODEL_NAME = "Gemini 2.0 Flash"

    val BUILT_IN: List<Pair<String, String>> = listOf(
        DEFAULT_MODEL_ID to DEFAULT_MODEL_NAME,
        ECHO_LUMEN_MODEL_ID to ECHO_LUMEN_MODEL_NAME,
    )

    val SHIPPED: List<Pair<String, String>> = BUILT_IN

    val BUILT_IN_IDS: Set<String> = SHIPPED.map { it.first }.toSet()

    fun displayName(modelId: String): String? =
        SHIPPED.firstOrNull { it.first == modelId }?.second
            ?: if (modelId == LEGACY_DEFAULT_MODEL_ID) LEGACY_DEFAULT_MODEL_NAME else null
}
