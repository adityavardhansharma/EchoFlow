package com.echoflow.data.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MemoryApiException(val code: Int) : Exception(when (code) {
    401 -> "Your Supermemory key was not accepted. Reconnect with a valid key."
    402 -> "Supermemory credits are exhausted. Your chats still work."
    403 -> "This key does not have permission for this action."
    429 -> "Supermemory is busy. Try again shortly."
    else -> "Supermemory could not complete this request ($code)."
})

data class RemoteMemory(val id: String, val text: String, val updated: String, val history: List<String>, val sources: List<String>, val forgotten: Boolean = false)
data class MemoryPage(val entries: List<RemoteMemory>, val hasMore: Boolean)
data class MemoryProfile(val stable: List<String>, val recent: List<String>)
data class MemoryBilling(val plan: String, val used: Double?, val limit: Double?, val reset: String?)

class SupermemoryClient(
    private val key: String,
    private val space: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://api.supermemory.ai",
) {
    suspend fun request(path: String, method: String = "POST", body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $key")
            .method(method, if (method == "GET") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())).build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (!it.isSuccessful) throw MemoryApiException(it.code)
                        val raw = it.body?.string().orEmpty()
                        if (raw.isBlank()) JSONObject() else JSONObject(raw)
                    } }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }
    private fun scoped() = JSONObject().put("containerTag", space)
    suspend fun profile(): MemoryProfile {
        val profile = request("/v4/profile", body = scoped()).optJSONObject("profile") ?: JSONObject()
        return MemoryProfile(profile.optJSONArray("static").strings(), profile.optJSONArray("dynamic").strings())
    }
    suspend fun search(query: String): List<RemoteMemory> = entries(request("/v4/search", body = scoped()
        .put("q", query.take(2000)).put("searchMode", "memories").put("limit", 8).put("threshold", 0.65)
        .put("include", JSONObject().put("documents", true))).optJSONArray("results"))
    suspend fun list(page: Int = 1): MemoryPage {
        val result = request("/v4/memories/list", body = JSONObject().put("containerTags", JSONArray().put(space))
            .put("page", page).put("limit", 30).put("sort", "updatedAt").put("order", "desc"))
        val pagination = result.optJSONObject("pagination")
        return MemoryPage(entries(result.optJSONArray("memoryEntries")).filterNot { it.forgotten }, page < (pagination?.optInt("totalPages", page) ?: page))
    }
    suspend fun add(text: String, permanent: Boolean = false): JSONObject {
        require(MemoryPrivacy.redact(text) == text) { "Remove credentials before saving a memory." }
        val response = request("/v4/memories", body = scoped()
        .put("memories", JSONArray().put(JSONObject().put("content", text).put("isStatic", permanent)
            .put("metadata", JSONObject().put("application", "echoflow").put("source", "explicit")))))
        check((response.optJSONArray("memories")?.length() ?: 0) > 0) { "Supermemory did not confirm that the memory was created. Refresh before trying again." }
        return response
    }
    suspend fun edit(id: String, text: String): JSONObject {
        require(MemoryPrivacy.redact(text) == text) { "Remove credentials before saving a memory." }
        return request("/v4/memories", "PATCH", scoped().put("id", id).put("newContent", text))
    }
    suspend fun forget(id: String) = request("/v4/memories", "DELETE", scoped().put("id", id))
    suspend fun reviewQueue(): List<RemoteMemory> = entries(request("/v3/container-tags/$space/inferred", "GET").optJSONArray("memories"))
    suspend fun review(id: String, approve: Boolean) = request("/v3/container-tags/$space/inferred/$id/review", body = JSONObject().put("action", if (approve) "approve" else "decline"))
    suspend fun document(id: String) = request("/v3/documents/$id", "GET")
    suspend fun ingest(chatId: String, transcript: String, date: String): String = request("/v3/documents", body = scoped()
        .put("content", transcript).put("customId", "echoflow-${MemoryLearning.revision(space).take(12)}-$chatId").put("dreaming", "dynamic").put("documentDate", date)
        .put("entityContext", "Conversations between one EchoFlow user and an assistant. Learn personal facts, recurring interests, preferences, ongoing projects and confirmed decisions. Repeated cricket questions can indicate an interest in cricket; individual scores are disposable. Treat assistant suggestions as unconfirmed until the user accepts them. Do not infer identity from a single general question. Never retain credentials.")
        .put("metadata", JSONObject().put("application", "echoflow").put("chatId", chatId))).getString("id")
    suspend fun billing(): MemoryBilling {
        val summary = request("/v3/auth/billing", "GET")
        // Shapes vary by billing generation. Unknown amounts stay unknown; never manufacture credits.
        val usage = try { request("/v3/auth/billing/usage", "GET") }
            catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        val credits = findCredits(usage) ?: summary.optJSONObject("credits")
        return MemoryBilling(summary.optString("plan", "unknown").removePrefix("api_"), credits?.numberOrNull("used"),
            credits?.numberOrNull("limit"), summary.optString("resetDate").takeIf { it.isNotBlank() })
    }
    companion object {
        /** Only USD credit fields can populate the credit balance; token counts aren't money. */
        internal fun findCredits(value: Any?): JSONObject? = when (value) {
            is JSONObject -> {
                if (value.optString("featureId", value.optString("id", value.optString("feature_id"))) == "usd_credits") value
                else value.optJSONObject("usd_credits") ?: value.keys().asSequence().mapNotNull { findCredits(value.opt(it)) }.firstOrNull()
            }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { findCredits(value.opt(it)) }
            else -> null
        }
        fun entries(array: JSONArray?): List<RemoteMemory> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val item = array?.optJSONObject(i) ?: return@mapNotNull null
            val text = item.optString("memory", item.optString("content"))
            val id = item.optString("id")
            if (id.isBlank() || text.isBlank()) return@mapNotNull null
            val history = item.optJSONArray("history")
            RemoteMemory(id, text, item.optString("updatedAt", item.optString("createdAt")),
                (0 until (history?.length() ?: 0)).mapNotNull { history?.optJSONObject(it)?.optString("memory") },
                item.optJSONArray("documentIds").strings().ifEmpty {
                    val documents = item.optJSONArray("documents")
                    (0 until (documents?.length() ?: 0)).mapNotNull { documents?.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                }, item.optBoolean("isForgotten"))
        }
    }
}
private fun JSONArray?.strings(): List<String> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optString(it)?.takeIf(String::isNotBlank) }
private fun JSONObject.numberOrNull(key: String): Double? = (opt(key) as? Number)?.toDouble()
