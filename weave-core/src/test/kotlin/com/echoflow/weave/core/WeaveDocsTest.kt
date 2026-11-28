package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.CatalogExamples
import com.echoflow.weave.core.check.Severity
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.core.prompt.WeaveDocs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 06 §2.1 and §8: the trimmed prompt, the reference index and the `weave_docs` tool. */
class WeaveDocsTest {

    private val phase3 = { tier: Int -> BuiltinCatalog.restrictedTo(tier, 3) }

    // Fields used by every chart-mark example, so each one checks inside one Chart.
    private val markData = """data = [{month: "Jan", total: 80, low: 70, high: 90, day: "Mon", hour: "9", n: 3, a: "x", b: "y", v: 1}]"""

    /** Turns an example into a block: state and named lines stay, the usage line becomes `root`. */
    private fun block(example: String): String {
        val lines = example.lines()
        val out = ArrayList<String>()
        out += "@weave 1 id: ex title: \"Example\""
        lines.forEachIndexed { i, l ->
            out += if (i == lines.lastIndex && !l.startsWith("$") && !Regex("""^[a-z_]\w* = """).containsMatchIn(l)) "root = $l" else l
        }
        if (out.none { it.startsWith("root = ") }) out += "root = Text(\"x\")"
        return out.joinToString("\n")
    }

    private fun errors(src: String): List<String> =
        Compiler.compile(src, phase3(4)).diagnostics.filter { it.severity == Severity.ERROR }.map { it.toString() }

    @Test fun `every component and mark has an example`() {
        for (c in BuiltinCatalog.components) assertTrue("${c.name} has no example", CatalogExamples.component(c.name) != null)
        for (m in BuiltinCatalog.marks) for (container in m.container) {
            assertTrue("$container.${m.name} has no example", CatalogExamples.mark(container, m.name) != null)
        }
        val known = BuiltinCatalog.components.map { it.name }.toSet()
        for (name in CatalogExamples.components.keys) assertTrue("example for unknown $name", name in known)
    }

    @Test fun `every component example checks with no errors at tier 4`() {
        for ((name, ex) in CatalogExamples.components) {
            val src = block(ex)
            assertEquals("$name:\n$src", emptyList<String>(), errors(src))
            assertTrue("$name example doesn't use $name", Regex("""\b$name\(|\b$name \[""").containsMatchIn(ex))
        }
    }

    @Test fun `every mark example checks inside its container`() {
        for ((key, ex) in CatalogExamples.marks) {
            val container = key.substringBefore('.')
            val src = if (container == "Chart") "@weave 1 id: ex\n$markData\nroot = Chart(data, polar: ${key == "Chart.arc"}) [$ex]"
            else "@weave 1 id: ex\nroot = Canvas(view: [0, 0, 100, 60]) [$ex]"
            assertEquals("$key:\n$src", emptyList<String>(), errors(src))
        }
    }

    @Test fun `tier 3 and 4 with tools get the common set and an index`() {
        for (tier in 3..4) {
            val c = phase3(tier)
            val full = EchoUiPrompt.section(tier, IntelligentUiLevel.BALANCED, catalog = c)
            val trimmed = EchoUiPrompt.section(tier, IntelligentUiLevel.BALANCED, catalog = c, docsIndex = true)
            assertTrue(trimmed.length < full.length)
            assertTrue(trimmed.contains("### Reference index"))
            assertFalse(full.contains("### Reference index"))
            assertTrue(trimmed.contains("call the weave_docs tool"))
            val lines = trimmed.lines()
            for (comp in c.components) {
                val hasLine = lines.any { it.startsWith(comp.name + "(") || it.startsWith(comp.name + " [") || it.startsWith(comp.name + " —") }
                val indexed = lines.any { l -> l.substringAfter(": ", "").split(' ').contains(comp.name) && EchoUiPrompt.GROUPS.any { l.startsWith("$it: ") } }
                if (comp.name in WeaveDocs.COMMON_SET) {
                    assertTrue("tier $tier: ${comp.name} lost its line", hasLine)
                    assertFalse(indexed)
                } else {
                    // A prompt never names a component without a way to get its signature (06 §2.1).
                    assertTrue("tier $tier: ${comp.name} missing from the index", indexed)
                    assertFalse("tier $tier: ${comp.name} kept its line", hasLine)
                }
            }
            // Functions, actions and effects stay inline.
            assertTrue(trimmed.contains("### Functions") && trimmed.contains("### Actions") && trimmed.contains("### Effects"))
        }
    }

    @Test fun `below tier 3 the prompt is never trimmed`() {
        for (tier in 1..2) {
            val c = phase3(tier)
            assertEquals(
                EchoUiPrompt.section(tier, IntelligentUiLevel.IMMERSIVE, catalog = c),
                EchoUiPrompt.section(tier, IntelligentUiLevel.IMMERSIVE, catalog = c, docsIndex = true),
            )
        }
    }

    @Test fun `usesIndex needs phase 3, tier 3 and tool use`() {
        assertTrue(WeaveDocs.usesIndex(3, 3, true))
        assertTrue(WeaveDocs.usesIndex(4, 3, true))
        assertFalse(WeaveDocs.usesIndex(2, 3, true))
        assertFalse(WeaveDocs.usesIndex(4, 2, true))
        assertFalse(WeaveDocs.usesIndex(4, 3, false))
    }

