package com.echoflow.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** A piece of a streamed completion: either reasoning ("thinking") or final answer content. */
sealed class StreamChunk {
    data class Reasoning(val text: String) : StreamChunk()
    data class Content(val text: String) : StreamChunk()
}

class OpenRouterService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val dynamicAdapter = moshi.adapter(Any::class.java)

    /**
     * Converts an image URI to standard raw Base64 string.
     */
    private fun getBase64FromUri(uriString: String?): String? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Transforms database messages into OpenRouter compliant multi-modal format structure
     */
    private fun buildMessagesPayload(history: List<ChatMessage>): List<Map<String, Any>> {
        return history.map { msg ->
            val base64 = getBase64FromUri(msg.localAttachmentUri)
            if (base64 != null && msg.role == "user") {
                val mime = msg.localAttachmentMimeType ?: "image/jpeg"
                mapOf(
                    "role" to msg.role,
                    "content" to listOf(
                        mapOf("type" to "text", "text" to msg.content),
                        mapOf(
                            "type" to "image_url",
                            "image_url" to mapOf("url" to "data:$mime;base64,$base64")
                        )
                    )
                )
            } else {
                mapOf(
                    "role" to msg.role,
                    "content" to msg.content
                )
            }
        }
    }

    /**
     * Parse errors returned from OpenRouter payload
     */
    private fun parseErrorMessage(errJson: String): String? {
        return try {
            val map = dynamicAdapter.fromJson(errJson) as? Map<*, *>
            val errorMap = map?.get("error") as? Map<*, *>
            val message = errorMap?.get("message") as? String
            message
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Run standard non-streaming api completion.
     */
    suspend fun sendChatMessage(apiKey: String, model: String, history: List<ChatMessage>, webSearchEnabled: Boolean = false): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val messagesPayload = buildMessagesPayload(history)
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messagesPayload,
            "stream" to false
        )
        if (webSearchEnabled) {
            requestMap["plugins"] = listOf(
                mapOf("id" to "web")
            )
        }

        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://localhost")
            .addHeader("X-Title", "OpenRouter Chat Android")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedErrorMsg = parseErrorMessage(responseString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized keys. Verify your OpenRouter API key in Settings."
                    404 -> "Model: \"$model\" is unavailable."
                    403 -> "Action forbidden. Your OpenRouter credit might be depleted."
                    else -> parsedErrorMsg ?: "HTTP code ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }

            try {
                val responseMap = dynamicAdapter.fromJson(responseString) as? Map<*, *>
                val choices = responseMap?.get("choices") as? List<*>
                val choice = choices?.firstOrNull() as? Map<*, *>
                val message = choice?.get("message") as? Map<*, *>
                val content = message?.get("content") as? String
                content ?: throw Exception("No Response content received.")
            } catch (e: Exception) {
                throw Exception("Response Parsing failure: ${e.message}")
            }
        }
    }

    /**
     * Run Streaming completions with flows.
     */
    fun sendChatMessageStream(apiKey: String, model: String, history: List<ChatMessage>, webSearchEnabled: Boolean = false): Flow<StreamChunk> = flow {
        if (apiKey.isBlank()) {
            throw Exception("API key is missing! Please configure it in your Settings.")
        }

        val messagesPayload = buildMessagesPayload(history)
        val requestMap = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messagesPayload,
            "stream" to true,
            // Ask OpenRouter to stream reasoning tokens for reasoning-capable models. Models that
            // don't support it simply ignore this and never send a `reasoning` delta.
            "include_reasoning" to true,
            "reasoning" to mapOf("enabled" to true)
        )
        if (webSearchEnabled) {
            requestMap["plugins"] = listOf(
                mapOf("id" to "web")
            )
        }

        val jsonPayload = dynamicAdapter.toJson(requestMap)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("HTTP-Referer", "https://localhost")
            .addHeader("X-Title", "OpenRouter Chat Android")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorString = response.body?.string().orEmpty()
                val parsedErrorMsg = parseErrorMessage(errorString)
                val statusMessage = when (response.code) {
                    401 -> "Unauthorized keys. Verify your OpenRouter API key."
                    404 -> "Model: \"$model\" is unavailable."
                    403 -> "Your OpenRouter balance might be empty."
                    else -> parsedErrorMsg ?: "HTTP ${response.code}"
                }
                throw Exception("API Failure: $statusMessage")
            }

            val body = response.body ?: throw Exception("Empty stream body received.")
            val reader = body.charStream().buffered()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line!!.trim()
                if (currentLine.startsWith("data: ")) {
                    val dataPart = currentLine.substring(6).trim()
                    if (dataPart == "[DONE]" || dataPart.startsWith("[DONE]")) {
                        break
                    }
                    try {
                        val map = dynamicAdapter.fromJson(dataPart) as? Map<*, *>
                        val choices = map?.get("choices") as? List<*>
                        val choice = choices?.firstOrNull() as? Map<*, *>
                        val delta = choice?.get("delta") as? Map<*, *>
                        // Reasoning tokens (different providers name the field differently).
                        val reasoning = (delta?.get("reasoning") as? String)
                            ?: (delta?.get("reasoning_content") as? String)
                        if (!reasoning.isNullOrEmpty()) {
                            emit(StreamChunk.Reasoning(reasoning))
                        }
                        val content = delta?.get("content") as? String
                        if (!content.isNullOrEmpty()) {
                            emit(StreamChunk.Content(content))
                        }
                    } catch (e: Exception) {
                        // Resilient inline SSE fail ignores
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Title generation request handler.
     */
    suspend fun generateTitle(apiKey: String, model: String, firstUserMessage: String): String {
        return try {
            val titleRule = "Generate a short title of 3 to 6 words for this chat based on the user message. " +
                    "Return ONLY the title, with NO explanation, NO headers, NO quotes, and NO markdown. " +
                    "User message: $firstUserMessage"

            val promptHistory = listOf(
                ChatMessage(
                    id = "title_gen",
                    chatId = "temp",
                    role = "user",
                    content = titleRule,
                    createdAt = System.currentTimeMillis()
                )
            )
            val rawTitle = sendChatMessage(apiKey, model, promptHistory).trim()
            // Clean simple quote wraps
            rawTitle.removeSurrounding("\"").removeSurrounding("'").trim()
        } catch (e: Exception) {
            // Fallback: Use first few words of the user's message
            val words = firstUserMessage.split("\\s+".toRegex())
            val fallback = words.take(4).joinToString(" ") + if (words.size > 4) "..." else ""
            fallback
        }
    }
}
