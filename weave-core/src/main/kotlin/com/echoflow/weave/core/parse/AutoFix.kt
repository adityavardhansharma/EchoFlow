package com.echoflow.weave.core.parse

import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic

/**
 * Program-level structural auto-fix rules (02 §8.2): S9 (capitalised binding names) and S12
 * (merging same-trigger effects). Call-level rules (S2–S8, S10) run inside the checker, where slot
 * types are known; S1 and S11 run in the parser.
 */
object AutoFix {

    /** S5: hex colour to a colour role (02 §8.3). */
    fun hexToRole(hex: String, textSlot: Boolean): String {
        val h = hex.removePrefix("#")
        val rgb = when (h.length) {
            3, 4 -> Triple(h.substring(0, 1).repeat(2), h.substring(1, 2).repeat(2), h.substring(2, 3).repeat(2))
            else -> Triple(h.substring(0, 2), h.substring(2, 4), h.substring(4, 6))
        }
        val r = rgb.first.toInt(16) / 255.0
        val g = rgb.second.toInt(16) / 255.0
        val b = rgb.third.toInt(16) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        val d = max - min
        val s = if (d == 0.0) 0.0 else d / (1 - Math.abs(2 * l - 1))
        var hue = when {
            d == 0.0 -> 0.0
            max == r -> 60 * (((g - b) / d) % 6)
            max == g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }
        if (hue < 0) hue += 360
        if (s < 0.15 || l < 0.08 || l > 0.95) return if (textSlot) "muted" else "neutral"
        return when {
            hue < 20 || hue >= 345 -> "error"
            hue < 70 -> "warning"
            hue < 170 -> "success"
            else -> "primary"
        }
    }

    /**
     * S9: renames capitalised binding names that aren't components, rewriting references.
     * [isComponent] tells whether a name is a catalog component. Returns the new lines.
     */
    fun s9(lines: List<ParsedLine>, isComponent: (String) -> Boolean): List<ParsedLine> {
        val declared = lines.mapNotNull { (it.stmt as? Binding)?.name ?: (it.stmt as? FnDecl)?.name }.toSet()
        val renames = HashMap<String, String>()
        val out = lines.map { pl ->
            val b = pl.stmt as? Binding
            if (!pl.capitalisedName || b == null) return@map pl
            val lower = b.name.replaceFirstChar { it.lowercaseChar() }
            if (isComponent(b.name) || lower in declared) {
                pl.copy(stmt = null, diags = pl.diags + Diag.badName(b.span, b.name))
            } else {
                renames[b.name] = lower
                pl.copy(
                    stmt = b.copy(name = lower),
                    diags = pl.diags + Diag.autofix(b.span, "\"${b.name}\" renamed to \"$lower\""),
                )
            }
        }
        if (renames.isEmpty()) return out
        return out.map { pl -> pl.stmt?.let { pl.copy(stmt = Rewrite.stmt(it) { e -> renameRef(e, renames) }) } ?: pl }
    }

    private fun renameRef(e: Expr, renames: Map<String, String>): Expr? =
        if (e is Call && e.kind == CallKind.COMPONENT && e.callee in renames && e.leads.isEmpty() && e.named.isEmpty() && e.children == null)
            Name(renames.getValue(e.callee), e.span)
        else null

    /**
     * S12: merges a run of effects with the same trigger key into one effect whose action is the
     * source-ordered list of `!if(cond, action)` / `action` (02 §5.5). A `-on <key>` delete splits runs.
     */
    fun s12(stmts: List<Stmt>, diags: MutableList<Diagnostic>): List<Stmt> {
        val out = ArrayList<Stmt>()
        val openRun = HashMap<String, Int>() // key -> index in out of the run's merged effect
        for (s in stmts) {
            when {
                s is Effect -> {
                    val at = openRun[s.key]
                    if (at == null) {
                        openRun[s.key] = out.size
                        out += s
                    } else {
                        val prev = out[at] as Effect
                        val prevItems = if (prev.condition == null && prev.span.start == -1) (prev.action as ListLit).items
                        else listOf(contribution(prev))
                        val merged = Effect(
                            prev.trigger, null,
                            ListLit(prevItems + contribution(s), Span(prev.span.line, prev.action.span.start, s.span.end)),
                            prev.key, Span(prev.span.line, -1, s.span.end),
                        )
                        out[at] = merged
                        diags += Diag.autofix(Span(s.span.line, s.span.start, s.span.end), "merged effects on \"${s.key}\"")
                    }
                }
                s is Delete && s.target.startsWith("on ") -> {
                    openRun.remove(s.target.removePrefix("on "))
                    out += s
                }
                else -> out += s
            }
        }
        // restore real spans on merged effects (start = -1 marked "already merged")
        return out.map { if (it is Effect && it.span.start == -1) it.copy(span = Span(it.span.line, it.trigger.span.start, it.span.end)) else it }
    }

    private fun contribution(e: Effect): Expr =
        if (e.condition == null) e.action
        else Call(CallKind.ACTION, "if", listOf(e.condition, e.action), emptyList(), null, e.span)
}

/** Generic bottom-up rewriting of expressions. [f] returns a replacement or null to keep. */
object Rewrite {
    fun stmt(s: Stmt, f: (Expr) -> Expr?): Stmt = when (s) {
        is Binding -> s.copy(expr = expr(s.expr, f))
        is StateDecl -> s.copy(init = expr(s.init, f))
        is FnDecl -> s.copy(body = s.body?.let { expr(it, f) })
        is Effect -> s.copy(
            trigger = expr(s.trigger, f) as? Call ?: s.trigger,
            condition = s.condition?.let { expr(it, f) },
            action = expr(s.action, f),
        )
        else -> s
    }

    fun expr(e: Expr, f: (Expr) -> Expr?): Expr {
        val inner: Expr = when (e) {
            is Member -> e.copy(target = expr(e.target, f))
            is Index -> e.copy(target = expr(e.target, f), index = expr(e.index, f))
            is Unary -> e.copy(operand = expr(e.operand, f))
            is BinaryOp -> e.copy(left = expr(e.left, f), right = expr(e.right, f))
            is Ternary -> e.copy(cond = expr(e.cond, f), then = expr(e.then, f), otherwise = expr(e.otherwise, f))
            is ListLit -> e.copy(items = e.items.map { expr(it, f) })
            is RecLit -> e.copy(fields = e.fields.map { (k, v) -> k to expr(v, f) })
            is Lambda -> e.copy(body = expr(e.body, f))
            is Call -> e.copy(
                leads = e.leads.map { expr(it, f) },
                named = e.named.map { (k, v) -> k to expr(v, f) },
                children = e.children?.map { expr(it, f) },
            )
            is Str -> e.copy(parts = e.parts.map { p -> if (p is InterpPart) p.copy(expr = expr(p.expr, f)) else p })
            else -> e
        }
        return f(inner) ?: inner
    }
}
