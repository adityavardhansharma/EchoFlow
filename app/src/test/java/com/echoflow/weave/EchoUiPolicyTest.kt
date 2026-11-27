package com.echoflow.weave

import com.echoflow.data.SystemPrompts
import com.echoflow.weave.compose.WeaveEffects
import com.echoflow.weave.compose.components.Inputs
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.runtime.EchoUiPromptCache
import com.echoflow.weave.runtime.WeaveRuntime
import com.echoflow.weave.runtime.tiers.EchoUiModelTiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** App-side Echo UI rules: prompt placement, tiers, levels, anti-spoofing. */
class EchoUiPolicyTest {

    @Test fun `T-505 the Weave section sits right before the freshness gate`() {
        val section = EchoUiPromptCache.section(2, IntelligentUiLevel.BALANCED)
        assertTrue(section.contains("```weave"))
        val prompt = SystemPrompts.build(false, "off", "Thursday, November 26, 2026", echoUi = section)
        val at = prompt.indexOf(section)
        assertTrue(at > 0)
        assertTrue("freshness gate must stay last", prompt.substring(at + section.length).trim().isNotEmpty())
        assertFalse(prompt.substring(at + section.length).contains("```weave"))
        assertEquals(SystemPrompts.build(false, "off", "Thursday, November 26, 2026"), SystemPrompts.build(false, "off", "Thursday, November 26, 2026", echoUi = ""))
    }

    @Test fun `tiers come from the table, local models are 0, overrides win`() {
        assertEquals(0, EchoUiModelTiers.tierFor("local/gemma", isLocal = true, override = 3))
        assertEquals(3, EchoUiModelTiers.tierFor("anthropic/claude-x", isLocal = false, override = null))
        assertEquals(1, EchoUiModelTiers.tierFor("openai/gpt-4o-mini", isLocal = false, override = null))
        assertEquals(2, EchoUiModelTiers.tierFor("someone/new-model", isLocal = false, override = null))
        assertEquals(1, EchoUiModelTiers.tierFor("someone/new-model", isLocal = false, override = 1))
        assertEquals(2, EchoUiModelTiers.tierFor("anthropic/claude-x", isLocal = false, override = null, downgraded = true))
    }

    @Test fun `levels decide the prompt and the block limit`() {
        val off = EchoUiModelTiers.plan(IntelligentUiLevel.OFF, 3, "what is the capital of France?")
        assertEquals(0, off.tier); assertEquals(0, off.maxBlocks)
        val explicit = EchoUiModelTiers.plan(IntelligentUiLevel.OFF, 3, "make me a tip calculator")
        assertTrue(explicit.tier > 0); assertEquals(IntelligentUiLevel.BALANCED, explicit.level); assertEquals(1, explicit.maxBlocks)
        assertEquals(3, EchoUiModelTiers.plan(IntelligentUiLevel.IMMERSIVE, 4, "hi").maxBlocks)
    }

    @Test fun `T-400 secret-shaped text fields are refused, addresses are not`() {
        for (l in listOf("Password", "ATM PIN", "ATM PIN code", "UPI PIN", "PIN number", "OTP", "Card number", "CVV")) assertTrue(l, Inputs.isSecretShaped(l, null))
        for (l in listOf("Pincode", "PIN code", "Postal code", "Spinner", "Pinned notes", "Name")) assertFalse(l, Inputs.isSecretShaped(l, null))
    }

    @Test fun `confirm sheet bolds the registrable domain`() {
        assertEquals("example.com", WeaveEffects.registrableDomain("login.secure.example.com"))
        assertEquals("bank.co.in", WeaveEffects.registrableDomain("netbanking.bank.co.in"))
        assertEquals("localhost", WeaveEffects.registrableDomain("localhost"))
    }

    @Test fun `stored sources always carry the block id`() {
        assertEquals("@weave 1 id: b2\nroot = Text(\"x\")", WeaveRuntime.withHeaderId("root = Text(\"x\")", "b2"))
        assertEquals("@weave 1 id: b3 title: \"T\"\nroot = Text(\"x\")", WeaveRuntime.withHeaderId("@weave 1 title: \"T\"\nroot = Text(\"x\")", "b3"))
        assertEquals("@weave 1 id: loan\nroot = Text(\"x\")", WeaveRuntime.withHeaderId("@weave 1 id: loan\nroot = Text(\"x\")", "b1"))
    }

    @Test fun `tier 3 and 4 prompts offer the later phases this build renders`() {
        val t3 = EchoUiPromptCache.section(3, IntelligentUiLevel.BALANCED)
        val t4 = EchoUiPromptCache.section(4, IntelligentUiLevel.IMMERSIVE)
        assertTrue(t3.contains("!ask") && t3.contains("lazy"))
        assertTrue(t4.contains("Canvas") && t4.contains("Quiz"))
        assertTrue("tier 4 prompt is ${t4.length} chars", t4.length < 60_000)
        println("Echo UI prompt sizes: tier 3 ${t3.length / 4} tokens, tier 4 ${t4.length / 4} tokens (approx.)")
    }
}
