package com.echoflow.data.usage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl

/**
 * What a provider reports back per request. Only [Usd] counts toward the combined spend total;
 * the others are shown on the provider's own page in their own unit.
 */
enum class UsageUnit { Usd, Credits, Tokens, Requests }

/**
 * Every provider whose responses the ledger reads, and the unit its spend is shown in. OpenAI,
 * Anthropic and Gemini report tokens only; their dollars come from list prices ([ListPrices]).
 * Cerebras and Sarvam have no public per-model list to price from, so they stay in tokens.
 */
enum class UsageProvider(val label: String, val unit: UsageUnit, private val host: String) {
    OpenRouter("OpenRouter", UsageUnit.Usd, "openrouter.ai"),
    OpenAi("OpenAI", UsageUnit.Usd, "api.openai.com"),
    Claude("Anthropic", UsageUnit.Usd, "api.anthropic.com"),
    Gemini("Google Gemini", UsageUnit.Usd, "generativelanguage.googleapis.com"),
    Cerebras("Cerebras", UsageUnit.Tokens, "api.cerebras.ai"),
    XAi("xAI", UsageUnit.Usd, "api.x.ai"),
    Sarvam("Sarvam", UsageUnit.Tokens, "api.sarvam.ai"),
    Deepgram("Deepgram", UsageUnit.Usd, "api.deepgram.com"),
    Exa("Exa", UsageUnit.Usd, "api.exa.ai"),
    Parallel("Parallel", UsageUnit.Requests, "api.parallel.ai"),
    Firecrawl("Firecrawl", UsageUnit.Credits, "api.firecrawl.dev");

    companion object {
        fun forUrl(url: HttpUrl): UsageProvider? = entries.firstOrNull { it.host == url.host }
    }
}

/** The kind of call, read from the endpoint path. Shown in a record's detail. */
enum class UsageKind { Chat, Image, Video, Transcription, Search, Research, Browse, Other;
    companion object {
        fun forPath(path: String): UsageKind = when {
            path.endsWith("/chat/completions") || path.endsWith("/messages") ||
                path.endsWith("/responses") || path.contains(":streamGenerateContent") ||
                path.contains(":generateContent") -> Chat
            path.endsWith("/images") -> Image
            path.contains("/videos") -> Video
            path.contains("/audio/transcriptions") || path.endsWith("/listen") ||
                path.contains("speech-to-text") -> Transcription
            path.endsWith("/search") -> Search
            path.contains("/tasks/runs") || path.contains("deep-research") || path.contains("/agent") -> Research
            path.contains("/scrape") -> Browse
            else -> Other
        }
    }
}

/**
 * One request as its provider reported it. Every metric is nullable: null means the provider did
 * not report it, never zero. [keyHash] ties the row to the API key that paid for it, so replacing
 * a key starts a clean page instead of mixing two accounts.
 */
@Entity(
    tableName = "usage_records",
    indices = [Index(value = ["provider", "keyHash", "createdAt"])],
)
data class UsageRecord(
    @PrimaryKey val id: String,
    val provider: String,
    val keyHash: String,
    val createdAt: Long,
    val kind: String,
    val model: String? = null,
    val externalId: String? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val cachedTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val costUsd: Double? = null,
    val credits: Double? = null,
    val audioSeconds: Double? = null,
    val detail: String? = null,
)

/** One priced request, for charting spend over time. */
data class UsagePoint(val provider: String, val keyHash: String, val createdAt: Long, val costUsd: Double)

/** Per-provider sums for one key over a window. */
data class UsageTotal(
    val provider: String,
    val keyHash: String,
    val requests: Int,
    val costUsd: Double?,
    val credits: Double?,
    val inputTokens: Long?,
    val outputTokens: Long?,
)

@Dao
interface UsageDao {
    @Upsert suspend fun put(record: UsageRecord)

    @Query("SELECT * FROM usage_records WHERE id = :id")
    suspend fun find(id: String): UsageRecord?

    @Query(
        "SELECT * FROM usage_records WHERE provider = :provider AND keyHash = :keyHash " +
            "AND createdAt >= :since ORDER BY createdAt DESC"
    )
    fun observe(provider: String, keyHash: String, since: Long): Flow<List<UsageRecord>>

    @Query(
        "SELECT provider, keyHash, COUNT(*) AS requests, SUM(costUsd) AS costUsd, " +
            "SUM(credits) AS credits, SUM(inputTokens) AS inputTokens, SUM(outputTokens) AS outputTokens " +
            "FROM usage_records WHERE createdAt >= :since GROUP BY provider, keyHash"
    )
    fun observeTotals(since: Long): Flow<List<UsageTotal>>

    @Query(
        "SELECT provider, keyHash, createdAt, costUsd FROM usage_records " +
            "WHERE createdAt >= :since AND costUsd IS NOT NULL ORDER BY createdAt ASC"
    )
    fun observePoints(since: Long): Flow<List<UsagePoint>>

    /** List-priced rows stored before a price list was available. */
    @Query(
        "SELECT * FROM usage_records WHERE provider IN (:providers) AND costUsd IS NULL " +
            "AND inputTokens IS NOT NULL AND model IS NOT NULL LIMIT :limit"
    )
    suspend fun unpricedTokens(providers: List<String>, limit: Int): List<UsageRecord>

    /** Deepgram rows whose price has not been looked up yet, oldest first. */
    @Query(
        "SELECT * FROM usage_records WHERE provider = :provider AND keyHash = :keyHash " +
            "AND costUsd IS NULL AND externalId IS NOT NULL AND createdAt <= :before " +
            "ORDER BY createdAt ASC LIMIT :limit"
    )
    suspend fun unpriced(provider: String, keyHash: String, before: Long, limit: Int): List<UsageRecord>

    @Query("UPDATE usage_records SET costUsd = :costUsd WHERE id = :id")
    suspend fun setCost(id: String, costUsd: Double)
}
