package com.echoflow.weave.core.program

import com.echoflow.weave.core.Limits
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.check.Checker
import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.lex.Lexer
import com.echoflow.weave.core.lex.Normalize
import com.echoflow.weave.core.parse.AutoFix
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.CallKind
import com.echoflow.weave.core.parse.CanonicalPrinter
import com.echoflow.weave.core.parse.Delete
import com.echoflow.weave.core.parse.Effect
import com.echoflow.weave.core.parse.Export
import com.echoflow.weave.core.parse.FnDecl
import com.echoflow.weave.core.parse.HeaderStmt
import com.echoflow.weave.core.parse.Lit
import com.echoflow.weave.core.parse.ParsedLine
import com.echoflow.weave.core.parse.Parser
import com.echoflow.weave.core.parse.Splitter
import com.echoflow.weave.core.parse.StateDecl
import com.echoflow.weave.core.parse.Stmt
import com.echoflow.weave.core.parse.Str
import com.echoflow.weave.core.parse.TextPart

/** Statements in source order after checking, plus diagnostics. The input to [Edits]. */
data class Fragment(val header: HeaderStmt?, val stmts: List<Stmt>, val diagnostics: List<Diagnostic>)

/**
 * The whole-body pipeline (05 §2): normalise → lex → split → parse → auto-fix → check → program.
 * Pure and deterministic; never throws for any input.
 */
object Compiler {

    /** Front end: a body to parsed lines (shared with the incremental parser). */
    fun parseLines(body: String, baseOffset: Int = 0, baseLine: Int = 1): List<ParsedLine> {
        val lexer = Lexer(body, baseOffset, baseLine)
        val tokens = lexer.lex()
        val lines = Splitter.split(tokens)
        val notes = lexer.notes
        return lines.map { l ->
            Parser.parseLine(l, notes.filter { it.offset >= l.start && it.offset < l.end })
        }
    }

    /** A complete program from a fence body. */
    fun compile(body: String, catalog: Catalog = BuiltinCatalog.forBuild(2), complete: Boolean = true): WeaveProgram {
        val lines = parseLines(Normalize.body(body))
        return build(lines, catalog, complete, lines.size)
    }

    /** Checks parsed lines as a fragment (a new block, or an edit when [edit]). */
    fun fragment(lines: List<ParsedLine>, catalog: Catalog, edit: Boolean, streaming: Boolean = false): Fragment {
        val diags = ArrayList<Diagnostic>()
        var header: HeaderStmt? = null
        val kept = ArrayList<ParsedLine>()
        var count = 0
        for ((i, pl) in lines.withIndex()) {
            val s = pl.stmt
            if (s is HeaderStmt) {
                if (i == 0 && !edit) { header = s; diags += pl.diags; continue }
                diags += pl.diags + Diag.headerPosition(s.span)
                continue
            }
            if (s != null && !edit && s is Delete) {
                diags += pl.diags + Diag.unexpected(s.span, "-")
                continue
            }
            count++
            if (count > Limits.STATEMENTS) {
                diags += Diag.limit(s?.span ?: com.echoflow.weave.core.parse.Span.NONE, "${Limits.STATEMENTS} statements")
                break
            }
            kept += pl
        }
        val fixed = AutoFix.s9(kept) { BuiltinCatalog.component(it) != null }
        // Duplicates within the fragment: later wins (02 §5.2). Effects merge instead (S12).
        val stmts = ArrayList<Pair<Stmt, ParsedLine>>()
        val lastIndex = HashMap<String, Int>()
        for (pl in fixed) {
            diags.addAll(pl.diags)
            val s = pl.stmt ?: continue
            stmts += s to pl
            nameKey(s)?.let { lastIndex[it] = stmts.size - 1 }
        }
        val deduped = ArrayList<Stmt>()
        for ((i, pair) in stmts.withIndex()) {
            val s = pair.first
            val k = nameKey(s)
            if (k != null && lastIndex[k] != i && !edit) {
                diags += Diag.duplicate(s.span, k.substringAfter(':'), k.substringAfter(':'))
                continue
            }
            deduped += s
        }
        val bindings = deduped.filterIsInstance<Binding>().map { it.name }.toSet()
        val functions = deduped.filterIsInstance<FnDecl>().map { it.name }.toSet()
        val states = deduped.filterIsInstance<StateDecl>().map { it.name }.toSet()
        return checkFragment(header, deduped, catalog, bindings, functions, states, diags, streaming = streaming)
    }

    /** Runs the checker with an explicit name environment (edits check against the whole block). */
    fun checkFragment(
        header: HeaderStmt?, stmts: List<Stmt>, catalog: Catalog,
        bindings: Set<String>, functions: Set<String>, states: Set<String>, diags0: List<Diagnostic> = emptyList(),
        deleted: Set<String> = emptySet(),
        streaming: Boolean = false,
    ): Fragment {
        val diags = ArrayList(diags0)
        val checker = Checker(catalog, bindings, functions, states, deleted, streaming)
        val checked = ArrayList<Stmt>()
        for (s in stmts) {
            val name = when (s) {
                is Binding -> s.name; is StateDecl -> "$" + s.name; is FnDecl -> s.name; else -> null
            }
            val c = checker.checkStmt(s, name)
            diags.addAll(checker.diags); checker.diags.clear()
            if (c == null) continue
            checked += if (c is Effect) c.copy(key = CanonicalPrinter.triggerKey(c.trigger)) else c
        }
        val merged = AutoFix.s12(checked, diags)
        return Fragment(header, merged, diags)
    }

