package com.echoflow.weave.core.eval

import com.echoflow.weave.core.Limits
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Binary operators, 03 §6. Anything not listed is `null`. */
object Ops {
    fun binary(op: String, a: WValue, b: WValue, f: Formatter, limit: (String) -> Nothing): WValue = when (op) {
        "+" -> plus(a, b, f)
        "-" -> minus(a, b, f)
        "*" -> when {
            a is WNum && b is WNum -> WNum.of(a.v * b.v)
            a is WDur && b is WNum -> WDur(Math.floor(a.ms * b.v + 0.5).toLong())
            a is WNum && b is WDur -> WDur(Math.floor(b.ms * a.v + 0.5).toLong())
            a is WStr && b is WNum -> if (b.v in 0.0..1000.0 && a.v.length.toLong() * b.v.toLong() <= com.echoflow.weave.core.Limits.STRING_CHARS) WStr(a.v.repeat(b.v.toInt())) else WNull
            else -> WNull
        }
        "/" -> when {
            a is WNum && b is WNum -> if (b.v == 0.0) WNull else WNum.of(a.v / b.v)
            a is WDur && b is WNum -> if (b.v == 0.0) WNull else WDur(Math.floor(a.ms / b.v + 0.5).toLong())
            a is WDur && b is WDur -> if (b.ms == 0L) WNull else WNum.of(a.ms.toDouble() / b.ms)
            else -> WNull
        }
        "%" -> if (a is WNum && b is WNum && b.v != 0.0) WNum.of(a.v - b.v * Math.floor(a.v / b.v)) else WNull
        "==" -> WBool.of(weaveEquals(a, b))
        "!=" -> WBool.of(!weaveEquals(a, b))
        "<" -> WBool.of((weaveCompare(a, b) ?: return WBool.FALSE) < 0)
        "<=" -> WBool.of((weaveCompare(a, b) ?: return WBool.FALSE) <= 0)
        ">" -> WBool.of((weaveCompare(a, b) ?: return WBool.FALSE) > 0)
        ">=" -> WBool.of((weaveCompare(a, b) ?: return WBool.FALSE) >= 0)
        "in" -> WBool.of(contains(b, a, f))
        ".." -> if (a is WNum && b is WNum) range(a.v, b.v, limit) else WNull
        else -> WNull
    }

    fun contains(container: WValue, x: WValue, f: Formatter): Boolean = when (container) {
        is WList -> container.items.any { weaveEquals(it, x) }
        is WRec -> f.auto(x) in container.fields
        is WStr -> f.auto(x).let { container.v.contains(it) }
        else -> false
    }

    private fun plus(a: WValue, b: WValue, f: Formatter): WValue = when {
        a is WNum && b is WNum -> WNum.of(a.v + b.v)
        a is WStr || b is WStr -> concat(a, b, f)
        a is WList && b is WList -> if (a.items.size + b.items.size > com.echoflow.weave.core.Limits.LIST_ITEMS) WNull else WList(a.items + b.items)
        a is WRec && b is WRec -> WRec(LinkedHashMap(a.fields).apply { putAll(b.fields) })
        a is WDate && b is WDur -> safe { WDate(a.v.plusDays(b.ms / 86_400_000L)) }
        a is WDateTime && b is WDur -> safe { WDateTime(a.v.plusMillis(b.ms)) }
        a is WDur && b is WDur -> safe { WDur(Math.addExact(a.ms, b.ms)) }
        else -> WNull
    }

    private fun minus(a: WValue, b: WValue, f: Formatter): WValue = when {
        a is WNum && b is WNum -> WNum.of(a.v - b.v)
        a is WDate && b is WDur -> safe { WDate(a.v.minusDays(b.ms / 86_400_000L)) }
        a is WDateTime && b is WDur -> safe { WDateTime(a.v.minusMillis(b.ms)) }
        a is WDate && b is WDate -> safe { WDur(Math.multiplyExact(ChronoUnit.DAYS.between(b.v, a.v), 86_400_000L)) }
        a is WDateTime && b is WDateTime -> safe { WDur(Math.subtractExact(a.v.toEpochMilli(), b.v.toEpochMilli())) }
        a is WDur && b is WDur -> safe { WDur(Math.subtractExact(a.ms, b.ms)) }
        else -> WNull
    }

    /** Arithmetic that overflows a long or leaves the supported date range is `null` (03 §6). */
    private inline fun safe(block: () -> WValue): WValue = try {
        block()
    } catch (_: ArithmeticException) {
        WNull
    } catch (_: java.time.DateTimeException) {
        WNull
    }

    @Suppress("unused")
    private fun localDate(i: java.time.Instant, f: Formatter) = LocalDateTime.ofInstant(i, f.zone).toLocalDate()

    /** Count of integers from [a] to [b] without building them; too many is `E304`. */
    private fun range(a: Double, b: Double, limit: (String) -> Nothing): WValue {
        val lo = roundHalfUp(a) ?: return WNull
        val hi = roundHalfUp(b) ?: return WNull
        val span = if (hi >= lo) hi - lo else lo - hi
        if (span < 0 || span >= Limits.RANGE_ITEMS) limit("range")
        val from = lo
        val items = ArrayList<WValue>(span.toInt() + 1)
        var k = from
        val step = if (hi >= lo) 1L else -1L
        while (true) {
            items += WNum(k.toDouble())
            if (k == hi) break
            k += step
        }
        return WList(items)
    }

    /** Half-up rounding that stays null instead of saturating at the long extremes. */
    private fun roundHalfUp(d: Double): Long? {
        if (!d.isFinite()) return null
        val shifted = d + 0.5
        if (shifted >= Long.MAX_VALUE.toDouble() || shifted <= Long.MIN_VALUE.toDouble()) return null
        return Math.floor(shifted).toLong()
    }

    /**
     * String concatenation that stops once the result would pass [Limits.STRING_CHARS], so a list
     * of one huge string is never formatted in full before the cap is noticed.
     */
    private fun concat(a: WValue, b: WValue, f: Formatter): WValue {
        val cap = Limits.STRING_CHARS
        val sb = StringBuilder()
        fun add(text: String): Boolean {
            val room = cap - sb.length
            if (text.length > room) return false
            sb.append(text)
            return true
        }
        fun append(v: WValue): Boolean = when (v) {
            is WList -> {
                for ((i, item) in v.items.withIndex()) {
                    if (i > 0 && !add(", ")) return false
                    if (!append(item)) return false
                }
                true
            }
            else -> add(f.auto(v))
        }
        return if (append(a) && append(b)) WStr(sb.toString()) else WNull
    }
}
