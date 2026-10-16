package com.echoflow.data.voice

import com.echoflow.data.ProviderHttpSupport
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** ElevenLabs answered with an HTTP error. [code] is the status; [message] is ready to show. */
class ElevenLabsApiException(val code: Int, message: String, val body: String = "") : Exception(message) {
    /** A scoped key that lacks one permission still authenticates; it just can't call this endpoint. */
    val missingPermission: Boolean get() = code == 401 && body.contains("missing_permission", ignoreCase = true)
    val unauthorized: Boolean get() = code == 401 && !missingPermission
}

/**
 * The ElevenLabs REST calls voice mode makes with the user's own key: their plan, the LLMs and
 * voices they can use, the one agent EchoFlow keeps on their account, a signed URL for each live
 * session, and the stored transcript afterwards. Each call is one request with no retry; the
 * caller decides what a failure means.
 */
class ElevenLabsVoiceApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = BASE_URL,
) {
    suspend fun account(apiKey: String): Result<VoiceAccount> = get(apiKey, "v1/user/subscription")
        .map { VoiceApiJson.account(it) ?: throw IllegalStateException("ElevenLabs sent an unreadable plan.") }

    suspend fun llms(apiKey: String): Result<List<VoiceLlm>> = get(apiKey, "v1/convai/llm/list")
        .map { VoiceApiJson.llms(it) }

    /** The user's own voices (premade, cloned, generated, saved from the library). */
    suspend fun myVoices(apiKey: String, search: String, pageToken: String?): Result<VoicePage> =
        get(
            apiKey, "v2/voices",
            "page_size" to "100",
            "search" to search.trim().ifBlank { null },
            "next_page_token" to pageToken,
            "include_total_count" to "false",
        ).map { VoiceApiJson.myVoices(it) }

    /** The public Voice Library: thousands of community voices, searchable by name or style. */
    suspend fun libraryVoices(apiKey: String, search: String, page: Int): Result<VoicePage> =
        get(
            apiKey, "v1/shared-voices",
            "page_size" to "30",
            "search" to search.trim().ifBlank { null },
            "page" to page.toString(),
        ).map { VoiceApiJson.libraryVoices(it, page) }

    /** Copies a library voice into My Voices and returns the id an agent can speak with. */
    suspend fun addLibraryVoice(apiKey: String, voice: VoiceOption): Result<String> {
        val ref = voice.library ?: return Result.success(voice.voiceId)
        return send(
            apiKey, "POST", "v1/voices/add/${ref.publicOwnerId}/${voice.voiceId}",
            mapOf("new_name" to voice.name),
        ).map { VoiceApiJson.string(it, "voice_id") ?: voice.voiceId }
    }

    suspend fun createAgent(apiKey: String, body: Map<String, Any?>): Result<String> =
        send(apiKey, "POST", "v1/convai/agents/create", body)
            .map { VoiceApiJson.string(it, "agent_id") ?: throw IllegalStateException("ElevenLabs created no agent.") }

    suspend fun updateAgent(apiKey: String, agentId: String, body: Map<String, Any?>): Result<Unit> =
        send(apiKey, "PATCH", "v1/convai/agents/$agentId", body).map { }

    /** A one-time WebSocket URL for a private agent; the key itself never goes on the socket. */
    suspend fun signedUrl(apiKey: String, agentId: String): Result<String> =
        get(apiKey, "v1/convai/conversation/get-signed-url", "agent_id" to agentId)
            .map { VoiceApiJson.string(it, "signed_url") ?: throw IllegalStateException("ElevenLabs sent no session URL.") }

    suspend fun conversation(apiKey: String, conversationId: String): Result<VoiceConversationRecord> =
        get(apiKey, "v1/convai/conversations/$conversationId")
            .map { VoiceApiJson.conversation(it) ?: throw IllegalStateException("ElevenLabs sent an unreadable transcript.") }

    private suspend fun get(apiKey: String, path: String, vararg query: Pair<String, String?>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "${baseUrl.trimEnd('/')}/$path".toHttpUrl().newBuilder().apply {
                    query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) }
                }.build()
                execute(Request.Builder().url(url).header("xi-api-key", apiKey.trim()).get().build())
            }
        }

    private suspend fun send(apiKey: String, method: String, path: String, body: Map<String, Any?>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = VoiceApiJson.write(body).toRequestBody(JSON)
                execute(
                    Request.Builder()
                        .url("${baseUrl.trimEnd('/')}/$path")
                        .header("xi-api-key", apiKey.trim())
                        .method(method, json)
                        .build(),
                )
            }
        }

    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw ElevenLabsApiException(
                response.code,
                ProviderHttpSupport.errorMessage("ElevenLabs", response.code, text),
                text,
            )
        }
        text
    }

    companion object {
        const val BASE_URL = "https://api.elevenlabs.io"
        private val JSON = "application/json".toMediaType()
    }
}

