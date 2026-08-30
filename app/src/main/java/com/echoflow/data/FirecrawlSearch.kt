package com.echoflow.data

/**
 * Firecrawl Search API (`POST /v2/search`) request and response contract.
 * Shared by keyed Firecrawl and keyless EchoCrawl so parsing stays unit-testable.
 */
internal object FirecrawlSearch {
    const val ENDPOINT = "https://api.firecrawl.dev/v2/search"

    fun requestBody(query: String, maxResults: Int, scrapeMarkdown: Boolean): Map<String, Any> {
        val body = linkedMapOf<String, Any>(
            "query" to query,
            "limit" to maxResults.coerceIn(1, 10),
        )
        if (scrapeMarkdown) {
            // Paid Firecrawl: full-page markdown. EchoCrawl must not send this — scrape
            // burns the keyless credit pool and often times out as a "fetch" failure.
            body["scrapeOptions"] = mapOf("formats" to listOf("markdown"))
        }
        return body
    }

    fun parseResults(
        response: Map<*, *>,
        snippetChars: Int,
        preferMarkdown: Boolean,
    ): List<SearchSource> {
        if (response["success"] == false) {
            throw Exception(apiErrorMessage(response) ?: "Search was rejected.")
        }
        val seen = mutableSetOf<String>()
        return webItems(response).mapNotNull { item ->
            val url = item["url"] as? String ?: return@mapNotNull null
            if (url.isBlank() || !seen.add(url)) return@mapNotNull null
            SearchSource(
                title = (item["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = snippetOf(item, snippetChars, preferMarkdown),
            )
        }
    }

    fun apiErrorMessage(json: Map<*, *>?): String? {
        if (json == null) return null
        return listOf("error", "message")
            .mapNotNull { (json[it] as? String)?.takeIf { text -> text.isNotBlank() } }
            .firstOrNull()
    }

    private fun webItems(json: Map<*, *>): List<Map<*, *>> {
        val out = mutableListOf<Map<*, *>>()
        fun addList(raw: Any?) {
            val list = raw as? List<*> ?: return
            list.forEach { item -> (item as? Map<*, *>)?.let(out::add) }
        }
        when (val data = json["data"]) {
            is Map<*, *> -> {
                addList(data["web"])
                addList(data["news"])
                addList(data["results"])
            }
            is List<*> -> addList(data)
        }
        addList(json["web"])
        addList(json["results"])
        return out
    }

    private fun snippetOf(item: Map<*, *>, maxChars: Int, preferMarkdown: Boolean): String? {
        val keys = if (preferMarkdown) {
            listOf("markdown", "description", "snippet", "content")
        } else {
            listOf("description", "snippet", "content", "markdown")
        }
        val text = keys
            .mapNotNull { item[it] as? String }
            .firstOrNull { it.isNotBlank() }
            ?: return null
        return text.take(maxChars)
    }
}
