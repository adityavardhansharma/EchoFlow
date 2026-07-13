package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LocalImagePromptComposerTest {
    @Test fun `activation phrase prefixes the prompt without duplicating`() {
        assertEquals(
            "analog style, a portrait at dusk",
            LocalImagePromptComposer.composePrompt("analog style", "a portrait at dusk"),
        )
        assertEquals(
            "analog style portrait",
            LocalImagePromptComposer.composePrompt("analog style", "analog style portrait"),
        )
        assertEquals("a castle", LocalImagePromptComposer.composePrompt(null, "a castle"))
    }

    @Test fun `negative prompts combine and blanks drop out`() {
        assertEquals(
            "blur, haze, low quality, extra fingers",
            LocalImagePromptComposer.composeNegativePrompt("blur, haze, low quality", "extra fingers"),
        )
        assertEquals("blur", LocalImagePromptComposer.composeNegativePrompt("blur", null))
        assertEquals("extra fingers", LocalImagePromptComposer.composeNegativePrompt("  ", "extra fingers"))
        assertEquals("", LocalImagePromptComposer.composeNegativePrompt(null, null))
    }

    @Test fun `iterations clamp to the supported range`() {
        assertEquals(10, LocalImagePromptComposer.clampIterations(1))
        assertEquals(20, LocalImagePromptComposer.clampIterations(20))
        assertEquals(30, LocalImagePromptComposer.clampIterations(400))
    }

    @Test fun `fixed seed passes through and random draws fresh non-negative seeds`() {
        assertEquals(7, LocalImagePromptComposer.resolveSeed(7))
        val random = Random(42)
        val first = LocalImagePromptComposer.resolveSeed(null, random)
        val second = LocalImagePromptComposer.resolveSeed(null, random)
        assertTrue(first >= 0 && second >= 0)
        assertNotEquals(first, second)
    }
}
