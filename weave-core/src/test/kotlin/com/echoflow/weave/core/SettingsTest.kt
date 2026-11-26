package com.echoflow.weave.core

import com.echoflow.weave.core.prompt.EchoUiTiers
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.core.prompt.IntelligentUiLevel.BALANCED
import com.echoflow.weave.core.prompt.IntelligentUiLevel.IMMERSIVE
import com.echoflow.weave.core.prompt.IntelligentUiLevel.OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §11: settings logic that lives in weave-core. */
class SettingsTest {
    @Test fun `T-600 T-601 stored level`() {
        assertEquals(BALANCED, IntelligentUiLevel.fromStorage(null))
        assertEquals(BALANCED, IntelligentUiLevel.fromStorage("loud"))
    }

    @Test fun `T-603 to T-607 effective tier`() {
        assertEquals(3, EchoUiTiers.effectiveTier(BALANCED, 4, false, false, 4))
        assertEquals(1, EchoUiTiers.effectiveTier(IMMERSIVE, 1, false, false, 4))
        assertEquals(3, EchoUiTiers.effectiveTier(OFF, 3, true, false, 4))
        assertEquals(BALANCED, EchoUiTiers.promptLevel(OFF, true, false))
        assertEquals(1, EchoUiTiers.maxBlocks(EchoUiTiers.promptLevel(OFF, true, false)))
        assertEquals(3, EchoUiTiers.effectiveTier(OFF, 3, false, true, 4))
        assertEquals(2, EchoUiTiers.effectiveTier(IMMERSIVE, 4, false, false, 2))
        assertEquals(0, EchoUiTiers.maxBlocks(OFF))
    }

    @Test fun `T-608 explicit requests`() {
        listOf(
            "make a loan calculator", "show it as a table", "plot my expenses for March", "draw a chart of rainfall",
            "turn this into a checklist", "make this interactive", "build me a budget tracker",
            "show me how the table of contents works", "give me a chart of my spending", "put this in a table",
        ).forEach { assertTrue(it, EchoUiTiers.isExplicitUiRequest(it)) }
    }

    @Test fun `T-608b not explicit requests`() {
        listOf(
            "what's the capital of France", "any good tabletop games?", "the plot of the novel", "explain the periodic table",
            "how do I set a timer on my phone?", "is this form valid HTML?", "give up on this game", "lay the table for dinner",
            "my money is stuck in a card",
        ).forEach { assertFalse(it, EchoUiTiers.isExplicitUiRequest(it)) }
    }

    @Test fun `T-609 unknown models`() {
        assertEquals(2, EchoUiTiers.unknownModelTier(true, 128_000))
        assertEquals(1, EchoUiTiers.unknownModelTier(false, 16_000))
        assertEquals(0, EchoUiTiers.unknownModelTier(false, 4_000))
    }
}
