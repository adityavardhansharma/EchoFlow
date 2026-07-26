package com.echoflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomProviderCapabilitiesTest {
    @Test
    fun `xAI image capability allows documented multimodal model families`() {
        assertTrue(CustomProviderCapabilities.xAiSupportsImages("grok-4.3-latest"))
        assertTrue(CustomProviderCapabilities.xAiSupportsImages("grok-4.20"))
        assertTrue(CustomProviderCapabilities.xAiSupportsImages("grok-4.5"))
        assertTrue(CustomProviderCapabilities.xAiSupportsImages("grok-latest"))
    }

    @Test
    fun `xAI image capability rejects text-only and unknown models`() {
        assertFalse(CustomProviderCapabilities.xAiSupportsImages("grok-420-reasoning"))
        assertFalse(CustomProviderCapabilities.xAiSupportsImages("grok-code-fast-1"))
        assertFalse(CustomProviderCapabilities.xAiSupportsImages("future-model"))
        assertFalse(CustomProviderCapabilities.xAiSupportsImages(""))
    }
}
