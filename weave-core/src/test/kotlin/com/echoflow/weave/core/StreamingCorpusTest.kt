package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.lex.Normalize
import com.echoflow.weave.core.parse.IncrementalParser
import com.echoflow.weave.core.parse.ParsedLine
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.WeaveProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 07 §8: streaming corpus. Steps 1–3; step 4 (height monotonicity) is a Compose test. */
class StreamingCorpusTest {
    private val printer = WeaveProgram.printer()
    private fun sig(l: ParsedLine) = (l.stmt?.let { printer.stmt(it) } ?: "<dropped>") + " " + l.diags.map { it.code }

    private fun feed(p: String, chunks: List<Int>): WeaveProgram {
        val ip = IncrementalParser(BuiltinCatalog.forBuild(2))
        var frozen = emptyList<String>()
        var pos = 0
        for (c in chunks) {
            pos = minOf(p.length, pos + c)
            ip.update(p.substring(0, pos), complete = false)
            val now = ip.frozenLines.map { sig(it) }
            assertTrue("frozen statements changed at $pos:\n$frozen\n$now", now.size >= frozen.size && now.subList(0, frozen.size) == frozen)
            frozen = now
        }
        val final = ip.update(p, complete = true)
        // the frozen prefix must equal the full parse's first statements
        val full = Compiler.parseLines(Normalize.body(p)).map { sig(it) }
        assertEquals(frozen, full.subList(0, frozen.size))
        return final
    }

    @Test fun `every corpus program streams to the same program`() {
        val programs = Corpus.programs()
        assertTrue(programs.size >= 3)
        for ((k, p) in programs.withIndex()) {
            val whole = Compiler.compile(p, BuiltinCatalog.forBuild(2))
            val byChar = feed(p, List(p.length) { 1 })
            assertEquals(whole.print(), byChar.print())
            assertEquals(whole.diagnostics.map { it.code }, byChar.diagnostics.map { it.code })
            val rnd = Random(k)
            val chunks = ArrayList<Int>()
            var total = 0
            while (total < p.length) { val c = rnd.nextInt(1, 7); chunks += c; total += c }
            val byToken = feed(p, chunks)
            assertEquals(whole.print(), byToken.print())
        }
    }
}
