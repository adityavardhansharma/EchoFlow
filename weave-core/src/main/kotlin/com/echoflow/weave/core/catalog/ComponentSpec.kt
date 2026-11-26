package com.echoflow.weave.core.catalog

import com.echoflow.weave.core.check.TokenSet
import com.echoflow.weave.core.check.TypeSpec
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.parse.BinaryOp
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.Expr
import com.echoflow.weave.core.parse.ListLit
import com.echoflow.weave.core.parse.Lit
import com.echoflow.weave.core.parse.Name
import com.echoflow.weave.core.parse.Parser
import com.echoflow.weave.core.parse.Str
import com.echoflow.weave.core.parse.TextPart
import com.echoflow.weave.core.eval.WStr

/** 05 §3.3. [defaultText] is the default as written in 04 (used verbatim in prompt lines). */
data class ParamSpec(
    val name: String,
    val type: TypeSpec,
    val default: WValue?,
    val required: Boolean,
    val doc: String = "",
    val deprecated: String? = null,
    val defaultText: String? = null,
)

sealed interface ChildrenSpec {
    object None : ChildrenSpec
    object Any : ChildrenSpec
    data class Exactly(val n: Int) : ChildrenSpec
    data class Only(val names: Set<String>) : ChildrenSpec
    /** Chart / Canvas: children are marks of this container. */
    data class Marks(val container: String) : ChildrenSpec
}

enum class PlaceholderKind { CHART, TEXT, METRIC, TABLE, INPUT, BUTTON, BAR, NONE }

data class ComponentSpec(
    val name: String,
    val leads: List<ParamSpec>,
    val params: List<ParamSpec>,
    val positionalOrder: List<String>,
    val aliases: Map<String, String>,
    val children: ChildrenSpec,
    val tier: Int,
    val phase: Int,
    val doc: String,
    val placeholder: PlaceholderKind,
    val blockLevel: Boolean,
    val intrinsics: Boolean = true,
    /** Section heading in the prompt (06 §3.3). */
    val group: String = "Layout",
) {
    fun param(name: String): ParamSpec? = leads.firstOrNull { it.name == name } ?: params.firstOrNull { it.name == name }
    val allParams get() = leads + params
    /** True when the first lead binds state (inputs, 04 §7). */
    val bindsState get() = leads.firstOrNull()?.type == TypeSpec.STATE
}

data class MarkSpec(
    val name: String,
    val container: Set<String>,
    val leads: List<ParamSpec>,
    val params: List<ParamSpec>,
    val acceptsChildren: Boolean,
    val tier: Int,
    val phase: Int,
    val doc: String,
) {
    fun param(name: String): ParamSpec? = leads.firstOrNull { it.name == name } ?: params.firstOrNull { it.name == name }
    val allParams get() = leads + params
}

data class ActionSpec(
    val name: String,
    val leads: List<ParamSpec>,
    val params: List<ParamSpec>,
    val local: Boolean,
    val needsConfirm: Boolean,
    val tier: Int,
    val phase: Int,
    val doc: String,
    /** `!reset($a, $b, …)` takes any number of leads. */
    val variadic: Boolean = false,
    /** Leads written as bare binding names (`!open(details)`), resolved as names, not values. */
    val nameLeads: Boolean = false,
    val positionalOrder: List<String> = params.map { it.name },
) {
    fun param(name: String): ParamSpec? = leads.firstOrNull { it.name == name } ?: params.firstOrNull { it.name == name }
    val allParams get() = leads + params
}

data class FunctionSpec(
    val name: String,
    val params: List<ParamSpec>,
    val lazyArgs: Set<Int>,
    val returns: TypeSpec,
    val tier: Int,
    val doc: String,
    val phase: Int = 1,
)

/** Effect triggers, 03 §8.2. */
data class TriggerSpec(val name: String, val params: List<ParamSpec>, val tier: Int, val phase: Int)

/**
 * A small parser for the signatures as written in 04/03, e.g.
 * `value: place, min = 0, step?: num, variant: filled|tonal = filled`. Keeps the catalog close to
 * the spec text.
 */
object Sig {
    fun params(sig: String): List<ParamSpec> = splitTop(sig).filter { it.isNotBlank() }.map { param(it.trim()) }

    private fun param(p: String): ParamSpec {
        // name[?][: type][ = default]
        var rest = p
        var default: String? = null
        val eq = topLevelIndex(rest, " = ").let { if (it < 0) topLevelIndex(rest, "=") else it }
        if (eq >= 0) {
            default = rest.substring(eq).removePrefix(" = ").removePrefix("=").trim()
            rest = rest.substring(0, eq).trim()
        }
        var type: String? = null
        val colon = rest.indexOf(':')
        if (colon >= 0) { type = rest.substring(colon + 1).trim(); rest = rest.substring(0, colon).trim() }
        val optional = rest.endsWith("?")
        val name = rest.removeSuffix("?")
        val ts = when {
            type != null -> type(type)
            default != null -> inferType(name, default)
            else -> inferType(name, null)
        }
        val dv = default?.let { defaultValue(it) }
        return ParamSpec(name, ts, dv, required = !optional && default == null, defaultText = default)
    }

