package com.echoflow.weave.core.json

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.json.TolerantJson.J
import com.echoflow.weave.core.json.TolerantJson.JArr
import com.echoflow.weave.core.json.TolerantJson.JBool
import com.echoflow.weave.core.json.TolerantJson.JNull
import com.echoflow.weave.core.json.TolerantJson.JNum
import com.echoflow.weave.core.json.TolerantJson.JObj
import com.echoflow.weave.core.json.TolerantJson.JStr

/**
 * Weave-JSON → Weave source (02 §12.2–12.3). The result goes through the ordinary parser and
 * checker, so a JSON block and its source equivalent produce the same program (T-120).
 */
object JsonLowering {
    private val STATE_PATH = Regex("""^\$[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*|\[\d+])*$""")

    /** Lowers a block (or, with `"edit"`, an edit body). Null when the JSON isn't Weave-JSON. */
    fun lower(json: String, catalog: Catalog = BuiltinCatalog): Lowered? {
        val root = TolerantJson.parse(json) as? JObj ?: return null
        val f = root.fields
        val edit = (f["edit"] as? JStr)?.v
        if (edit == null && f["weave"] == null) return null
        val out = ArrayList<String>()
        if (edit == null) {
            val v = (f["weave"] as? JNum)?.v?.toInt() ?: 1
            val attrs = buildList {
                (f["id"] as? JStr)?.let { add("id: ${it.v}") }
                (f["title"] as? JStr)?.let { add("title: ${str(it.v)}") }
                (f["size"] as? JStr)?.let { add("size: ${it.v}") }
                (f["icon"] as? JStr)?.let { add("icon: ${it.v}") }
                (f["keep"] as? JBool)?.let { add("keep: ${it.v}") }
            }
            out += "@weave $v" + if (attrs.isEmpty()) "" else " " + attrs.joinToString(" ")
        }
        (f["state"] as? JObj)?.fields?.forEach { (k, v) ->
            out += if (v is JObj && v.fields.containsKey("=")) {
                val type = (v.fields["type"] as? JStr)?.v?.let { ": $it" } ?: ""
                val flags = listOfNotNull("transient".takeIf { (v.fields["transient"] as? JBool)?.v == true }, "reset".takeIf { (v.fields["reset"] as? JBool)?.v == true })
                "$$k$type = ${(v.fields["="] as? JStr)?.v ?: "null"}" + flags.joinToString("") { " $it" }
            } else "$$k = ${literal(v)}"
        }
        val nodes = (f["nodes"] as? JObj)?.fields ?: LinkedHashMap()
        // root first, as the prompt orders statements
        nodes["root"]?.let { out += "root = ${node(it, nodes.keys, catalog)}" }
        nodes.forEach { (k, v) -> if (k != "root") out += "$k = ${node(v, nodes.keys, catalog)}" }
        (f["let"] as? JObj)?.fields?.forEach { (k, v) -> (v as? JStr)?.let { out += "$k = ${it.v.removePrefix("=")}" } }
        (f["fn"] as? JObj)?.fields?.forEach { (sig, v) ->
            when (v) {
                is JStr -> out += "fn $sig = ${v.v.removePrefix("=")}"
                is JObj -> (v.fields["js"] as? JStr)?.let { out += "js fn $sig = \"\"\"${it.v}\"\"\"" }
                else -> {}
            }
        }
        (f["on"] as? JArr)?.items?.forEach { e ->
            val o = (e as? JObj)?.fields ?: return@forEach
            val trigger = (o["trigger"] as? JStr)?.v ?: return@forEach
            val cond = (o["when"] as? JStr)?.v?.let { " when $it" } ?: ""
            val act = when (val d = o["do"]) {
                is JStr -> d.v
                is JArr -> d.items.mapNotNull { (it as? JStr)?.v }.joinToString(", ", "[", "]")
                else -> return@forEach
            }
            out += "on $trigger$cond -> $act"
        }
        (f["export"] as? JArr)?.items?.mapNotNull { (it as? JStr)?.v }?.takeIf { it.isNotEmpty() }?.let { out += "export " + it.joinToString(", ") }
        (f["delete"] as? JArr)?.items?.mapNotNull { (it as? JStr)?.v }?.forEach { out += "-$it" }
        return Lowered(out.joinToString("\n"), edit)
    }

    data class Lowered(val source: String, val editTarget: String?)

    private fun str(s: String) = Formatter.jsonString(s)

    /** A JSON value as a Weave literal (state values: plain data). */
    private fun literal(v: J): String = when (v) {
        is JNum -> v.raw
        is JBool -> v.v.toString()
        JNull -> "null"
        is JStr -> str(v.v)
        is JArr -> v.items.joinToString(", ", "[", "]") { literal(it) }
        is JObj -> v.fields.entries.joinToString(", ", "{", "}") { (k, x) -> "${key(k)}: ${literal(x)}" }
    }

    private fun key(k: String) = if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(k)) k else str(k)

    /** An argument value per the 02 §12.2 table. */
    private fun arg(v: J, names: Set<String>, catalog: Catalog, tokenSlot: Boolean = false): String = when (v) {
        is JNum -> v.raw
        is JBool -> v.v.toString()
        JNull -> "null"
        is JStr -> {
            val s = v.v
            when {
                s.startsWith("=") -> s.substring(1)
                s.startsWith("!") -> s
                STATE_PATH.matches(s) -> s
                s.startsWith("'") && s.length > 1 && s[1] in "=!$" -> str(s.substring(1))
                tokenSlot && Regex("[a-z][A-Za-z0-9_]*").matches(s) -> s
                else -> str(s)
            }
        }
        is JArr -> v.items.joinToString(", ", "[", "]") { arg(it, names, catalog) }
        is JObj -> if (v.fields.containsKey("t")) node(v, names, catalog) else v.fields.entries.joinToString(", ", "{", "}") { (k, x) -> "${key(k)}: ${arg(x, names, catalog)}" }
    }

    private fun isToken(t: com.echoflow.weave.core.check.TypeSpec?): Boolean = when (t) {
        is com.echoflow.weave.core.check.TypeSpec.Tok, is com.echoflow.weave.core.check.TypeSpec.Enum -> true
        is com.echoflow.weave.core.check.TypeSpec.Union -> t.options.any { isToken(it) }
        else -> false
    }

    /** `{"t": T, "a": {…}, "c": […]}` → `T(named…) [children…]`; leads are passed by name. */
    private fun node(v: J, names: Set<String>, catalog: Catalog): String {
        val o = (v as? JObj)?.fields ?: return "null"
        val type = (o["t"] as? JStr)?.v ?: return "null"
        val spec = catalog.full.component(type)
        val a = (o["a"] as? JObj)?.fields ?: LinkedHashMap()
        val args = a.entries.map { (k0, x) ->
            val k = if (k0 == "bind") spec?.leads?.firstOrNull()?.name ?: "value" else k0
            val type = (spec?.param(k) ?: BuiltinCatalog.COMMON.firstOrNull { it.name == k })?.type
            "$k: ${arg(x, names, catalog, tokenSlot = isToken(type))}"
        }
        val kids = (o["c"] as? JArr)?.items?.map { c ->
            when (c) {
                is JStr -> if (c.v in names) c.v else "Text(${str(c.v)})"
                is JObj -> node(c, names, catalog)
                else -> "null"
            }
        }
        return "$type(${args.joinToString(", ")})" + (kids?.let { " [" + it.joinToString(", ") + "]" } ?: "")
    }
}
