package com.echoflow.weave.eval

import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue

/**
 * Programmatic checks for eval tasks (07 §10.1). Models choose their own labels and layout, so
 * checks match labels with case-insensitive patterns and read numbers out of the rendered text
 * (including Indian grouping and compact suffixes) with a tolerance.
 */
sealed interface Check {
    /** Writes [value] into the first input whose label (or bound state name) matches [label]. */
    data class Input(val label: String, val value: Double) : Check

    /** Taps the first button whose label matches. */
    data class Tap(val label: String) : Check

    /**
     * Some number in the rendered block is within [tol] of [value] (or [relTol] × value). When
     * [label] is given, only metrics with a matching label and text lines containing it are read.
     */
    data class Number(val value: Double, val label: String? = null, val tol: Double = 0.0, val relTol: Double = 0.005) : Check

    /** The block's plain text matches [pattern] (case-insensitive). */
    data class Text(val pattern: String) : Check

    /** At least [min] nodes of any of [types]. */
    data class Has(val types: Set<String>, val min: Int = 1) : Check

    /** The block has something the user can operate: an input, or a button / item with an action. */
    object Interactive : Check

    /** All of the alternatives in [any] — passes when any one passes. */
    data class AnyOf(val any: List<Check>) : Check
}

val CHARTS = setOf("Chart", "LineChart", "BarChart", "AreaChart")
val INPUTS = setOf("Slider", "Segmented", "TextField", "Select", "Toggle", "Check", "Radio", "Chips", "Stepper", "Tabs")

data class CheckResult(val pass: Boolean, val detail: String)

class CheckRunner(private val s: Session) {

    fun run(checks: List<Check>): CheckResult {
        for (c in checks) {
            val r = runOne(c)
            if (!r.pass) return r
        }
        return CheckResult(true, "ok")
    }

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private fun label(n: WNode): String = listOfNotNull(
        (n.args["label"] as? WStr)?.v,
        (n.args["title"] as? WStr)?.v,
        (n.args["suffix"] as? WStr)?.v,
        (n.args["prefix"] as? WStr)?.v,
    ).joinToString(" ")

    private fun runOne(c: Check): CheckResult = when (c) {
        is Check.Input -> {
            val inputs = s.nodes().filter { it.places.isNotEmpty() && it.type in INPUTS }
            val hit = inputs.firstOrNull { rx(c.label).containsMatchIn(label(it)) }
                ?: inputs.firstOrNull { rx(c.label).containsMatchIn(it.places.values.first().state) }
            if (hit == null) CheckResult(false, "no input matching /${c.label}/ among ${inputs.map { label(it).ifEmpty { it.places.values.first().state } }}")
            else {
                val place = hit.places.values.first()
                val v: WValue = if (hit.type == "TextField" && hit.args["kind"] != WSym("number") && hit.args["kind"] != WStr("number")) WStr(fmtPlain(c.value)) else WNum(c.value)
                s.input(place, v, hit.args["onChange"])
                CheckResult(true, "set ${label(hit)} = ${c.value}")
            }
        }
        is Check.Tap -> {
            val b = s.nodes().firstOrNull { it.type == "Button" && rx(c.label).containsMatchIn((it.args["label"] as? WStr)?.v ?: "") }
            val a = b?.args?.get("onTap")
            if (a == null) CheckResult(false, "no button /${c.label}/") else { s.fire(a); CheckResult(true, "tapped") }
        }
        is Check.Number -> {
            val texts = candidateTexts(c.label)
            val nums = texts.flatMap { numbersIn(it) }
            val tol = maxOf(c.tol, Math.abs(c.value) * c.relTol)
            if (nums.any { Math.abs(it - c.value) <= tol }) CheckResult(true, "found ${c.value}")
            else CheckResult(false, "expected ${c.value} ± $tol${c.label?.let { " near /$it/" } ?: ""}; saw ${nums.take(12)}")
        }
        is Check.Text -> {
            val r = rx(c.pattern)
            // text the user can reach: as shown, or after one tap on any button ("Reveal answers")
            var found = r.containsMatchIn(s.root()?.let { allText() } ?: "")
            if (!found) for (b in s.nodes(allPages = true).filter { it.type == "Button" }) {
                b.args["onTap"]?.let { s.fire(it) }
                if (r.containsMatchIn(allText())) { found = true; break }
            }
            if (found) CheckResult(true, "text ok") else CheckResult(false, "text /${c.pattern}/ not found")
        }
        is Check.Has -> {
            val n = s.nodes().count { it.type in c.types }
            if (n >= c.min) CheckResult(true, "has") else CheckResult(false, "needs ${c.min} of ${c.types}, found $n")
        }
        Check.Interactive -> {
            val ok = s.nodes().any { (it.places.isNotEmpty() && it.type in INPUTS) || it.args["onTap"] != null || it.args["onRow"] != null }
            if (ok) CheckResult(true, "interactive") else CheckResult(false, "nothing to interact with")
        }
        is Check.AnyOf -> {
            val rs = c.any.map { runOne(it) }
            rs.firstOrNull { it.pass } ?: CheckResult(false, rs.joinToString(" | ") { it.detail })
        }
    }

