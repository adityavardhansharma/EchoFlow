package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Curated STT catalog prices are user-facing and must stay in sync with OpenRouter listings.
 * Update both the catalog strings and these expectations when OpenRouter changes STT pricing.
 *
 * Reference (as of 2026-08):
 * - openai/gpt-transcribe = $0.0045/min
 * - x-ai/grok-stt-1.0 = $0.10/hour ≈ $0.0017/min
 * - nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b = $0.000003/sec = $0.00018/min
 * - google/chirp-3 = $0.016/min
 *
 * Artificial Analysis AA-WER (non-streaming): GPT Transcribe 3.3%, Grok STT 4.0%.
 */
class SttCatalogTest {

    @Test fun `catalog lists the curated cloud models`() {
        assertEquals(
            listOf(
                "openai/gpt-transcribe",
                "x-ai/grok-stt-1.0",
                "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b",
                "google/chirp-3",
            ),
            SttCatalog.CLOUD_MODELS.map { it.id },
        )
    }

    @Test fun `user-facing prices match current OpenRouter STT listings`() {
        assertEquals("~\$0.0045 / min", SttCatalog.byId("openai/gpt-transcribe")!!.pricing)
        assertEquals("~\$0.0017 / min", SttCatalog.byId("x-ai/grok-stt-1.0")!!.pricing)
        assertEquals(
            "~\$0.00018 / min",
            SttCatalog.byId("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b")!!.pricing,
        )
        assertEquals("~\$0.016 / min", SttCatalog.byId("google/chirp-3")!!.pricing)
    }

    @Test fun `dollar tags follow OpenRouter per-minute price`() {
        assertEquals(SttCostTier.Moderate, SttCatalog.byId("openai/gpt-transcribe")!!.costTier)
        assertEquals(SttCostTier.Cheap, SttCatalog.byId("x-ai/grok-stt-1.0")!!.costTier)
        assertEquals(
            SttCostTier.Cheap,
            SttCatalog.byId("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b")!!.costTier,
        )
        assertEquals(SttCostTier.Expensive, SttCatalog.byId("google/chirp-3")!!.costTier)
        assertEquals(1, SttCostTier.Cheap.dollars)
        assertEquals(2, SttCostTier.Moderate.dollars)
        assertEquals(3, SttCostTier.Expensive.dollars)
    }

    @Test fun `exactly one model carries the Best eval badge and it is GPT Transcribe`() {
        val best = SttCatalog.CLOUD_MODELS.filter { it.isBest }
        assertEquals(1, best.size)
        assertEquals("openai/gpt-transcribe", best.single().id)
    }

    @Test fun `cost-tier cutoffs sit between the catalog prices`() {
        assertEquals(SttCostTier.Cheap, SttCostTier.fromUsdPerMinute(0.00018))
        assertEquals(SttCostTier.Cheap, SttCostTier.fromUsdPerMinute(0.0017))
        assertEquals(SttCostTier.Moderate, SttCostTier.fromUsdPerMinute(0.0045))
        assertEquals(SttCostTier.Expensive, SttCostTier.fromUsdPerMinute(0.016))
    }

    @Test fun `default is GPT Transcribe and sits first so unknown ids fall through to it`() {
        assertEquals("openai/gpt-transcribe", SttCatalog.DEFAULT_MODEL_ID)
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.CLOUD_MODELS.first().id)
        assertNotNull(SttCatalog.byId(SttCatalog.DEFAULT_MODEL_ID))
        assertTrue(SttCatalog.CLOUD_MODELS.first().isBest)
    }

    @Test fun `resolve falls back to the default model for blank or unknown ids`() {
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.resolve("").id)
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.resolve("gone/model").id)
        assertEquals(SttCatalog.DEFAULT_MODEL_ID, SttCatalog.resolve("fish-audio/transcribe-1").id)
        assertEquals("x-ai/grok-stt-1.0", SttCatalog.resolve("x-ai/grok-stt-1.0").id)
        assertEquals(
            "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b",
            SttCatalog.resolve("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b").id,
        )
    }
}
