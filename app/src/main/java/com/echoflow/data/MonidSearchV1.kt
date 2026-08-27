package com.echoflow.data

/**
 * Monid run API (`POST /v1/run`) used as a client search backend.
 *
 * EchoFlow calls `context.dev` `/web/search` — ranked live-web results with titles
 * and snippets — then normalizes them to [SearchSource]. Parsing stays here so it
 * can be unit-tested without hitting the network.
 */
internal object MonidSearchV1 {
    const val RUN_ENDPOINT = "https://api.monid.ai/v1/run"
    const val PROVIDER = "context.dev"
    const val SEARCH_PATH = "/web/search"

    val terminalStatuses = setOf("COMPLETED", "FAILED", "BLOCKED", "STOPPED", "TIME_OUT")

    fun pollUrl(runId: String): String = "https://api.monid.ai/v1/runs/$runId"

    fun requestBody(query: String, maxResults: Int): Map<String, Any> = mapOf(
        "provider" to PROVIDER,
        "endpoint" to SEARCH_PATH,
        "input" to mapOf(
            "query" to query,
            "numResults" to maxResults,
        ),
    )

    fun isTerminal(status: String?): Boolean = status in terminalStatuses

    fun parseCompletedRun(response: Map<*, *>): List<SearchSource> {
        val output = response["output"]
        val results = resultsFromOutput(output) ?: return emptyList()
        return results.mapNotNull { raw ->
            val result = raw as? Map<*, *> ?: return@mapNotNull null
            val url = result["url"] as? String ?: return@mapNotNull null
            SearchSource(
                title = (result["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = snippetOf(result),
                publishedDate = (result["date"] as? String)
                    ?: (result["publishedDate"] as? String),
            )
        }
    }

    private fun resultsFromOutput(output: Any?): List<*>? {
        when (output) {
            is List<*> -> return output
            is Map<*, *> -> {
                (output["results"] as? List<*>)?.let { return it }
                (output["data"] as? List<*>)?.let { return it }
                val nested = output["output"]
                if (nested != null && nested !== output) return resultsFromOutput(nested)
            }
        }
        return null
    }

    private fun snippetOf(result: Map<*, *>): String? {
        val description = result["description"] as? String
        val snippet = result["snippet"] as? String
        val markdown = when (val raw = result["markdown"]) {
            is String -> raw
            is Map<*, *> -> (raw["content"] as? String) ?: (raw["text"] as? String)
            else -> null
        }
        return (description ?: snippet ?: markdown)?.take(2000)
    }
}
