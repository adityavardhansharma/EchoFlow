package com.echoflow.data

/**
 * Client-side web search backends that EchoFlow runs on-device (as opposed to
 * OpenRouter server search). Keyed providers store an API key on the phone.
 * EchoCrawl is keyless and is chat-only — never Data Agent, Browser Flow, or Deep Research.
 */
object ClientSearchProviders {
    const val EXA = "exa"
    const val PARALLEL = "parallel"
    const val FIRECRAWL = "firecrawl"
    const val ECHOCRAWL = "echocrawl"

    /** Providers that need a saved API key. */
    val keyedIds: List<String> = listOf(EXA, PARALLEL, FIRECRAWL)

    /** Providers that can power normal chat (and project chat) web search. */
    val chatIds: List<String> = keyedIds + ECHOCRAWL

    val asSet: Set<String> = chatIds.toSet()
    val keyedSet: Set<String> = keyedIds.toSet()

    fun requiresApiKey(provider: String): Boolean = provider in keyedSet

    fun isReady(provider: String, apiKey: String): Boolean =
        provider in asSet && (!requiresApiKey(provider) || apiKey.isNotBlank())
}
