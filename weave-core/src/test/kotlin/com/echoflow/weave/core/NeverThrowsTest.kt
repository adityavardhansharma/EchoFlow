package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.parse.IncrementalParser
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.Edits
import org.junit.Test
import kotlin.random.Random

/** Golden rule 1 (03): no input makes the core throw. Random mutations of the corpus. */
class NeverThrowsTest {
    private val alphabet = "()[]{},:;.=+-*/%?!\$\"'#@|><_ \n\tabcxyzRootCardText0123456789\\`“é"

    private fun mutate(s: String, r: Random): String {
        val sb = StringBuilder(s)
        repeat(r.nextInt(1, 8)) {
            if (sb.isEmpty()) return@repeat
            val i = r.nextInt(sb.length)
            when (r.nextInt(3)) {
                0 -> sb.deleteCharAt(i)
                1 -> sb.insert(i, alphabet[r.nextInt(alphabet.length)])
                else -> sb.setCharAt(i, alphabet[r.nextInt(alphabet.length)])
            }
        }
        return sb.toString()
    }

    @Test fun `mutated programs never throw`() {
        val r = Random(7)
        val cat = BuiltinCatalog.forBuild(2)
        for (p in Corpus.programs()) repeat(60) {
            val m = mutate(p, r)
            val prog = Compiler.compile(m, cat)
            prog.print()
            val s = Session(prog, W.ctx())
            s.root()?.let { root -> s.nodes().forEach { n -> s.plainText(n); n.args["onTap"]?.let { a -> s.fire(a) } } }
            Edits.apply(prog, mutate(m, r), cat)
            val ip = IncrementalParser(cat)
            var k = 0
            while (k < m.length) { k = minOf(m.length, k + r.nextInt(1, 9)); ip.update(m.substring(0, k), false) }
        }
    }
}
