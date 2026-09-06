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

    @Test fun `requires HTTPS for every provider endpoint`() {
        assertFalse(ProviderHttpSupport.validateBaseUrl("http://127.0.0.1:11434").ok)
        assertFalse(ProviderHttpSupport.validateBaseUrl("http://192.168.1.9:8080").ok)
        assertTrue(ProviderHttpSupport.validateBaseUrl("https://example.com").ok)
    }

    @Test fun `parses common model collection shapes and removes duplicates`() {
        assertEquals(listOf("a", "b"), ProviderHttpSupport.parseModelIds("""{"data":[{"id":"a"},{"name":"b"},{"id":"a"}]}"""))
        assertEquals(listOf("llama", "phi"), ProviderHttpSupport.parseModelIds("""{"models":[{"model":"llama"},"phi"]}"""))
        assertEquals(emptyList<String>(), ProviderHttpSupport.parseModelIds("not json"))
    }

    @Test fun `maps provider errors with status precedence`() {
        assertEquals("OpenAI rejected the API key or request.", ProviderHttpSupport.errorMessage("OpenAI", 401, "{}"))
        assertEquals("bad input", ProviderHttpSupport.errorMessage("OpenAI", 400, """{"error":{"message":"bad input"}}"""))
        assertEquals("OpenAI returned HTTP 500.", ProviderHttpSupport.errorMessage("OpenAI", 500, ""))
    }
}
