package com.echoflow.weave.core.parse

import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import java.math.BigDecimal

/**
 * The one exact text form of a program, 02 §13. Lives in `parse` (next to the parser, which needs
 * it for trigger keys); `program/` re-exports the program-level entry point.
 *
 * [paramOrder] gives the catalog parameter order for a callee so named arguments print in catalog
 * order (02 §13 rule 3); without it, source order is kept.
 */
class CanonicalPrinter(
    private val paramOrder: (CallKind, String) -> List<String>? = { _, _ -> null },
    private val compact: Boolean = false,
) {
    private val sep get() = if (compact) "," else ", "
    private val colon get() = if (compact) ":" else ": "
    private fun op(o: String) = if (compact && o !in WORD_OPS) o else " $o "

    // ---- statements ----------------------------------------------------------------------------

    fun stmt(s: Stmt): String = when (s) {
        is HeaderStmt -> buildString {
            append("@weave ").append(s.version)
            for (k in HEADER_ORDER) s.attrs[k]?.let { append(' ').append(k).append(": ").append(expr(it)) }
        }
        is StateDecl -> buildString {
            append('$').append(s.name)
            s.type?.let { append(": ").append(it.keyword) }
            append(" = ").append(expr(s.init))
            if (s.transient) append(" transient")
            if (s.reset) append(" reset")
        }
        is Binding -> "${s.name} = ${expr(s.expr)}"
        is FnDecl -> if (s.js != null) "js fn ${s.name}(${s.params.joinToString(", ")}) = \"\"\"${s.js}\"\"\""
        else "fn ${s.name}(${s.params.joinToString(", ")}) = ${expr(s.body!!)}"
        is Effect -> buildString {
            append("on ").append(trigger(s.trigger))
            s.condition?.let { append(" when ").append(expr(it)) }
            append(" -> ").append(expr(s.action))
        }
        is Export -> "export " + s.names.joinToString(", ")
        is Delete -> "-" + s.target
    }

    fun trigger(c: Call): String =
        if (c.leads.isEmpty() && c.named.isEmpty()) c.callee else c.callee + args(c)

    // ---- expressions ---------------------------------------------------------------------------

    fun expr(e: Expr): String = print(e)

    private fun prec(e: Expr): Int = when (e) {
        is Lambda -> 0
        is Ternary -> 2
        is BinaryOp -> when (e.op) {
            "??" -> 3; "or" -> 4; "and" -> 5
            "==", "!=", "<", "<=", ">", ">=", "in" -> 7
            ".." -> 8; "+", "-" -> 9; "*", "/", "%" -> 10
            else -> 9
        }
        is Unary -> if (e.op == "not") 6 else 11
        else -> 12
    }

    private fun wrap(e: Expr, needParens: Boolean) = if (needParens) "(" + print(e) + ")" else print(e)

    private fun print(e: Expr): String = when (e) {
        is Lit -> value(e.value)
        is Str -> string(e)
        is Name -> e.name
        is StateRef -> "$" + e.name
        is Qualified -> e.block + "::" + e.name
        is HexLit -> e.hex
        is Member -> wrap(e.target, prec(e.target) < 12) + "." + e.name
        is Index -> wrap(e.target, prec(e.target) < 12) + "[" + print(e.index) + "]"
        is Unary -> if (e.op == "not") "not " + wrap(e.operand, e.operand is BinaryOp || e.operand is Ternary || e.operand is Lambda)
        else "-" + wrap(e.operand, prec(e.operand) < 11)
        is BinaryOp -> {
            val p = prec(e)
            val (lp, rp) = when {
                e.op == "??" -> (prec(e.left) <= p) to (prec(e.right) < p)
                p == 7 || p == 8 -> (prec(e.left) <= p) to (prec(e.right) <= p)
                else -> (prec(e.left) < p) to (prec(e.right) <= p)
            }
            wrap(e.left, lp) + op(e.op) + wrap(e.right, rp)
        }
        is Ternary -> wrap(e.cond, prec(e.cond) <= 2) + op("?") + print(e.then) + op(":") + print(e.otherwise)
        is ListLit -> "[" + e.items.joinToString(sep) { print(it) } + "]"
        is RecLit -> "{" + e.fields.joinToString(sep) { (k, v) -> key(k) + colon + print(v) } + "}"
        is Lambda -> (if (e.params.size == 1) e.params[0] else "(" + e.params.joinToString(sep) + ")") + op("=>") + print(e.body)
        is Call -> call(e)
    }

    private fun call(c: Call): String = buildString {
        if (c.kind == CallKind.ACTION) append('!')
        append(c.callee)
        val hasArgs = c.leads.isNotEmpty() || c.named.isNotEmpty()
        if (hasArgs || c.kind == CallKind.ACTION || c.kind == CallKind.FUNCTION || c.kind == CallKind.MARK) append(args(c))
        c.children?.let { ch -> append(if (compact) "[" else " [").append(ch.joinToString(sep) { print(it) }).append(']') }
    }

    private fun args(c: Call): String {
        val order = paramOrder(c.kind, c.callee)
        val named = if (order == null) c.named else c.named.sortedBy { (k, _) -> order.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } }
        val parts = c.leads.map { print(it) } + named.map { (k, v) -> k + colon + print(v) }
        return "(" + parts.joinToString(sep) + ")"
    }

    private fun key(k: String) = if (IDENT.matches(k)) k else quote(k)

    private fun string(s: Str): String = buildString {
        append('"')
        for (p in s.parts) when (p) {
            is TextPart -> append(escape(p.text))
            is InterpPart -> {
                append('{').append(print(p.expr))
                for (f in p.formats) {
                    append(" | ").append(f.name)
                    if (f.args.isNotEmpty()) append('(').append(f.args.joinToString(", ") { value(it) }).append(')')
                }
                append('}')
            }
        }
        append('"')
    }

    fun value(v: WValue): String = when (v) {
        WNull -> "null"
        is WBool -> v.v.toString()
        is WNum -> number(v.v)
        is WStr -> quote(v.v)
        is WSym -> v.name
        is WDur -> duration(v.ms)
        else -> v.toString()
    }

    companion object {
        private val WORD_OPS = setOf("and", "or", "in")
        private val IDENT = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val HEADER_ORDER = listOf("id", "title", "size", "icon", "keep", "tier")

        fun number(d: Double): String {
            if (d == Math.floor(d) && Math.abs(d) < 1e21) return BigDecimal(d).toPlainString()
            val s = BigDecimal(d.toString()).stripTrailingZeros().toPlainString()
            return s
        }

        fun duration(ms: Long): String = when {
            ms != 0L && ms % 86_400_000L == 0L -> "${ms / 86_400_000L}d"
            ms != 0L && ms % 3_600_000L == 0L -> "${ms / 3_600_000L}h"
            ms != 0L && ms % 60_000L == 0L -> "${ms / 60_000L}m"
            ms != 0L && ms % 1_000L == 0L -> "${ms / 1_000L}s"
            else -> "${ms}ms"
        }

        fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            .replace("\t", "\\t").replace("{", "{{").replace("}", "}}")

        fun quote(s: String) = "\"" + escape(s) + "\""

        /** The trigger key, 02 §5.5: the canonical trigger with no spaces at all. */
        fun triggerKey(c: Call): String = CanonicalPrinter(compact = true).trigger(c)
    }
}
