package com.echoflow.data.usage

import android.content.Context
import com.squareup.moshi.Moshi
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Per-token list prices for one model, in dollars. */
data class ModelPrice(val prompt: Double, val completion: Double, val cacheRead: Double?)

/**
 * OpenAI, Anthropic and Google return token counts but no price, so their requests are priced
 * here from each model's published per-token rates. The rates come from OpenRouter's public model
 * list, which passes those labs' list prices through unchanged, and are cached on the phone for a
 * day. A model the list doesn't know stays unpriced rather than guessed.
 */
object ListPrices {
    private const val URL = "https://openrouter.ai/api/v1/models"
    private const val FILE = "usage_list_prices.json"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    private val json = Moshi.Builder().build().adapter(Any::class.java)
    private val lock = Mutex()
    @Volatile private var prices: Map<String, ModelPrice>? = null

    /** Providers whose requests are priced from list rates. */
    val priced = setOf(UsageProvider.OpenAi, UsageProvider.Claude, UsageProvider.Gemini)

    /** Loads the cached list, fetching a fresh one when it's missing or a day old. */
    suspend fun load(context: Context): Map<String, ModelPrice> = lock.withLock {
        withContext(Dispatchers.IO) {
            val file = File(context.applicationContext.filesDir, FILE)
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MS
            prices?.takeIf { fresh }?.let { return@withContext it }
            if (!fresh) {
                runCatching {
                    client.newCall(Request.Builder().url(URL).get().build()).execute().use { response ->
                        val body = response.body?.string()
                        if (response.isSuccessful && body != null && parse(body).isNotEmpty()) file.writeText(body)
                    }
                }
            }
            val loaded = if (file.exists()) runCatching { parse(file.readText()) }.getOrDefault(emptyMap()) else emptyMap()
            prices = loaded
            loaded
        }
    }

    fun parse(body: String): Map<String, ModelPrice> {
        val data = (json.fromJson(body) as? Map<*, *>)?.get("data") as? List<*> ?: return emptyMap()
        return data.mapNotNull { raw ->
            val model = raw as? Map<*, *> ?: return@mapNotNull null
            val id = model["id"] as? String ?: return@mapNotNull null
            val pricing = model["pricing"] as? Map<*, *> ?: return@mapNotNull null
            fun rate(key: String) = (pricing[key] as? String)?.toDoubleOrNull()
            val prompt = rate("prompt") ?: return@mapNotNull null
            val completion = rate("completion") ?: return@mapNotNull null
            if (prompt < 0 || completion < 0) return@mapNotNull null
            id to ModelPrice(prompt, completion, rate("input_cache_read"))
        }.toMap()
    }

    /** The list-price entry for a direct model id, trying the id forms each lab uses. */
    fun find(prices: Map<String, ModelPrice>, provider: UsageProvider, model: String): ModelPrice? {
        val vendor = when (provider) {
            UsageProvider.OpenAi -> "openai"
            UsageProvider.Claude -> "anthropic"
            UsageProvider.Gemini -> "google"
            else -> return null
        }
        val id = model.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        val undated = id.replace(Regex("-\\d{4}-\\d{2}-\\d{2}$"), "").replace(Regex("-\\d{8}$"), "")
        // Anthropic writes versions with hyphens (claude-opus-4-5); OpenRouter with a dot (4.5).
        val dotted = undated.replace(Regex("(\\d)-(\\d)(?=$|-)"), "$1.$2")
        val latest = undated.removeSuffix("-latest")
        return listOf(id, undated, dotted, latest).distinct().firstNotNullOfOrNull { prices["$vendor/$it"] }
    }

    /** Dollars for one request: uncached input, cached input and output at their own rates. */
    fun cost(price: ModelPrice, inputTokens: Long, outputTokens: Long, cachedTokens: Long?): Double {
        val cached = (cachedTokens ?: 0L).coerceIn(0L, inputTokens)
        return (inputTokens - cached) * price.prompt +
            cached * (price.cacheRead ?: price.prompt) +
            outputTokens * price.completion
    }

    /** Prices rows stored before a list was available. Returns how many were priced. */
    suspend fun reprice(context: Context, dao: UsageDao): Int {
        val list = load(context)
        if (list.isEmpty()) return 0
        var count = 0
        for (row in dao.unpricedTokens(priced.map { it.name }, limit = 500)) {
            val provider = runCatching { UsageProvider.valueOf(row.provider) }.getOrNull() ?: continue
            val price = row.model?.let { find(list, provider, it) } ?: continue
            dao.setCost(row.id, cost(price, row.inputTokens ?: 0L, row.outputTokens ?: 0L, row.cachedTokens))
            count++
        }
        return count
    }
}