    @Test fun `answer gives the signature line and an example`() {
        val c = phase3(4)
        val a = WeaveDocs.answer(listOf("Quiz"), 4, c)
        assertTrue(a, a.startsWith(EchoUiPrompt.componentLine(c.component("Quiz")!!)))
        assertTrue(a.contains("Example:\n" + CatalogExamples.component("Quiz")))
    }

    @Test fun `Canvas comes with its marks`() {
        val a = WeaveDocs.answer(listOf("Canvas"), 4, phase3(4))
        assertTrue(a, a.contains("\n  circle(x, y, r"))
        assertTrue(a.contains("\n  group("))
    }

    @Test fun `marks and groups are answered`() {
        val c = phase3(4)
        val circle = WeaveDocs.answer(listOf("circle"), 4, c)
        assertTrue(circle, circle.startsWith("circle(x, y, r") && circle.contains("(a mark inside Canvas)"))
        // `line` is a Chart mark and a Canvas mark.
        val line = WeaveDocs.answer(listOf("line"), 4, c)
        assertTrue(line, line.contains("(a mark inside Chart)") && line.contains("(a mark inside Canvas)"))
        assertEquals(EchoUiPrompt.functions(4, c), WeaveDocs.answer(listOf("functions"), 4, c))
        assertEquals(EchoUiPrompt.actions(4, c), WeaveDocs.answer(listOf("Actions"), 4, c))
        assertEquals(EchoUiPrompt.effects(4, c), WeaveDocs.answer(listOf("effects"), 4, c))
    }

    @Test fun `unknown, above-tier and unregistered names are unknown`() {
        assertEquals("unknown: Nope", WeaveDocs.answer(listOf("Nope"), 4, phase3(4)))
        // Shader is tier 4 and Canvas tier 2: lower tiers can't learn about them or their marks.
        assertEquals("unknown: Shader", WeaveDocs.answer(listOf("Shader"), 3, phase3(3)))
        assertEquals("unknown: circle", WeaveDocs.answer(listOf("circle"), 1, phase3(1)))
        assertEquals("unknown: Canvas", WeaveDocs.answer(listOf("Canvas"), 1, phase3(1)))
        // A component the renderer doesn't register is unknown too (05 §3.3).
        val unregistered = phase3(4).registeredOnly(WeaveDocs.COMMON_SET)
        assertEquals("unknown: Quiz", WeaveDocs.answer(listOf("Quiz"), 4, unregistered))
    }

    @Test fun `names are deduplicated and capped at 30`() {
        val c = phase3(4)
        val a = WeaveDocs.answer(listOf("Gauge", "Gauge", "!Gauge"), 4, c)
        assertEquals(1, Regex("""(?m)^Gauge\(value""").findAll(a).count())
        val many = WeaveDocs.answer(c.components.map { it.name }, 4, c)
        assertTrue(many.endsWith("Only the first 30 names were answered."))
        assertTrue(WeaveDocs.answer(emptyList(), 4, c).startsWith("Call weave_docs with"))
    }

    @Test fun `tool arguments parse in the shapes models send`() {
        assertEquals(listOf("Quiz", "Canvas"), WeaveDocs.parseNames("""{"names": ["Quiz", "Canvas"]}"""))
        assertEquals(listOf("Quiz"), WeaveDocs.parseNames("""{"name": "Quiz"}"""))
        assertEquals(listOf("Quiz", "Sort"), WeaveDocs.parseNames("""{"names": "Quiz, Sort"}"""))
        assertEquals(listOf("Quiz"), WeaveDocs.parseNames("""["Quiz"]"""))
        assertEquals(listOf("Quiz"), WeaveDocs.parseNames("Quiz"))
        assertNull(WeaveDocs.parseNames("""{"names": []}"""))
        assertNull(WeaveDocs.parseNames("""{"other": 1}"""))
        assertNull(WeaveDocs.parseNames(""))
    }

    @Test fun `trimmed prompt snapshots`() {
        val dir = File("src/test/resources/prompts").apply { mkdirs() }
        val update = System.getProperty("weave.updateSnapshots") == "true"
        val tokens = StringBuilder("# Trimmed prompt token counts (06 §2.1)\n\nTokenizer: approximate, ceil(UTF-8 bytes / 4). Phase 3 catalog, every component registered.\n\n| Snapshot | Full tokens | Trimmed tokens |\n|---|---|---|\n")
        for (tier in 3..4) for (level in listOf(IntelligentUiLevel.BALANCED, IntelligentUiLevel.IMMERSIVE)) {
            val name = "tier${tier}_${level.storageKey}_source_docs.txt"
            val full = EchoUiPrompt.section(tier, level, catalog = phase3(tier))
            val text = EchoUiPrompt.section(tier, level, catalog = phase3(tier), docsIndex = true)
            val f = File(dir, name)
            if (update || !f.exists()) f.writeText(text)
            assertEquals(name, f.readText(), text)
            tokens.append("| $name | ${(full.toByteArray().size + 3) / 4} | ${(text.toByteArray().size + 3) / 4} |\n")
        }
        val tf = File(dir, "TOKENS_DOCS.md")
        if (update || !tf.exists()) tf.writeText(tokens.toString())
    }
}
