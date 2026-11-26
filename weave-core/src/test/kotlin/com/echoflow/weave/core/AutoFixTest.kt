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
