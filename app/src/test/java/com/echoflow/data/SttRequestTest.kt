package com.echoflow.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Request-level coverage for the curated STT default. [SttCatalogTest] only sees the catalog;
 * a swapped default that never reaches `/audio/transcriptions`, or an MAI Transcribe body we
 * fail to parse, would pass that suite and break only on a real dictation.
 */
@RunWith(RobolectricTestRunner::class)
class SttRequestTest {

    @Test fun `the catalog default is the model id posted on the transcription request`() {
        val body = SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA==")
        assertEquals("microsoft/mai-transcribe-2", body["model"])
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, body["model"])
        val audio = body["input_audio"] as Map<*, *>
        assertEquals("wav", audio["format"])
        assertEquals("dGVzdA==", audio["data"])
    }

    @Test fun `encoded body carries the MAI Transcribe model id and clean style`() {
        val json = SttPayloads.encode(SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA=="))
        assertTrue(json.contains("\"model\":\"microsoft/mai-transcribe-2\""))
        assertTrue(json.contains("\"transcribeStyle\":\"clean\""))
    }

    @Test fun `parses the OpenRouter text field MAI Transcribe returns`() {
        assertEquals("hello there", SttPayloads.parseTranscript("""{"text":"hello there"}"""))
        assertEquals("trimmed", SttPayloads.parseTranscript("""{"text":"  trimmed  "}"""))
    }

    @Test fun `parses chat-completions content as a fallback`() {
        assertEquals(
            "from chat",
            SttPayloads.parseTranscript("""{"choices":[{"message":{"content":"from chat"}}]}"""),
        )
    }

    @Test fun `blank or missing text is not a transcript`() {
        assertNull(SttPayloads.parseTranscript("""{"text":"  "}"""))
        assertNull(SttPayloads.parseTranscript("{}"))
        assertNull(SttPayloads.parseTranscript("not-json"))
    }

    @Test fun `HTTP 400 retries MAI transcription once with Muse`() = runTest {
        val models = mutableListOf<String>()
        val client = client { request ->
            models += request.modelId()
            if (models.size == 2) {
                val fallbackBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                assertTrue(!fallbackBody.contains("transcribeStyle"))
            }
            if (models.size == 1) response(request, """{"error":{"message":"bad request"}}""", 400)
            else response(request, """{"text":"fallback worked"}""")
        }

        val result = SpeechToTextTranscriber(client)
            .transcribe("router-key", SttCatalog.MAI_MODEL_ID, ByteArray(100))

        assertEquals("fallback worked", result.getOrThrow())
        assertEquals(listOf(SttCatalog.MAI_MODEL_ID, SttCatalog.MUSE_MODEL_ID), models)
    }

    @Test fun `HTTP 400 retries Muse transcription once with Grok`() = runTest {
        val models = mutableListOf<String>()
        val client = client { request ->
            models += request.modelId()
            if (models.size == 1) response(request, """{"error":{"message":"bad request"}}""", 400)
            else response(request, """{"text":"grok worked"}""")
        }

        val result = SpeechToTextTranscriber(client)
            .transcribe("router-key", SttCatalog.MUSE_MODEL_ID, ByteArray(100))

        assertEquals("grok worked", result.getOrThrow())
        assertEquals(listOf(SttCatalog.MUSE_MODEL_ID, SttCatalog.GROK_MODEL_ID), models)
    }

    @Test fun `non-400 transcription failures do not fall back`() = runTest {
        var calls = 0
        val client = client { request ->
            calls++
            response(request, """{"error":{"message":"temporarily unavailable"}}""", 503)
        }

        val result = SpeechToTextTranscriber(client)
            .transcribe("router-key", SttCatalog.MAI_MODEL_ID, ByteArray(100))

        assertTrue(result.isFailure)
        assertEquals(1, calls)
    }

    private fun Request.modelId(): String {
        val body = Buffer().also { body!!.writeTo(it) }.readUtf8()
        return Regex("\\\"model\\\":\\\"([^\\\"]+)\\\"").find(body)!!.groupValues[1]
    }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
