package com.echoflow.data

/**
 * Parallel GA Search API (`POST /v1/search`) request and response contract.
 * Kept separate from [WebSearchService] so serialization and parsing stay unit-testable.
 */
internal object ParallelSearchV1 {
    const val ENDPOINT = "https://api.parallel.ai/v1/search"
    const val MODE = "basic"

    fun requestBody(query: String, maxResults: Int): Map<String, Any> = mapOf(
        "objective" to query,
        "search_queries" to listOf(query),
        "mode" to MODE,
        "advanced_settings" to mapOf("max_results" to maxResults),
    )

    fun parseResults(response: Map<*, *>): List<SearchSource> {
        val results = response["results"] as? List<*> ?: return emptyList()
        return results.mapNotNull { raw ->
            val result = raw as? Map<*, *> ?: return@mapNotNull null
            val url = result["url"] as? String ?: return@mapNotNull null
            val excerpts = (result["excerpts"] as? List<*>)?.filterIsInstance<String>()
            SearchSource(
                title = (result["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = excerpts?.joinToString("\n")?.take(3000),
                publishedDate = result["publish_date"] as? String,
            )
        }
    }
}
