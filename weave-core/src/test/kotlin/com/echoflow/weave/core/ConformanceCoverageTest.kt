package com.echoflow.weave.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 07 preamble: "An implementation is correct when all pass." Every numbered case in 07 must be
 * named by a test somewhere in the repo, by its own id (`T-012a`), its group (`T-012`) or a range
 * (`T-250 to T-262`), or be listed in [OUTSIDE_UNIT_TESTS] with where it is verified instead.
 */
class ConformanceCoverageTest {

    /** Cases that unit tests can't check, and where each one is verified. */
    private val OUTSIDE_UNIT_TESTS = mapOf(
        "T-410" to "emulator screenshots in the PR (CLAUDE.md): popups live in their own window, which Roborazzi can't capture",
        "T-450" to "process death needs a device; the save path itself is covered by T-467 and StateCodecTest",
    )

    private val root = File("..").canonicalFile
    private val testDirs = listOf("weave-core/src/test", "weave-js/src/test", "weave-eval/src/test", "app/src/test", "app/src/androidTest")

    private fun specIds(): List<String> {
        val f = File(root, "docs/echo-ui/07-conformance.md")
        if (!f.exists()) return emptyList()
        return Regex("""^\| (T-\d+[a-z]?) """, RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.distinct().toList()
    }

    private fun testText(): String = testDirs.map { File(root, it) }.filter { it.exists() }
        .flatMap { d -> d.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ConformanceCoverageTest.kt" } }
        .joinToString("\n") { it.readText() }

    @Test fun `every 07 case is named by a test or listed with where it is verified`() {
        val ids = specIds()
        if (ids.isEmpty()) return
        val text = testText()
        val named = Regex("""T-(\d+[a-z]?)""").findAll(text).map { it.groupValues[1] }.toSet()
        val ranges = Regex("""T-(\d+)[a-z]? to T-(\d+)""").findAll(text).flatMap { m ->
            (m.groupValues[1].toInt()..m.groupValues[2].toInt()).asSequence()
        }.toSet()
        val missing = ids.filter { id ->
            val n = id.removePrefix("T-")
            val base = Regex("""\d+""").find(n)!!.value
            id !in OUTSIDE_UNIT_TESTS && n !in named && base !in named && base.toInt() !in ranges
        }
        assertEquals("07 cases no test names: $missing", emptyList<String>(), missing)
    }

    @Test fun `the outside list only names real cases`() {
        val ids = specIds().toSet()
        if (ids.isEmpty()) return
        for (id in OUTSIDE_UNIT_TESTS.keys) assertTrue("$id isn't in 07", id in ids)
    }
}
