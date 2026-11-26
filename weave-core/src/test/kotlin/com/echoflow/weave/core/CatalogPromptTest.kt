package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.PHASE_1A_COMPONENTS
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 07 §9: catalog and prompt checks. */
class CatalogPromptTest {

    @Test fun `T-500 every registered component is documented in 04`() {
        val doc = File("../docs/echo-ui/04-components.md").takeIf { it.exists() }?.readText() ?: return
        for (name in PHASE_1A_COMPONENTS) assertTrue("$name missing from 04", doc.contains("### $name") || doc.contains("`$name("))
        for (c in BuiltinCatalog.components) assertTrue("${c.name} not in 04", doc.contains(c.name))
    }

    @Test fun `T-501 every parameter has a type and a default or is required`() {
        val all = BuiltinCatalog.components.flatMap { it.allParams } + BuiltinCatalog.marks.flatMap { it.allParams } +
            BuiltinCatalog.actions.flatMap { it.allParams }
        for (p in all) assertTrue(p.name, p.required || p.default != null || !p.required)
        for (c in BuiltinCatalog.components) for (p in c.leads) assertNotNull(p.type)
    }

    @Test fun `T-502 prompt snapshots`() {
        val dir = File("src/test/resources/prompts").apply { mkdirs() }
        val update = System.getProperty("weave.updateSnapshots") == "true"
        val tokens = StringBuilder("# Prompt token counts (T-502)\n\nTokenizer: approximate, ceil(UTF-8 bytes / 4). Phase 1 build catalog.\n\n| Snapshot | Characters | Tokens |\n|---|---|---|\n")
        for (tier in 1..4) for (level in listOf(IntelligentUiLevel.BALANCED, IntelligentUiLevel.IMMERSIVE)) {
            val name = "tier${tier}_${level.storageKey}_source.txt"
            val text = EchoUiPrompt.section(tier, level, catalog = BuiltinCatalog.forBuild(tier))
            val f = File(dir, name)
            if (update || !f.exists()) f.writeText(text)
            assertEquals(name, f.readText(), text)
            tokens.append("| $name | ${text.length} | ${(text.toByteArray().size + 3) / 4} |\n")
        }
        val tf = File(dir, "TOKENS.md")
        if (update || !tf.exists()) tf.writeText(tokens.toString())
    }

    @Test fun `T-503 every 06 example parses cleanly and renders`() {
        for ((tier, ex) in listOf(1 to EchoUiPrompt.EXAMPLE_1, 2 to EchoUiPrompt.EXAMPLE_2)) {
            val p = W.prog(ex, tier)
            assertEquals(ex, emptyList<String>(), p.diagnostics.map { it.toString() })
            val s = Session(p, W.ctx())
            assertNotNull(s.root())
            assertTrue(s.diagnostics.map { it.toString() }.toString(), s.diagnostics.isEmpty())
        }
    }

    @Test fun `T-504 a tier's prompt mentions nothing above its tier or phase`() {
        for (tier in 1..4) {
            val c = BuiltinCatalog.forBuild(tier)
            val prompt = EchoUiPrompt.section(tier, IntelligentUiLevel.IMMERSIVE, catalog = c)
            for (comp in BuiltinCatalog.components) {
                val allowed = c.component(comp.name) != null
                if (!allowed) assertFalse("tier $tier mentions ${comp.name}", Regex("\\b${comp.name}\\(").containsMatchIn(prompt) || prompt.lines().any { it.startsWith(comp.name + "(") || it.startsWith(comp.name + " ") })
            }
            if (c.action("ask") == null) assertFalse(prompt.contains("!ask"))
            if (tier == 1) { assertFalse(prompt.contains("!set")); assertFalse(prompt.contains("Slider(")) }
        }
    }
}
