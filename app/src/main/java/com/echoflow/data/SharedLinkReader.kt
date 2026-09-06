package com.echoflow.data

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Read-only page fetch, explicitly selected in the share review. Never executes page scripts. */
class SharedLinkReader {
    companion object {
        fun link(text: String): String? = Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE).find(text)?.value
            ?.trimEnd('.', ',', ')', ']')?.takeIf { url -> url.toHttpUrlOrNull()?.let { it.isHttps && it.username.isBlank() && it.password.isBlank() } == true }
    }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val address = chain.connection()?.socket()?.inetAddress ?: error("Could not verify page address.")
            require(!address.isLoopbackAddress && !address.isSiteLocalAddress && !address.isLinkLocalAddress && !address.isAnyLocalAddress && !address.isMulticastAddress && (address.address[0].toInt() and 0xfe) != 0xfc) {
                "Only public HTTPS pages can be read here."
            }
            chain.proceed(chain.request())
        }.build()
    suspend fun read(input: SharedInput): SharedInput = withContext(Dispatchers.IO) {
        var url = requireNotNull(link(input.text)) { "No public HTTPS link was found." }
        repeat(5) {
            var next: String? = null
            val text = client.newCall(Request.Builder().url(url).header("Accept", "text/html,text/plain").build()).useCancellable { response ->
                if (response.code in 300..399) {
                    val resolved = response.header("Location")?.let { response.request.url.resolve(it) }
                    require(resolved != null && resolved.isHttps && resolved.username.isBlank() && resolved.password.isBlank()) { "The page redirected to an unsupported address." }
                    next = resolved.toString(); return@useCancellable null
                }
                require(response.isSuccessful) { "Could not read this page (HTTP ${response.code}). You can send the link without reading it." }
                val body = requireNotNull(response.body)
                val mime = body.contentType()?.let { "${it.type}/${it.subtype}" }
                require(mime in setOf("text/html", "text/plain", "application/xhtml+xml")) { "This link is not a readable web page. Share the file instead." }
                val raw = body.charStream().use { reader ->
                    val buffer = CharArray(200_001)
                    var length = 0
                    while (length < buffer.size) {
                        val n = reader.read(buffer, length, buffer.size - length)
                        if (n < 0) break
                        length += n
                    }
                    String(buffer, 0, length)
                }
                val readable = if (mime == "text/plain") raw else HtmlCompat.fromHtml(raw.replace(Regex("<(script|style|noscript)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), ""), HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                require(readable.isNotBlank()) { "This page has no readable text. It may require sign-in or JavaScript." }
                readable.take(8_000) + if (readable.length > 8_000 || raw.length > 200_000) "\n[Page excerpt truncated.]" else ""
            }
            if (text != null) return@withContext input.copy(text = input.text + "\n\nPage read from $url (scripts were not run):\n$text")
            url = requireNotNull(next)
        }
        error("The page redirected too many times.")
    }
}
