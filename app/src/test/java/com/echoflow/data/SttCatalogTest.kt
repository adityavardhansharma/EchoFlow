package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Curated STT catalog prices are user-facing and must stay in sync with OpenRouter listings.
 * Update both the catalog strings and these expectations when OpenRouter changes STT pricing.
 *
 * Reference (as of 2026-09, shown per hour):
 * - microsoft/mai-transcribe-2 = $0.10/hour
 * - meta/muse-voice-transcribe-1.0 = $0.18/hour
 * - openai/gpt-transcribe = $0.0045/min = $0.27/hour
 * - x-ai/grok-stt-1.0 = $0.10/hour
 * - nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b = $0.000003/sec = $0.0108/hour
 * - google/chirp-3 = $0.016/min = $0.96/hour
 * - saaras:v4 = ₹30/hour ≈ $0.36/hour
 */
class SttCatalogTest {

    @Test fun `catalog lists the curated cloud models`() {
        assertEquals(
            listOf(
                "microsoft/mai-transcribe-2",
                "meta/muse-voice-transcribe-1.0",
                "openai/gpt-transcribe",
                "x-ai/grok-stt-1.0",
                "nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b",
                "google/chirp-3",
            ),
            SttCatalog.CLOUD_MODELS.map { it.id },
        )
    }

    @Test fun `user-facing prices match current OpenRouter STT listings`() {
        assertEquals("~\$0.10 / hr", SttCatalog.byId("microsoft/mai-transcribe-2")!!.pricing)
        assertEquals("~\$0.27 / hr", SttCatalog.byId("openai/gpt-transcribe")!!.pricing)
        assertEquals("~\$0.10 / hr", SttCatalog.byId("x-ai/grok-stt-1.0")!!.pricing)
        assertEquals(
            "~\$0.0108 / hr",
            SttCatalog.byId("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b")!!.pricing,
        )
        assertEquals("~\$0.96 / hr", SttCatalog.byId("google/chirp-3")!!.pricing)
        assertEquals("~\$0.36 / hr", SttCatalog.SARVAM_MODEL.pricing)
    }

    @Test fun `displayed hourly price matches the numeric rate behind the dollar tier`() {
        // `pricing` is rendered directly while `costTier` derives from `usdPerMinute`;
        // keep them equivalent so a manual update cannot show one price and tag another.
        for (model in SttCatalog.CLOUD_MODELS + SttCatalog.SARVAM_MODEL) {
            val displayed =
                model.pricing
                    .removePrefix("~\$")
                    .removeSuffix(" / hr")
                    .toDouble()
            assertEquals(model.usdPerMinute * 60.0, displayed, 0.0051)
        }
    }

    @Test fun `dollar tags follow OpenRouter per-minute price`() {
        assertEquals(SttCostTier.Moderate, SttCatalog.byId("microsoft/mai-transcribe-2")!!.costTier)
        assertEquals(SttCostTier.Moderate, SttCatalog.byId("openai/gpt-transcribe")!!.costTier)
        assertEquals(SttCostTier.Cheap, SttCatalog.byId("x-ai/grok-stt-1.0")!!.costTier)
        assertEquals(
            SttCostTier.Cheap,
            SttCatalog.byId("nvidia/nemotron-3.5-asr-streaming-multilingual-0.6b")!!.costTier,
        )
        assertEquals(SttCostTier.Expensive, SttCatalog.byId("google/chirp-3")!!.costTier)
        assertEquals(SttCostTier.Moderate, SttCatalog.SARVAM_MODEL.costTier)
        assertTrue(SttCatalog.SARVAM_MODEL.showCostTier)
        assertEquals(1, SttCostTier.Cheap.dollars)
        assertEquals(2, SttCostTier.Moderate.dollars)
        assertEquals(3, SttCostTier.Expensive.dollars)
    }

    @Test fun `exactly one model carries the Best badge and it is MAI Transcribe 2`() {
        val best = SttCatalog.CLOUD_MODELS.filter { it.isBest }
        assertEquals(1, best.size)
        assertEquals("microsoft/mai-transcribe-2", best.single().id)
        assertEquals("MAI Transcribe 2", best.single().name)
        assertFalse(best.single().name.contains("1.0"))
    }

    @Test fun `cost-tier cutoffs sit between the catalog prices`() {
        assertEquals(SttCostTier.Cheap, SttCostTier.fromUsdPerMinute(0.00018))
        assertEquals(SttCostTier.Cheap, SttCostTier.fromUsdPerMinute(0.0017))
        assertEquals(SttCostTier.Moderate, SttCostTier.fromUsdPerMinute(0.003))
        assertEquals(SttCostTier.Moderate, SttCostTier.fromUsdPerMinute(0.0045))
        assertEquals(SttCostTier.Expensive, SttCostTier.fromUsdPerMinute(0.016))
        assertEquals(SttCostTier.Moderate, SttCostTier.fromUsdPerMinute(0.006))
    }

    @Test fun `default is MAI Transcribe 2 and sits first so unknown ids fall through to it`() {
        assertEquals("microsoft/mai-transcribe-2", SttCatalog.DEFAULT_MODEL_ID)
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
