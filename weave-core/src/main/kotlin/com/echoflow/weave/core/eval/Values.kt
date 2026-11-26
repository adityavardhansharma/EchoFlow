package com.echoflow.weave.core.eval

import com.echoflow.weave.core.parse.Expr
import com.echoflow.weave.core.parse.Span
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The Weave value model, 03 §1.1. All values are immutable and compare structurally, except
 * [WAction], [WFn] and [WNode] (05 §3.2).
 *
 * (03 §1.1 asks for `@Immutable`; that is a Compose annotation, so `weave-core` documents the
 * contract here and `weave-compose` treats these classes as stable.)
 */
sealed interface WValue {
    val typeName: String
}

object WNull : WValue {
    override val typeName = "null"
    override fun toString() = "null"
}

data class WBool(val v: Boolean) : WValue {
    override val typeName get() = "bool"
    companion object {
        val TRUE = WBool(true)
        val FALSE = WBool(false)
        fun of(b: Boolean) = if (b) TRUE else FALSE
    }
}

/** Never NaN or ±∞: use [WNum.of], which turns those into [WNull]. */
data class WNum(val v: Double) : WValue {
    override val typeName get() = "num"
    override fun equals(other: Any?) = other is WNum && (other.v == v)
    override fun hashCode() = if (v == 0.0) 0 else v.hashCode()
    companion object {
        fun of(d: Double): WValue = if (d.isNaN() || d.isInfinite()) WNull else WNum(if (d == 0.0) 0.0 else d)
        fun of(i: Int): WValue = WNum(i.toDouble())
        fun of(l: Long): WValue = WNum(l.toDouble())
    }
}

data class WStr(val v: String) : WValue {
    override val typeName get() = "str"
}

data class WDur(val ms: Long) : WValue {
    override val typeName get() = "dur"
}

data class WDate(val v: LocalDate) : WValue {
    override val typeName get() = "date"
}

data class WTime(val v: LocalTime) : WValue {
    override val typeName get() = "time"
}

data class WDateTime(val v: Instant) : WValue {
    override val typeName get() = "datetime"
}

data class WList(val items: List<WValue>) : WValue {
    override val typeName get() = "list"
    companion object {
        val EMPTY = WList(emptyList())
    }
}

/** Insertion-ordered; equality ignores order (03 §3.2). */
class WRec(val fields: LinkedHashMap<String, WValue>) : WValue {
    override val typeName get() = "rec"
    operator fun get(k: String): WValue = fields[k] ?: WNull
    override fun equals(other: Any?) = other is WRec && other.fields == fields
    override fun hashCode() = fields.hashCode()
    override fun toString() = "WRec($fields)"
    companion object {
        fun of(vararg pairs: Pair<String, WValue>) = WRec(linkedMapOf(*pairs))
    }
}

data class WSym(val name: String) : WValue {
    override val typeName get() = "sym"
}

data class WImage(val uri: String, val w: Int, val h: Int) : WValue {
    override val typeName get() = "image"
}

/**
 * A state place (03 §5.4): a state name and a path of members/indexes. Input leads and state
 * actions carry places; they are never stored in state.
 */
data class Place(val state: String, val path: List<WValue>) {
    override fun toString() = "$" + state + path.joinToString("") { if (it is WStr) ".${it.v}" else "[$it]" }
}

/**
 * A UI element (04). [args] hold evaluated, slot-coerced values (actions stay actions).
 * [places] holds the bound place of input leads. [waiting] is true when the node's own arguments
 * read a waiting state (03 §7.1), so the renderer shows its placeholder.
 */
class WNode(
    val type: String,
    val args: Map<String, WValue>,
    val children: List<WNode>,
    val key: String,
    val sourceStmt: String,
    val places: Map<String, Place> = emptyMap(),
    val waiting: Boolean = false,
    /** Set when the node can't render (E204/E205/E210 on a lead): the error chip text (04 §2.8). */
    val error: String? = null,
) : WValue {
    override val typeName get() = "node"
    override fun toString() = "WNode($type, $args, $children)"
}

