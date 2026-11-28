package com.echoflow.weave.core.parse

import com.echoflow.weave.core.check.WType
import com.echoflow.weave.core.eval.WValue

/** Offsets into the normalised body; [line] is 1-based. 05 §3.1. */
data class Span(val line: Int, val start: Int, val end: Int) {
    companion object {
        val NONE = Span(0, 0, 0)
    }
}

sealed interface Stmt {
    val span: Span
}

data class HeaderStmt(val version: Int, val attrs: Map<String, Expr>, override val span: Span) : Stmt

data class StateDecl(
    val name: String,
    val type: WType?,
    val init: Expr,
    val transient: Boolean,
    val reset: Boolean,
    override val span: Span,
) : Stmt

data class Binding(val name: String, val expr: Expr, override val span: Span) : Stmt

data class FnDecl(
    val name: String,
    val params: List<String>,
    val body: Expr?,
    val js: String?,
    override val span: Span,
    /** False when the checker refused this `js fn` (below tier 4, `E209`): it keeps its source but calls are `null`. */
    val jsAllowed: Boolean = true,
) : Stmt

data class Effect(
    val trigger: Call,
    val condition: Expr?,
    val action: Expr,
    val key: String,
    override val span: Span,
) : Stmt

/** `"$x"` for state. */
data class Export(val names: List<String>, override val span: Span) : Stmt

/** `"x"`, `"$x"` or `"on <key>"`. */
data class Delete(val target: String, override val span: Span) : Stmt

sealed interface Expr {
    val span: Span
}

data class Lit(val value: WValue, override val span: Span) : Expr
data class Str(val parts: List<StrPart>, override val span: Span) : Expr
data class Name(val name: String, override val span: Span) : Expr
data class StateRef(val name: String, override val span: Span) : Expr
data class Qualified(val block: String, val name: String, override val span: Span) : Expr
data class Member(val target: Expr, val name: String, override val span: Span) : Expr
data class Index(val target: Expr, val index: Expr, override val span: Span) : Expr
data class Unary(val op: String, val operand: Expr, override val span: Span) : Expr
data class BinaryOp(val op: String, val left: Expr, val right: Expr, override val span: Span) : Expr
data class Ternary(val cond: Expr, val then: Expr, val otherwise: Expr, override val span: Span) : Expr
data class ListLit(val items: List<Expr>, override val span: Span) : Expr
data class RecLit(val fields: List<Pair<String, Expr>>, override val span: Span) : Expr
data class Lambda(val params: List<String>, val body: Expr, override val span: Span) : Expr

data class Call(
    val kind: CallKind,
    val callee: String,
    val leads: List<Expr>,
    val named: List<Pair<String, Expr>>,
    val children: List<Expr>?,
    override val span: Span,
) : Expr

enum class CallKind { COMPONENT, ACTION, MARK, FUNCTION }

/** A `#RRGGBB` token (02 §2.10). Only valid as input to auto-fix S5; never survives checking. */
data class HexLit(val hex: String, override val span: Span) : Expr

sealed interface StrPart
data class TextPart(val text: String) : StrPart
data class InterpPart(val expr: Expr, val formats: List<FormatSpec>) : StrPart

/** `num(2)`, `plural(item, items)`, `currency("USD")`: args are WNum, WStr or WSym. */
data class FormatSpec(val name: String, val args: List<WValue>)
