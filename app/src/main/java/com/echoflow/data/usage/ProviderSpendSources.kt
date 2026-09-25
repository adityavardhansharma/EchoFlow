package com.echoflow.data.usage

import android.content.Context
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenRouter's own spend figures for one key, from `GET /api/v1/key`. These include everything the
 * key ever paid for, in any app, so they are the only source of spend from before tracking began.
 * OpenRouter's day, week (Mon–Sun) and month are UTC.
 */
data class OpenRouterKeySpend(
    val keyHash: String,
    val total: Double,
    val today: Double,
    val week: Double,
    val month: Double,
    val limit: Double?,
    val limitRemaining: Double?,
    val fetchedAt: Long,
)

object OpenRouterKeyUsage {
    private const val URL = "https://openrouter.ai/api/v1/key"
    private const val PREFS = "usage_ledger"
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Moshi.Builder().build().adapter(Any::class.java)

    /** Fetches fresh figures and caches them; on failure the cached figures stay as they were. */
    suspend fun refresh(context: Context, apiKey: String): Result<OpenRouterKeySpend> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(URL).header("Authorization", "Bearer ${apiKey.trim()}").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error(
                    if (response.code == 401) "OpenRouter rejected this key." else "OpenRouter returned HTTP ${response.code}."
                )
                val spend = parse(body, UsageKeys.hash(apiKey), System.currentTimeMillis())
                    ?: error("OpenRouter returned an unreadable response.")
                store(context, body, spend.fetchedAt, spend.keyHash)
                spend
            }
        }
    }

    fun cached(context: Context, apiKey: String): OpenRouterKeySpend? {
        val keyHash = UsageKeys.hash(apiKey)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val body = prefs.getString("openrouter_key_$keyHash", null) ?: return null
        return parse(body, keyHash, prefs.getLong("openrouter_key_at_$keyHash", 0L))
    }

    /** Writes a `/key` body for [keyHash] as if it had just been fetched. */
    fun store(context: Context, body: String, fetchedAt: Long, keyHash: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("openrouter_key_$keyHash", body)
            .putLong("openrouter_key_at_$keyHash", fetchedAt)
            .apply()
    }

    fun parse(body: String, keyHash: String, fetchedAt: Long): OpenRouterKeySpend? = runCatching {
        val data = (json.fromJson(body) as? Map<*, *>)?.get("data") as? Map<*, *> ?: return null
        fun number(key: String) = (data[key] as? Number)?.toDouble()
        OpenRouterKeySpend(
            keyHash = keyHash,
            total = number("usage") ?: return null,
            today = number("usage_daily") ?: 0.0,
            week = number("usage_weekly") ?: 0.0,
            month = number("usage_monthly") ?: 0.0,
            limit = number("limit"),
            limitRemaining = number("limit_remaining"),
            fetchedAt = fetchedAt,
        )
    }.getOrNull()
}

/**
 * Deepgram does not put a price on a transcription response, but reports the exact charge per
 * request on its management API. Rows the ledger stored with a request id get their price filled
 * in here, once Deepgram has settled it. Keys without usage access simply stay unpriced.
 */
object DeepgramCosts {
    private const val BASE = "https://api.deepgram.com/v1"
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val json = Moshi.Builder().build().adapter(Any::class.java)

    /** Returns how many rows were priced. */
    suspend fun fill(dao: UsageDao, apiKey: String, now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val keyHash = UsageKeys.hash(apiKey)
        // Deepgram needs a moment to settle a request's charge.
        val rows = dao.unpriced(UsageProvider.Deepgram.name, keyHash, before = now - 60_000L, limit = 50)
        if (rows.isEmpty()) return@withContext 0
        val projectId = get("$BASE/projects", apiKey)?.let(::projectId) ?: return@withContext 0
        var priced = 0
        for (row in rows) {
            val body = get("$BASE/projects/$projectId/requests/${row.externalId}", apiKey) ?: continue
            val usd = requestUsd(body) ?: continue
            dao.setCost(row.id, usd)
            priced++
        }
        priced
    }

    internal fun projectId(body: String): String? = runCatching {
        val projects = (json.fromJson(body) as? Map<*, *>)?.get("projects") as? List<*>
        (projects?.firstOrNull() as? Map<*, *>)?.get("project_id") as? String
    }.getOrNull()

    internal fun requestUsd(body: String): Double? = runCatching {
        val root = json.fromJson(body) as? Map<*, *>
        val request = root?.get("request") as? Map<*, *> ?: root
        val details = (request?.get("response") as? Map<*, *>)?.get("details") as? Map<*, *>
        (details?.get("usd") as? Number)?.toDouble()
    }.getOrNull()

    private fun get(url: String, apiKey: String): String? = runCatching {
        val request = Request.Builder().url(url).header("Authorization", "Token ${apiKey.trim()}").get().build()
        client.newCall(request).execute().use { if (it.isSuccessful) it.body?.string() else null }
    }.getOrNull()
}
