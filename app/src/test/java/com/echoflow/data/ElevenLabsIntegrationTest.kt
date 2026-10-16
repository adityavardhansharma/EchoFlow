package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.echoflow.ui.CustomProviderModelCatalog
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
class ElevenLabsIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun enabledConfig(): CustomProviderConfig = SettingsRepository(context)
        .getCustomProviderConfigDirect().copy(
            cloudApisEnabled = true, elevenLabsEnabled = true, elevenLabsApiKey = "el-test-key",
        )

    @Test fun `Scribe v2 is always listed and uses the ElevenLabs key only while one is saved`() {
        val repository = SettingsRepository(context)
        val none = repository.getCustomProviderConfigDirect()
        assertTrue(SttCatalog.availableModels(none).any { it.id == SttCatalog.SCRIBE_MODEL_ID })
        assertFalse(SttCatalog.usesElevenLabsDirect(SttCatalog.SCRIBE_MODEL_ID, none))
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.SCRIBE_MODEL_ID, "router-key", none))

        repository.saveCustomProviderConfig(enabledConfig().copy(elevenLabsApiKey = " el-test-key "))
        val config = SettingsRepository(context).getCustomProviderConfigDirect()
        assertEquals("el-test-key", config.elevenLabsApiKey)
        assertTrue(SttCatalog.usesElevenLabsDirect(SttCatalog.SCRIBE_MODEL_ID, config))
        assertTrue(SttCatalog.usesProviderDirect(SttCatalog.SCRIBE_MODEL_ID, config))
        assertFalse(SttCatalog.supportsRambler(SttCatalog.SCRIBE_MODEL_ID, config))
        assertEquals("el-test-key", SttCatalog.apiKey(SttCatalog.SCRIBE_MODEL_ID, "router-key", config))
        // Other models keep using OpenRouter, and ElevenLabs never adds chat models.
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.MAI_MODEL_ID, "router-key", config))
        assertTrue(CustomProviderModelCatalog.entries(config).isEmpty())

        for (off in listOf(config.copy(elevenLabsEnabled = false), config.copy(cloudApisEnabled = false), config.copy(elevenLabsApiKey = ""))) {
            assertFalse(SttCatalog.usesElevenLabsDirect(SttCatalog.SCRIBE_MODEL_ID, off))
            assertEquals("router-key", SttCatalog.apiKey(SttCatalog.SCRIBE_MODEL_ID, "router-key", off))
        }
    }

    @Test fun `system-wide dictation snapshot carries the ElevenLabs route`() {
        val repository = SettingsRepository(context)
        repository.saveApiKey("router-key")
        repository.saveCustomProviderConfig(enabledConfig())
        repository.saveSttCloudModel(SttCatalog.SCRIBE_MODEL_ID)
        repository.saveSttRamblerEnabled(true)
        val snapshot = SettingsRepository(context).getDictationConfiguration()
        assertEquals(SttCatalog.SCRIBE_MODEL_ID, snapshot.model)
        assertEquals("el-test-key", snapshot.key)
        assertTrue(snapshot.providerDirect)
        assertFalse(snapshot.rambler)

        repository.saveCustomProviderConfig(enabledConfig().copy(elevenLabsApiKey = ""))
        val viaRouter = SettingsRepository(context).getDictationConfiguration()
        assertEquals(SttCatalog.SCRIBE_MODEL_ID, viaRouter.model)
        assertEquals("router-key", viaRouter.key)
        assertFalse(viaRouter.providerDirect)
    }

    @Test fun `request uploads the WAV with xi-api-key, scribe_v2, no audio tags, and repeated keyterms`() {
        val request = ElevenLabsDictation.request(" el-test-key ", ByteArray(64), listOf("EchoFlow", "Aditya Sharma"))
        assertEquals("el-test-key", request.header("xi-api-key"))
        assertEquals("https://api.elevenlabs.io/v1/speech-to-text", request.url.toString())
        val parts = (request.body as MultipartBody).parts.associate { part ->
            val name = Regex("name=\"([^\"]+)\"").find(part.headers!!["Content-Disposition"]!!)!!.groupValues[1]
            name to Buffer().also { part.body.writeTo(it) }.readUtf8()
        }
        assertEquals("scribe_v2", parts["model_id"])
        assertEquals("false", parts["tag_audio_events"])
        assertEquals("none", parts["timestamps_granularity"])
        val keyterms = (request.body as MultipartBody).parts
            .filter { it.headers!!["Content-Disposition"]!!.contains("name=\"keyterms\"") }
            .map { Buffer().also { b -> it.body.writeTo(b) }.readUtf8() }
        assertEquals(listOf("EchoFlow", "Aditya Sharma"), keyterms)
        assertEquals("audio/wav", (request.body as MultipartBody).parts.last().body.contentType().toString())
    }

    @Test fun `keyterms drop forbidden characters and stay within ElevenLabs' limits`() {
        assertEquals(
            listOf("Jyoti", "a b", "C++ code"),
            ElevenLabsDictation.keyterms(listOf("Jyoti", "a[b]", "C++ code", "jyoti", "one two three four five six", "x".repeat(50))),
        )
        assertEquals(ElevenLabsDictation.MAX_KEYTERMS, ElevenLabsDictation.keyterms((1..120).map { "t$it" }).size)
    }

    @Test fun `parses the text field`() {
        assertEquals("Kal meeting hai.", ElevenLabsDictation.parseTranscript("""{"language_code":"hin","text":" Kal meeting hai. ","words":[]}"""))
        assertNull(ElevenLabsDictation.parseTranscript("""{"text":""}"""))
        assertNull(ElevenLabsDictation.parseTranscript("not json"))
    }

    @Test fun `direct route retries once without rejected keyterms`() = runTest {
        val seen = mutableListOf<Boolean>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            assertEquals("el-test-key", request.header("xi-api-key"))
            seen += (request.body as MultipartBody).parts.any {
                it.headers!!["Content-Disposition"]!!.contains("name=\"keyterms\"")
            }
            if (seen.size == 1) response(request, """{"detail":{"status":"invalid_keyterms","message":"bad keyterm"}}""", 422)
            else response(request, """{"text":"hello"}""")
        })
        val result = transcriber.transcribe(
            "el-test-key", SttCatalog.SCRIBE_MODEL_ID, ByteArray(64),
            vocabulary = listOf("EchoFlow"), providerDirect = true,
        )
        assertEquals("hello", result.getOrThrow())
        assertEquals(listOf(true, false), seen)
    }

    @Test fun `ElevenLabs errors surface detail message`() = runTest {
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            response(request, """{"detail":{"status":"quota_exceeded","message":"Quota exceeded."}}""", 429)
        })
        val result = transcriber.transcribe("el-test-key", SttCatalog.SCRIBE_MODEL_ID, ByteArray(64), providerDirect = true)
        assertEquals("Quota exceeded.", result.exceptionOrNull()?.message)
    }

    @Test fun `OpenRouter route sends keyterms and turns off audio-event tags`() {
        val json = SttPayloads.encode(
            SttPayloads.requestBody(SttCatalog.SCRIBE_MODEL_ID, "dGVzdA==", vocabulary = listOf(" Aditya ", "Jyoti", "aditya")),
        )
        assertTrue(json.contains("\"keyterms\":[\"Aditya\",\"Jyoti\"]"))
        assertTrue(json.contains("\"provider\":{\"options\":{\"elevenlabs\":{\"tag_audio_events\":false}}}"))
        assertFalse(SttPayloads.encode(SttPayloads.requestBody(SttCatalog.SCRIBE_MODEL_ID, "dGVzdA==")).contains("keyterms"))
    }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