/** JSON in and out of the ElevenLabs API, kept pure so tests can read real response shapes. */
internal object VoiceApiJson {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val any = moshi.adapter(Any::class.java)

    /** Nulls are dropped, so an unset optional field is left out of the request entirely. */
    fun write(value: Map<String, Any?>): String = any.toJson(value)

    fun read(body: String): Map<*, *>? = runCatching { any.fromJson(body) as? Map<*, *> }.getOrNull()

    fun string(body: String, key: String): String? = (read(body)?.get(key) as? String)?.takeIf { it.isNotBlank() }

    fun account(body: String): VoiceAccount? {
        val map = read(body) ?: return null
        val tier = map["tier"] as? String ?: return null
        return VoiceAccount(
            tier = tier,
            status = map["status"] as? String,
            creditsUsed = (map["character_count"] as? Number)?.toLong() ?: 0L,
            creditLimit = (map["character_limit"] as? Number)?.toLong() ?: 0L,
            resetsAtUnix = (map["next_character_count_reset_unix"] as? Number)?.toLong(),
        )
    }

    fun llms(body: String): List<VoiceLlm> {
        val items = read(body)?.get("llms") as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val id = entry["llm"] as? String ?: return@mapNotNull null
            if (entry["is_checkpoint"] == true) return@mapNotNull null
            val deprecated = (entry["deprecation_info"] as? Map<*, *>)?.get("is_deprecated") == true
            VoiceLlm(id, deprecated)
        }
    }

    fun myVoices(body: String): VoicePage {
        val map = read(body) ?: return VoicePage(emptyList(), null)
        val voices = (map["voices"] as? List<*>).orEmpty().mapNotNull { item ->
            val v = item as? Map<*, *> ?: return@mapNotNull null
            val id = v["voice_id"] as? String ?: return@mapNotNull null
            val labels = v["labels"] as? Map<*, *>
            VoiceOption(
                voiceId = id,
                name = (v["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Untitled voice",
                category = v["category"] as? String,
                description = (v["description"] as? String)?.takeIf { it.isNotBlank() }
                    ?: labels?.get("description") as? String,
                accent = labels?.get("accent") as? String,
                gender = labels?.get("gender") as? String,
                age = labels?.get("age") as? String,
                useCase = (labels?.get("use_case") ?: labels?.get("use case")) as? String,
                language = labels?.get("language") as? String,
                previewUrl = v["preview_url"] as? String,
            )
        }
        val next = (map["next_page_token"] as? String)?.takeIf { map["has_more"] == true && it.isNotBlank() }
        return VoicePage(voices, next)
    }

    fun libraryVoices(body: String, page: Int): VoicePage {
        val map = read(body) ?: return VoicePage(emptyList(), null)
        val voices = (map["voices"] as? List<*>).orEmpty().mapNotNull { item ->
            val v = item as? Map<*, *> ?: return@mapNotNull null
            val id = v["voice_id"] as? String ?: return@mapNotNull null
            val owner = v["public_owner_id"] as? String ?: return@mapNotNull null
            VoiceOption(
                voiceId = id,
                name = (v["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Untitled voice",
                category = v["category"] as? String,
                description = (v["description"] as? String)?.takeIf { it.isNotBlank() } ?: v["descriptive"] as? String,
                accent = v["accent"] as? String,
                gender = v["gender"] as? String,
                age = v["age"] as? String,
                useCase = v["use_case"] as? String,
                language = v["language"] as? String,
                previewUrl = v["preview_url"] as? String,
                library = LibraryVoiceRef(owner, v["free_users_allowed"] == true),
            )
        }
        return VoicePage(voices, if (map["has_more"] == true) (page + 1).toString() else null)
    }

    fun conversation(body: String): VoiceConversationRecord? {
        val map = read(body) ?: return null
        val id = map["conversation_id"] as? String ?: return null
        val metadata = map["metadata"] as? Map<*, *>
        val charging = metadata?.get("charging") as? Map<*, *>
        val turns = (map["transcript"] as? List<*>).orEmpty().mapNotNull { item ->
            val t = item as? Map<*, *> ?: return@mapNotNull null
            val role = t["role"] as? String ?: return@mapNotNull null
            if (role != "user" && role != "agent") return@mapNotNull null
            val text = (t["message"] as? String)?.trim().orEmpty()
            if (text.isEmpty()) return@mapNotNull null
            VoiceTranscriptTurn(role == "user", text, (t["time_in_call_secs"] as? Number)?.toDouble() ?: 0.0)
        }
        return VoiceConversationRecord(
            conversationId = id,
            status = map["status"] as? String ?: "done",
            turns = turns,
            durationSeconds = (metadata?.get("call_duration_secs") as? Number)?.toDouble(),
            credits = (metadata?.get("cost") as? Number)?.toDouble(),
            costFiat = (metadata?.get("cost_fiat") as? Number)?.toDouble(),
            llmChargeUsd = (charging?.get("llm_price") as? Number)?.toDouble(),
        )
    }
}
