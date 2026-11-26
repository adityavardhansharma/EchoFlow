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
            a is WStr && b is WNum -> if (b.v in 0.0..1000.0) WStr(a.v.repeat(b.v.toInt())) else WNull
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
        ".." -> if (a is WNum && b is WNum) {
            val lo = Math.floor(a.v + 0.5).toLong()
            val hi = Math.floor(b.v + 0.5).toLong()
            if (Math.abs(hi - lo) + 1 > Limits.RANGE_ITEMS) limit("range")
            WList(if (lo <= hi) (lo..hi).map { WNum(it.toDouble()) } else (lo downTo hi).map { WNum(it.toDouble()) })
        } else WNull
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
        a is WStr || b is WStr -> WStr(f.auto(a) + f.auto(b))
        a is WList && b is WList -> WList(a.items + b.items)
        a is WRec && b is WRec -> WRec(LinkedHashMap(a.fields).apply { putAll(b.fields) })
        a is WDate && b is WDur -> WDate(a.v.plusDays(b.ms / 86_400_000L))
        a is WDateTime && b is WDur -> WDateTime(a.v.plusMillis(b.ms))
        a is WDur && b is WDur -> WDur(a.ms + b.ms)
        else -> WNull
    }

    private fun minus(a: WValue, b: WValue, f: Formatter): WValue = when {
        a is WNum && b is WNum -> WNum.of(a.v - b.v)
        a is WDate && b is WDur -> WDate(a.v.minusDays(b.ms / 86_400_000L))
        a is WDateTime && b is WDur -> WDateTime(a.v.minusMillis(b.ms))
        a is WDate && b is WDate -> WDur(ChronoUnit.DAYS.between(b.v, a.v) * 86_400_000L)
        a is WDateTime && b is WDateTime -> WDur(a.v.toEpochMilli() - b.v.toEpochMilli())
        a is WDur && b is WDur -> WDur(a.ms - b.ms)
        else -> WNull
    }

    @Suppress("unused")
    private fun localDate(i: java.time.Instant, f: Formatter) = LocalDateTime.ofInstant(i, f.zone).toLocalDate()
}