    private fun nameKey(s: Stmt): String? = when (s) {
        is StateDecl -> "s:$" + s.name
        is Binding -> "v:" + s.name
        is FnDecl -> "v:" + s.name
        else -> null
    }

    fun build(lines: List<ParsedLine>, catalog: Catalog, complete: Boolean, frozenCount: Int): WeaveProgram {
        val f = fragment(lines, catalog, edit = false, streaming = !complete)
        // frozen names come from the checked statements (after auto-fix renames such as S9's
        // `Header` → `header`), picked by source position: a statement is frozen when it ends
        // within the frozen lines
        val frozenNames = if (complete) null else {
            val boundary = lines.take(frozenCount).mapNotNull { it.stmt?.span?.end }.maxOrNull() ?: -1
            f.stmts.filter { it.span.end in 0..boundary }.mapNotNull { st ->
                when (st) { is StateDecl -> "$" + st.name; is Binding -> st.name; is FnDecl -> st.name; else -> null }
            }.toSet()
        }
        return assemble(f, catalog, complete, frozenCount, frozenNames = frozenNames)
    }

    /** Builds the program maps, header, root and exports from a checked fragment. */
    /**
     * [truncate]: drop statements past the source limit (a streamed or compiled block). An edit
     * passes false: an oversized edit is rejected whole by `Edits.apply` instead (02 §11.2).
     */
    fun assemble(
        f: Fragment, catalog: Catalog, complete: Boolean, frozenCount: Int, version: Int = 1, frozenNames: Set<String>? = null,
        truncate: Boolean = true,
    ): WeaveProgram {
        val diags = ArrayList(f.diagnostics)
        // 05 §13.3: statements are accepted in source order while the source fits; the first one
        // that would pass the limit, and everything after it, is dropped (not left active)
        val printer = WeaveProgram.printer(catalog)
        var used = f.header?.let { h -> runCatching { printer.stmt(h) }.getOrDefault("").toByteArray(Charsets.UTF_8).size + 1 } ?: 0
        val accepted = ArrayList<Stmt>()
        for (st in f.stmts) {
            val size = runCatching { printer.stmt(st) }.getOrDefault("").toByteArray(Charsets.UTF_8).size + 1
            if (truncate && used + size > Limits.SOURCE_BYTES) { diags += Diag.limit(st.span, "source"); break }
            used += size
            accepted += st
        }
        val states = LinkedHashMap<String, StateDecl>()
        val bindings = LinkedHashMap<String, Binding>()
        val functions = LinkedHashMap<String, FnDecl>()
        val effects = LinkedHashMap<String, Effect>()
        val exports = LinkedHashSet<String>()
        val exportStmts = ArrayList<Export>()
        for (s in accepted) when (s) {
            is StateDecl -> states[s.name] = s
            is Binding -> { functions.remove(s.name); bindings[s.name] = s }
            is FnDecl -> { bindings.remove(s.name); functions[s.name] = s }
            is Effect -> effects[s.key] = s
            is Export -> exportStmts += s
            else -> {}
        }
        for (e in exportStmts) for (n in e.names) {
            val ok = if (n.startsWith("$")) n.removePrefix("$") in states else n in bindings || n in functions
            if (ok) exports += n else diags += Diag.unresolved(e.span, n)
        }
        val header = header(f.header, diags)
        var root: String? = if ("root" in bindings) "root" else null
        if (root == null && complete) {
            val first = bindings.values.firstOrNull { it.expr is Call && (it.expr as Call).kind == CallKind.COMPONENT }
            if (first != null) { root = first.name; diags += Diag.noRoot(first.span, first.name) }
            else diags += Diag.noRoot(com.echoflow.weave.core.parse.Span.NONE, "")
        }
        return WeaveProgram(header, states, bindings, functions, effects, exports, root, diags, complete, frozenCount, version, frozenNames)
    }

    private val ID = Regex("[a-z][a-z0-9_]{0,31}")
    private val SIZES = setOf("inline", "card", "wide", "full")

    private fun header(h: HeaderStmt?, diags: MutableList<Diagnostic>): Header {
        if (h == null) return Header()
        var id: String? = null
        var title: String? = null
        var size = "inline"
        var icon: String? = null
        var keep = false
        var tier: Int? = null
        for ((k, v) in h.attrs) {
            val value = (v as? Lit)?.value
            val text = when {
                value is WSym -> value.name
                value is WStr -> value.v
                v is Str -> v.parts.filterIsInstance<TextPart>().joinToString("") { it.text }
                value is WNum -> CanonicalPrinter.number(value.v)
                value is WBool -> value.v.toString()
                else -> null
            }
            when (k) {
                "id" -> if (text != null && ID.matches(text)) id = text else diags += Diag.autofix(v.span, "invalid id \"$text\" replaced with a generated one")
                "title" -> title = text?.let { if (it.codePointCount(0, it.length) > 60) it.take(59) + "…" else it }
                "size" -> if (text in SIZES) size = text!! else diags += Diag.unknownArg(v.span, "@weave", "size: $text")
                "icon" -> icon = text
                "keep" -> keep = text == "true"
                "tier" -> tier = text?.toDoubleOrNull()?.toInt()
                else -> diags += Diag.unknownArg(v.span, "@weave", k)
            }
        }
        return Header(h.version, id, title, size, icon, keep, tier, h)
    }
}
