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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MistralProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        SettingsPreferenceStorage.legacy(context).edit().clear().commit()
        SettingsPreferenceStorage.secureOrNull(context)?.edit()?.clear()?.commit()
    }

    private fun enabledConfig(): CustomProviderConfig = SettingsRepository(context)
        .getCustomProviderConfigDirect().copy(
            cloudApisEnabled = true,
            mistralEnabled = true,
            mistralApiKey = "mistral-key",
            mistralModel = "mistral-medium-latest",
            mistralSelectedModels = "mistral-medium-latest\nvoxtral-small-latest",
        )

    // ── Chat ──────────────────────────────────────────────────────────────────────────

    @Test fun `selected Mistral models surface once under the Mistral group`() {
        val entries = CustomProviderModelCatalog.entries(enabledConfig())
        assertEquals(listOf("custom/mistral/mistral-medium-latest", "custom/mistral/voxtral-small-latest"), entries.map { it.id })
        assertTrue(entries.all { it.group == "Mistral" && !it.isLocalLike })
    }

    @Test fun `Mistral models stay hidden and sends are refused when the brand or cloud APIs are off`() {
        assertTrue(CustomProviderModelCatalog.entries(enabledConfig().copy(mistralEnabled = false)).isEmpty())
        assertTrue(CustomProviderModelCatalog.entries(enabledConfig().copy(cloudApisEnabled = false)).isEmpty())
        assertNull(CustomProviderRouting.disabledMessage("mistral", enabledConfig()))
        assertTrue(CustomProviderRouting.disabledMessage("mistral", enabledConfig().copy(mistralEnabled = false))!!.startsWith("Mistral is turned off"))
    }

    @Test fun `Mistral routes to its own API with native tools`() {
        assertEquals("mistral", CustomProviderRouting.providerFor("custom/mistral/mistral-large-latest"))
        assertEquals("mistral-large-latest", CustomProviderRouting.requestModel("mistral", "custom/mistral/mistral-large-latest"))
        assertEquals("https://api.mistral.ai/v1", CustomProviderConfig.MISTRAL_BASE_URL)
        assertTrue(CustomProviderCapabilities.hasNativeToolCalling("mistral"))
    }

    @Test fun `saved Mistral settings round-trip with the key trimmed`() {
        val repository = SettingsRepository(context)
        repository.saveCustomProviderConfig(enabledConfig().copy(mistralApiKey = " mistral-key "))
        val saved = SettingsRepository(context).getCustomProviderConfigDirect()
        assertEquals("mistral-key", saved.mistralApiKey)
        assertTrue(saved.mistralEnabled)
        assertEquals("mistral-medium-latest\nvoxtral-small-latest", saved.mistralSelectedModels)
        repository.saveCustomProviderConfig(saved.copy(mistralVisionModels = "mistral-medium-latest"))
        assertEquals("mistral-medium-latest", SettingsRepository(context).getCustomProviderConfigDirect().mistralVisionModels)
        assertTrue(saved.mistralAvailable)
    }

    @Test fun `fetched models take images only when Mistral flagged them as vision`() {
        val config = enabledConfig().copy(
            mistralModels = "mistral-medium-latest\nmistral-large-2411\nmagistral-small-2506\npixtral-12b-2409",
            mistralVisionModels = "mistral-medium-latest\npixtral-12b-2409",
        )
        assertTrue(CustomProviderRouting.imagesAllowed("mistral", "mistral-medium-latest", config))
        assertTrue(CustomProviderRouting.imagesAllowed("mistral", "pixtral-12b-2409", config))
        // Older dated versions of vision families were text-only, and the listing says so.
        assertFalse(CustomProviderRouting.imagesAllowed("mistral", "mistral-large-2411", config))
        assertFalse(CustomProviderRouting.imagesAllowed("mistral", "magistral-small-2506", config))
        assertFalse(CustomProviderRouting.pdfsAllowed("mistral", "mistral-medium-latest", config))
    }

    @Test fun `hand-typed models fall back to Pixtral and current vision aliases`() {
        val config = enabledConfig()
        for (id in listOf("mistral-medium-latest", "mistral-small-latest", "pixtral-large-latest", "magistral-medium-latest")) {
            assertTrue(id, CustomProviderRouting.imagesAllowed("mistral", id, config))
        }
        for (id in listOf("mistral-small-2409", "codestral-latest", "devstral-medium-latest", "voxtral-small-latest", "open-mistral-nemo", "")) {
            assertFalse(id, CustomProviderRouting.imagesAllowed("mistral", id, config))
        }
    }

    @Test fun `model list keeps chat models, Voxtral chat included, and drops speech, OCR and retired models`() {
        val body = """
            {"object":"list","data":[
              {"id":"mistral-medium-latest","capabilities":{"completion_chat":true,"vision":true}},
              {"id":"voxtral-small-latest","capabilities":{"completion_chat":true,"audio":true}},
              {"id":"voxtral-small-transcribe-3-0","capabilities":{"completion_chat":false,"audio_transcription":true}},
              {"id":"voxtral-mini-transcribe-realtime-3-0","capabilities":{"completion_chat":true}},
              {"id":"voxtral-mini-tts-3-0","capabilities":{"completion_chat":false}},
              {"id":"mistral-embed","capabilities":{"completion_chat":false}},
              {"id":"mistral-ocr-latest","capabilities":{"completion_chat":true,"vision":true}},
              {"id":"mistral-moderation-latest","capabilities":{"completion_chat":false}},
              {"id":"mistral-large-2411","capabilities":{"completion_chat":true},"deprecation":"2026-12-01T00:00:00Z"},
              {"id":"mistral-medium-latest","capabilities":{"completion_chat":true}}
            ]}
        """.trimIndent()
        assertEquals(listOf("mistral-medium-latest", "voxtral-small-latest"), ProviderHttpSupport.parseMistralChatModelIds(body))
        // Only listed chat models count as vision; OCR's flag is ignored with the model itself.
        assertEquals(listOf("mistral-medium-latest"), ProviderHttpSupport.parseMistralVisionModelIds(body))
        assertTrue(ProviderHttpSupport.parseMistralChatModelIds("not json").isEmpty())
    }

    // ── Dictation ─────────────────────────────────────────────────────────────────────

    @Test fun `Voxtral is always listed and uses the Mistral key only while one is saved`() {
        val none = SettingsRepository(context).getCustomProviderConfigDirect()
        assertTrue(SttCatalog.availableModels(none).any { it.id == SttCatalog.VOXTRAL_MODEL_ID })
        assertFalse(SttCatalog.usesMistralDirect(SttCatalog.VOXTRAL_MODEL_ID, none))
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.VOXTRAL_MODEL_ID, "router-key", none))
        // Context bias is a Mistral API field, so through OpenRouter no vocabulary is sent.
        assertFalse(SttCatalog.sendsVocabulary(SttCatalog.VOXTRAL_MODEL_ID, none))
        assertTrue(SttCatalog.sendsVocabulary(SttCatalog.SCRIBE_MODEL_ID, none))

        val config = enabledConfig()
        assertTrue(SttCatalog.usesMistralDirect(SttCatalog.VOXTRAL_MODEL_ID, config))
        assertTrue(SttCatalog.usesProviderDirect(SttCatalog.VOXTRAL_MODEL_ID, config))
        assertTrue(SttCatalog.sendsVocabulary(SttCatalog.VOXTRAL_MODEL_ID, config))
        assertEquals("mistral-key", SttCatalog.apiKey(SttCatalog.VOXTRAL_MODEL_ID, "router-key", config))
        assertEquals("router-key", SttCatalog.apiKey(SttCatalog.MAI_MODEL_ID, "router-key", config))
        // Exactly one Mistral model sits in the dictation list.
        assertEquals(1, SttCatalog.availableModels(config).count { it.provider == "Mistral" })

        for (off in listOf(config.copy(mistralEnabled = false), config.copy(cloudApisEnabled = false), config.copy(mistralApiKey = ""))) {
            assertFalse(SttCatalog.usesMistralDirect(SttCatalog.VOXTRAL_MODEL_ID, off))
            assertEquals("router-key", SttCatalog.apiKey(SttCatalog.VOXTRAL_MODEL_ID, "router-key", off))
        }
    }

    @Test fun `system-wide dictation snapshot carries the Mistral route and its vocabulary`() {
        val repository = SettingsRepository(context)
        repository.saveApiKey("router-key")
        repository.saveCustomProviderConfig(enabledConfig())
        repository.saveSttCloudModel(SttCatalog.VOXTRAL_MODEL_ID)
        repository.saveSttVocabulary(listOf("EchoFlow", "Aditya"))
        val snapshot = SettingsRepository(context).getDictationConfiguration()
        assertEquals(SttCatalog.VOXTRAL_MODEL_ID, snapshot.model)
        assertEquals("mistral-key", snapshot.key)
        assertTrue(snapshot.providerDirect)
        assertFalse(snapshot.smartMode)
        assertEquals(listOf("EchoFlow", "Aditya"), snapshot.vocabulary)

        repository.saveCustomProviderConfig(enabledConfig().copy(mistralApiKey = ""))
        val viaRouter = SettingsRepository(context).getDictationConfiguration()
        assertEquals(SttCatalog.VOXTRAL_MODEL_ID, viaRouter.model)
        assertEquals("router-key", viaRouter.key)
        assertFalse(viaRouter.providerDirect)
        assertTrue(viaRouter.vocabulary.isEmpty())
    }

    @Test fun `request uploads the WAV with a bearer key and repeated context_bias parts`() {
        val request = MistralDictation.request(" mistral-key ", ByteArray(64), listOf("EchoFlow", "Aditya Sharma"))
        assertEquals("Bearer mistral-key", request.header("Authorization"))
        assertEquals("https://api.mistral.ai/v1/audio/transcriptions", request.url.toString())
        val parts = (request.body as MultipartBody).parts
        assertEquals("voxtral-small-transcribe-3", partsNamed(parts, "model").single())
        assertEquals(listOf("EchoFlow", "Aditya Sharma"), partsNamed(parts, "context_bias"))
        assertTrue(partsNamed(parts, "language").isEmpty())
        assertEquals("audio/wav", parts.last().body.contentType().toString())
    }

    @Test fun `parses the text field`() {
        assertEquals("Kal meeting hai.", MistralDictation.parseTranscript("""{"model":"voxtral-small-transcribe-3","text":" Kal meeting hai. ","language":null,"segments":[]}"""))
        assertNull(MistralDictation.parseTranscript("""{"text":""}"""))
        assertNull(MistralDictation.parseTranscript("not json"))
    }

    @Test fun `direct route retries once without rejected context bias`() = runTest {
        val seen = mutableListOf<Boolean>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            assertEquals("Bearer mistral-key", request.header("Authorization"))
            seen += partsNamed((request.body as MultipartBody).parts, "context_bias").isNotEmpty()
            if (seen.size == 1) response(request, """{"object":"error","message":"Prompt is too long","type":"invalid_request_prompt_too_long"}""", 400)
            else response(request, """{"text":"hello"}""")
        })
        val result = transcriber.transcribe(
            "mistral-key", SttCatalog.VOXTRAL_MODEL_ID, ByteArray(64),
            vocabulary = listOf("EchoFlow"), providerDirect = true,
        )
        assertEquals("hello", result.getOrThrow())
        assertEquals(listOf(true, false), seen)
    }

    @Test fun `an unknown model id retries with the versioned Voxtral id`() = runTest {
        val models = mutableListOf<String>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            models += partsNamed((request.body as MultipartBody).parts, "model").single()
            if (models.size == 1) response(request, """{"object":"error","message":"Invalid model: voxtral-small-transcribe-3"}""", 400)
            else response(request, """{"text":"hello"}""")
        })
        val result = transcriber.transcribe("mistral-key", SttCatalog.VOXTRAL_MODEL_ID, ByteArray(64), providerDirect = true)
        assertEquals("hello", result.getOrThrow())
        assertEquals(MistralDictation.MODELS, models)
    }

    @Test fun `Mistral errors surface their message`() = runTest {
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            response(request, """{"object":"error","message":"Rate limit exceeded","type":"rate_limited"}""", 429)
        })
        val result = transcriber.transcribe("mistral-key", SttCatalog.VOXTRAL_MODEL_ID, ByteArray(64), providerDirect = true)
        assertEquals("Rate limit exceeded", result.exceptionOrNull()?.message)
    }

    @Test fun `OpenRouter route sends Voxtral without vocabulary`() = runTest {
        val bodies = mutableListOf<String>()
        val transcriber = SpeechToTextTranscriber(client = client { request ->
            assertEquals("https://openrouter.ai/api/v1/audio/transcriptions", request.url.toString())
            bodies += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            response(request, """{"text":"hello"}""")
        })
        val result = transcriber.transcribe("router-key", SttCatalog.VOXTRAL_MODEL_ID, ByteArray(64), vocabulary = listOf("EchoFlow"))
        assertEquals("hello", result.getOrThrow())
        assertEquals(1, bodies.size)
        assertTrue(bodies.single().contains("\"model\":\"mistralai/voxtral-small-transcribe-3-0\""))
        assertFalse(bodies.single().contains("EchoFlow"))
    }

    private fun partsNamed(parts: List<MultipartBody.Part>, name: String): List<String> = parts
        .filter { it.headers!!["Content-Disposition"]!!.contains("name=\"$name\"") }
        .map { Buffer().also { b -> it.body.writeTo(b) }.readUtf8() }

    private fun client(handler: (Request) -> Response) = OkHttpClient.Builder()
        .addInterceptor { handler(it.request()) }.build()

    private fun response(request: Request, body: String, code: Int = 200): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody("application/json".toMediaType())).build()
}
