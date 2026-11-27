package com.echoflow.weave.core.program

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.check.Severity
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.CallKind
import com.echoflow.weave.core.parse.CanonicalPrinter
import com.echoflow.weave.core.parse.Effect
import com.echoflow.weave.core.parse.Export
import com.echoflow.weave.core.parse.FnDecl
import com.echoflow.weave.core.parse.HeaderStmt
import com.echoflow.weave.core.parse.StateDecl
import com.echoflow.weave.core.parse.Stmt

/** The block header, 02 §7. [id] is null until the thread assigns one (`b1`, `b2`, …). */
data class Header(
    val version: Int = 1,
    val id: String? = null,
    val title: String? = null,
    val size: String = "inline",
    val icon: String? = null,
    val keep: Boolean = false,
    val tier: Int? = null,
    /** The original header statement, for printing. */
    val stmt: HeaderStmt? = null,
)

/** A checked program, 05 §3.4. Immutable. */
class WeaveProgram(
    val header: Header,
    statesIn: Map<String, StateDecl>,
    bindingsIn: Map<String, Binding>,
    functionsIn: Map<String, FnDecl>,
    effectsIn: Map<String, Effect>,
    val exports: Set<String>,
    val root: String?,
    val diagnostics: List<Diagnostic>,
    val complete: Boolean,
    val frozenCount: Int,
    val version: Int = 1,
) {
    /** A higher `@weave` version: render as code with "Needs a newer EchoFlow" (02 §7). */
    val needsNewerApp get() = header.version > 1

    val states: Map<String, StateDecl> = java.util.Collections.unmodifiableMap(LinkedHashMap(statesIn))
    val bindings: Map<String, Binding> = java.util.Collections.unmodifiableMap(LinkedHashMap(bindingsIn))
    val functions: Map<String, FnDecl> = java.util.Collections.unmodifiableMap(LinkedHashMap(functionsIn))
    val effects: Map<String, Effect> = java.util.Collections.unmodifiableMap(LinkedHashMap(effectsIn))

    /**
     * False when the block must render as a code fence with the "Couldn't render" chip.
     * A binding named `root` is not enough: it has to evaluate to a node (02 §5.6).
     */
    val renderable: Boolean
        get() = !needsNewerApp && root != null &&
            runCatching { com.echoflow.weave.core.eval.Session(this).root() != null }.getOrDefault(false)

    val errors get() = diagnostics.filter { it.severity == Severity.ERROR }
    val warnings get() = diagnostics.filter { it.severity == Severity.WARNING }

    /** All statements (without the header), in canonical group order. */
    fun statements(): List<Stmt> {
        val out = ArrayList<Stmt>()
        out += states.values
        root?.let { bindings[it] }?.let { out += it }
        out += bindings.values.filter { it.name != root }
        out += functions.values
        out += effects.values
        if (exports.isNotEmpty()) out += Export(exports.toList(), com.echoflow.weave.core.parse.Span.NONE)
        return out
    }

    /** Canonical print, 02 §13. */
    fun print(catalog: Catalog = BuiltinCatalog): String {
        val pr = printer(catalog)
        val groups = ArrayList<List<String>>()
        header.stmt?.let { groups += listOf(pr.stmt(it)) }
        groups += states.values.map { pr.stmt(it) }
        val rootB = root?.let { bindings[it] }
        groups += (listOfNotNull(rootB) + bindings.values.filter { it.name != root }).map { pr.stmt(it) }
        groups += functions.values.map { pr.stmt(it) }
        groups += effects.values.map { pr.stmt(it) }
        if (exports.isNotEmpty()) groups += listOf("export " + exports.joinToString(", "))
        return groups.filter { it.isNotEmpty() }.joinToString("\n\n") { it.joinToString("\n") }
    }

    companion object {
        fun printer(catalog: Catalog = BuiltinCatalog) = CanonicalPrinter(paramOrder = { kind, name ->
            when (kind) {
                CallKind.COMPONENT -> catalog.full.component(name)?.let { s -> s.allParams.map { it.name } + BuiltinCatalog.COMMON_NAMES }
                CallKind.ACTION -> catalog.full.action(name)?.allParams?.map { it.name }
                CallKind.MARK -> catalog.full.marks.firstOrNull { it.name == name }?.allParams?.map { it.name }
                CallKind.FUNCTION -> catalog.full.function(name)?.params?.map { it.name }
            }
        })
    }
}

