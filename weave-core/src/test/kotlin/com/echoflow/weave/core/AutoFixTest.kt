package com.echoflow.weave.core

import com.echoflow.weave.core.parse.AutoFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §2.1: auto-fix rules. */
class AutoFixTest {
    private fun line(src: String, start: String = "a"): String {
        val setup = "\$x = true\n\$y = true\n\$z = true\n"
        return W.prog(setup + src + "\nroot = Text(\"r\")").print().lines().first { it.startsWith("$start ") }
    }

    private fun root(src: String) = W.print(src).lines().first { it.startsWith("root") }

    @Test fun `T-070 to T-076 lexical rules`() {
        assertEquals("a = \$x and \$y or \$z", line("a = \$x && \$y || \$z"))
        assertEquals("a = not \$x", line("a = !\$x"))
        assertEquals("a = not (1 == 2)", line("a = !(1 == 2)"))
        assertEquals("a = \$x == 1", line("a = \$x === 1"))
        assertEquals("a = 0.5 + 1", line("a = .5 + 1"))
        assertEquals("a = true", line("a = True"))
        assertEquals("a = 1", line("a = 1;"))
    }

    @Test fun `T-077 to T-080 token snapping`() {
        assertEquals("root = Card(gap: m) [Text(\"x\")]", root("root = Card(gap: 14) [Text(\"x\")]"))
        assertEquals("root = Card(gap: l) [Text(\"x\")]", root("root = Card(gap: 20) [Text(\"x\")]"))
        assertEquals("root = Card(shape: m) [Text(\"x\")]", root("root = Card(shape: 24) [Text(\"x\")]"))
        assertEquals("root = Card(gap: xl) [Text(\"x\")]", root("root = Card(gap: 21) [Text(\"x\")]"))
        assertEquals("root = Card(gap: m) [Text(\"x\")]", root("root = Card(gap: \"m\") [Text(\"x\")]"))
    }

    @Test fun `T-081 to T-084 structural rules`() {
        assertEquals("a = \$x > 1 ? \"big\" : \"small\"", line("a = if(\$x > 1, \"big\", \"small\")"))
        assertEquals("root = Card [Text(\"x\")]", root("root = card [Text(\"x\")]"))
        assertTrue(root("d = [{a: 1, b: 2}]\nroot = LINECHART(d, x: \"a\", y: \"b\")").startsWith("root = LineChart("))
        assertEquals("root = Column [a, b]", root("a = Text(\"1\")\nb = Text(\"2\")\nroot = Column(children: [a, b])"))
    }

    @Test fun `T-085 hex to role`() {
        val got = listOf("#FF0000", "#4CAF50", "#2196F3", "#FFC107", "#9E9E9E", "#000000").map { AutoFix.hexToRole(it, false) }
        assertEquals(listOf("error", "success", "primary", "warning", "neutral", "neutral"), got)
    }

    @Test fun `T-086 record with = is fixed`() = assertEquals("r = {name: \"a\"}", line("r = {name = \"a\"}", "r"))

    @Test fun `T-087 elif is not fixed`() = assertEquals(listOf("E105"), W.codes("a = 2 elif\nroot = Text(\"x\")"))
}

/** Spec 1.3 fixes found by the eval harness (07 §10). */
class EvalFindingsTest {
    @Test fun `T-089 when as a function parses`() {
        val p = W.prog("\$show = true\nroot = Column [when(\$show, Text(\"hi\"))]")
        assertEquals(emptyList<String>(), W.codes(p))
        assertEquals("hi", W.session("\$show = true\nroot = Column [when(\$show, Text(\"hi\"))]").let { it.plainText(it.root()!!) })
        // the effect keyword still works
        assertEquals(1, W.prog("\$x = 0\nroot = Text(\$x)\non change(\$x) when \$x > 1 -> !toast(\"a\")").effects.size)
    }

    @Test fun `T-088 S13 format calls in format slots`() {
        val p = W.prog("root = Metric(\"BMI\", 22.857, format: dec(1))")
        assertEquals("root = Metric(\"BMI\", 22.857, format: \"dec(1)\")", p.print())
        assertEquals(listOf("W403"), W.codes(p))
        val s = W.session("root = Metric(\"BMI\", 22.857, format: dec(1))")
        assertEquals("BMI: 22.9", s.plainText(s.root()!!))
    }

    @Test fun `T-089b item and index are allowed as parameters`() {
        assertEquals(emptyList<String>(), W.codes("xs = [1, 2]\nys = map(xs, (item, index) => item + index)\nroot = Text(ys)"))
        assertEquals(listOf("E106"), W.codes("item = 1\nroot = Text(\"x\")"))
    }
}
