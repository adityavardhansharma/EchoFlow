package com.echoflow.weave.runtime

import com.echoflow.data.HttpClients
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Block-safe tools (05 §8.7). Results are data: strings are never parsed as Weave.
 */
object WeaveTools {

    private fun str(args: WValue, key: String): String = ((args as? WRec)?.get(key) as? WStr)?.v ?: ((args as? WStr)?.v ?: "")

    /** `web.search` through the user's configured search provider. */
    fun webSearch(search: suspend (String) -> List<Triple<String, String, String?>>?): WeaveTool = object : WeaveTool {
        override val name = "web.search"
        override fun consentText(args: WValue) = "search the web for \"${str(args, "query").take(80)}\""
        override suspend fun run(args: WValue): WValue {
            val results = search(str(args, "query")) ?: error("Web search isn't set up")
            return WList(results.take(8).map { (t, u, s) -> WRec.of("title" to WStr(t), "url" to WStr(u), "snippet" to WStr(s ?: "")) })
        }
    }

    private val client by lazy { HttpClients.builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

    /** `web.read`: an https page as plain text (first 20,000 characters). */
    val webRead: WeaveTool = object : WeaveTool {
        override val name = "web.read"
        override fun consentText(args: WValue) = "read ${str(args, "url").take(80)}"
        override suspend fun run(args: WValue): WValue = withContext(Dispatchers.IO) {
            val url = str(args, "url")
            require(url.startsWith("https://")) { "Only https pages" }
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val html = r.body?.string().orEmpty().take(2_000_000)
                val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.trim().orEmpty()
                val text = html.replace(Regex("(?is)<(script|style|noscript)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&nbsp;"), " ").replace(Regex("&amp;"), "&").replace(Regex("\\s+"), " ").trim().take(20_000)
                WRec.of("title" to WStr(title), "text" to WStr(text), "url" to WStr(url))
            }
        }
    }
}
