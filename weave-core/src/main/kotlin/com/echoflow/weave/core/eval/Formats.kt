package com.echoflow.weave.core.eval

import com.echoflow.weave.core.parse.FormatSpec
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/**
 * Formats, 03 §11. 03 names `android.icu`; `weave-core` is plain JVM, so grouping (including
 * Indian lakh/crore grouping), currency and date patterns are implemented here to match the outputs
 * 07 §3.4 fixes. Rounding is half-up everywhere.
 */
class Formatter(
    val locale: Locale = Locale.forLanguageTag("en-IN"),
    val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    val now: () -> Instant = { Instant.now() },
    val currency: String = currencyOf(locale),
) {
    private val indian get() = locale.country == "IN"
    private val symbols: DecimalFormatSymbols = DecimalFormatSymbols.getInstance(locale)
    private val english get() = locale.language == "en"
    private val groupSep: Char get() = if (english) ',' else symbols.groupingSeparator
    private val decSep: Char get() = if (english) '.' else symbols.decimalSeparator

    // ---- auto -----------------------------------------------------------------------------------

    /**
     * 03 §11.1. Output stops at [Limits.STRING_CHARS] (ending in "…"), so a value that shares
     * nested lists (`a30 = [a29, a29]`) is never walked in full: every visited item adds at least
     * a separator, so the walk ends within the cap.
     */
    fun auto(v: WValue): String {
        val sb = StringBuilder()
        val cap = com.echoflow.weave.core.Limits.STRING_CHARS
        if (!autoTo(sb, v, cap)) {
            // never split a surrogate pair at the cut
            val cut = if (Character.isHighSurrogate(sb[cap - 1])) cap - 1 else cap
            sb.setLength(cut); sb.append('…')
        }
        return sb.toString()
    }

    /** Appends [text] if it fits under [cap]; otherwise only enough to pass it, and false. */
    private fun appendCapped(sb: StringBuilder, text: String, cap: Int): Boolean {
        val room = cap - sb.length
        if (text.length > room) { sb.append(text, 0, maxOf(0, room) + 1); return false }
        sb.append(text)
        return true
    }

    private fun autoTo(sb: StringBuilder, v: WValue, cap: Int): Boolean = when (v) {
        is WList -> {
            var ok = true
            for ((i, item) in v.items.withIndex()) {
                if (i > 0) sb.append(", ")
                if (sb.length > cap || !autoTo(sb, item, cap)) { ok = false; break }
            }
            ok && sb.length <= cap
        }
        is WRec -> jsonTo(sb, v, cap)
        is WStr -> appendCapped(sb, v.v, cap)
        else -> appendCapped(sb, autoLeaf(v), cap)
    }

    private fun autoLeaf(v: WValue): String = when (v) {
        WNull -> "—"
        is WBool -> v.v.toString()
        is WNum -> autoNum(v.v)
        is WStr -> v.v
        is WSym -> v.name
        is WDur -> duration(v.ms)
        is WDate -> date(v.v, "medium")
        is WTime -> time(v.v.atDate(LocalDate.of(2000, 1, 1)))
        is WDateTime -> datetime(v.v)
        is WList, is WRec -> auto(v)
        is WNode -> PlainText.of(v, this)
        is WAction -> "!" + v.kind
        is WFn -> "fn"
        is WImage -> ""
    }

    fun autoNum(x: Double): String =
        if (x == Math.floor(x) && Math.abs(x) < 1e15) int(x) else number(x, 2, trim = true)

    // ---- numbers --------------------------------------------------------------------------------

    fun int(x: Double, indianGrouping: Boolean = indian): String = number(x, 0, trim = false, indianGrouping = indianGrouping)

    /** [decimals] fixed (or up to, with [trim]), grouped, half-up. */
    fun number(x: Double, decimals: Int, trim: Boolean, indianGrouping: Boolean = indian): String {
        // a format like num(1000000000) must not drive a huge allocation
        var bd = BigDecimal(x.toString()).setScale(decimals.coerceIn(0, MAX_DECIMALS), RoundingMode.HALF_UP)
        if (trim) bd = bd.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }
        val neg = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = if (plain.contains('.')) plain.substringAfter('.') else ""
        val grouped = group(intPart, indianGrouping)
        return (if (neg) "-" else "") + grouped + (if (frac.isNotEmpty()) decSep + frac else "")
    }

    private fun group(digits: String, indianGrouping: Boolean): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val size = if (indianGrouping) 2 else 3
        val chunks = ArrayList<String>()
        while (rest.length > size) { chunks.add(0, rest.takeLast(size)); rest = rest.dropLast(size) }
        if (rest.isNotEmpty()) chunks.add(0, rest)
        for (c in chunks) sb.append(c).append(groupSep)
        return sb.append(last3).toString()
    }

    fun compact(x: Double, indianRules: Boolean = indian): String {
        val a = Math.abs(x)
        val sign = if (x < 0) "-" else ""
        fun one(v: Double) = number(v, 1, trim = true, indianGrouping = indianRules)
        return when {
            indianRules && a >= 1e7 -> sign + one(a / 1e7) + " Cr"
            indianRules && a >= 1e5 -> sign + one(a / 1e5) + " L"
            !indianRules && a >= 1e12 -> sign + one(a / 1e12) + "T"
            !indianRules && a >= 1e9 -> sign + one(a / 1e9) + "B"
            !indianRules && a >= 1e6 -> sign + one(a / 1e6) + "M"
            a >= 1e3 -> sign + one(a / 1e3) + "K"
            else -> int(x, indianRules)
        }
    }

    fun currencySymbol(code: String): String = when (code) {
        "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY" -> "¥"
        else -> runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault("$code ")
    }

    fun currency(x: Double, code: String, indianGrouping: Boolean = indian): String {
        val defaultDigits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)
        val digits = if (Math.abs(x) >= 1000 || x == Math.floor(x)) 0 else defaultDigits
        val n = number(Math.abs(x), digits, trim = false, indianGrouping = indianGrouping)
        return (if (x < 0) "-" else "") + currencySymbol(code) + n
    }

    fun ordinal(x: Double): String {
        val n = Math.round(x)
        if (!english) return int(n.toDouble())
        val m100 = Math.abs(n) % 100
        val suffix = if (m100 in 11..13) "th" else when (Math.abs(n) % 10) { 1L -> "st"; 2L -> "nd"; 3L -> "rd"; else -> "th" }
        return int(n.toDouble()) + suffix
    }

    fun bytes(x: Double): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var v = x
        var i = 0
        while (Math.abs(v) >= 1000 && i < units.size - 1) { v /= 1000; i++ }
        return number(v, 1, trim = true) + " " + units[i]
    }

    // ---- durations ------------------------------------------------------------------------------

    fun duration(ms: Long): String {
        val a = Math.abs(ms)
        val sign = if (ms < 0) "-" else ""
        if (a < 1000) return "$sign${a}ms"
        val parts = listOf(86_400_000L to "d", 3_600_000L to "h", 60_000L to "m", 1_000L to "s")
        var rest = a
        val out = ArrayList<String>()
        for ((unit, name) in parts) {
            val q = rest / unit
            rest %= unit
            if (q > 0) out += "$q$name"
        }
        return sign + out.take(2).joinToString(" ")
    }

    fun clock(ms: Long): String {
        val s = Math.abs(ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    // ---- dates ----------------------------------------------------------------------------------

    private val dateLocale: Locale get() = if (english) Locale.ENGLISH else locale

    private fun datePattern(style: String): String? = when {
        !english -> null
        locale.country == "US" -> when (style) { "short" -> "M/d/yy"; "long" -> "MMMM d, yyyy"; "full" -> "EEEE, MMMM d, yyyy"; else -> "MMM d, yyyy" }
        else -> when (style) { "short" -> "dd/MM/yy"; "long" -> "d MMMM yyyy"; "full" -> "EEEE, d MMMM yyyy"; else -> "d MMM yyyy" }
    }

    fun date(d: LocalDate, style: String = "medium"): String {
        val p = datePattern(style)
        if (p != null) return d.format(DateTimeFormatter.ofPattern(p, dateLocale))
        val fs = when (style) { "short" -> FormatStyle.SHORT; "long" -> FormatStyle.LONG; "full" -> FormatStyle.FULL; else -> FormatStyle.MEDIUM }
        return d.format(DateTimeFormatter.ofLocalizedDate(fs).withLocale(locale))
    }

    fun time(t: LocalDateTime): String {
        if (!english) return t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        val s = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        return if (locale.country == "US") s.uppercase() else s.lowercase()
    }

    fun datetime(i: Instant): String {
        val ldt = LocalDateTime.ofInstant(i, zone)
        return date(ldt.toLocalDate()) + ", " + time(ldt)
    }

    fun relative(i: Instant): String {
        val secs = Duration.between(now(), i).seconds
        val a = Math.abs(secs)
        val (n, unit) = when {
            a < 60 -> return "now"
            a < 3600 -> a / 60 to "min"
            a < 86_400 -> a / 3600 to "hr"
            a < 7 * 86_400 -> a / 86_400 to (if (a / 86_400 == 1L) "day" else "days")
            a < 35 * 86_400 -> a / (7 * 86_400) to "wk"
            a < 365 * 86_400 -> a / (30 * 86_400) to "mo"
            else -> a / (365 * 86_400) to "yr"
        }
        return if (secs > 0) "in $n $unit" else "$n $unit ago"
    }

    // ---- JSON (rec → str) -----------------------------------------------------------------------

    /**
     * JSON text of [v]. It stops once it passes [cap] characters (the result is then cut off and
     * only good for a size check), so a shared nested value is never serialised in full.
     */
    fun json(v: WValue, cap: Int = JSON_CAP): String {
        val sb = StringBuilder()
        jsonTo(sb, v, cap)
        return sb.toString()
    }

    private fun jsonTo(sb: StringBuilder, v: WValue, cap: Int): Boolean {
        when (v) {
            WNull -> sb.append("null")
            is WBool -> sb.append(v.v)
            is WNum -> sb.append(com.echoflow.weave.core.parse.CanonicalPrinter.number(v.v))
            // escaping only lengthens text: a string longer than the room left is already over
            is WStr -> { if (v.v.length > cap - sb.length) { sb.append(v.v, 0, maxOf(0, cap - sb.length) + 1); return false }; sb.append(jsonString(v.v)) }
            is WSym -> sb.append(jsonString(v.name))
            is WList -> {
                sb.append('[')
                for ((i, item) in v.items.withIndex()) {
                    if (i > 0) sb.append(',')
                    if (sb.length > cap || !jsonTo(sb, item, cap)) return false
                }
                sb.append(']')
            }
            is WRec -> {
                sb.append('{')
                for ((i, e) in v.fields.entries.withIndex()) {
                    if (i > 0) sb.append(',')
                    sb.append(jsonString(e.key)).append(':')
                    if (sb.length > cap || !jsonTo(sb, e.value, cap)) return false
                }
                sb.append('}')
            }
            else -> sb.append(jsonString(auto(v)))
        }
        return sb.length <= cap
    }

    // ---- applying a format ----------------------------------------------------------------------

    /** Applies one format (03 §11). Wrong type → `auto`, no diagnostic. */
    fun apply(v: WValue, f: FormatSpec): String = apply(v, f.name, f.args)

    fun apply(v: WValue, name: String, args: List<WValue>): String {
        val n = (v as? WNum)?.v
        val argNum = (args.firstOrNull() as? WNum)?.v?.toInt()
        val argWord = when (val a = args.firstOrNull()) { is WSym -> a.name; is WStr -> a.v; else -> null }
        return when (name) {
            "auto" -> auto(v)
            "int" -> n?.let { int(it) } ?: auto(v)
            "num" -> n?.let { number(it, argNum ?: 2, trim = false) } ?: auto(v)
            "dec" -> n?.let { number(it, argNum ?: 2, trim = true) } ?: auto(v)
            "compact" -> n?.let { compact(it) } ?: auto(v)
            "percent" -> n?.let { (if (argNum != null) number(it * 100, argNum, false) else number(it * 100, 1, true)) + "%" } ?: auto(v)
            "pct" -> n?.let { (if (argNum != null) number(it, argNum, false) else number(it, 1, true)) + "%" } ?: auto(v)
            "currency" -> n?.let { currency(it, (argWord ?: currency).uppercase()) } ?: auto(v)
            "inr" -> n?.let { currency(it, "INR", indianGrouping = true) } ?: auto(v)
            "usd", "eur", "gbp", "jpy" -> n?.let { currency(it, name.uppercase()) } ?: auto(v)
            "money" -> n?.let { currency(it, currency) } ?: auto(v)
            "inrCompact" -> n?.let { (if (it < 0) "-" else "") + "₹" + compact(Math.abs(it), indianRules = true) } ?: auto(v)
            "moneyCompact" -> n?.let { (if (it < 0) "-" else "") + currencySymbol(currency) + compact(Math.abs(it)) } ?: auto(v)
            "signed" -> n?.let { if (it > 0) "+" + autoNum(it) else if (it < 0) "−" + autoNum(-it) else autoNum(it) } ?: auto(v)
            "ordinal" -> n?.let { ordinal(it) } ?: auto(v)
            "bytes" -> n?.let { bytes(it) } ?: auto(v)
            "upper" -> auto(v).uppercase(locale)
            "lower" -> auto(v).lowercase(locale)
            "title" -> titleCase(auto(v))
            "trim" -> auto(v).trim()
            "truncate" -> {
                val s = auto(v)
                val max = argNum ?: return s
                if (s.codePointCount(0, s.length) <= max) s else s.substring(0, s.offsetByCodePoints(0, max)) + "…"
            }
            "plural" -> n?.let { if (it == 1.0) wordArg(args, 0) else wordArg(args, 1) } ?: auto(v)
            "date" -> when (v) {
                is WDate -> date(v.v, argWord ?: "medium")
                is WDateTime -> date(LocalDateTime.ofInstant(v.v, zone).toLocalDate(), argWord ?: "medium")
                else -> auto(v)
            }
            "weekday" -> localDate(v)?.format(DateTimeFormatter.ofPattern(if (argWord == "long") "EEEE" else "EEE", dateLocale)) ?: auto(v)
            "monthYear" -> localDate(v)?.format(DateTimeFormatter.ofPattern("MMM yyyy", dateLocale)) ?: auto(v)
            "time" -> when (v) {
                is WTime -> time(v.v.atDate(LocalDate.of(2000, 1, 1)))
                is WDateTime -> time(LocalDateTime.ofInstant(v.v, zone))
                else -> auto(v)
            }
            "datetime" -> (v as? WDateTime)?.let { datetime(it.v) } ?: auto(v)
            "relative" -> when (v) {
                is WDateTime -> relative(v.v)
                is WDate -> relative(v.v.atStartOfDay(zone).toInstant())
                else -> auto(v)
            }
            "duration" -> when (v) { is WDur -> duration(v.ms); is WNum -> duration(Math.round(v.v)); else -> auto(v) }
            "clock" -> when (v) { is WDur -> clock(v.ms); is WNum -> clock(Math.round(v.v)); else -> auto(v) }
            else -> auto(v)
        }
    }

    /** Parses a format given as a string (`fmt(x, "num(2)")`, `format: "inr"`). */
    fun applyText(v: WValue, spec: String?): String {
        if (spec.isNullOrBlank()) return auto(v)
        var out: WValue = v
        var text = auto(v)
        for (piece in spec.split('|')) {
            val s = piece.trim()
            val name = s.substringBefore('(').trim()
            val args = if (s.contains('(')) s.substringAfter('(').substringBeforeLast(')').split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { a ->
                a.toDoubleOrNull()?.let { WNum(it) } ?: WSym(a.trim('"'))
            } else emptyList()
            text = apply(out, name, args)
            out = WStr(text)
        }
        return text
    }

    private fun wordArg(args: List<WValue>, i: Int): String = when (val a = args.getOrNull(i)) {
        is WSym -> a.name; is WStr -> a.v; null -> ""; else -> auto(a)
    }

    private fun localDate(v: WValue): LocalDate? = when (v) {
        is WDate -> v.v
        is WDateTime -> LocalDateTime.ofInstant(v.v, zone).toLocalDate()
        else -> null
    }

    fun titleCase(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(locale) } }

    companion object {
        /** Most decimals any format shows. */
        const val MAX_DECIMALS = 20
        /** Past the state limit, so a cut-off result still fails the size check (05 §13.3). */
        const val JSON_CAP = com.echoflow.weave.core.Limits.STATE_BYTES + 1

        fun currencyOf(l: Locale): String = runCatching { Currency.getInstance(l).currencyCode }.getOrNull() ?: "USD"

        fun jsonString(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) when (c) {
                '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\t' -> sb.append("\\t"); '\r' -> sb.append("\\r")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
            return sb.append('"').toString()
        }
    }
}

