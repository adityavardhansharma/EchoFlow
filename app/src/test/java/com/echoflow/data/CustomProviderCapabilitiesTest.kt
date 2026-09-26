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

    @Test
    fun `Claude sampling stays on models that still accept temperature`() {
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-sonnet-4-6"))
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-sonnet-4-5"))
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-opus-4-6"))
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-haiku-4-5-20251001"))
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-3-7-sonnet-20250219"))
        assertTrue(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun `Claude sampling is omitted on 4_7 and later`() {
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-opus-4-7"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-opus-4.8"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-opus-4-8"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-opus-5"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-sonnet-5"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-fable-5"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("claude-mythos-preview"))
        assertFalse(CustomProviderCapabilities.claudeSupportsSamplingParams("anthropic/claude-opus-4-8"))
    }

    @Test
    fun `Claude payload omits temperature only when sampling is unsupported`() {
        val params = InferenceParams(temperature = 0.7f, topK = 0, topP = 1f, maxTokens = 256)
        val withTemp = mutableMapOf<String, Any?>()
        CustomProviderCapabilities.putClaudeSampling(withTemp, "claude-sonnet-4-6", params)
        assertTrue(withTemp.containsKey("temperature"))

        val withoutTemp = mutableMapOf<String, Any?>()
        CustomProviderCapabilities.putClaudeSampling(withoutTemp, "claude-opus-4-8", params)
        assertFalse(withoutTemp.containsKey("temperature"))
    }

    @Test
    fun `Vercel gateway drops sampling only for Claude models that reject it`() {
        val gateway = CustomProviderConfig.VERCEL_BASE_URL
        fun payload() = mutableMapOf<String, Any>("temperature" to 0.7f, "top_p" to 0.9f)

        val opus = payload().also { CustomProviderCapabilities.dropGatewaySampling(it, gateway, "anthropic/claude-opus-5") }
        assertFalse("temperature" in opus || "top_p" in opus)

        val sonnet45 = payload().also { CustomProviderCapabilities.dropGatewaySampling(it, gateway, "anthropic/claude-sonnet-4.5") }
        assertTrue("temperature" in sonnet45 && "top_p" in sonnet45)

        val gpt = payload().also { CustomProviderCapabilities.dropGatewaySampling(it, gateway, "openai/gpt-5") }
        assertTrue("temperature" in gpt)

        val otherHost = payload().also { CustomProviderCapabilities.dropGatewaySampling(it, "http://localhost:1234/v1", "claude-opus-5") }
        assertTrue("temperature" in otherHost)
    }

    @Test
    fun `open-model hosts send images only to known vision families`() {
        assertTrue(CustomProviderCapabilities.openModelSupportsImages("meta-llama/llama-4-scout-17b-16e-instruct"))
        assertTrue(CustomProviderCapabilities.openModelSupportsImages("@cf/meta/llama-3.2-11b-vision-instruct"))
        assertTrue(CustomProviderCapabilities.openModelSupportsImages("Qwen/Qwen2.5-VL-72B-Instruct"))
        assertFalse(CustomProviderCapabilities.openModelSupportsImages("llama-3.3-70b-versatile"))
        assertFalse(CustomProviderCapabilities.openModelSupportsImages("@cf/openai/gpt-oss-120b"))
    }

    @Test
    fun `Cloudflare base URL carries the account ID`() {
        assertTrue(
            CustomProviderConfig.cloudflareBaseUrl(" abc123 ") ==
                "https://api.cloudflare.com/client/v4/accounts/abc123/ai/v1"
        )
    }
}
