package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
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
class GeminiDictationIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun geminiConfig(): CustomProviderConfig = SettingsRepository(context)
        .getCustomProviderConfigDirect().copy(cloudApisEnabled = true, geminiEnabled = true, geminiApiKey = "gm-key")

    @Test fun `Gemini Transcribe uses the Gemini key only while one is saved`() {
        val config = geminiConfig()
        assertTrue(SttCatalog.usesGeminiDirect(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, config))
        assertTrue(SttCatalog.supportsRambler(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, config))
        assertEquals("gm-key", SttCatalog.apiKey(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, "router-key", config))
        // Other models keep using OpenRouter.
        assertFalse(SttCatalog.supportsRambler(SttCatalog.MAI_MODEL_ID, config))
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.MAI_MODEL_ID, "router-key", config))

        for (off in listOf(config.copy(geminiApiKey = ""), config.copy(geminiEnabled = false), config.copy(cloudApisEnabled = false))) {
            assertFalse(SttCatalog.supportsRambler(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, off))
            assertEquals("router-key", SttCatalog.apiKey(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, "router-key", off))
        }
    }

    @Test fun `system-wide dictation snapshot carries the Gemini route and Rambler`() {
        val repository = SettingsRepository(context)
        repository.saveApiKey("router-key")
        repository.saveCustomProviderConfig(geminiConfig())
        repository.saveSttCloudModel(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID)
        repository.saveSttRamblerEnabled(true)
        val snapshot = SettingsRepository(context).getDictationConfiguration()
        assertEquals("gm-key", snapshot.key)
        assertTrue(snapshot.geminiDirect)
        assertTrue(snapshot.rambler)

        repository.saveCustomProviderConfig(geminiConfig().copy(geminiApiKey = ""))
        val viaRouter = SettingsRepository(context).getDictationConfiguration()
        assertEquals("router-key", viaRouter.key)
        assertFalse(viaRouter.geminiDirect)
        assertFalse(viaRouter.rambler)
    }

    @Test fun `direct route sends smart mode and retries once without a rejected vocabulary`() = runTest {
        val bodies = mutableListOf<String>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            assertEquals("gm-key", request.header("x-goog-api-key"))
            bodies += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            if (bodies.size == 1) response(request, """{"error":{"message":"bad vocabulary"}}""", 400)
            else response(request, """{"steps":[{"type":"model_output","content":[{"type":"text","text":"hello"}]}]}""")
        })
        val result = transcriber.transcribe(
            "gm-key", SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID, ByteArray(64),
            vocabulary = listOf("EchoFlow"), geminiDirect = true, rambler = true,
        )
        assertEquals("hello", result.getOrThrow())
        assertEquals(2, bodies.size)
        assertTrue(bodies[0].contains("custom_vocabulary"))
        assertFalse(bodies[1].contains("custom_vocabulary"))
        assertTrue(bodies.all { it.contains("\"mode\":\"smart\"") })
    }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
