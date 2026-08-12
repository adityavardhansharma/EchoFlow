package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Curated STT catalog prices are user-facing and must stay in sync with OpenRouter listings.
 * Update both the catalog strings and these expectations when OpenRouter changes STT pricing.
 *
 * Reference (as of 2026-08):
 * - fish-audio/transcribe-1 ≈ \$0.006/min (\$0.0001/s duration billing)
 * - x-ai/grok-stt-1.0 = \$0.10/hour ≈ \$0.0017/min
 * - google/chirp-3 = \$0.016/min
 */
class SttCatalogTest {

    @Test fun `catalog lists the three curated cloud models`() {
        assertEquals(
            listOf("fish-audio/transcribe-1", "x-ai/grok-stt-1.0", "google/chirp-3"),
            SttCatalog.CLOUD_MODELS.map { it.id },
        )
    }

    @Test fun `user-facing prices match current OpenRouter STT listings`() {
        assertEquals("~\$0.006 / min", SttCatalog.byId("fish-audio/transcribe-1")!!.pricing)
        assertEquals("~\$0.0017 / min", SttCatalog.byId("x-ai/grok-stt-1.0")!!.pricing)
        assertEquals("~\$0.016 / min", SttCatalog.byId("google/chirp-3")!!.pricing)
    }

    @Test fun `resolve falls back to the default model for blank or unknown ids`() {
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.resolve("").id)
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.resolve("gone/model").id)
        assertNotNull(SttCatalog.byId(SttCatalog.DEFAULT_MODEL_ID))
        assertEquals("x-ai/grok-stt-1.0", SttCatalog.resolve("x-ai/grok-stt-1.0").id)
    }
}