/** Plain text of a node, 04 §2.4. */
object PlainText {
    private val TEXTY = setOf("Text", "Markdown", "Quote", "Code")
    private val INPUTS = setOf("Slider", "Segmented", "TextField", "Select", "Toggle", "Check", "Radio", "Chips", "Stepper")

    /**
     * Plain text of [n]. A tree can share subtrees (`n30 = Column [n29, n29]`), so the walk is
     * bounded: it visits at most [MAX_NODES] nodes and stops once the text passes the string limit.
     */
    fun of(n: WNode, f: Formatter): String {
        val budget = Budget()
        val text = walk(n, f, budget)
        val cap = com.echoflow.weave.core.Limits.STRING_CHARS
        return if (text.length > cap) text.take(cap) + "…" else text
    }

    private const val MAX_NODES = 10_000

    private class Budget(var nodes: Int = MAX_NODES, var chars: Int = com.echoflow.weave.core.Limits.STRING_CHARS)

    private fun walk(n: WNode, f: Formatter, b: Budget): String {
        if (b.nodes <= 0 || b.chars <= 0) return ""
        b.nodes--
        val text = self(n, f, b)
        // a container's text is made of its children's: only leaves spend the character budget
        if (n.children.isEmpty()) b.chars -= text.length
        return text
    }

