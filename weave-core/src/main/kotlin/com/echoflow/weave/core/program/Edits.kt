package com.echoflow.weave.core.program

import com.echoflow.weave.core.Limits
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.lex.Normalize
import com.echoflow.weave.core.parse.AutoFix
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.CanonicalPrinter
import com.echoflow.weave.core.parse.Delete
import com.echoflow.weave.core.parse.Effect
import com.echoflow.weave.core.parse.Export
import com.echoflow.weave.core.parse.FnDecl
import com.echoflow.weave.core.parse.HeaderStmt
import com.echoflow.weave.core.parse.StateDecl
import com.echoflow.weave.core.parse.Stmt

/**
 * Applying an edit fragment to a block, 02 §11. The result is version n+1, or a rejection that
 * leaves the block at version n.
 */
data class EditResult(
    val program: WeaveProgram,
    val accepted: Boolean,
    /** Why the edit was rejected (logged), when [accepted] is false. */
    val rejection: String?,
    /** Diagnostics from the edit fragment itself. */
    val diagnostics: List<Diagnostic>,
    /** States declared with `reset` in the edit: re-initialised from the new expression (02 §11.3). */
    val resetStates: Set<String>,
)

object Edits {

    fun apply(program: WeaveProgram, editBody: String, catalog: Catalog = BuiltinCatalog.forBuild(2)): EditResult {
        val lines = Compiler.parseLines(Normalize.body(editBody))
        val diags = ArrayList<Diagnostic>()
        val raw = ArrayList<Stmt>()
        for ((_, pl) in lines.withIndex()) {
            diags += pl.diags
            val s = pl.stmt ?: continue
            if (s is HeaderStmt) { diags += Diag.headerPosition(s.span); continue }
            raw += s
        }
        val fixedLines = AutoFix.s9(lines.filter { it.stmt != null && it.stmt !is HeaderStmt }) { BuiltinCatalog.component(it) != null }
        val edit = fixedLines.mapNotNull { it.stmt }
            .map { if (it is Effect) it.copy(key = CanonicalPrinter.triggerKey(it.trigger)) else it }
        val mergedEdit = AutoFix.s12(edit, diags)

        // The block's statements keyed by namespace, in their current order.
        val cur = LinkedHashMap<String, Stmt>()
        for (s in program.states.values) cur["s:" + s.name] = s
        program.root?.let { program.bindings[it] }?.let { cur["v:" + it.name] = it }
        for (b in program.bindings.values) cur["v:" + b.name] = b
        for (f in program.functions.values) cur["v:" + f.name] = f
        for (e in program.effects.values) cur["e:" + e.key] = e
        val exports = LinkedHashSet(program.exports)
        val reset = HashSet<String>()

        for (s in mergedEdit) when (s) {
            is StateDecl -> { cur["s:" + s.name] = s; if (s.reset) reset += s.name }
            is Binding -> cur["v:" + s.name] = s
            is FnDecl -> cur["v:" + s.name] = s
            is Effect -> cur["e:" + s.key] = s
            is Export -> exports += s.names
            is Delete -> {
                val key = when {
                    s.target == "root" -> { diags += Diag.badName(s.span, "root"); null }
                    s.target.startsWith("on ") -> "e:" + s.target.removePrefix("on ")
                    s.target.startsWith("$") -> "s:" + s.target.removePrefix("$")
                    else -> "v:" + s.target
                }
                if (key != null && cur.remove(key) == null) {
                    if (key.startsWith("s:")) exports.remove("$" + key.removePrefix("s:")) else exports.remove(s.target)
                    diags += Diag.unresolved(s.span, s.target)
                }
            }
            else -> {}
        }

        val stmts = cur.values.toList() + (if (exports.isEmpty()) emptyList() else listOf(Export(exports.toList(), com.echoflow.weave.core.parse.Span.NONE)))
        val bindings = stmts.filterIsInstance<Binding>().map { it.name }.toSet()
        val functions = stmts.filterIsInstance<FnDecl>().map { it.name }.toSet()
        val states = stmts.filterIsInstance<StateDecl>().map { it.name }.toSet()
        val checked = Compiler.checkFragment(program.header.stmt, stmts, catalog, bindings, functions, states)
        val next = Compiler.assemble(checked, catalog, complete = true, frozenCount = stmts.size, version = program.version + 1)
        val allDiags = diags + next.diagnostics
        val result = WeaveProgram(
            program.header, next.states, next.bindings, next.functions, next.effects, next.exports, next.root,
            allDiags, true, next.frozenCount, next.version,
        )

        val rejection = when {
            !rootRenders(result) -> "no renderable root"
            stmts.size > Limits.STATEMENTS -> "over ${Limits.STATEMENTS} statements"
            result.print(catalog).toByteArray(Charsets.UTF_8).size > Limits.SOURCE_BYTES -> "over ${Limits.SOURCE_BYTES} bytes"
            else -> null
        }
        return if (rejection == null) EditResult(result, true, null, diags, reset)
        else EditResult(program, false, rejection, diags + allDiags, emptySet())
    }

    /** The root binding exists and its expression is not a removed node (E201/E209 → null). */
    private fun rootRenders(p: WeaveProgram): Boolean {
        val r = p.root?.let { p.bindings[it] } ?: return false
        val e = r.expr
        return !(e is com.echoflow.weave.core.parse.Lit && e.value == com.echoflow.weave.core.eval.WNull)
    }
}
