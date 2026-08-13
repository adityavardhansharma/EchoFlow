package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request-level coverage for the curated STT default. [SttCatalogTest] only sees the catalog;
 * a swapped default that never reaches `/audio/transcriptions`, or a GPT Transcribe body we
 * fail to parse, would pass that suite and break only on a real dictation.
 */
class SttRequestTest {

    @Test fun `the catalog default is the model id posted on the transcription request`() {
        val body = SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA==")
        assertEquals("openai/gpt-transcribe", body["model"])
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, body["model"])
        val audio = body["input_audio"] as Map<*, *>
        assertEquals("wav", audio["format"])
        assertEquals("dGVzdA==", audio["data"])
    }

    @Test fun `encoded body still carries the GPT Transcribe model id`() {
        val json = SttPayloads.encode(SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA=="))
        assertTrue(json.contains("\"model\":\"openai/gpt-transcribe\""))
    }

    @Test fun `parses the OpenRouter text field GPT Transcribe returns`() {
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
}
