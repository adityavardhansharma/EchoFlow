package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHttpSupportTest {
    @Test fun `joins versioned and ollama API paths`() {
        assertEquals("https://host/v1/chat/completions", ProviderHttpSupport.joinApiUrl("https://host", "chat/completions"))
        assertEquals("https://host/v1/models", ProviderHttpSupport.joinApiUrl("https://host/v1/", "/models"))
        assertEquals("http://localhost:11434/api/chat", ProviderHttpSupport.joinApiUrl("http://localhost:11434", "api/chat"))
    }

    @Test fun `allows local HTTP but requires HTTPS for public hosts`() {
        assertTrue(ProviderHttpSupport.validateBaseUrl("http://127.0.0.1:11434").ok)
        assertTrue(ProviderHttpSupport.validateBaseUrl("http://192.168.1.9:8080").ok)
        assertFalse(ProviderHttpSupport.validateBaseUrl("http://example.com").ok)
        assertTrue(ProviderHttpSupport.validateBaseUrl("https://example.com").ok)
    }

    @Test fun `parses common model collection shapes and removes duplicates`() {
        assertEquals(listOf("a", "b"), ProviderHttpSupport.parseModelIds("""{"data":[{"id":"a"},{"name":"b"},{"id":"a"}]}"""))
        assertEquals(listOf("llama", "phi"), ProviderHttpSupport.parseModelIds("""{"models":[{"model":"llama"},"phi"]}"""))
        assertEquals(emptyList<String>(), ProviderHttpSupport.parseModelIds("not json"))
    }

    @Test fun `keeps only language models from a typed model list`() {
        val body = """{"object":"list","data":[
            {"id":"anthropic/claude-opus-5","type":"language"},
            {"id":"openai/text-embedding-3-small","type":"embedding"},
            {"id":"google/imagen-4","type":"image"},
            {"id":"openai/gpt-5"}
        ]}"""
        assertEquals(listOf("anthropic/claude-opus-5", "openai/gpt-5"), ProviderHttpSupport.parseLanguageModelIds(body))
    }

    @Test fun `keeps only chat models from Together's bare model array`() {
        val body = """[
            {"id":"meta-llama/Llama-3.3-70B-Instruct-Turbo","type":"chat"},
            {"id":"Qwen/Qwen2.5-Coder-32B-Instruct","type":"code"},
            {"id":"black-forest-labs/FLUX.1-schnell","type":"image"},
            {"id":"BAAI/bge-large-en-v1.5","type":"embedding"}
        ]"""
        assertEquals(
            listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Qwen/Qwen2.5-Coder-32B-Instruct"),
            ProviderHttpSupport.parseTogetherChatModelIds(body),
        )
    }

    @Test fun `keeps current Gemini series and newer chat models and drops older and non-chat ones`() {
        val body = """{"models":[
            {"name":"models/gemini-2.5-pro","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-pro-latest","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-4-argon","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.1-pro-preview","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-9-flash-preview-01-2030","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.10-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.8-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.7-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.5-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3-pro-preview","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.7-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.1-flash-lite","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.8-live","supportedGenerationMethods":["bidiGenerateContent"]},
            {"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-nano-banana-2.1","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-3.5-transcribe","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemma-3-27b-it","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-preview-tts","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-image","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-native-audio-latest","supportedGenerationMethods":["bidiGenerateContent"]},
            {"name":"models/gemini-2.5-computer-use-preview-10-2025","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-robotics-er-1.5-preview","supportedGenerationMethods":["generateContent"]},
            {"name":"models/lyria-realtime-exp","supportedGenerationMethods":["bidiGenerateMusic"]},
            {"name":"models/imagen-4.0-generate-001","supportedGenerationMethods":["predict"]},
            {"name":"models/veo-3.0-generate-001","supportedGenerationMethods":["predictLongRunning"]},
            {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]},
            {"name":"models/aqa","supportedGenerationMethods":["generateAnswer"]},
            {"name":"models/gemini-2.0-flash","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-1.5-pro","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-preview-09-2025","supportedGenerationMethods":["generateContent"]},
            {"name":"models/gemini-2.5-flash-lite","description":"Deprecated: use gemini-flash-lite-latest.","supportedGenerationMethods":["generateContent"]}
        ],"nextPageToken":"abc"}"""
        assertEquals(
            listOf(
                "gemini-flash-latest", "gemini-pro-latest", "gemini-4-argon", "gemini-3.8-flash",
                "gemini-9-flash-preview-01-2030", "gemini-3.10-flash", "gemini-3.8-flash-lite", "gemini-3.6-flash", "gemini-3.7-flash-lite",
                "gemini-3.7-flash", "gemma-3-27b-it",
            ),
            ProviderHttpSupport.parseGeminiChatModelIds(body),
        )
        assertEquals("abc", ProviderHttpSupport.geminiNextPageToken(body))
        assertEquals(null, ProviderHttpSupport.geminiNextPageToken("""{"models":[]}"""))
    }

    @Test fun `reads model names from Workers AI model search`() {
        val body = """{"success":true,"result":[
            {"id":"a1","name":"@cf/meta/llama-3.1-8b-instruct","task":{"name":"Text Generation"}},
            {"id":"a2","name":"@cf/openai/gpt-oss-120b","task":{"name":"Text Generation"}}
        ]}"""
        assertEquals(
            listOf("@cf/meta/llama-3.1-8b-instruct", "@cf/openai/gpt-oss-120b"),
            ProviderHttpSupport.parseCloudflareModelNames(body),
        )
    }

    @Test fun `maps provider errors with status precedence`() {
        assertEquals("OpenAI rejected the API key or request.", ProviderHttpSupport.errorMessage("OpenAI", 401, "{}"))
        assertEquals("bad input", ProviderHttpSupport.errorMessage("OpenAI", 400, """{"error":{"message":"bad input"}}"""))
        assertEquals("OpenAI returned HTTP 500.", ProviderHttpSupport.errorMessage("OpenAI", 500, ""))
    }
}
