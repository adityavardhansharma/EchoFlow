package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client-side web search across providers (Exa, Parallel, Firecrawl), normalized to
 * [SearchSource]. Used as a tool by OpenRouter models (function calling) and local
 * models (prompt protocol).
 */
class WebSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dynamicAdapter = moshi.adapter(Any::class.java)

    suspend fun search(
        provider: String,
        apiKey: String,
        query: String,
        maxResults: Int = 5
    ): List<SearchSource> = withContext(Dispatchers.IO) {
        when (provider) {
            "exa" -> searchExa(apiKey, query, maxResults)
            "parallel" -> searchParallel(apiKey, query, maxResults)
            "firecrawl" -> searchFirecrawl(apiKey, query, maxResults)
            else -> throw Exception("Unknown search provider: $provider")
        }
    }

    private fun searchExa(apiKey: String, query: String, maxResults: Int): List<SearchSource> {
        val body = mapOf(
            "query" to query,
            "numResults" to maxResults,
            "contents" to mapOf(
                "text" to mapOf("maxCharacters" to 2000)
            )
        )
        val json = executePost(
            url = "https://api.exa.ai/search",
            headers = mapOf("x-api-key" to apiKey),
            body = body,
            providerLabel = "Exa"
        )
        val results = json["results"] as? List<*> ?: return emptyList()
        return results.mapNotNull { raw ->
            val r = raw as? Map<*, *> ?: return@mapNotNull null
            val url = r["url"] as? String ?: return@mapNotNull null
            SearchSource(
                title = (r["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = (r["text"] as? String)?.take(2000),
                publishedDate = r["publishedDate"] as? String
            )
        }
    }

    private fun searchParallel(apiKey: String, query: String, maxResults: Int): List<SearchSource> {
        val body = mapOf(
            "objective" to query,
            "max_results" to maxResults
        )
        val json = executePost(
            url = "https://api.parallel.ai/v1beta/search",
            headers = mapOf("x-api-key" to apiKey),
            body = body,
            providerLabel = "Parallel"
        )
        val results = json["results"] as? List<*> ?: return emptyList()
        return results.mapNotNull { raw ->
            val r = raw as? Map<*, *> ?: return@mapNotNull null
            val url = r["url"] as? String ?: return@mapNotNull null
            val excerpts = (r["excerpts"] as? List<*>)?.filterIsInstance<String>()
            SearchSource(
                title = (r["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = excerpts?.joinToString("\n")?.take(3000),
                publishedDate = r["publish_date"] as? String
            )
        }
    }

    private fun searchFirecrawl(apiKey: String, query: String, maxResults: Int): List<SearchSource> {
        val body = mapOf(
            "query" to query,
            "limit" to maxResults,
            "scrapeOptions" to mapOf("formats" to listOf("markdown"))
        )
        val json = executePost(
            url = "https://api.firecrawl.dev/v2/search",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = body,
            providerLabel = "Firecrawl"
        )
        val data = json["data"] as? Map<*, *> ?: return emptyList()
        val web = data["web"] as? List<*> ?: return emptyList()
        return web.mapNotNull { raw ->
            val r = raw as? Map<*, *> ?: return@mapNotNull null
            val url = r["url"] as? String ?: return@mapNotNull null
            // Firecrawl scrapes whole pages; cap markdown hard so it never floods the
            // context window of a small local model.
            val snippet = (r["markdown"] as? String)?.take(4000)
                ?: (r["description"] as? String)
            SearchSource(
                title = (r["title"] as? String).orEmpty().ifBlank { url },
                url = url,
                snippet = snippet
            )
        }
    }

    private fun executePost(
        url: String,
        headers: Map<String, String>,
        body: Map<String, Any>,
        providerLabel: String
    ): Map<*, *> {
        val requestBody = dynamicAdapter.toJson(body).toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        client.newCall(builder.build()).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "Invalid $providerLabel API key — check Settings."
                    429 -> "$providerLabel rate limit reached — try again shortly."
                    else -> "$providerLabel search failed (HTTP ${response.code})."
                }
                throw Exception(message)
            }
            return dynamicAdapter.fromJson(responseString) as? Map<*, *>
                ?: throw Exception("$providerLabel returned an unreadable response.")
        }
    }
}
