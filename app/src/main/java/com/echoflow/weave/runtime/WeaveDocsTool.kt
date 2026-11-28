package com.echoflow.weave.runtime

import com.echoflow.weave.core.prompt.WeaveDocs
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The `weave_docs` tool for one chat turn (06 §8, 01 §21.2). Installed in the reply's coroutine
 * context only when that turn's prompt was trimmed to the common set and index; the OpenRouter
 * request paths read it and offer the tool, the same way they read `MemoryTools`.
 *
 * Answers come from the same catalog the prompt was built from ([EchoUiPromptCache.catalog]), so
 * the tool never documents something the turn's tier, the build's phase or the renderer rules out.
 */
class WeaveDocsTool(private val tier: Int) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<WeaveDocsTool> {
        /** Lookups per reply. The model's first block never waits on one (06 §2.1). */
        const val MAX_CALLS = 4
    }

    private var calls = 0

    /** False once a retry has gone back to the untrimmed prompt with no tools. */
    @Volatile var enabled = true

    fun handles(name: String) = enabled && name == WeaveDocs.TOOL_NAME

    /** OpenAI-style function tool definition (06 §8.2); empty when disabled. */
    fun schemas(): List<Map<String, Any>> = if (!enabled) emptyList() else listOf(
        mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to WeaveDocs.TOOL_NAME,
                "description" to WeaveDocs.DESCRIPTION,
                "parameters" to WeaveDocs.PARAMETERS,
            ),
        ),
    )

    fun execute(arguments: String): String {
        if (++calls > MAX_CALLS) return "weave_docs limit reached for this reply. Write the block with the signatures you already have."
        val names = WeaveDocs.parseNames(arguments)
            ?: return "Invalid arguments. Call weave_docs with {\"names\": [\"Quiz\", \"Canvas\"]}."
        return WeaveDocs.answer(names, tier, EchoUiPromptCache.catalog(tier))
    }
}
