package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.ui.CustomProviderModelCatalog
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepgramIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun enabledConfig(): CustomProviderConfig = SettingsRepository(context)
        .getCustomProviderConfigDirect().copy(
            cloudApisEnabled = true, deepgramEnabled = true, deepgramApiKey = "dg-test-key",
        )

    @Test fun `Nova-3 appears in dictation only while a Deepgram key is saved`() {
        val repository = SettingsRepository(context)
        assertFalse(SttCatalog.availableModels(repository.getCustomProviderConfigDirect()).contains(SttCatalog.DEEPGRAM_MODEL))
        repository.saveCustomProviderConfig(enabledConfig().copy(deepgramApiKey = " dg-test-key "))
        repository.saveSttCloudModel(SttCatalog.DEEPGRAM_MODEL_ID)
        val restarted = SettingsRepository(context)
        val config = restarted.getCustomProviderConfigDirect()
        assertEquals("dg-test-key", config.deepgramApiKey)
        assertEquals(SttCatalog.DEEPGRAM_MODEL_ID, restarted.getSttCloudModelDirect())
        assertTrue(SttCatalog.availableModels(config).contains(SttCatalog.DEEPGRAM_MODEL))
        assertEquals("dg-test-key", SttCatalog.apiKey(SttCatalog.DEEPGRAM_MODEL_ID, "router-key", config))
        // Deepgram is dictation-only: it never adds chat models.
        assertTrue(CustomProviderModelCatalog.entries(config).isEmpty())

        val floating = restarted.getDictationConfiguration()
        assertEquals(SttCatalog.DEEPGRAM_MODEL_ID, floating.model)
        assertEquals("dg-test-key", floating.key)
        assertFalse(floating.hinglish)

        for (disabled in listOf(config.copy(deepgramEnabled = false), config.copy(cloudApisEnabled = false), config.copy(deepgramApiKey = ""))) {
            assertFalse(SttCatalog.availableModels(disabled).contains(SttCatalog.DEEPGRAM_MODEL))
            assertEquals("", SttCatalog.apiKey(SttCatalog.DEEPGRAM_MODEL_ID, "router-key", disabled))
        }
        // Removing the key moves the saved selection back to an available model.
        restarted.saveCustomProviderConfig(config.copy(deepgramApiKey = ""))
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SettingsRepository(context).getSttCloudModelDirect())
    }

    @Test fun `request posts raw WAV with Token auth, multilingual, smart format, and repeated keyterms`() {
        val request = DeepgramDictation.request(
            "https://api.deepgram.com", " dg-test-key ", ByteArray(64), listOf("EchoFlow", "Aditya Sharma"),
        )
        assertEquals("Token dg-test-key", request.header("Authorization"))
        assertEquals("audio/wav", request.body!!.contentType().toString())
        assertEquals(64L, request.body!!.contentLength())
        assertEquals("/v1/listen", request.url.encodedPath)
        assertEquals("nova-3", request.url.queryParameter("model"))
        assertEquals("multi", request.url.queryParameter("language"))
        assertEquals("true", request.url.queryParameter("smart_format"))
        assertEquals(listOf("EchoFlow", "Aditya Sharma"), request.url.queryParameterValues("keyterm"))
        assertTrue(request.url.encodedQuery!!.contains("keyterm=Aditya%20Sharma"))
        assertNull(DeepgramDictation.request("https://api.deepgram.com", "k", ByteArray(4)).url.queryParameter("keyterm"))
    }

    @Test fun `keyterms drop separators and stay within Deepgram's limits`() {
        assertEquals(listOf("Jyoti", "a b", "term 0.5"), DeepgramDictation.keyterms(listOf("Jyoti", "a, b", "term:0.5", "jyoti")))
        assertEquals(DeepgramDictation.MAX_KEYTERMS, DeepgramDictation.keyterms((1..100).map { "t$it" }).size)
        val long = DeepgramDictation.keyterms((1..100).map { "word$it " + "x".repeat(50) })
        assertTrue(long.size < DeepgramDictation.MAX_KEYTERMS)
        assertTrue(long.isNotEmpty())
    }

    @Test fun `parses the first alternative transcript`() {
        val body = """{"results":{"channels":[{"alternatives":[{"transcript":" Kal meeting hai, EchoFlow demo. ","confidence":0.97}]}]}}"""
        assertEquals("Kal meeting hai, EchoFlow demo.", DeepgramDictation.parseTranscript(body))
        assertNull(DeepgramDictation.parseTranscript("""{"results":{"channels":[{"alternatives":[{"transcript":""}]}]}}"""))
        assertNull(DeepgramDictation.parseTranscript("not json"))
    }

    @Test fun `a rejected keyterm request retries once without keyterms`() = runTest {
        val seen = mutableListOf<List<String>>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            seen += request.url.queryParameterValues("keyterm").filterNotNull()
            if (seen.size == 1) response(request, """{"err_code":"Bad Request","err_msg":"Too many keyterm tokens"}""", 400)
            else response(request, """{"results":{"channels":[{"alternatives":[{"transcript":"hello"}]}]}}""")
        })
        val result = transcriber.transcribe("dg-test-key", SttCatalog.DEEPGRAM_MODEL_ID, ByteArray(64), vocabulary = listOf("EchoFlow"))
        assertEquals("hello", result.getOrThrow())
        assertEquals(listOf(listOf("EchoFlow"), emptyList()), seen)
    }

    @Test fun `Deepgram errors surface err_msg`() = runTest {
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            response(request, """{"err_code":"Bad Request","err_msg":"Bad Request: failed to process audio"}""", 400)
        })
        val result = transcriber.transcribe("dg-test-key", SttCatalog.DEEPGRAM_MODEL_ID, ByteArray(64))
        assertEquals("Bad Request: failed to process audio", result.exceptionOrNull()?.message)
    }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
