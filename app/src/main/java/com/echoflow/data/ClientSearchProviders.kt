package com.echoflow.data

/**
 * Client-side web search backends: the API key lives on the device and EchoFlow
 * runs the search itself. These work with cloud, on-device, and custom-endpoint
 * models. OpenRouter server search is *not* in this set.
 */
object ClientSearchProviders {
    const val EXA = "exa"
    const val PARALLEL = "parallel"
    const val FIRECRAWL = "firecrawl"
    const val MONID = "monid"

    val ids: List<String> = listOf(EXA, PARALLEL, FIRECRAWL, MONID)
    val asSet: Set<String> = ids.toSet()

    fun prefKey(provider: String): String? = when (provider) {
        EXA -> "exa_api_key"
        PARALLEL -> "parallel_api_key"
        FIRECRAWL -> "firecrawl_api_key"
        MONID -> "monid_api_key"
        else -> null
    }

    fun displayName(provider: String): String = when (provider) {
        EXA -> "Exa"
        PARALLEL -> "Parallel"
        FIRECRAWL -> "Firecrawl"
        MONID -> "Monid"
        else -> provider.replaceFirstChar { it.uppercase() }
    }
}