/** An action value; runs only when an event fires (03 §7.2). Equality: kind + origin. */
class WAction(
    val kind: String,
    val args: List<Expr>,
    val named: List<Pair<String, Expr>>,
    val scope: Scope,
    val origin: Span,
) : WValue {
    override val typeName get() = "action"
    override fun equals(other: Any?) = other is WAction && other.kind == kind && other.origin == origin
    override fun hashCode() = kind.hashCode() * 31 + origin.hashCode()
    override fun toString() = "!$kind"
}

/** A callable: a lambda/`fn` ([body] non-null) or a built-in ([builtin] non-null). Equality by origin. */
class WFn(
    val params: List<String>,
    val body: Expr?,
    val scope: Scope?,
    val origin: Span?,
    val builtin: String? = null,
    val name: String? = null,
) : WValue {
    override val typeName get() = "fn"
    override fun equals(other: Any?) = other is WFn && other.origin == origin && other.builtin == builtin && other.name == name
    override fun hashCode() = (origin?.hashCode() ?: 0) * 31 + (builtin?.hashCode() ?: 0)
    override fun toString() = "fn(${params.joinToString()})"
}

/** A lexical scope for lambdas, `fn` parameters and event variables (03 §4.1). */
class Scope(val vars: Map<String, WValue>, val parent: Scope?, val isFnBoundary: Boolean = false) {
    fun lookup(name: String): WValue? {
        var s: Scope? = this
        while (s != null) {
            s.vars[name]?.let { return it }
            s = s.parent
        }
        return null
    }

    companion object {
        val EMPTY = Scope(emptyMap(), null)
    }
}

// ---- truthiness, equality, ordering (03 §3) ----------------------------------------------------

fun WValue.truthy(): Boolean = when (this) {
    WNull -> false
    is WBool -> v
    is WNum -> v != 0.0
    is WStr -> v.isNotEmpty()
    is WList -> items.isNotEmpty()
    is WRec -> fields.isNotEmpty()
    is WDur -> ms != 0L
    else -> true
}

/** 03 §3.2. */
fun weaveEquals(a: WValue, b: WValue): Boolean = when {
    a is WSym && b is WStr -> a.name == b.v
    a is WStr && b is WSym -> a.v == b.name
    a is WList && b is WList -> a.items.size == b.items.size && a.items.indices.all { weaveEquals(a.items[it], b.items[it]) }
    a is WRec && b is WRec -> a.fields.keys == b.fields.keys && a.fields.all { (k, v) -> weaveEquals(v, b.fields[k]!!) }
    else -> a == b
}

/** 03 §3.3. Returns null when the pair is not comparable. */
fun weaveCompare(a: WValue, b: WValue): Int? = when {
    a is WNum && b is WNum -> a.v.compareTo(b.v)
    a is WStr && b is WStr -> compareCodePoints(a.v, b.v)
    a is WSym && b is WSym -> compareCodePoints(a.name, b.name)
    a is WDur && b is WDur -> a.ms.compareTo(b.ms)
    a is WDate && b is WDate -> a.v.compareTo(b.v)
    a is WTime && b is WTime -> a.v.compareTo(b.v)
    a is WDateTime && b is WDateTime -> a.v.compareTo(b.v)
    a is WBool && b is WBool -> a.v.compareTo(b.v)
    else -> null
}

private fun compareCodePoints(a: String, b: String): Int {
    val x = a.codePoints().toArray()
    val y = b.codePoints().toArray()
    for (k in 0 until minOf(x.size, y.size)) if (x[k] != y[k]) return x[k].compareTo(y[k])
    return x.size.compareTo(y.size)
}

fun wList(vararg v: WValue) = WList(v.toList())
fun wNum(d: Double) = WNum.of(d)
fun wStr(s: String) = WStr(s)
