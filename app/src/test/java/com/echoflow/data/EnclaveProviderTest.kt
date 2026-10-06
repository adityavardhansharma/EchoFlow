package com.echoflow.data

import com.echoflow.ui.CustomProviderModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnclaveProviderTest {
    private val config = CustomProviderConfig(
        cloudApisEnabled = true,
        enclaveEnabled = true,
        enclaveApiKey = "enclave-key",
        enclaveModel = "auto",
        enclaveSelectedModels = "auto\nqwen3-coder",
    )

    @Test fun `selected Enclave models surface once under the Enclave group`() {
        val entries = CustomProviderModelCatalog.entries(config)
        assertEquals(listOf("custom/enclave/auto", "custom/enclave/qwen3-coder"), entries.map { it.id })
        assertTrue(entries.all { it.group == "Enclave" && !it.isLocalLike })
    }

    @Test fun `Enclave models stay hidden when the brand or cloud APIs are off`() {
        assertTrue(CustomProviderModelCatalog.entries(config.copy(enclaveEnabled = false)).isEmpty())
        assertTrue(CustomProviderModelCatalog.entries(config.copy(cloudApisEnabled = false)).isEmpty())
    }

    @Test fun `Enclave points at its OpenAI-compatible router`() {
        assertEquals("https://router.enclave.ai/v1", CustomProviderConfig.ENCLAVE_BASE_URL)
    }
}
