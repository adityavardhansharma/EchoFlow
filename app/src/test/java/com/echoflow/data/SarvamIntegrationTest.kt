package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.ui.CustomProviderFlowRouter
import com.echoflow.ui.CustomProviderModelCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SarvamIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun enabledConfig(): CustomProviderConfig = SettingsRepository(context)
        .getCustomProviderConfigDirect().copy(
            cloudApisEnabled = true, sarvamEnabled = true, sarvamApiKey = "sarvam-test-key",
        )

    @Test fun `saved Sarvam key enables dictation and preselected 105b chat across restarts`() {
        val repository = SettingsRepository(context)
        assertFalse(SttCatalog.sarvamAvailable(repository.getCustomProviderConfigDirect()))
        repository.saveCustomProviderConfig(enabledConfig().copy(sarvamApiKey = " sarvam-test-key "))
        repository.saveSttCloudModel(SttCatalog.SARVAM_MODEL_ID)
        val restarted = SettingsRepository(context)
        val config = restarted.getCustomProviderConfigDirect()
        assertEquals("sarvam-test-key", config.sarvamApiKey)
        assertEquals(SttCatalog.SARVAM_MODEL_ID, restarted.getSttCloudModelDirect())
        assertTrue(SttCatalog.availableModels(config).contains(SttCatalog.SARVAM_MODEL))
        assertEquals(listOf("custom/sarvam/sarvam-105b"), CustomProviderModelCatalog.entries(config).map { it.id })
        assertEquals("sarvam-test-key", SttCatalog.apiKey(SttCatalog.SARVAM_MODEL_ID, "", config))
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.DEFAULT_MODEL_ID, "router-key", config))
        for (disabled in listOf(config.copy(sarvamEnabled = false), config.copy(cloudApisEnabled = false), config.copy(sarvamApiKey = ""))) {
            assertFalse(SttCatalog.availableModels(disabled).contains(SttCatalog.SARVAM_MODEL))
            assertEquals("", SttCatalog.apiKey(SttCatalog.SARVAM_MODEL_ID, "router-key", disabled))
        }
        repository.saveCustomProviderConfig(config.copy(sarvamSelectedModels = ""))
        assertTrue(CustomProviderModelCatalog.entries(SettingsRepository(context).getCustomProviderConfigDirect()).isEmpty())
    }

    @Test fun `dictation sends multipart Sarvam requests and preserves long recording audio`() = runTest {
        val original = wav(120)
        val chunks = SarvamDictation.wavChunks(original)
        assertEquals(5, chunks.size)
        assertArrayEquals(original.copyOfRange(44, original.size), chunks.fold(ByteArray(0)) { acc, chunk ->
            val header = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(chunk.size - 8, header.getInt(4))
            assertEquals(chunk.size - 44, header.getInt(40))
            assertTrue(chunk.size - 44 <= 30 * 32_000)
            acc + chunk.copyOfRange(44, chunk.size)
        })
        var count = 0
        val client = client { request ->
            count++
            assertEquals("https://api.sarvam.ai/speech-to-text", request.url.toString())
            assertEquals("sarvam-test-key", request.header("api-subscription-key"))
            assertNull(request.header("Authorization"))
            assertTrue(request.body!!.contentType().toString().startsWith("multipart/form-data"))
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            val fields = (request.body as MultipartBody).parts.associate { part ->
                part.headers?.get("Content-Disposition") to Buffer().also { part.body.writeTo(it) }.readUtf8()
            }
            assertEquals("saaras:v4", fields["form-data; name=\"model\""])
            assertEquals("transcribe", fields["form-data; name=\"mode\""])
            assertEquals("unknown", fields["form-data; name=\"language_code\""])
            assertTrue(body.contains("filename=\"dictation.wav\""))
            response(request, """{"transcript":" शब्द$count "}""")
        }
        val transcript = SpeechToTextTranscriber(client).transcribe("sarvam-test-key", SttCatalog.SARVAM_MODEL_ID, original)
        assertEquals("शब्द1 शब्द2 शब्द3 शब्द4 शब्द5", transcript.getOrThrow())
    }

    @Test fun `hinglish romanizes Hindi chunks and leaves other languages in native script`() = runTest {
        var transliterateCalls = 0
        val client = client { request ->
            when {
                request.url.toString().endsWith("/transliterate") -> {
                    transliterateCalls++
                    assertEquals("sarvam-test-key", request.header("api-subscription-key"))
                    val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    assertTrue(body.contains("\"source_language_code\":\"hi-IN\""))
                    assertTrue(body.contains("\"target_language_code\":\"en-IN\""))
                    response(request, """{"transliterated_text":"main office ja raha hun"}""")
                }
                else -> response(request, """{"transcript":"मैं ऑफिस जा रहा हूँ","language_code":"hi-IN"}""")
            }
        }
        val transcript = SpeechToTextTranscriber(client)
            .transcribe("sarvam-test-key", SttCatalog.SARVAM_MODEL_ID, wav(10), romanizeHindi = true)
        assertEquals("main office ja raha hun", transcript.getOrThrow())
        assertEquals(1, transliterateCalls)
    }

    @Test fun `hinglish leaves Tamil and English detections untouched without extra calls`() = runTest {
        var transliterateCalls = 0
        val bodies = listOf(
            """{"transcript":"வணக்கம்","language_code":"ta-IN"}""",
            """{"transcript":"hello there","language_code":"en-IN"}""",
        )
        var count = 0
        val client = client { request ->
            if (request.url.toString().endsWith("/transliterate")) {
                transliterateCalls++
                response(request, """{"transliterated_text":"should not happen"}""")
            } else {
                response(request, bodies[count++ % bodies.size])
            }
        }
        val transcript = SpeechToTextTranscriber(client)
            .transcribe("sarvam-test-key", SttCatalog.SARVAM_MODEL_ID, wav(10), romanizeHindi = true)
        assertEquals("வணக்கம்", transcript.getOrThrow())
        assertEquals(0, transliterateCalls)
    }

    @Test fun `hinglish off keeps Hindi in Devanagari with no transliteration call`() = runTest {
        var transliterateCalls = 0
        val client = client { request ->
            if (request.url.toString().endsWith("/transliterate")) {
                transliterateCalls++
                response(request, """{"transliterated_text":"should not happen"}""")
            } else {
                response(request, """{"transcript":"नमस्ते","language_code":"hi-IN"}""")
            }
        }
        val transcript = SpeechToTextTranscriber(client)
            .transcribe("sarvam-test-key", SttCatalog.SARVAM_MODEL_ID, wav(10), romanizeHindi = false)
        assertEquals("नमस्ते", transcript.getOrThrow())
        assertEquals(0, transliterateCalls)
    }

    @Test fun `a transliteration miss falls back to the original Hindi script`() = runTest {
        val client = client { request ->
            if (request.url.toString().endsWith("/transliterate")) {
                response(request, """{"error":{"message":"bad input"}}""", 400)
            } else {
                response(request, """{"transcript":"नमस्ते","language_code":"hi-IN"}""")
            }
        }
        val transcript = SpeechToTextTranscriber(client)
            .transcribe("sarvam-test-key", SttCatalog.SARVAM_MODEL_ID, wav(10), romanizeHindi = true)
        assertEquals("नमस्ते", transcript.getOrThrow())
    }

    @Test fun `sarvam result parsing keeps language codes and flags Hindi only`() {
        assertEquals("hi-IN", SttPayloads.parseSarvamResult("""{"transcript":"a","language_code":"hi-IN"}""").languageCode)
        assertEquals("ta-IN", SttPayloads.parseSarvamResult("""{"transcript":"a","language_code":"ta-IN"}""").languageCode)
        assertEquals(null, SttPayloads.parseSarvamResult("""{"transcript":"a"}""").languageCode)
        assertEquals("a", SttPayloads.parseSarvamTranscript("""{"transcript":"a","language_code":"hi-IN"}"""))
        assertTrue(SttPayloads.shouldRomanizeHindi("hi-IN"))
        assertTrue(SttPayloads.shouldRomanizeHindi("hi"))
        assertFalse(SttPayloads.shouldRomanizeHindi("ta-IN"))
        assertFalse(SttPayloads.shouldRomanizeHindi("en-IN"))
        assertFalse(SttPayloads.shouldRomanizeHindi(null))
        assertFalse(SttPayloads.shouldRomanizeHindi(""))
        assertEquals("main office", SttPayloads.parseTransliteratedText("""{"transliterated_text":"main office"}"""))
        assertEquals(null, SttPayloads.parseTransliteratedText("""{"transcript":"x"}"""))
        assertEquals(listOf("short"), SttPayloads.splitForTransliteration("short"))
        assertTrue(SttPayloads.splitForTransliteration("x".repeat(2500)).all { it.length <= SttPayloads.TRANSLITERATE_MAX_CHARS })
    }

    @Test fun `dictation propagates failure instead of returning a partial transcript`() = runTest {        var count = 0
        val client = client { request ->
            count++
            if (count == 1) response(request, """{"transcript":"first"}""")
            else response(request, """{"error":{"message":"Invalid key"}}""", 403)
        }
        val result = SpeechToTextTranscriber(client).transcribe("bad-key", SttCatalog.SARVAM_MODEL_ID, wav(60))
        assertTrue(result.isFailure)
        assertEquals(2, count)
    }

    @Test fun `Sarvam chat routes to direct API and parses content and reasoning`() = runTest {
        val client = client { request ->
            assertEquals("https://api.sarvam.ai/v1/chat/completions", request.url.toString())
            assertEquals("Bearer sarvam-test-key", request.header("Authorization"))
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            assertTrue(body.contains("\"model\":\"sarvam-105b\""))
            assertTrue(body.contains("\"stream\":true"))
            response(request, "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"Thinking\",\"content\":\"Hello\"}}]}\n\ndata: [DONE]\n\n")
        }
        val chunks = CustomProviderFlowRouter(CustomProviderService(client = client)).stream(
            "sarvam", enabledConfig(), "sarvam-105b", emptyList(), "Be helpful", InferenceLimits.CLOUD_DEFAULTS,
        ).toList()
        assertEquals(listOf(StreamChunk.Reasoning("Thinking"), StreamChunk.Content("Hello")), chunks)
    }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    private fun wav(seconds: Int): ByteArray = ByteArray(44 + seconds * 32_000).also { bytes ->
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(bytes.size - 8); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(bytes.size - 44)
        }
        for (i in 44 until bytes.size) bytes[i] = (i % 127).toByte()
    }
}
