package com.echoflow.data.usage

import android.content.Context
import android.util.Log
import com.echoflow.data.AppDatabase
import com.squareup.moshi.JsonReader
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import okio.Buffer

/** Finds the API key on an outgoing request and reduces it to a stable, non-secret id. */
object UsageKeys {
    fun from(request: Request): String? {
        val auth = request.header("Authorization")?.trim()
        val bearer = auth?.substringAfter(' ', missingDelimiterValue = "")?.trim()
            ?.takeIf { auth.startsWith("Bearer ", ignoreCase = true) || auth.startsWith("Token ", ignoreCase = true) }
        return listOf(
            bearer,
            request.header("x-api-key"),
            request.header("x-goog-api-key"),
            request.header("api-subscription-key"),
            request.url.queryParameter("key"),
        ).firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
    }

    /** First 16 hex chars of SHA-256: enough to tell keys apart, useless for recovering one. */
    fun hash(key: String): String = MessageDigest.getInstance("SHA-256")
        .digest(key.trim().toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)
}

/**
 * The on-device spend ledger. [UsageInterceptor] hands it finished responses; it parses them off
 * the caller's thread and stores one [UsageRecord] per request. Rows never leave the phone.
 */
object UsageLedger {
    private const val TAG = "UsageLedger"
    private const val PREFS = "usage_ledger"
    private const val TRACKING_STARTED = "tracking_started_at"

    @Volatile private var dao: UsageDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun install(context: Context) {
        if (dao != null) return
        val app = context.applicationContext
        dao = AppDatabase.getDatabase(app).usageDao()
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(TRACKING_STARTED)) {
            prefs.edit().putLong(TRACKING_STARTED, System.currentTimeMillis()).apply()
        }
    }

    /** When this install began itemising requests; earlier spend exists only as provider totals. */
    fun trackingStartedAt(context: Context): Long? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(TRACKING_STARTED, 0L).takeIf { it > 0L }

    fun submit(capture: UsageCapture) {
        val target = dao ?: return
        scope.launch {
            try {
                write(target, capture)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record ${capture.provider} usage", e)
            }
        }
    }

    /** Parses [capture] and upserts its row. Returns the stored record, or null if nothing was reported. */
    suspend fun write(dao: UsageDao, capture: UsageCapture): UsageRecord? {
        val accumulator = UsageAccumulator(capture.provider)
        capture.documents.forEach { doc -> UsageJson.read(doc)?.let(accumulator::accept) }
        val reading = accumulator.reading()
        if (!reading.hasUsage) return null

        val externalId = reading.externalId
        val id = "${capture.provider.name}:${externalId ?: UUID.randomUUID()}"
        // A polled job reports again on every poll; keep the time it was first seen.
        val createdAt = dao.find(id)?.createdAt ?: capture.finishedAt
        val record = UsageRecord(
            id = id,
            provider = capture.provider.name,
            keyHash = capture.keyHash,
            createdAt = createdAt,
            kind = capture.kind.name,
            model = reading.model ?: requestModel(capture.request),
            externalId = externalId,
            inputTokens = reading.inputTokens,
            outputTokens = reading.outputTokens,
            cachedTokens = reading.cachedTokens,
            reasoningTokens = reading.reasoningTokens,
            costUsd = reading.costUsd,
            credits = reading.credits,
            audioSeconds = reading.audioSeconds,
            detail = reading.detail,
        )
        dao.put(record)
        return record
    }

    /**
     * The model the request asked for, used only when the response did not name one. Gemini puts
     * it in the path, Deepgram in the query; everyone else in the JSON body's top-level `model`.
     */
    internal fun requestModel(request: Request): String? {
        val segments = request.url.pathSegments
        segments.firstOrNull { it.contains(":") && segments.getOrNull(segments.indexOf(it) - 1) == "models" }
            ?.let { return it.substringBefore(':') }
        request.url.queryParameter("model")?.takeIf { it.isNotBlank() }?.let { return it }
        val body = request.body ?: return null
        if (body.isOneShot() || body.contentType()?.subtype?.contains("json") != true) return null
        if (body.contentLength() > UsageInterceptor.MAX_JSON_BYTES) return null
        return runCatching {
            val buffer = Buffer().also(body::writeTo)
            val reader = JsonReader.of(buffer)
            reader.beginObject()
            var model: String? = null
            while (reader.hasNext() && model == null) {
                if (reader.nextName() == "model" && reader.peek() == JsonReader.Token.STRING) {
                    model = reader.nextString()
                } else reader.skipValue()
            }
            model?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
