package com.echoflow.data.jev

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class JevException(message: String, val code: Int = -1) : Exception(message)

/** Raw probabilities from one System One evaluation (before thresholding). */
data class JevScores(
    val modelVersion: String,
    val needsMemory: Double,
    val needsSave: Double,
    val needsWeb: Double,
    val routeChoice: String,
    val routeProbabilities: Map<String, Double>,
    val routeConfidence: Double,
)

/**
 * Minimal HTTP client for TypeSafe's System One endpoint. No SDK dependency —
 * one POST, structured JSON back, output tokens unmetered.
 */
class JevClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://api.typesafe.ai",
    private val model: String = JevThresholds.MODEL,
) {
    suspend fun classify(
        apiKey: String,
        message: String,
        previousAssistant: String? = null,
        memoryOn: Boolean = true,
        searchOn: Boolean = true,
    ): JevScores = withContext(Dispatchers.IO) {
        val state = JSONObject()
            .put("message", message.take(6000))
            .put("memory_on", memoryOn)
            .put("search_on", searchOn)
        if (!previousAssistant.isNullOrBlank()) {
            state.put("previous_assistant", previousAssistant.take(2000))
        }
        val body = JSONObject()
            .put("model", model)
            .put("state", state)
            .put("questions", JSONObject()
                .put("needs_memory", JSONObject()
                    .put("type", "noul")
                    .put("instructions", "Could personal history about the user change what should be included, excluded, ranked, recommended, or asked next for `message`?"))
                .put("needs_save", JSONObject()
                    .put("type", "noul")
                    .put("instructions", "Does `message` explicitly ask to remember something or state a durable personal fact useful in future conversations?"))
                .put("needs_web", JSONObject()
                    .put("type", "noul")
                    .put("instructions", "Could the correct answer to `message` have changed since training — a name, date, version, price, score, record, title-holder, or the current/latest of anything?"))
                .put("route", JSONObject()
                    .put("type", "choice")
                    .put("instructions", "Which sources are needed to answer `message`?")
                    .put("criteria", JSONObject()
                        .put("neither", "Answerable from the visible conversation and stable knowledge alone")
                        .put("memory_only", "Needs personal history from earlier chats, no web needed")
                        .put("web_only", "Needs current or public web facts, no personal history needed")
                        .put("both", "Needs both personal history and current web facts"))))
        val request = Request.Builder()
            .url("$baseUrl/v1/systemone")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val raw = suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            val text = it.body?.string().orEmpty()
                            if (!it.isSuccessful) throw JevException("Jev request failed (${it.code}).", it.code)
                            if (text.isBlank()) throw JevException("Empty Jev response.")
                            JSONObject(text)
                        }
                    }
                    if (cont.isActive) result.fold(cont::resume, cont::resumeWithException)
                }
            })
        }
        parse(raw)
    }

    internal fun parse(response: JSONObject): JevScores {
        val modelVersion = response.optString("model", model)
        val answers = response.optJSONObject("answers") ?: throw JevException("Invalid Jev response.")
        fun noul(id: String): Double =
            answers.optJSONObject(id)?.optDouble("noul", 0.0) ?: 0.0
        val route = answers.optJSONObject("route")
        val probs = mutableMapOf<String, Double>()
        route?.optJSONObject("probabilities")?.let { p ->
            p.keys().forEach { k -> probs[k] = p.optDouble(k, 0.0) }
        }
        return JevScores(
            modelVersion = modelVersion,
            needsMemory = noul("needs_memory").coerceIn(0.0, 1.0),
            needsSave = noul("needs_save").coerceIn(0.0, 1.0),
            needsWeb = noul("needs_web").coerceIn(0.0, 1.0),
            routeChoice = route?.optString("choice", "neither").orEmpty().ifBlank { "neither" },
            routeProbabilities = probs,
            routeConfidence = route?.optDouble("confidence", 0.0) ?: 0.0,
        )
    }
}
