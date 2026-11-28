package com.echoflow.weave.runtime

import com.echoflow.data.HttpClients
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Block-safe tools (05 §8.7). Results are data: strings are never parsed as Weave.
 */
object WeaveTools {

    private fun str(args: WValue, vararg keys: String): String {
        val rec = args as? WRec ?: return (args as? WStr)?.v ?: ""
        return keys.firstNotNullOfOrNull { (rec[it] as? WStr)?.v } ?: ""
    }

    /** 01 §6.5 writes `{q: …}`; `query` is accepted too. */
    private fun searchText(args: WValue) = str(args, "q", "query")

    /** `web.search` through the user's configured search provider. */
    fun webSearch(search: suspend (String) -> List<Triple<String, String, String?>>?): WeaveTool = object : WeaveTool {
        override val name = "web.search"
        override fun consentText(args: WValue) = "search the web for \"${searchText(args).take(80)}\""
        override suspend fun run(args: WValue): WValue {
            val results = search(searchText(args)) ?: error("Web search isn't set up")
            // the documented shape is `{results: [{title, url, snippet}]}` (01 §6.5)
            return WRec.of("results" to WList(results.take(8).map { (t, u, s) -> WRec.of("title" to WStr(t), "url" to WStr(u), "snippet" to WStr(s ?: "")) }))
        }
    }

    /**
     * Resolves through the system DNS but refuses any name that resolves to a loopback, private,
     * link-local or otherwise local address. OkHttp asks this for every connection, redirects
     * included, so a public name (or a redirect) can't lead a block to a private host.
     */
    private object PublicOnlyDns : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val all = okhttp3.Dns.SYSTEM.lookup(hostname)
            if (all.any { isPrivateAddress(it) }) throw java.net.UnknownHostException("Only public pages")
            return all
        }
    }

    fun isPrivateAddress(a: java.net.InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isAnyLocalAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        return when (b.size) {
            4 -> (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64 // 100.64/10 (CGNAT)
            16 -> (b[0].toInt() and 0xfe) == 0xfc // fc00::/7 (unique local)
            else -> false
        }
    }

    private val client by lazy {
        HttpClients.builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .dns(PublicOnlyDns).followSslRedirects(false).build()
    }

    /** `web.read`: an https page as plain text (first 20,000 characters). */
    val webRead: WeaveTool = object : WeaveTool {
        override val name = "web.read"
        override fun consentText(args: WValue) = "read ${str(args, "url").take(80)}"
        override suspend fun run(args: WValue): WValue = withContext(Dispatchers.IO) {
            val url = str(args, "url")
            require(url.startsWith("https://")) { "Only https pages" }
            val host = url.toHttpUrlOrNull()?.host ?: error("Not a valid address")
            require(!isPrivateHost(host)) { "Only public pages" }
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                // read at most 2 MB from the stream, so a huge page can't exhaust memory
                val html = r.body?.let { body ->
                    val buf = okio.Buffer()
                    val src = body.source()
                    while (buf.size < MAX_BYTES && src.read(buf, MAX_BYTES - buf.size) != -1L) Unit
                    buf.readString(body.contentType()?.charset() ?: Charsets.UTF_8)
                }.orEmpty()
                val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.trim().orEmpty()
                val text = html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&nbsp;"), " ").replace(Regex("&amp;"), "&").replace(Regex("\\s+"), " ").trim().take(20_000)
                WRec.of("title" to WStr(title), "text" to WStr(text), "url" to WStr(url))
            }
        }
    }

    private const val MAX_BYTES = 2_000_000L

    /** Loopback, private, link-local and `.local` hosts are never read by a block. */
    fun isPrivateHost(host: String): Boolean {
        val h = host.lowercase().trim('[', ']')
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true
        val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").find(h)?.groupValues?.drop(1)?.map { it.toInt() }
        if (v4 != null) {
            val (a, b) = v4
            return a == 10 || a == 127 || a == 0 || (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 100 && b in 64..127)
        }
        if (h.contains(':')) return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80") || h.startsWith("::ffff:")
        return false
    }

    /**
     * 01 §6.5: `web.read` only opens a URL that appears in the block's source or in an earlier
     * result. [known] is that text (source, query results and state, as the block holds them).
     */
    fun readAllowed(args: WValue, known: String): Boolean {
        val url = str(args, "url")
        return url.isNotEmpty() && known.contains(url)
    }
}
