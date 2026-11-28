package com.echoflow.weave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-409: the Weave renderer takes every colour from `MaterialTheme.colorScheme` roles (CLAUDE.md,
 * 04 §1.4). No literal colours in `weave/compose`, except the harmonised semantic colours in
 * `tokens/TokenResolver.kt` and colours that only feed `BlendMode.Clear` (which ignores them).
 */
class NoHardcodedColorsTest {
    private val literal = Regex("""Color\(0x|Color\.(Red|Green|Blue|Black|White|Gray|Grey|DarkGray|LightGray|Yellow|Cyan|Magenta)\b|Color\.parseColor|android\.graphics\.Color\.(RED|GREEN|BLUE|BLACK|WHITE|GRAY|YELLOW|CYAN|MAGENTA)\b""")

    @Test fun `T-409 no hardcoded colours in the Weave renderer`() {
        val dir = listOf(File("src/main/java/com/echoflow/weave/compose"), File("app/src/main/java/com/echoflow/weave/compose")).firstOrNull { it.exists() }
        assertTrue("renderer sources not found", dir != null)
        val hits = dir!!.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "TokenResolver.kt" }.flatMap { f ->
            f.readLines().withIndex().filter { (_, l) -> literal.containsMatchIn(l) && "BlendMode.Clear" !in l }.map { (i, l) -> "${f.name}:${i + 1}: ${l.trim()}" }
        }.toList()
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun `the check catches a literal`() {
        assertTrue(literal.containsMatchIn("val c = Color(0xFF00FF00)"))
        assertTrue(literal.containsMatchIn("tint = Color.Red"))
        assertTrue(!literal.containsMatchIn("scheme.primary"))
    }
}
