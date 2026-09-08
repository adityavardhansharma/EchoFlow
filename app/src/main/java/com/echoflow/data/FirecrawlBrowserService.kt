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
 * Thin client for Firecrawl Interact (the stateful browser session behind Browser Flow):
 *  - [startSession]  POST /v2/scrape                      → opens a browser, returns scrapeId + live URLs
 *  - [interact]      POST /v2/scrape/{scrapeId}/interact  → drives the same browser with a prompt
 *  - [stop]          DELETE /v2/scrape/{scrapeId}/interact → closes the session (billing/cleanup)
 *
 * Sessions are deliberately ephemeral: no `profile` is ever sent, so Firecrawl keeps no cookies
 * or login state. Interact is request/response (no token streaming); a prompt call can take up to
 * Firecrawl's 300s timeout, hence the long read/call timeouts.
 */
class FirecrawlBrowserService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // A single interact action can run up to Firecrawl's 300s cap; give headroom.
        .readTimeout(330, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .callTimeout(360, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val anyAdapter = moshi.adapter(Any::class.java)

    data class StartResult(
        val scrapeId: String,
        val liveViewUrl: String?,
        val interactiveLiveViewUrl: String?,
        val title: String?,
    )

    data class InteractResult(
        val success: Boolean,
        val output: String,
        val liveViewUrl: String?,
        val interactiveLiveViewUrl: String?,
    )

    /** Open a browser on [url]. Temporary session (no profile is sent). */
    suspend fun startSession(apiKey: String, url: String): StartResult = withContext(Dispatchers.IO) {
        val json = post(
            url = "https://api.firecrawl.dev/v2/scrape",
            apiKey = apiKey,
            body = mapOf(
                "url" to url,
                "formats" to listOf("markdown"),
                "actions" to emptyList<Any>(),
            ),
        )
        val data = json["data"] as? Map<*, *>
        val metadata = data?.get("metadata") as? Map<*, *>
        val scrapeId = firstString(json, data, metadata, listOf("scrapeId", "id", "sessionId"))
            ?: throw Exception("Firecrawl did not return a browser session id.")
        StartResult(
            scrapeId = scrapeId,
            liveViewUrl = firstString(json, data, metadata, listOf("liveViewUrl")),
            interactiveLiveViewUrl = firstString(json, data, metadata, listOf("interactiveLiveViewUrl")),
            title = (metadata?.get("title") as? String),
        )
    }

    /** Drive the same browser with a natural-language [prompt]. */
    suspend fun interact(apiKey: String, scrapeId: String, prompt: String): InteractResult =
        withContext(Dispatchers.IO) {
            val json = post(
                url = "https://api.firecrawl.dev/v2/scrape/$scrapeId/interact",
                apiKey = apiKey,
                body = mapOf(
                    "prompt" to prompt,
                    "timeout" to 280,
                ),
            )
            val data = json["data"] as? Map<*, *>
            val metadata = data?.get("metadata") as? Map<*, *>
            val output = firstString(json, data, metadata, listOf("output", "result", "stdout"))?.trim().orEmpty()
            InteractResult(
                success = (json["success"] as? Boolean) ?: output.isNotBlank(),
                output = output,
                liveViewUrl = firstString(json, data, metadata, listOf("liveViewUrl")),
                interactiveLiveViewUrl = firstString(json, data, metadata, listOf("interactiveLiveViewUrl")),
            )
        }

    /** Close the session. Best-effort — failures are swallowed; Firecrawl's TTL is the backstop. */
    suspend fun stop(apiKey: String, scrapeId: String) = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.firecrawl.dev/v2/scrape/$scrapeId/interact")
                .addHeader("Authorization", "Bearer $apiKey")
                .delete()
                .build()
            client.newCall(request).execute().use { /* ignore body/code */ }
        }
        Unit
    }

    private fun post(url: String, apiKey: String, body: Map<String, Any?>): Map<*, *> {
        val reqBody = anyAdapter.toJson(body).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqBody)
            .build()
        client.newCall(request).execute().use { response ->
            val str = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    401, 403 -> "Invalid Firecrawl API key — check Settings."
                    402 -> "Firecrawl credits exhausted — top up to keep browsing."
                    404 -> "The browser session expired. Start a new one."
                    429 -> "Firecrawl rate limit reached — try again shortly."
                    else -> "Firecrawl browser request failed (HTTP ${response.code})."
                }
                throw Exception(message)
            }
            return anyAdapter.fromJson(str) as? Map<*, *>
                ?: throw Exception("Firecrawl returned an unreadable response.")
        }
    }

    /** First non-blank string for any of [keys], checking top-level, data, then metadata. */
    private fun firstString(top: Map<*, *>, data: Map<*, *>?, metadata: Map<*, *>?, keys: List<String>): String? {
        for (k in keys) {
            (top[k] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
            (data?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
            (metadata?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}
