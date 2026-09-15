package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.memory.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryProviderToolsTest {
    private val args = """{"content":"I like cricket"}"""
    private fun sse(value: String) = "data: $value\n\n"
    @Test fun `all native transports execute memory-only calls and resume the answer`() = runBlocking {
        for (format in listOf("openai", "responses", "ollama", "claude", "gemini")) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val settings = MemorySettings(context.getSharedPreferences("provider-memory-$format", Context.MODE_PRIVATE))
            settings.connect("memory-secret", "personal"); settings.recall = true
            val bodies = mutableListOf<String>()
            var writes = 0
            val memoryHttp = OkHttpClient.Builder().addInterceptor { chain ->
                writes++
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(201).message("fixture")
                    .body("""{"memories":[{"id":"m1","memory":"I like cricket"}]}""".toResponseBody()).build()
            }.build()
            val providerHttp = OkHttpClient.Builder().addInterceptor { chain ->
                val buffer = Buffer(); chain.request().body!!.writeTo(buffer); bodies += buffer.readUtf8()
                val first = bodies.size == 1
                val response = when (format) {
                    "openai" -> if (first) sse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"remember_memory","arguments":${JSONObject.quote(args)}}}]}}]}""")
                        else sse("""{"choices":[{"delta":{"content":"Remembered."}}]}""")
                    "responses" -> if (first) sse("""{"type":"response.created","response":{"id":"r1"}}""") +
                        sse("""{"type":"response.function_call_arguments.done","item_id":"i1","call_id":"c1","name":"remember_memory","arguments":${JSONObject.quote(args)}}""")
                        else sse("""{"type":"response.output_text.delta","delta":"Remembered."}""")
                    "ollama" -> if (first) """{"message":{"tool_calls":[{"function":{"name":"remember_memory","arguments":$args}}]}}""" + "\n"
                        else """{"message":{"content":"Remembered."}}""" + "\n"
                    "claude" -> if (first) sse("""{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"c1","name":"remember_memory"}}""") +
                        sse("""{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":${JSONObject.quote(args)}}}""")
                        else sse("""{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Remembered."}}""")
                    else -> if (first) sse("""{"candidates":[{"content":{"parts":[{"thoughtSignature":"signed","functionCall":{"name":"remember_memory","args":$args}}]}}]}""")
                        else sse("""{"candidates":[{"content":{"parts":[{"text":"Remembered."}]}}]}""")
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body(response.toResponseBody("text/event-stream".toMediaType())).build()
            }.build()
            val streamer = CustomProviderToolStreamer(providerHttp, Moshi.Builder().build().adapter(Any::class.java),
                { _, _ -> emptyList() }, { _, _ -> emptyList() }, { _, _ -> emptyList() },
                { base, path -> "$base/$path" }, { ProviderValidationResult(true, "") }, { _, _, _ -> "fixture failure" })
            val params = InferenceParams(.5f, 40, .9f, 1000)
            val noSearch: suspend (String) -> List<SearchSource> = { error("Web search wasn't enabled") }
            val flow = when (format) {
                "openai" -> streamer.streamOpenAiTools("https://fixture.test", "model-key", "model", emptyList(), MemoryTools.PROMPT, params, noSearch)
                "responses" -> streamer.streamOpenAiResponsesTools("model-key", "model", emptyList(), MemoryTools.PROMPT, params, noSearch)
                "ollama" -> streamer.streamOllamaTools("https://fixture.test", "model", emptyList(), MemoryTools.PROMPT, params, noSearch)
                "claude" -> streamer.streamClaudeTools("model-key", "model", emptyList(), MemoryTools.PROMPT, params, noSearch)
                else -> streamer.streamGeminiTools("model-key", "model", emptyList(), MemoryTools.PROMPT, params, noSearch)
            }
            val tools = MemoryTools(context, "chat", false, settings, SupermemoryClient("memory-secret", "personal", memoryHttp))
            val events = flow.flowOn(tools).toList()
            assertEquals(format, 1, writes)
            assertEquals(format, 2, bodies.size)
            assertFalse(format, bodies.first().contains("web_search"))
            assertFalse(format, bodies.any { it.contains("memory-secret") })
            assertTrue(format, bodies.last().contains("Memory saved successfully"))
            assertTrue(format, events.contains(StreamChunk.Content("Remembered.")))
            if (format == "gemini") assertTrue(bodies.last().contains("signed"))
        }
    }
}
