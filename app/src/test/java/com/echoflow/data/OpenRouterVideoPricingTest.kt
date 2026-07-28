package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OpenRouter publishes video pricing under several SKU spellings in one directory response,
 * in two different units and sliced by resolution and/or audio. Getting that wrong would show
 * users a price 100× off, or quote a variant they are not buying.
 */
class OpenRouterVideoPricingTest {

    @Test fun `cents skus normalize to dollars per second, keyed by resolution`() {
        // xAI's shape.
        val prices = OpenRouterVideoModelDirectory.parsePricing(
            mapOf(
                "cents_per_image_input" to "1",
                "cents_per_video_output_second_480p" to "8",
                "cents_per_video_output_second_720p" to "14",
                "cents_per_video_output_second_1080p" to "25",
            )
        )
        assertEquals(3, prices.size)
        assertEquals(0.08, prices.first { it.resolution == "480p" }.usdPerSecond, 0.0001)
        assertEquals(0.14, prices.first { it.resolution == "720p" }.usdPerSecond, 0.0001)
        assertEquals(0.25, prices.first { it.resolution == "1080p" }.usdPerSecond, 0.0001)
        assertTrue(prices.all { it.audio == null })
    }

    @Test fun `dollar skus are already per second`() {
        // Sora's shape: resolution only, no audio split.
        val prices = OpenRouterVideoModelDirectory.parsePricing(
            mapOf("duration_seconds_720p" to "0.30", "duration_seconds_1080p" to "0.50")
        )
        assertEquals(0.30, prices.first { it.resolution == "720p" }.usdPerSecond, 0.0001)
        assertEquals(0.50, prices.first { it.resolution == "1080p" }.usdPerSecond, 0.0001)
    }

    @Test fun `audio variants are split out, with and without a resolution`() {
        // Veo's shape — the awkward one: audio prefixes, sometimes with a resolution suffix.
        val prices = OpenRouterVideoModelDirectory.parsePricing(
            mapOf(
                "duration_seconds_with_audio" to "0.12",
                "duration_seconds_without_audio" to "0.10",
                "duration_seconds_with_audio_720p" to "0.10",
                "duration_seconds_without_audio_720p" to "0.08",
                "duration_seconds_with_audio_4k" to "0.30",
                "duration_seconds_without_audio_4k" to "0.25",
            )
        )
        assertEquals(0.12, prices.first { it.audio == true && it.resolution == null }.usdPerSecond, 0.0001)
        assertEquals(0.08, prices.first { it.audio == false && it.resolution == "720p" }.usdPerSecond, 0.0001)
        assertEquals(0.30, prices.first { it.audio == true && it.resolution == "4K" }.usdPerSecond, 0.0001)
    }

    @Test fun `unrecognized and unparseable skus are ignored rather than shown as free`() {
        val prices = OpenRouterVideoModelDirectory.parsePricing(
            mapOf(
                "cents_per_image_input" to "1",
                "duration_seconds_720p" to "not-a-number",
                "cents_per_video_output_second_480p" to "5",
            )
        )
        assertEquals(1, prices.size)
        assertEquals("480p", prices.single().resolution)
    }

    @Test fun `the hint quotes the variant the user actually configured`() {
        val veo = OpenRouterVideoModelInfo(
            id = "google/veo-3.1-fast",
            name = "Veo 3.1 Fast",
            prices = OpenRouterVideoModelDirectory.parsePricing(
                mapOf(
                    "duration_seconds_with_audio_720p" to "0.10",
                    "duration_seconds_without_audio_720p" to "0.08",
                    "duration_seconds_with_audio_4k" to "0.30",
                )
            ),
        )
        assertEquals("~$0.10/sec at 720p, with audio", veo.priceHint("720p", audio = true))
        assertEquals("~$0.08/sec at 720p, without audio", veo.priceHint("720p", audio = false))
        // 1080p is unpriced for this model: fall back rather than showing nothing.
        assertEquals(0.08, veo.bestPrice("1080p", audio = false)!!.usdPerSecond, 0.0001)
    }

    @Test fun `an unpriced model has no hint instead of a zero`() {
        assertNull(OpenRouterVideoModelInfo(id = "x/y", name = "Y").priceHint("720p"))
        assertNull(OpenRouterVideoModelInfo(id = "x/y", name = "Y").pricePerSecond("720p"))
    }

    @Test fun `the bare figure drops qualifiers the surrounding label already carries`() {
        val veo = OpenRouterVideoModelInfo(
            id = "google/veo-3.1-fast",
            name = "Veo 3.1 Fast",
            prices = OpenRouterVideoModelDirectory.parsePricing(
                mapOf(
                    "duration_seconds_with_audio_720p" to "0.10",
                    "duration_seconds_without_audio_720p" to "0.08",
                )
            ),
        )
        // Under a button that says 720p, "at 720p, without audio" is the button read back.
        assertEquals("~$0.08/sec", veo.pricePerSecond("720p", audio = false))
        assertEquals("~$0.10/sec", veo.pricePerSecond("720p", audio = true))
    }

    @Test fun `first frame support is read from the declared frame image types`() {
        assertTrue(
            OpenRouterVideoModelInfo(id = "x/y", name = "Y", frameImageTypes = listOf("first_frame"))
                .supportsFirstFrame
        )
        assertFalse(OpenRouterVideoModelInfo(id = "x/y", name = "Y").supportsFirstFrame)
    }
}
