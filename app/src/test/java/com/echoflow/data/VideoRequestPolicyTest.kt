package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The request policy is the only thing standing between a user preference and a hard 400 from
 * OpenRouter, so every fallback path is pinned here.
 */
class VideoRequestPolicyTest {

    @Test
    fun `a model that declares no ratios is sent no ratio at all`() {
        assertNull(VideoRequestPolicy.resolveAspectRatio("16:9", emptyList()))
        assertNull(VideoRequestPolicy.resolveResolution("720p", emptyList()))
    }

    @Test
    fun `a supported preference is passed through untouched`() {
        assertEquals("9:16", VideoRequestPolicy.resolveAspectRatio("9:16", listOf("16:9", "9:16", "1:1")))
        assertEquals("1080p", VideoRequestPolicy.resolveResolution("1080p", listOf("720p", "1080p")))
    }

    @Test
    fun `an unsupported ratio falls back to one with the same orientation`() {
        // The user asked for portrait; 3:4 keeps that intent, 16:9 would flip the whole shot.
        assertEquals("3:4", VideoRequestPolicy.resolveAspectRatio("9:16", listOf("16:9", "3:4", "21:9")))
    }

    @Test
    fun `an unsupported ratio with no matching orientation still yields a supported value`() {
        val resolved = VideoRequestPolicy.resolveAspectRatio("9:16", listOf("16:9", "21:9"))
        assertTrue(resolved in listOf("16:9", "21:9"))
    }

    @Test
    fun `an unsupported resolution snaps to the closest the model offers`() {
        assertEquals("720p", VideoRequestPolicy.resolveResolution("1080p", listOf("480p", "720p")))
        assertEquals("480p", VideoRequestPolicy.resolveResolution("360p", listOf("480p", "1080p")))
    }

    @Test
    fun `aspect ratio strings convert to display ratios`() {
        assertEquals(16f / 9f, VideoRequestPolicy.aspectRatioValue("16:9"), 0.0001f)
        assertEquals(9f / 16f, VideoRequestPolicy.aspectRatioValue("9:16"), 0.0001f)
        assertEquals(1f, VideoRequestPolicy.aspectRatioValue("1:1"), 0.0001f)
    }

    @Test
    fun `malformed or missing ratios fall back to landscape instead of dividing by zero`() {
        assertEquals(16f / 9f, VideoRequestPolicy.aspectRatioValue(null), 0.0001f)
        assertEquals(16f / 9f, VideoRequestPolicy.aspectRatioValue("16:0"), 0.0001f)
        assertEquals(16f / 9f, VideoRequestPolicy.aspectRatioValue("wide"), 0.0001f)
    }

    @Test
    fun `orientation classifies the ratios the picker offers`() {
        assertEquals("landscape", VideoRequestPolicy.orientationOf("16:9"))
        assertEquals("portrait", VideoRequestPolicy.orientationOf("9:16"))
        assertEquals("square", VideoRequestPolicy.orientationOf("1:1"))
    }
}
