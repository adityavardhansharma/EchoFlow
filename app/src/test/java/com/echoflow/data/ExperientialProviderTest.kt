package com.echoflow.data

import com.echoflow.ui.CustomProviderModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperientialProviderTest {
    private val config = CustomProviderConfig(
        cloudApisEnabled = true,
        experientialEnabled = true,
        experientialApiKey = "xpl_key",
        experientialModel = "qwen3.8-27b",
        experientialSelectedModels = "qwen3.8-27b\nclaude-fable-5.1",
    )

    @Test fun `selected Experiential models surface once under the Experiential Labs group`() {
        val entries = CustomProviderModelCatalog.entries(config)
        assertEquals(listOf("custom/experiential/qwen3.8-27b", "custom/experiential/claude-fable-5.1"), entries.map { it.id })
        assertTrue(entries.all { it.group == "Experiential Labs" && !it.isLocalLike })
    }

    @Test fun `Experiential models stay hidden when the brand or cloud APIs are off`() {
        assertTrue(CustomProviderModelCatalog.entries(config.copy(experientialEnabled = false)).isEmpty())
        assertTrue(CustomProviderModelCatalog.entries(config.copy(cloudApisEnabled = false)).isEmpty())
    }

    @Test fun `Experiential points at its OpenAI-compatible gateway with native tools`() {
        assertEquals("https://api.experientiallabs.ai/v1", CustomProviderConfig.EXPERIENTIAL_BASE_URL)
        assertTrue(CustomProviderCapabilities.hasNativeToolCalling("experiential"))
    }
}
