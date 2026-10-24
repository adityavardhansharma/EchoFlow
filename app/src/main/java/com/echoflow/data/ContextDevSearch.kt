package com.echoflow.data

/**
 * Context.dev Search API (`POST /v1/web/search`) request and response contract.
 * Asks for full-page markdown with each result; kept separate from [WebSearchService]
 * so serialization and parsing stay unit-testable.
 */
internal object ContextDevSearch {
    const val ENDPOINT = "https://api.context.dev/v1/web/search"

    /** Server-side deadline, under the client's 60s read timeout. Ready results come back at the deadline. */
    private const val TIMEOUT_MS = 45_000

    fun requestBody(query: String, maxResults: Int): Map<String, Any> = mapOf(
        "query" to query.take(500),
        "numResults" to maxResults.coerceIn(1, 10),
        "markdownOptions" to mapOf(
            "enabled" to true,
            "useMainContentOnly" to true,
            "includeLinks" to false,
        ),
        "timeoutOpts" to mapOf(
            "milliseconds" to TIMEOUT_MS,
            "behavior" to "return-partial",
        ),
    )

    fun parseResults(response: Map<*, *>, snippetChars: Int = 4000): List<SearchSource> {
        val results = response["results"] as? List<*> ?: return emptyList()
        val seen = mutableSetOf<String>()
        return results.mapNotNull { raw ->
            val result = raw as? Map<*, *> ?: return@mapNotNull null
            val url = result["url"] as? String ?: return@mapNotNull null
            if (url.isBlank() || !seen.add(url)) return@mapNotNull null
            SearchSource(
                title = (result["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = snippetOf(result, snippetChars),
            )
        }
    }

    /** Page markdown when the scrape succeeded, otherwise the search description. */
    private fun snippetOf(result: Map<*, *>, maxChars: Int): String? {
        val markdown = (result["markdown"] as? Map<*, *>)
            ?.takeIf { it["code"] == "SUCCESS" }
            ?.get("markdown") as? String
        val text = markdown?.takeIf { it.isNotBlank() }
            ?: (result["description"] as? String)?.takeIf { it.isNotBlank() }
            ?: return null
        return text.take(maxChars)
    }
}
