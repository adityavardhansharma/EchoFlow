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
 *  - [snapshot] / [executeApproved] use app-owned code; model text is never executed.
 *  - [stop]          DELETE /v2/scrape/{scrapeId}/interact → closes the session (billing/cleanup)
 *
 * Sessions are deliberately ephemeral: no `profile` is ever sent, so Firecrawl keeps no cookies
 * or login state. Interact is request/response (no token streaming); a prompt call can take up to
 * Firecrawl's 300s timeout, hence the long read/call timeouts.
 */
open class FirecrawlBrowserService {

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
    open suspend fun startSession(apiKey: String, url: String): StartResult = withContext(Dispatchers.IO) {
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

    /** No free-form AI prompt ever reaches the browser executor. */
    open suspend fun snapshot(apiKey: String, scrapeId: String): Pair<BrowserSnapshot, InteractResult> {
        val result = executeCode(apiKey, scrapeId, BrowserActions.snapshotCode())
        return BrowserActions.parseSnapshot(result.output) to result
    }

    open suspend fun executeApproved(apiKey: String, scrapeId: String, action: PendingBrowserAction): InteractResult {
        require(System.currentTimeMillis() <= action.expiresAt) { "Approval expired. Request the action again." }
        return executeCode(apiKey, scrapeId, BrowserActions.executionCode(action))
    }

    private suspend fun executeCode(apiKey: String, scrapeId: String, code: String): InteractResult =
        withContext(Dispatchers.IO) {
            require(code.length <= 100_000) { "This page is too large to review safely. Use the live browser." }
            val json = post(
                url = "https://api.firecrawl.dev/v2/scrape/$scrapeId/interact",
                apiKey = apiKey,
                body = mapOf("code" to code, "language" to "node", "timeout" to 45),
            )
            val data = json["data"] as? Map<*, *>
            val metadata = data?.get("metadata") as? Map<*, *>
            val success = (json["success"] as? Boolean) ?: (data?.get("success") as? Boolean) ?: false
            val exit = (json["exitCode"] as? Number) ?: (data?.get("exitCode") as? Number)
            require(success && (exit == null || exit.toInt() == 0) && json["killed"] != true && data?.get("killed") != true) {
                "The browser action did not complete. Inspect the page before trying again."
            }
            InteractResult(
                success = true,
                output = firstString(json, data, metadata, listOf("stdout", "output", "result")).orEmpty().trim(),
                liveViewUrl = firstString(json, data, metadata, listOf("liveViewUrl")),
                interactiveLiveViewUrl = firstString(json, data, metadata, listOf("interactiveLiveViewUrl")),
            )
        }

    /** Close the session. Best-effort — failures are swallowed; Firecrawl's TTL is the backstop. */
    open suspend fun stop(apiKey: String, scrapeId: String) = withContext(Dispatchers.IO) {
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

    private suspend fun post(url: String, apiKey: String, body: Map<String, Any?>): Map<*, *> {
        val reqBody = anyAdapter.toJson(body).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(reqBody)
            .build()
        return client.newCall(request).useCancellable { response ->
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
            anyAdapter.fromJson(str) as? Map<*, *>
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
