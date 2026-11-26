package com.echoflow.weave.core.eval

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.ParamSpec
import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.TypeSpec
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.Expr
import com.echoflow.weave.core.parse.Index
import com.echoflow.weave.core.parse.Member
import com.echoflow.weave.core.parse.StateRef

/**
 * Evaluates component and mark calls into [WNode]s: arguments coerced to their slots (03 §2.3),
 * defaults filled in, input leads bound to places, children flattened (02 §6.4).
 */
internal class NodeBuilder(private val ev: Evaluator) {

    fun component(c: Call, scope: Scope): WValue {
        val spec = ev.catalog.full.component(c.callee) ?: return WNull
        return build(c, scope, spec.leads, { spec.param(it) ?: BuiltinCatalog.COMMON.firstOrNull { p -> p.name == it } }, spec.allParams)
    }

    fun mark(c: Call, scope: Scope): WValue {
        val spec = ev.catalog.full.marks.firstOrNull { it.name == c.callee } ?: return WNull
        return build(c, scope, spec.leads, { spec.param(it) }, spec.allParams)
    }

    private fun build(c: Call, scope: Scope, leads: List<ParamSpec>, param: (String) -> ParamSpec?, all: List<ParamSpec>): WValue {
        val args = LinkedHashMap<String, WValue>()
        val places = HashMap<String, Place>()
        var error: String? = null
        val waiting = ev.withWaitingTracking {
            for ((i, e) in c.leads.withIndex()) {
                val p = leads.getOrNull(i) ?: continue
                if (p.type == TypeSpec.STATE) {
                    val place = place(e, scope)
                    if (place != null) {
                        places[p.name] = place
                        args[p.name] = Places.read(ev.state(place.state), place.path)
                    } else args[p.name] = ev.eval(e, scope)
                    continue
                }
                val r = ev.coerce.slot(ev.eval(e, scope), p.type)
                if (!r.ok) {
                    ev.report(Diag.badType(e.span, c.callee, p.name, p.type.display, "another type"))
                    error = "${c.callee} needs ${p.name}"
                } else args[p.name] = r.value
            }
            for ((k, e) in c.named) {
                val p = param(k) ?: continue
                if (p.type == TypeSpec.STATE) {
                    place(e, scope)?.let { pl -> places[k] = pl; args[k] = Places.read(ev.state(pl.state), pl.path) }
                    continue
                }
                val raw = ev.eval(e, scope)
                val r = ev.coerce.slot(raw, p.type)
                if (!r.ok) ev.report(Diag.badType(e.span, c.callee, k, p.type.display, raw.typeName)) else args[k] = r.value
            }
        }
        for (p in all) {
            val v = args[p.name]
            if ((v == null || v == WNull) && p.default != null) args[p.name] = p.default
            if (p.required && (args[p.name] == null || args[p.name] == WNull) && p.name !in places && error == null && !waiting) {
                ev.report(Diag.missingRequired(c.span, c.callee, p.name))
                error = "${c.callee} needs ${p.name}"
            }
        }
        val children = ArrayList<WNode>()
        c.children?.forEach { e -> flatten(ev.eval(e, scope), children) }
        return WNode(c.callee, args, children, "", ev.currentStmt(), places, waiting, error)
    }

    private fun flatten(v: WValue, out: MutableList<WNode>) {
        when (v) {
            is WNode -> out += v
            is WList -> v.items.forEach { flatten(it, out) }
            WNull -> {}
            is WAction, is WFn -> {}
            else -> out += WNode("Text", mapOf("content" to WStr(ev.f.auto(v))), emptyList(), "", ev.currentStmt())
        }
    }

    /** A place expression (03 §5.4), or null when [e] is not one. */
    fun place(e: Expr, scope: Scope): Place? = when (e) {
        is StateRef -> Place(e.name, emptyList())
        is Member -> place(e.target, scope)?.let { Place(it.state, it.path + WStr(e.name)) }
        is Index -> place(e.target, scope)?.let { Place(it.state, it.path + ev.eval(e.index, scope)) }
        else -> null
    }
}
