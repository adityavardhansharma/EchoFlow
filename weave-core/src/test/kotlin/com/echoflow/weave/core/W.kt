package com.echoflow.weave.core

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.WeaveProgram
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Test helpers. Locale en-IN, zone Asia/Kolkata (07 preamble). */
object W {
    val NOW: Instant = Instant.parse("2026-11-26T04:30:00Z")
    fun fmt(locale: String = "en-IN") = Formatter(Locale.forLanguageTag(locale), ZoneId.of("Asia/Kolkata"), { NOW })
    fun ctx(locale: String = "en-IN") = EvalContext(fmt(locale))

    fun prog(src: String, tier: Int = 2): WeaveProgram = Compiler.compile(src, BuiltinCatalog.forBuild(tier))
    fun codes(p: WeaveProgram) = p.diagnostics.map { it.code }
    fun codes(src: String, tier: Int = 2) = codes(prog(src, tier))
    fun print(src: String) = prog(src).print()

    fun session(src: String, locale: String = "en-IN") = Session(prog(src), ctx(locale))

    /** Evaluates [expr] as a binding in a block with [setup] statements. */
    fun eval(expr: String, setup: String = "", locale: String = "en-IN"): WValue =
        session("$setup\nresult = $expr\nroot = Text(result)", locale).binding("result")

    fun evalWithDiags(expr: String, setup: String = ""): Pair<WValue, List<String>> {
        val p = prog("$setup\nresult = $expr\nroot = Text(result)")
        val s = Session(p, ctx())
        val v = s.binding("result")
        return v to (codes(p) + s.diagnostics.map { it.code })
    }
}
