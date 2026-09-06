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
        fun matches(word: String) = Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(word) + "(?![\\p{L}\\p{N}_])").containsMatchIn(t)
        if (HARD_KEYWORDS.any(::matches)) return RiskKind.HARD
        if (SEND_KEYWORDS.any(::matches)) return RiskKind.SEND
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