    private fun kids(n: WNode, f: Formatter, b: Budget, sep: String): String {
        val parts = ArrayList<String>()
        for (c in n.children) {
            if (b.nodes <= 0 || b.chars <= 0) break
            val t = walk(c, f, b)
            if (t.isNotEmpty()) parts += t
        }
        return parts.joinToString(sep)
    }

    private fun self(n: WNode, f: Formatter, b: Budget): String = when {
        n.type in TEXTY -> f.auto(n.args["content"] ?: n.args["source"] ?: WNull).let { if (n.args["content"] == null && n.args["source"] == null) "" else it }
        n.type == "Title" -> listOfNotNull(n.args["content"]?.let { f.auto(it) }, (n.args["subtitle"] as? WStr)?.v).joinToString("\n")
        n.type == "Callout" -> listOfNotNull((n.args["title"] as? WStr)?.v, n.args["content"]?.let { f.auto(it) }).joinToString("\n")
        n.type == "Metric" -> metricText(n, f)
        n.type in INPUTS -> (n.args["label"]?.let { f.auto(it) + ": " } ?: "") + f.auto(n.args["value"] ?: WNull)
        n.type == "Table" -> tableText(n, f)
        n.type == "List" -> kids(n, f, b, "\n")
        n.type == "Item" -> listOfNotNull(n.args["title"]?.let { f.auto(it) }, (n.args["subtitle"] as? WStr)?.v).joinToString(" ")
        n.type == "Row" -> kids(n, f, b, "  ").trim()
        n.type == "Image" -> (n.args["alt"] as? WStr)?.v ?: ""
        n.type == "Button" -> f.auto(n.args["label"] ?: WNull)
        else -> kids(n, f, b, "\n")
    }

