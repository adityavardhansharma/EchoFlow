package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request-level coverage for the curated STT default. [SttCatalogTest] only sees the catalog;
 * a swapped default that never reaches `/audio/transcriptions`, or an MAI Transcribe body we
 * fail to parse, would pass that suite and break only on a real dictation.
 */
class SttRequestTest {

    @Test fun `the catalog default is the model id posted on the transcription request`() {
        val body = SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA==")
        assertEquals("microsoft/mai-transcribe-2", body["model"])
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, body["model"])
        val audio = body["input_audio"] as Map<*, *>
        assertEquals("wav", audio["format"])
        assertEquals("dGVzdA==", audio["data"])
    }

    @Test fun `encoded body defaults MAI Transcribe to simple style`() {
        val json = SttPayloads.encode(SttPayloads.requestBody(SttCatalog.DEFAULT_MODEL_ID, "dGVzdA=="))
        assertTrue(json.contains("\"model\":\"microsoft/mai-transcribe-2\""))
        assertTrue(json.contains("\"transcribeStyle\":\"${SttPayloads.DEFAULT_TRANSCRIBE_STYLE}\""))
    }

    @Test fun `clean style remains available as an explicit option`() {
        val json = SttPayloads.encode(
            SttPayloads.requestBody(
                SttCatalog.DEFAULT_MODEL_ID,
                "dGVzdA==",
                SttPayloads.CLEAN_TRANSCRIBE_STYLE,
            ),
        )
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
}
