package com.echoflow.weave.js

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.JsResult
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.program.Compiler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 03 §10.12 and 01 §15.5: `js fn` through QuickJS. */
class QuickJsHostTest {

    private val host = QuickJsHost()

    @After fun close() = host.close()

    private fun ok(r: JsResult): String {
        assertTrue("expected Ok, got $r", r is JsResult.Ok)
        return (r as JsResult.Ok).json
    }

    @Test fun `JSON in, JSON out`() {
        assertEquals("8", ok(host.call("double", listOf("x"), "return x * 2", "[4]")))
        assertEquals("""{"sum":6,"names":["A","B"]}""", ok(host.call(
            "f", listOf("rows"),
            "return {sum: rows.reduce((a, r) => a + r.n, 0), names: rows.map(r => r.name.toUpperCase())}",
            """[[{"name":"a","n":1},{"name":"b","n":5}]]""",
        )))
    }

    @Test fun `undefined and missing arguments`() {
        assertEquals("null", ok(host.call("f", listOf("x"), "if (x > 1) return x", "[0]")))
        assertEquals("true", ok(host.call("f", listOf("a", "b"), "return b === undefined", "[1]")))
        // `_` repeats and is never bound (02 §5.4).
        assertEquals("3", ok(host.call("f", listOf("_", "_", "c"), "return c", "[1, 2, 3]")))
    }

    @Test fun `modern JavaScript works`() {
        val src = """
            const [first, ...rest] = xs;
            const m = new Map(rest.map((v, i) => [i, v ** 2]));
            class Acc { #t = 0; add(v) { this.#t += v; return this; } get total() { return this.#t; } }
            return { first, total: [...m.values()].reduce((a, v) => new Acc().add(a).add(v).total, 0), label: `n=${'$'}{xs.length}` };
        """.trimIndent()
        assertEquals("""{"first":1,"total":13,"label":"n=3"}""", ok(host.call("f", listOf("xs"), src, "[[1, 2, 3]]")))
    }

    @Test fun `a throw or a syntax error is an error`() {
        assertTrue(host.call("f", emptyList(), "throw new Error('nope')", "[]") is JsResult.Error)
        assertTrue(host.call("f", emptyList(), "return (", "[]") is JsResult.Error)
        // A BigInt can't be JSON.
        assertTrue(host.call("f", emptyList(), "return 1n", "[]") is JsResult.Error)
    }

    @Test fun `an endless loop stops at the time limit and the runtime keeps working`() {
        val started = System.nanoTime()
        val r = host.call("spin", emptyList(), "while (true) {}", "[]")
        val ms = (System.nanoTime() - started) / 1_000_000
        assertEquals(JsResult.Limit("time"), r)
        assertTrue("took $ms ms", ms < 2_000)
        assertEquals("2", ok(host.call("f", emptyList(), "return 1 + 1", "[]")))
    }

    @Test fun `running out of memory is a limit and the next call gets a fresh runtime`() {
        val r = host.call("hog", emptyList(), "const a = []; while (true) a.push(new Array(1e5).fill(1));", "[]")
        assertTrue("got $r", r == JsResult.Limit("memory") || r == JsResult.Limit("time"))
        assertEquals("3", ok(host.call("f", emptyList(), "return 3", "[]")))
    }

    @Test fun `no host objects, no timers, no leaked globals`() {
        assertEquals(
            "[\"undefined\",\"undefined\",\"undefined\"]",
            ok(host.call("f", emptyList(), "return [typeof fetch, typeof require, typeof setTimeout]", "[]")),
        )
        // Strict mode: assigning an undeclared name throws instead of making a global.
        assertTrue(host.call("f", emptyList(), "leak = 1; return 1", "[]") is JsResult.Error)
    }

    @Test fun `results are cached and a closed host refuses calls`() {
        val src = "globalThis.n = (globalThis.n || 0) + 1; return globalThis.n"
        assertEquals("1", ok(host.call("count", emptyList(), src, "[]")))
        assertEquals("1", ok(host.call("count", emptyList(), src, "[]")))
        assertEquals("2", ok(host.call("count", emptyList(), src, "[0]")))
        host.close()
        assertTrue(host.call("count", emptyList(), src, "[]") is JsResult.Error)
    }

    // ---- through the evaluator --------------------------------------------------------------

    private fun session(src: String, tier: Int = 4, onError: (String, String) -> Unit = { _, _ -> }) =
        Session(Compiler.compile(src, BuiltinCatalog.restrictedTo(tier, 3)), EvalContext(js = host, onJsError = onError))

    @Test fun `a block calls its js fn`() {
        val s = session(
            "@weave 1 id: t\n" +
                "js fn stats(xs) = \"\"\"\n  const s = xs.reduce((a, b) => a + b, 0);\n  return {sum: s, mean: s / xs.length};\n\"\"\"\n" +
                "result = stats([2, 4, 6])\nroot = Text(result.mean)",
        )
        val v = s.binding("result") as WRec
        assertEquals(WNum.of(12.0), v.fields["sum"])
        assertEquals(WNum.of(4.0), v.fields["mean"])
        assertTrue(s.diagnostics.isEmpty())
    }

    @Test fun `records, strings and lists cross both ways`() {
        val s = session(
            "@weave 1 id: t\njs fn tag(r, word) = \"\"\"return [r.name + '-' + word, r.n + 1]\"\"\"\n" +
                "out = tag({name: \"tea\", n: 1}, \"hot\")\nroot = Text(\"x\")",
        )
        assertEquals(WList(listOf(WStr("tea-hot"), WNum.of(2.0))), s.binding("out"))
    }

    @Test fun `over the limit is null with E303`() {
        val s = session("@weave 1 id: t\njs fn spin() = \"\"\"while (true) {}\"\"\"\nout = spin()\nroot = Text(\"x\")")
        assertEquals(WNull, s.binding("out"))
        assertTrue(s.diagnostics.map { it.code }.toString(), s.diagnostics.any { it.code == "E303" })
    }

    @Test fun `a throw is null and reported to the host`() {
        val errors = ArrayList<String>()
        val s = session("@weave 1 id: t\njs fn bad() = \"\"\"throw new Error('x')\"\"\"\nout = bad()\nroot = Text(\"x\")") { n, _ -> errors += n }
        assertEquals(WNull, s.binding("out"))
        assertEquals(listOf("bad"), errors)
        assertTrue(s.diagnostics.none { it.code == "E303" })
    }

    @Test fun `below tier 4 a js fn is E209 and never runs`() {
        val errors = ArrayList<String>()
        val p = Compiler.compile("@weave 1 id: t\njs fn f() = \"\"\"return 1\"\"\"\nout = f()\nroot = Text(\"x\")", BuiltinCatalog.restrictedTo(3, 3))
        assertTrue(p.diagnostics.any { it.code == "E209" })
        val s = Session(p, EvalContext(js = object : com.echoflow.weave.core.eval.JsHost {
            override fun call(name: String, params: List<String>, source: String, argsJson: String): JsResult { errors += name; return JsResult.Ok("1") }
        }))
        assertEquals(WNull, s.binding("out"))
        assertTrue(errors.isEmpty())
    }

    @Test fun `without a host a js fn is null`() {
        val s = Session(Compiler.compile("@weave 1 id: t\njs fn f() = \"\"\"return 1\"\"\"\nout = f()\nroot = Text(\"x\")", BuiltinCatalog.restrictedTo(4, 3)), EvalContext())
        assertEquals(WNull, s.binding("out"))
    }
}
