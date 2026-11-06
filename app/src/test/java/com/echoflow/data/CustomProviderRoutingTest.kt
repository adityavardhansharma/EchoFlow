package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class CustomProviderRoutingTest {
    @Test fun `every direct prefix routes to its provider and strips to the API model id`() {
        val cases = mapOf(
            CustomProviderConfig.PREFIX_OPENAI to "openai",
            CustomProviderConfig.PREFIX_CLAUDE to "claude",
            CustomProviderConfig.PREFIX_GEMINI to "gemini",
            CustomProviderConfig.PREFIX_CEREBRAS to "cerebras",
            CustomProviderConfig.PREFIX_SARVAM to "sarvam",
            CustomProviderConfig.PREFIX_XAI to "xai",
            CustomProviderConfig.PREFIX_VERCEL to "vercel",
            CustomProviderConfig.PREFIX_GROQ to "groq",
            CustomProviderConfig.PREFIX_TOGETHER to "together",
            CustomProviderConfig.PREFIX_ENCLAVE to "enclave",
            CustomProviderConfig.PREFIX_EXPERIENTIAL to "experiential",
            CustomProviderConfig.PREFIX_CLOUDFLARE to "cloudflare",
            CustomProviderConfig.PREFIX_OLLAMA to "ollama",
            CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE to "openai-compatible",
        )
        for ((prefix, provider) in cases) {
            val picked = prefix + "some-model"
            assertEquals(prefix, provider, CustomProviderRouting.providerFor(picked))
            assertEquals(prefix, "some-model", CustomProviderRouting.requestModel(provider, picked))
        }
    }

    @Test fun `OpenRouter and on-device models aren't direct`() {
        assertNull(CustomProviderRouting.providerFor("openai/gpt-5"))
        assertNull(CustomProviderRouting.providerFor("local/gemma"))
        assertEquals("openai/gpt-5", CustomProviderRouting.requestModel(null, "openai/gpt-5"))
    }

    @Test fun `Sarvam and OpenRouter skip the raw attachment checks`() {
        assertFalse(CustomProviderRouting.sendsRawAttachments("sarvam"))
        assertFalse(CustomProviderRouting.sendsRawAttachments(null))
        assertTrue(CustomProviderRouting.sendsRawAttachments("groq"))
    }

    @Test fun `messages point at the right settings page`() {
        assertEquals("PDF is off for this custom endpoint. Turn it on in Settings → Echo Labs → Custom API Endpoint.",
            CustomProviderRouting.pdfOffMessage("ollama"))
        assertEquals("PDF is off for this custom endpoint. Turn it on in Settings → Custom.",
            CustomProviderRouting.pdfOffMessage("cerebras"))
        assertTrue(CustomProviderRouting.imageOffMessage("together", "m").contains("Llama 4"))
        assertTrue(CustomProviderRouting.imageOffMessage("xai", "m").contains("grok"))
    }
}
