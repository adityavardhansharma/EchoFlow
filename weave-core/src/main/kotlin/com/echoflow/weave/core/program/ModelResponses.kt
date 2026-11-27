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

    /**
     * A `lazy` fragment (03 §9.3) as an edit that defines `<binding>__lazy` from the fragment's
     * root; helpers are prefixed with the binding name so they can't collide, and state is
     * dropped. Null when the fragment has no root.
     */
    fun lazyEdit(bindingName: String, fragment: String, catalog: Catalog = BuiltinCatalog): String? {
        val printer = WeaveProgram.printer(catalog)
        val lines = Compiler.parseLines(fragment).mapNotNull { it.stmt }
        val bindings = lines.filterIsInstance<Binding>()
        val helpers = bindings.filter { it.name != "root" }.map { it.name } + lines.filterIsInstance<FnDecl>().map { it.name }
        val root = bindings.lastOrNull { it.name == "root" } ?: return null
        fun rename(text: String): String {
            var t = text
            for (h in helpers) t = t.replace(Regex("\\b${Regex.escape(h)}\\b"), "${bindingName}__$h")
            return t
        }
        val out = ArrayList<String>()
        out += rename(printer.stmt(root)).replaceFirst(Regex("^root\\b"), lazyName(bindingName))
        for (s in lines) when (s) {
            is Binding -> if (s.name != "root") out += rename(printer.stmt(s))
            is FnDecl -> out += rename(printer.stmt(s))
            else -> {}
        }
        return out.joinToString("\n")
    }

    fun lazyName(bindingName: String) = "${bindingName}__lazy"
}
