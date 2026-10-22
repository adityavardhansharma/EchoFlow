package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeVoiceProtocolTest {

    private val spec = RealtimeSessionSpec(model = "gemini-3.8-live", voice = "Kore", instructions = "Be brief.", greet = true)
    private val decode: (String) -> ByteArray = { it.toByteArray() }

    // ── Gemini Live ─────────────────────────────────────────────────────────────────────

    @Test
    fun `gemini setup names the model, voice, prompt and both transcriptions`() {
        val setup = VoiceApiJson.read(GeminiLiveProtocol.setup(spec, resumeHandle = null).single())!!["setup"] as Map<*, *>
        assertEquals("models/gemini-3.8-live", setup["model"])
        val generation = setup["generationConfig"] as Map<*, *>
        assertEquals(listOf("AUDIO"), generation["responseModalities"])
        val voice = ((generation["speechConfig"] as Map<*, *>)["voiceConfig"] as Map<*, *>)["prebuiltVoiceConfig"] as Map<*, *>
        assertEquals("Kore", voice["voiceName"])
        val parts = (setup["systemInstruction"] as Map<*, *>)["parts"] as List<*>
        assertEquals("Be brief.", (parts.single() as Map<*, *>)["text"])
        assertTrue(setup.containsKey("inputAudioTranscription"))
        assertTrue(setup.containsKey("outputAudioTranscription"))
        assertEquals(emptyMap<String, Any>(), setup["sessionResumption"])
        assertTrue(setup.containsKey("contextWindowCompression"))
    }

    @Test
    fun `gemini resumes with the handle it was given`() {
        val setup = VoiceApiJson.read(GeminiLiveProtocol.setup(spec, resumeHandle = "h-1").single())!!["setup"] as Map<*, *>
        assertEquals(mapOf("handle" to "h-1"), setup["sessionResumption"])
    }

    @Test
    fun `gemini key goes in the url, encoded`() {
        assertTrue(GeminiLiveProtocol.url("AIza a+b", "gemini-3.8-live").endsWith("BidiGenerateContent?key=AIza+a%2Bb"))
    }

    @Test
    fun `gemini audio and text go in as realtime input`() {
        val audio = VoiceApiJson.read(GeminiLiveProtocol.audio("QUJD"))!!["realtimeInput"] as Map<*, *>
        assertEquals(mapOf("data" to "QUJD", "mimeType" to "audio/pcm;rate=16000"), audio["audio"])
        val text = VoiceApiJson.read(GeminiLiveProtocol.userText("hi", null).single())!!["realtimeInput"] as Map<*, *>
        assertEquals("hi", text["text"])
        val paused = VoiceApiJson.read(GeminiLiveProtocol.audioPaused())!!["realtimeInput"] as Map<*, *>
        assertEquals(true, paused["audioStreamEnd"])
    }

    @Test
    fun `gemini setupComplete means ready`() {
        assertEquals(listOf(RealtimeEvent.Ready), GeminiLiveProtocol.parse("""{"setupComplete":{}}""", decode))
    }

    @Test
    fun `gemini server content is read in order - user words, audio, reply words, done`() {
        val events = GeminiLiveProtocol.parse(
            """{"serverContent":{"inputTranscription":{"text":" book a"},""" +
                """"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"UENN"}}]},""" +
                """"outputTranscription":{"text":"Sure"},"turnComplete":true},""" +
                """"usageMetadata":{"promptTokenCount":100,"responseTokenCount":40,""" +
                """"promptTokensDetails":[{"modality":"AUDIO","tokenCount":60},{"modality":"TEXT","tokenCount":40}],""" +
                """"responseTokensDetails":[{"modality":"AUDIO","tokenCount":40}]}}""",
            decode,
        )
        assertEquals(RealtimeEvent.UserTranscriptDelta(" book a"), events[0])
        // The decoder is the identity here, so the payload comes through as sent.
        assertEquals("UENN", String((events[1] as RealtimeEvent.Audio).pcm))
        assertEquals(RealtimeEvent.AgentTranscriptDelta("Sure"), events[2])
        assertEquals(RealtimeEvent.TurnComplete, events[3])
        assertEquals(RealtimeEvent.Usage(RealtimeUsage(textIn = 40, audioIn = 60, textOut = 0, audioOut = 40)), events[4])
    }

    @Test
    fun `gemini interruption, resume handle and goAway are read`() {
        assertEquals(listOf(RealtimeEvent.Interrupted), GeminiLiveProtocol.parse("""{"serverContent":{"interrupted":true}}""", decode))
        assertEquals(
            listOf(RealtimeEvent.ResumeHandle("abc")),
            GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"abc","resumable":true}}""", decode),
        )
        assertEquals(
            emptyList<RealtimeEvent>(),
            GeminiLiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"abc","resumable":false}}""", decode),
        )
        assertEquals(listOf(RealtimeEvent.GoAway), GeminiLiveProtocol.parse("""{"goAway":{"timeLeft":"50s"}}""", decode))
    }

    @Test
    fun `gemini has nothing to send to stop a reply`() {
        assertEquals(emptyList<String>(), GeminiLiveProtocol.cancelReply(RealtimeVoiceProtocol.Cancel(null, 900), false))
    }

    // ── OpenAI Realtime ─────────────────────────────────────────────────────────────────

    @Test
    fun `openai session update sets voice, prompt, pcm and transcription`() {
        val json = VoiceApiJson.read(OpenAiRealtimeProtocol.setup(spec.copy(model = "gpt-realtime-2.1", voice = "marin"), null).single())!!
        assertEquals("session.update", json["type"])
        val session = json["session"] as Map<*, *>
        assertEquals("realtime", session["type"])
        assertEquals("Be brief.", session["instructions"])
        val audio = session["audio"] as Map<*, *>
        val input = audio["input"] as Map<*, *>
        assertEquals("audio/pcm", (input["format"] as Map<*, *>)["type"])
        assertEquals(24000.0, ((input["format"] as Map<*, *>)["rate"] as Number).toDouble(), 0.0)
        assertEquals(OpenAiRealtimeProtocol.TRANSCRIPTION_MODEL, (input["transcription"] as Map<*, *>)["model"])
        assertEquals(true, (input["turn_detection"] as Map<*, *>)["interrupt_response"])
        assertEquals("marin", (audio["output"] as Map<*, *>)["voice"])
    }

    @Test
    fun `openai model goes in the url and the key in a bearer header`() {
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-realtime-2.1", OpenAiRealtimeProtocol.url("sk", "gpt-realtime-2.1"))
        assertEquals(mapOf("Authorization" to "Bearer sk-1"), OpenAiRealtimeProtocol.headers(" sk-1 "))
    }

    @Test
    fun `openai events are read`() {
        fun one(json: String) = OpenAiRealtimeProtocol.parse(json, decode).single()
        assertEquals(RealtimeEvent.Ready, one("""{"type":"session.updated","session":{}}"""))
        val audio = one("""{"type":"response.output_audio.delta","item_id":"it_1","delta":"UENN"}""") as RealtimeEvent.Audio
        assertEquals("it_1", audio.itemId)
        assertEquals(RealtimeEvent.AgentTranscriptDelta("Hel", "it_1"), one("""{"type":"response.output_audio_transcript.delta","item_id":"it_1","delta":"Hel"}"""))
        assertEquals(RealtimeEvent.AgentTranscriptDone("Hello there", "it_1"), one("""{"type":"response.output_audio_transcript.done","item_id":"it_1","transcript":" Hello there "}"""))
        assertEquals(RealtimeEvent.UserSpeechStarted, one("""{"type":"input_audio_buffer.speech_started","audio_start_ms":10}"""))
        assertEquals(RealtimeEvent.UserCommitted("u_1"), one("""{"type":"input_audio_buffer.committed","item_id":"u_1"}"""))
        assertEquals(RealtimeEvent.UserTranscriptDelta("book"), one("""{"type":"conversation.item.input_audio_transcription.delta","item_id":"u_1","delta":"book"}"""))
        assertEquals(RealtimeEvent.UserTranscript("u_1", "book a table"), one("""{"type":"conversation.item.input_audio_transcription.completed","item_id":"u_1","transcript":"book a table"}"""))
        assertEquals(RealtimeEvent.Error("Bad"), one("""{"type":"error","error":{"message":"Bad"}}"""))
    }

    @Test
    fun `openai response done reports usage net of cached tokens`() {
        val events = OpenAiRealtimeProtocol.parse(
            """{"type":"response.done","response":{"usage":{"input_token_details":{"text_tokens":500,"audio_tokens":200,""" +
                """"cached_tokens":300,"cached_tokens_details":{"text_tokens":250,"audio_tokens":50}},""" +
                """"output_token_details":{"text_tokens":20,"audio_tokens":120}}}}""",
            decode,
        )
        assertEquals(RealtimeEvent.Usage(RealtimeUsage(textIn = 250, audioIn = 150, cachedIn = 300, textOut = 20, audioOut = 120)), events[0])
        assertEquals(RealtimeEvent.TurnComplete, events[1])
    }

    @Test
    fun `cancelling an already finished reply is not an error`() {
        assertTrue(
            OpenAiRealtimeProtocol.parse("""{"type":"error","error":{"code":"response_cancel_not_active","message":"x"}}""", decode).isEmpty(),
        )
    }

    @Test
    fun `openai cut-in cancels and truncates to what was heard`() {
        val sent = OpenAiRealtimeProtocol.cancelReply(RealtimeVoiceProtocol.Cancel("it_1", 1500), serverAlreadyStopped = false)
            .map { VoiceApiJson.read(it)!! }
        assertEquals("response.cancel", sent[0]["type"])
        assertEquals("conversation.item.truncate", sent[1]["type"])
        assertEquals("it_1", sent[1]["item_id"])
        assertEquals(1500.0, (sent[1]["audio_end_ms"] as Number).toDouble(), 0.0)
        // Talking over it: the server already cancelled, so only the truncate is sent.
        val spoken = OpenAiRealtimeProtocol.cancelReply(RealtimeVoiceProtocol.Cancel("it_1", 10), serverAlreadyStopped = true)
        assertEquals(1, spoken.size)
    }

    @Test
    fun `openai typed text adds a user message and asks for a reply`() {
        val sent = OpenAiRealtimeProtocol.userText("hello", null).map { VoiceApiJson.read(it)!! }
        assertEquals(listOf("conversation.item.create", "response.create"), sent.map { it["type"] })
        val content = ((sent[0]["item"] as Map<*, *>)["content"] as List<*>).single() as Map<*, *>
        assertEquals(mapOf("type" to "input_text", "text" to "hello"), content)
    }

    @Test
    fun `openai has no audio pause message`() {
        assertNull(OpenAiRealtimeProtocol.audioPaused())
    }

    // ── Pricing and providers ───────────────────────────────────────────────────────────

    @Test
    fun `a call is priced per million tokens`() {
        val usd = RealtimePricing.usd("gpt-realtime-2.1", RealtimeUsage(audioIn = 1_000_000, audioOut = 1_000_000))!!
        assertEquals(96.0, usd, 1e-9)
        assertNull(RealtimePricing.usd("unknown-model", RealtimeUsage(audioIn = 1)))
    }

    @Test
    fun `the picked provider runs when ready, otherwise the first ready one`() {
        val keys = VoiceProviderKeys(elevenLabs = "el", elevenLabsVerified = false, gemini = "g", openAi = "")
        assertEquals(VoiceProvider.Gemini, keys.resolve(VoiceProvider.ElevenLabs))
        assertEquals(VoiceProvider.Gemini, keys.resolve(VoiceProvider.OpenAi))
        assertEquals(VoiceProvider.ElevenLabs, keys.copy(elevenLabsVerified = true).resolve(VoiceProvider.ElevenLabs))
        assertNull(VoiceProviderKeys().resolve(VoiceProvider.Gemini))
    }

    @Test
    fun `unknown saved voices and models fall back to the provider default`() {
        assertEquals("Puck", RealtimeCatalog.voiceOrDefault(VoiceProvider.Gemini, "Nope"))
        assertEquals("Kore", RealtimeCatalog.voiceOrDefault(VoiceProvider.Gemini, "kore"))
        assertEquals("marin", RealtimeCatalog.voiceOrDefault(VoiceProvider.OpenAi, null))
        assertEquals("gpt-realtime-2.1", RealtimeCatalog.modelOrDefault(VoiceProvider.OpenAi, "gpt-realtime"))
    }

    @Test
    fun `a reply cut off while playing keeps only the words heard`() {
        val text = "Sure, here are three ideas for the weekend trip."
        assertEquals(text, RealtimeVoiceSession.heardPart(text, playedMillis = 4_000, receivedMillis = 4_000))
        assertEquals(text, RealtimeVoiceSession.heardPart(text, playedMillis = 100, receivedMillis = 0))
        assertEquals("", RealtimeVoiceSession.heardPart(text, playedMillis = 0, receivedMillis = 4_000))
        assertEquals("Sure, here are three…", RealtimeVoiceSession.heardPart(text, playedMillis = 2_000, receivedMillis = 4_000))
        assertEquals("", RealtimeVoiceSession.heardPart(text, playedMillis = 300, receivedMillis = 4_000))
    }
}