    fun metricValue(n: WNode, f: Formatter): String {
        val v = n.args["value"] ?: WNull
        val fmt = (n.args["format"] as? WStr)?.v
        val prefix = (n.args["prefix"] as? WStr)?.v ?: ""
        val suffix = (n.args["suffix"] as? WStr)?.v ?: ""
        return prefix + f.applyText(v, fmt) + suffix
    }

    private fun metricText(n: WNode, f: Formatter) = f.auto(n.args["label"] ?: WNull) + ": " + metricValue(n, f)

    private fun tableText(n: WNode, f: Formatter): String {
        val rows = (n.args["rows"] as? WList)?.items?.filterIsInstance<WRec>() ?: return ""
        val cols = (n.args["cols"] as? WList)?.items?.filterIsInstance<WRec>()
        val headers = cols?.map { f.auto(it["header"]) } ?: rows.firstOrNull()?.fields?.keys?.toList() ?: emptyList()
        val lines = ArrayList<String>()
        lines += headers.joinToString("\t")
        for (r in rows) {
            lines += if (cols == null) r.fields.values.joinToString("\t") { f.auto(it) }
            else cols.joinToString("\t") { c ->
                val field = c["value"]
                val value = if (field is WStr) r[field.v] else if (field is WSym) r[field.name] else WNull
                f.applyText(value, (c["format"] as? WStr)?.v ?: (c["format"] as? WSym)?.name)
            }
        }
        return lines.joinToString("\n")
    }
}
