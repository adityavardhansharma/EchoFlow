package com.echoflow.data

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiDictationTest {
    @Test fun `request posts inline WAV to the interactions endpoint with the Gemini key`() {
        val request = GeminiDictation.request(" gm-key ", "UklGRg==")
        assertEquals("https://generativelanguage.googleapis.com/v1beta/interactions", request.url.toString())
        assertEquals("gm-key", request.header("x-goog-api-key"))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertTrue(body.contains("\"model\":\"gemini-3.5-transcribe\""))
        assertTrue(body.contains("\"type\":\"audio\""))
        assertTrue(body.contains("\"data\":\"UklGRg==\""))
        assertTrue(body.contains("\"mime_type\":\"audio/wav\""))
        assertFalse(body.contains("generation_config"))
    }

    @Test fun `vocabulary and Rambler go in the transcription config`() {
        val body = GeminiDictation.requestBody("x", listOf("EchoFlow", " echoflow ", "Jyoti"), rambler = true)
        @Suppress("UNCHECKED_CAST")
        val config = (body["generation_config"] as Map<String, Any>)["transcription_config"] as Map<String, Any>
        assertEquals(listOf("EchoFlow", "Jyoti"), config["custom_vocabulary"])
        assertEquals("smart", config["mode"])

        @Suppress("UNCHECKED_CAST")
        val plainConfig = GeminiDictation.requestBody("x", listOf("EchoFlow"))["generation_config"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val plain = plainConfig["transcription_config"] as Map<String, Any>
        assertFalse(plain.containsKey("mode"))
    }

    @Test fun `reads the transcript from model output steps`() {
        val body = """{"id":"interactions/1","status":"completed","steps":[
            {"id":"s1","type":"model_output","content":[{"type":"text","text":" Meet Jyoti at 4 pm. ","annotations":[]}]}
        ]}"""
        assertEquals("Meet Jyoti at 4 pm.", GeminiDictation.parseTranscript(body))
        assertNull(GeminiDictation.parseTranscript("""{"steps":[{"type":"model_output","content":[{"type":"text","text":"  "}]}]}"""))
        assertNull(GeminiDictation.parseTranscript("not json"))
    }
}
