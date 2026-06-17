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
        val scrapeId = firstString(json, data, listOf("scrapeId", "id", "sessionId"))
            ?: throw Exception("Firecrawl did not return a browser session id.")
        val metadata = data?.get("metadata") as? Map<*, *>
        StartResult(
            scrapeId = scrapeId,
            liveViewUrl = firstString(json, data, listOf("liveViewUrl")),
            interactiveLiveViewUrl = firstString(json, data, listOf("interactiveLiveViewUrl")),
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
            val output = firstString(json, data, listOf("output", "result", "stdout"))?.trim().orEmpty()
            InteractResult(
                success = (json["success"] as? Boolean) ?: output.isNotBlank(),
                output = output,
                liveViewUrl = firstString(json, data, listOf("liveViewUrl")),
                interactiveLiveViewUrl = firstString(json, data, listOf("interactiveLiveViewUrl")),
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

    /** First non-blank string for any of [keys], checking the top-level then the nested data map. */
    private fun firstString(top: Map<*, *>, data: Map<*, *>?, keys: List<String>): String? {
        for (k in keys) {
            (top[k] as? String)?.takeIf { it.isNotBlank() }?.let { return it }
            (data?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }
}

/**
 * Pure, testable helpers for Browser Flow's website resolution and safety gating. No network —
 * the manager owns the actual web search and Firecrawl calls.
 */
object BrowserResolver {

    private val URL_REGEX = Regex("""\b((https?://)?([a-z0-9-]+\.)+[a-z]{2,}(/[^\s]*)?)""", RegexOption.IGNORE_CASE)

    /** Sites we treat as sensitive — confirm with the user before opening. */
    private val SENSITIVE_HINTS = listOf(
        "bank", "icici", "hdfc", "sbi", "axis", "kotak", "paypal", "stripe", "razorpay",
        "paytm", "phonepe", "gov", "irctc", "uidai", "incometax", "nsdl", "health", "hospital",
        "insurance", "aadhaar", "passport", "wallet", "crypto", "binance", "coinbase",
    )

    /** Domains we avoid auto-picking during name resolution unless the user asked for them. */
    private val DISPREFERRED_HOSTS = listOf(
        "wikipedia.org", "facebook.com", "instagram.com", "twitter.com", "x.com",
        "youtube.com", "play.google.com", "apps.apple.com", "linkedin.com", "pinterest.com",
        "reddit.com", "tiktok.com", "amazon.com/dp", "quora.com",
    )

    enum class RiskKind {
        /** Payment / checkout / booking / irreversible account change — hard handoff, never automate. */
        HARD,

        /** Sending a message/email/post — draft then confirm before it leaves. */
        SEND,
    }

    private val HARD_KEYWORDS = listOf(
        "pay", "payment", "checkout", "check out", "buy now", "purchase", "place order",
        "place the order", "complete the order", "book ticket", "book the", "booking", "reserve",
        "confirm purchase", "confirm order", "delete account", "close account", "transfer money",
        "wire", "subscribe", "renew", "add card", "card number", "cvv",
    )

    private val SEND_KEYWORDS = listOf(
        "send email", "send an email", "send mail", "send message", "send a message",
        "send dm", "reply to", "post a", "post this", "submit the form", "submit form",
        "send it", "email them", "message them",
    )

    private val BLOCKER_HINTS = mapOf(
        "login" to "A login is required. Open the live browser to sign in yourself, then tell me to continue.",
        "log in" to "A login is required. Open the live browser to sign in yourself, then tell me to continue.",
        "sign in" to "A sign-in is required. Open the live browser to sign in yourself, then tell me to continue.",
        "captcha" to "A CAPTCHA appeared. Open the live browser to solve it, then tell me to continue.",
        "verify you are human" to "A human-verification step appeared. Open the live browser to complete it, then tell me to continue.",
        "payment" to "This step involves payment. Open the live browser and complete it yourself — I won't automate payments.",
        "enter your card" to "This step asks for card details. Open the live browser and complete it yourself.",
        "otp" to "A one-time password is needed. Open the live browser to enter it, then tell me to continue.",
    )

    fun extractUrl(text: String): String? {
        val match = URL_REGEX.find(text)?.value?.trim()?.trimEnd('.', ',', ')') ?: return null
        // Ignore bare "x.y" that's really a sentence fragment (require a known-ish TLD length already in regex).
        return if (match.contains('.')) normalizeUrl(match) else null
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed
        else "https://$trimmed"
    }

    fun domainOf(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return url.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.")
            .substringBefore('/')
            .substringBefore('?')
    }

    fun isSensitive(url: String?): Boolean {
        val host = domainOf(url).lowercase()
        if (host.isBlank()) return false
        return SENSITIVE_HINTS.any { host.contains(it) }
    }

    fun isDispreferred(url: String?): Boolean {
        val u = (url ?: "").lowercase()
        return DISPREFERRED_HOSTS.any { u.contains(it) }
    }

    /** Classify an instruction's risk, or null if it's ordinary navigation/reading. */
    fun classifyInstruction(text: String): RiskKind? {
        val t = text.lowercase()
        if (SEND_KEYWORDS.any { t.contains(it) }) return RiskKind.SEND
        if (HARD_KEYWORDS.any { t.contains(it) }) return RiskKind.HARD
        return null
    }

    /** If the agent's output indicates a blocker (login/CAPTCHA/payment), return a handoff message. */
    fun detectBlocker(output: String): String? {
        val t = output.lowercase()
        for ((hint, message) in BLOCKER_HINTS) {
            if (t.contains(hint)) return message
        }
        return null
    }

    /** Rank web-search hits into resolution candidates, official-looking domains first. */
    fun rankCandidates(sources: List<SearchSource>, query: String): List<BrowserCandidate> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.length > 2 }
        return sources
            .asSequence()
            .filter { it.url.startsWith("http") }
            .map { src ->
                val domain = domainOf(src.url)
                var score = 0
                if (!isDispreferred(src.url)) score += 3
                // A homepage (no deep path) is more likely the official site.
                if (src.url.trimEnd('/').count { it == '/' } <= 2) score += 2
                if (tokens.any { domain.contains(it) }) score += 4
                if (domain.endsWith(".com") || domain.endsWith(".in") || domain.endsWith(".org") || domain.endsWith(".net")) score += 1
                BrowserCandidate(title = src.title.ifBlank { domain }, url = src.url, domain = domain) to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.domain }
            .take(4)
            .toList()
    }
}