    private val NAMED_TYPES = mapOf(
        "any" to TypeSpec.ANY, "num" to TypeSpec.NUM, "str" to TypeSpec.STR, "bool" to TypeSpec.BOOL, "dur" to TypeSpec.DUR,
        "date" to TypeSpec.DATE, "datetime" to TypeSpec.Simple("datetime"), "time" to TypeSpec.Simple("time"),
        "list" to TypeSpec.LIST, "rec" to TypeSpec.REC, "node" to TypeSpec.NODE, "nodes" to TypeSpec.NODES,
        "action" to TypeSpec.ACTION, "fn" to TypeSpec.FN, "field" to TypeSpec.FIELD, "place" to TypeSpec.STATE, "state" to TypeSpec.STATE,
        "name" to TypeSpec.NAME, "image" to TypeSpec.Simple("image"),
        "icon" to TypeSpec.ICON, "color" to TypeSpec.COLOR, "space" to TypeSpec.SPACE, "height" to TypeSpec.HEIGHT,
        "width" to TypeSpec.WIDTH, "tone" to TypeSpec.TONE, "shape" to TypeSpec.SHAPE,
        "align" to TypeSpec.Enum(Tokens.ALIGN), "valign" to TypeSpec.Enum(Tokens.VALIGN), "justify" to TypeSpec.Enum(Tokens.JUSTIFY),
        "axis" to TypeSpec.REC, "col" to TypeSpec.REC,
    )

    fun type(t: String): TypeSpec {
        val s = t.trim()
        if (s.startsWith("token<")) {
            val set = s.removePrefix("token<").removeSuffix(">")
            return when (set) {
                "space" -> TypeSpec.SPACE; "height" -> TypeSpec.HEIGHT; "width" -> TypeSpec.WIDTH
                "shape" -> TypeSpec.SHAPE; "text" -> TypeSpec.TEXT; "icon" -> TypeSpec.ICON
                "haptic" -> TypeSpec.HAPTIC; "elev" -> TypeSpec.Tok(TokenSet.ELEV)
                "export" -> TypeSpec.Enum(Tokens.EXPORT)
                else -> TypeSpec.ANY
            }
        }
        if (s.startsWith("list<") || s.startsWith("fn(")) return if (s.startsWith("fn(")) TypeSpec.FN else TypeSpec.LIST
        val parts = s.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size == 1) return NAMED_TYPES[parts[0]] ?: TypeSpec.Enum(parts)
        val known = parts.mapNotNull { NAMED_TYPES[it] ?: if (it.startsWith("list<") || it.startsWith("fn(")) type(it) else null }
        val words = parts.filter { NAMED_TYPES[it] == null && !it.startsWith("list<") && !it.startsWith("fn(") }
        if (known.isEmpty()) return TypeSpec.Enum(words)
        val opts = known + (if (words.isEmpty()) emptyList() else listOf(TypeSpec.Enum(words)))
        return if (opts.size == 1) opts[0] else TypeSpec.Union(opts)
    }

    /** Types for params written without one (`gap = s`), by name, then by default literal. */
    private fun inferType(name: String, default: String?): TypeSpec {
        when (name) {
            "gap", "rowGap", "pad", "padX", "padY" -> return TypeSpec.SPACE
            "h", "minH" -> return TypeSpec.HEIGHT
            "w" -> return TypeSpec.WIDTH
            "tone" -> return TypeSpec.TONE
            "emphasis" -> return TypeSpec.Enum(Tokens.EMPHASIS)
            "shape", "round" -> return TypeSpec.SHAPE
            "icon", "trailIcon" -> return TypeSpec.ICON
            "label", "title", "subtitle", "format", "prefix", "suffix", "placeholder", "help" -> return TypeSpec.STR
            "onTap", "onLongPress", "onDoubleTap" -> return TypeSpec.ACTION
            "series", "group", "x", "y", "y0", "size" -> if (default == null) return TypeSpec.FIELD
        }
        if (default == null) return TypeSpec.ANY
        val v = defaultValue(default)
        return when (v) {
            is WNum -> TypeSpec.NUM
            is com.echoflow.weave.core.eval.WBool -> TypeSpec.BOOL
            is WStr -> TypeSpec.STR
            is WList -> TypeSpec.LIST
            else -> TypeSpec.ANY
        }
    }

    /** Evaluates a default written in the spec: numbers, `1/2`, words (tokens), lists, strings. */
    fun defaultValue(text: String): WValue {
        val e = Parser.parseExpr(text) ?: return WSym(text)
        return constEval(e)
    }

    private fun constEval(e: Expr): WValue = when (e) {
        is Lit -> e.value
        is Name -> WSym(e.name)
        is Str -> WStr(e.parts.filterIsInstance<TextPart>().joinToString("") { it.text })
        is ListLit -> WList(e.items.map { constEval(it) })
        is BinaryOp -> {
            val a = constEval(e.left); val b = constEval(e.right)
            if (a is WNum && b is WNum) when (e.op) {
                "/" -> WNum.of(a.v / b.v); "*" -> WNum.of(a.v * b.v); "+" -> WNum.of(a.v + b.v); "-" -> WNum.of(a.v - b.v)
                else -> WNull
            } else WNull
        }
        is Call -> WSym(e.callee)
        else -> WNull
    }

    private fun splitTop(s: String): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        var last = 0
        for ((i, c) in s.withIndex()) when (c) {
            '(', '[', '{', '<' -> depth++
            ')', ']', '}', '>' -> depth--
            ',' -> if (depth == 0) { out += s.substring(last, i); last = i + 1 }
        }
        out += s.substring(last)
        return out
    }

    private fun topLevelIndex(s: String, needle: String): Int {
        var depth = 0
        var i = 0
        while (i <= s.length - needle.length) {
            val c = s[i]
            if (c == '(' || c == '[' || c == '{' || c == '<') depth++
            if (c == ')' || c == ']' || c == '}' || c == '>') depth--
            if (depth == 0 && s.startsWith(needle, i) && !s.startsWith("=>", i) && !(needle == "=" && s.startsWith("==", i))) return i
            i++
        }
        return -1
    }
}
