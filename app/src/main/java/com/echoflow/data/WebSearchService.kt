package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Client-side web search across providers (Exa, Parallel, Firecrawl, Monid), normalized to
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
            ClientSearchProviders.EXA -> searchExa(apiKey, query, maxResults)
            ClientSearchProviders.PARALLEL -> searchParallel(apiKey, query, maxResults)
            ClientSearchProviders.FIRECRAWL -> searchFirecrawl(apiKey, query, maxResults)
            ClientSearchProviders.MONID -> searchMonid(apiKey, query, maxResults)
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
        ).body
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
        val json = executePost(
            url = ParallelSearchV1.ENDPOINT,
            headers = mapOf("x-api-key" to apiKey),
            body = ParallelSearchV1.requestBody(query, maxResults),
            providerLabel = "Parallel"
        )
        return ParallelSearchV1.parseResults(json.body)
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
        ).body
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

    private suspend fun searchMonid(apiKey: String, query: String, maxResults: Int): List<SearchSource> {
        val started = executePost(
            url = MonidSearchV1.RUN_ENDPOINT,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = MonidSearchV1.requestBody(query, maxResults),
            providerLabel = "Monid",
            acceptAccepted = true,
        )
        val completed = when (started.code) {
            200 -> started.body
            202 -> pollMonidRun(apiKey, started.body)
            else -> throw Exception("Monid search failed (HTTP ${started.code}).")
        }
        val status = completed["status"] as? String
        if (status == "FAILED") {
            throw Exception("Monid search failed.")
        }
        if (status == "BLOCKED") {
            throw Exception("Monid blocked this search (budget or run cap). Pause or change the control at https://app.monid.ai.")
        }
        if (status == "STOPPED" || status == "TIME_OUT") {
            throw Exception("Monid search did not finish.")
        }
        val providerHttp = ((completed["providerResponse"] as? Map<*, *>)?.get("httpStatus") as? Number)?.toInt()
        if (providerHttp != null && providerHttp >= 400) {
            throw Exception("Monid search failed (HTTP $providerHttp).")
        }
        return MonidSearchV1.parseCompletedRun(completed)
    }

    private suspend fun pollMonidRun(apiKey: String, started: Map<*, *>): Map<*, *> {
        val runId = started["runId"] as? String
            ?: throw Exception("Monid did not return a run id.")
        var waitMs = 2_000L
        repeat(24) {
            delay(waitMs)
            waitMs = (waitMs * 2).coerceAtMost(8_000L)
            val polled = executeGet(
                url = MonidSearchV1.pollUrl(runId),
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                providerLabel = "Monid",
            )
            if (MonidSearchV1.isTerminal(polled["status"] as? String)) return polled
        }
        throw Exception("Monid search timed out — try again shortly.")
    }

    private fun executeGet(
        url: String,
        headers: Map<String, String>,
        providerLabel: String,
    ): Map<*, *> {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        client.newCall(builder.build()).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw httpError(providerLabel, response.code)
            }
            return dynamicAdapter.fromJson(responseString) as? Map<*, *>
                ?: throw Exception("$providerLabel returned an unreadable response.")
        }
    }

    private data class JsonResponse(val code: Int, val body: Map<*, *>)

    private fun executePost(
        url: String,
        headers: Map<String, String>,
        body: Map<String, Any>,
        providerLabel: String,
        acceptAccepted: Boolean = false,
    ): JsonResponse {
        val requestBody = dynamicAdapter.toJson(body).toRequestBody("application/json".toMediaType())
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }

        client.newCall(builder.build()).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            val accepted = acceptAccepted && response.code == 202
            if (!response.isSuccessful && !accepted) {
                throw httpError(providerLabel, response.code)
            }
            val parsed = dynamicAdapter.fromJson(responseString) as? Map<*, *>
                ?: throw Exception("$providerLabel returned an unreadable response.")
            return JsonResponse(response.code, parsed)
        }
    }

    private fun httpError(providerLabel: String, code: Int): Exception {
        val message = when (code) {
            401, 403 -> "Invalid $providerLabel API key — check Settings."
            429 -> "$providerLabel rate limit reached — try again shortly."
            else -> "$providerLabel search failed (HTTP $code)."
        }
        return Exception(message)
    }
}
