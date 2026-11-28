package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.parse.IncrementalParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 07 §7: streaming parser. */
class StreamingTest {
    private fun spec(tail: String): List<String> {
        val p = IncrementalParser(BuiltinCatalog.forBuild(2)).update(tail, complete = false)
        return p.statements().map { com.echoflow.weave.core.program.WeaveProgram.printer().stmt(it) }
    }

    @Test fun `T-090 partial children`() = assertEquals(listOf("root = Column [a]"), spec("root = Column [a, b"))
    @Test fun `T-091 open string`() = assertEquals(listOf("x = \"hel\""), spec("x = \"hel"))
    @Test fun `T-092 named arg without a value`() = assertEquals(listOf("s = Slider(\$r)"), spec("s = Slider(\$r, min: "))
    @Test fun `T-093 trailing operator`() = assertEquals(listOf("t = Text(\"a\")"), spec("t = Text(\"a\" +"))
    @Test fun `T-094 partial component name`() = assertEquals(emptyList<String>(), spec("root = Sli"))

    @Test fun `T-095 a line is not frozen until the next one starts`() {
        val ip = IncrementalParser()
        ip.update("a = 1\n", false)
        assertEquals(0, ip.frozenLines.size)
        ip.update("a = 1\nb", false)
        assertEquals(1, ip.frozenLines.size)
    }

    @Test fun `T-096 a leading pipe continues the statement`() {
        val ip = IncrementalParser()
        ip.update("xs = items\n", false)
        val p = ip.update("xs = items\n  |> take(2)", false)
        assertEquals(listOf("xs = take(items, 2)"), p.statements().map { com.echoflow.weave.core.program.WeaveProgram.printer().stmt(it) })
        assertTrue(ip.frozenLines.isEmpty())
    }
}