    /**
     * All the text a user can read: each visible node's own text plus labels and option values.
     * [Session.nodes] already skips hidden nodes and walks children, so containers add nothing
     * themselves (their plain text would include hidden children), and raw `rows`/`items`/`data`
     * are not read: only the columns a `Table` shows and the rows a `List` renders count.
     */
    private fun allText(): String {
        val sb = StringBuilder()
        for (n in s.nodes(allPages = true)) {
            if (n.type in OWN_TEXT) sb.append(s.plainText(n)).append('\n')
            for ((k, v) in n.args) if (k in TEXT_ARGS) sb.append(s.ctx.formatter.auto(v)).append('\n')
        }
        return sb.toString()
    }

    private fun candidateTexts(label: String?): List<String> {
        val nodes = s.nodes(allPages = true)
        if (label == null) return listOf(allText())
        val r = rx(label)
        val out = ArrayList<String>()
        for (n in nodes) {
            if (n.type == "Metric" && r.containsMatchIn(s.ctx.formatter.auto(n.args["label"] ?: WStr("")))) {
                out += com.echoflow.weave.core.eval.PlainText.metricValue(n, s.ctx.formatter)
            }
        }
        out += allText().lines().filter { r.containsMatchIn(it) }
        return out
    }

    companion object {
        private val TEXT_ARGS = setOf("label", "title", "subtitle", "overline", "content", "options", "labels", "prefix", "suffix")

        /** Nodes whose plain text is their own, not their children's (04 §2.4). */
        private val OWN_TEXT = setOf(
            "Text", "Markdown", "Quote", "Code", "Title", "Callout", "Metric", "Table", "Item", "Image", "Button",
            "Slider", "Segmented", "TextField", "Select", "Toggle", "Check", "Radio", "Chips", "Stepper",
        )
        private val NUM = Regex("""[-−]?\d[\d,]*(?:\.\d+)?(?:\s*(Cr|L|K|M|B|lakh|crore)(?![A-Za-z]))?""")

        /**
         * Numbers in display text: `₹43,391`, `12,34,568`, `2.5 Cr`, `5.6K`, `1.2M`, `-3`. A number
         * with a unit yields both readings (`560 lakh` is 560 in a lakh-denominated task).
         */
        fun numbersIn(text: String): List<Double> = NUM.findAll(text).flatMap { m ->
            val unit = m.groupValues[1]
            val raw = m.value.replace("−", "-").replace(",", "").removeSuffix(unit).trim()
            val n = raw.toDoubleOrNull() ?: return@flatMap emptySequence()
            val k = when (unit) { "Cr", "crore" -> 1e7; "L", "lakh" -> 1e5; "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
            if (k == 1.0) sequenceOf(n) else sequenceOf(n * k, n)
        }.toList()

        fun fmtPlain(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
    }
}
