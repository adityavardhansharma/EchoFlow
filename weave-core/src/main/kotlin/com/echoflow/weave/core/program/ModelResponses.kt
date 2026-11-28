package com.echoflow.weave.core.program

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.Delete
import com.echoflow.weave.core.parse.FnDecl
import com.echoflow.weave.core.parse.HeaderStmt
import com.echoflow.weave.core.parse.StateDecl

/** Reading hidden-request responses (05 §8.1) and restricting them (03 §9.2). */
object ModelResponses {
    private val FENCE = Regex("""(?m)^[ \t]*(```|~~~)\s*weave(?:-json)?([^\n]*)\n([\s\S]*?)(?:\n[ \t]*\1[ \t]*$|\z)""")

    /** The body of the first `weave` fence, or the whole text when there is none. */
    fun firstFence(text: String): String = FENCE.find(text)?.groupValues?.get(3)?.trimEnd() ?: text.trim()

    /**
     * Keeps only what `!ask` may change (03 §9.2): definitions of [update] names, plus brand-new
     * names (helpers). State declarations survive only if their name is new; deletes and headers
     * are dropped. Returns the edit text to apply and the names it skipped (for the log).
     */
    fun restrictAsk(program: WeaveProgram, editBody: String, update: Set<String>, catalog: Catalog = BuiltinCatalog): Pair<String, List<String>> {
        val printer = WeaveProgram.printer(catalog)
        val kept = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (pl in Compiler.parseLines(editBody)) {
            when (val s = pl.stmt) {
                is Binding -> if (s.name in update || (s.name !in program.bindings && s.name !in program.functions)) kept += printer.stmt(s) else skipped += s.name
                is FnDecl -> if (s.name in update || (s.name !in program.functions && s.name !in program.bindings)) kept += printer.stmt(s) else skipped += s.name
                is StateDecl -> if (s.name !in program.states) kept += printer.stmt(s) else skipped += "$" + s.name
                is Delete, is HeaderStmt -> skipped += (s as? Delete)?.target ?: "@weave"
                null -> {}
                else -> skipped += s.javaClass.simpleName
            }
        }
        return kept.joinToString("\n") to skipped
    }

    /** A lazy edit and the helper names it defines, so the next generation may replace them. */
    data class LazyEdit(val source: String, val helpers: Set<String>)

    /**
     * A `lazy` fragment (03 §9.3) as an edit that defines `<binding>__lazy` from the fragment's
     * root; helpers are prefixed with the binding name so they can't collide, and state is
     * dropped. Null when the fragment has no root.
     *
     * [taken] are names already defined in the block that the edit must not overwrite (pass the
     * program's names minus the helpers this lazy part made last time). Renaming walks the parsed
     * tree, so strings, record keys, member names and named arguments are never touched.
     */
    fun lazyEdit(bindingName: String, fragment: String, catalog: Catalog = BuiltinCatalog): String? =
        lazyEditFor(bindingName, fragment, emptySet(), catalog)?.source

    fun lazyEditFor(bindingName: String, fragment: String, taken: Set<String>, catalog: Catalog = BuiltinCatalog): LazyEdit? {
        val printer = WeaveProgram.printer(catalog)
        val lines = Compiler.parseLines(fragment).mapNotNull { it.stmt }
        val bindings = lines.filterIsInstance<Binding>()
        val root = bindings.lastOrNull { it.name == "root" } ?: return null
        val helpers = (bindings.filter { it.name != "root" }.map { it.name } + lines.filterIsInstance<FnDecl>().map { it.name }).distinct()
        val used = HashSet(taken).apply { add(lazyName(bindingName)) }
        val renames = LinkedHashMap<String, String>()
        for (h in helpers) {
            var candidate = "${bindingName}__$h"
            var n = 2
            while (candidate in used) candidate = "${bindingName}__${h}_${n++}"
            used += candidate
            renames[h] = candidate
        }
        // scope-aware: a name bound by a fn or lambda parameter refers to that parameter, not the helper
        fun ren(e: com.echoflow.weave.core.parse.Expr, bound: Set<String>): com.echoflow.weave.core.parse.Expr {
            fun r(x: com.echoflow.weave.core.parse.Expr) = ren(x, bound)
            return when (e) {
                is com.echoflow.weave.core.parse.Name -> if (e.name in renames && e.name !in bound) e.copy(name = renames.getValue(e.name)) else e
                is com.echoflow.weave.core.parse.Lambda -> e.copy(body = ren(e.body, bound + e.params))
                is com.echoflow.weave.core.parse.Call -> e.copy(
                    callee = if (e.kind == com.echoflow.weave.core.parse.CallKind.FUNCTION && e.callee in renames && e.callee !in bound) renames.getValue(e.callee) else e.callee,
                    leads = e.leads.map(::r), named = e.named.map { (k, v) -> k to r(v) }, children = e.children?.map(::r),
                )
                is com.echoflow.weave.core.parse.Member -> e.copy(target = r(e.target))
                is com.echoflow.weave.core.parse.Index -> e.copy(target = r(e.target), index = r(e.index))
                is com.echoflow.weave.core.parse.Unary -> e.copy(operand = r(e.operand))
                is com.echoflow.weave.core.parse.BinaryOp -> e.copy(left = r(e.left), right = r(e.right))
                is com.echoflow.weave.core.parse.Ternary -> e.copy(cond = r(e.cond), then = r(e.then), otherwise = r(e.otherwise))
                is com.echoflow.weave.core.parse.ListLit -> e.copy(items = e.items.map(::r))
                is com.echoflow.weave.core.parse.RecLit -> e.copy(fields = e.fields.map { (k, v) -> k to r(v) })
                is com.echoflow.weave.core.parse.Str -> e.copy(parts = e.parts.map { p -> if (p is com.echoflow.weave.core.parse.InterpPart) p.copy(expr = r(p.expr)) else p })
                else -> e
            }
        }
        fun rename(stmt: com.echoflow.weave.core.parse.Stmt): com.echoflow.weave.core.parse.Stmt = when (stmt) {
            is Binding -> stmt.copy(expr = ren(stmt.expr, emptySet()))
            is FnDecl -> stmt.copy(body = stmt.body?.let { ren(it, stmt.params.toSet()) })
            else -> stmt
        }
        val out = ArrayList<String>()
        out += printer.stmt(rename((root as Binding).copy(name = lazyName(bindingName))))
        for (s in lines) when (s) {
            is Binding -> if (s.name != "root") out += printer.stmt(rename(s.copy(name = renames.getValue(s.name))))
            is FnDecl -> out += printer.stmt(rename(s.copy(name = renames.getValue(s.name))))
            else -> {}
        }
        return LazyEdit(out.joinToString("\n"), renames.values.toSet())
    }

    fun lazyName(bindingName: String) = "${bindingName}__lazy"
}
