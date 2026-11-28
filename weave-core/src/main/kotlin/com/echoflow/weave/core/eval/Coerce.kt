package com.echoflow.weave.core.eval

import com.echoflow.weave.core.catalog.Tokens
import com.echoflow.weave.core.check.TypeSpec
import com.echoflow.weave.core.check.WType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Result of coercing a value into a slot: [ok] false is `E205` (the argument is dropped). */
data class Coerced(val value: WValue, val ok: Boolean)

/** Coercion table, 03 §2.3, plus string parsing rules 03 §2.4–2.5. */
class Coercion(private val f: Formatter) {

    fun toType(v: WValue, t: WType): Coerced = when (t) {
        WType.ANY -> Coerced(v, true)
        WType.NUM -> num(v)
        WType.STR -> str(v)
        WType.BOOL -> Coerced(WBool.of(v.truthy()), v !is WSym)
        WType.LIST -> list(v)
        WType.REC -> if (v is WRec || v == WNull) Coerced(v, true) else Coerced(WNull, false)
        WType.DUR -> dur(v)
        WType.DATE -> date(v)
        WType.TIME -> time(v)
        WType.DATETIME -> datetime(v)
    }

    fun slot(v: WValue, t: TypeSpec): Coerced = when (t) {
        is TypeSpec.Simple -> when (t.name) {
            "any", "field", "state", "name", "action", "fn", "nodes", "node" -> Coerced(v, true)
            "num" -> num(v)
            "str" -> str(v)
            "bool" -> Coerced(WBool.of(v.truthy()), true)
            "dur" -> dur(v)
            "date" -> date(v)
            "time" -> time(v)
            "datetime" -> datetime(v)
            "list" -> list(v)
            "rec" -> if (v is WRec || v == WNull) Coerced(v, true) else Coerced(WNull, false)
            "image" -> Coerced(v, v is WImage || v == WNull)
            else -> Coerced(v, true)
        }
        is TypeSpec.Tok -> when {
            v is WSym || v is WStr || v is WNum || v == WNull -> Coerced(v, true)
            else -> Coerced(WNull, false)
        }
        is TypeSpec.Enum -> {
            val w = when (v) { is WSym -> v.name; is WStr -> v.v; else -> null }
            if (v == WNull) Coerced(WNull, true) else if (w != null && w in t.values) Coerced(WSym(w), true) else Coerced(WNull, false)
        }
        is TypeSpec.Union -> {
            val exact = t.options.firstOrNull { exactly(v, it) }
            if (exact != null) slot(v, exact) else t.options.map { slot(v, it) }.firstOrNull { it.ok } ?: Coerced(WNull, false)
        }
    }

    private fun exactly(v: WValue, t: TypeSpec): Boolean = when (t) {
        is TypeSpec.Simple -> when (t.name) {
            "num" -> v is WNum; "str" -> v is WStr; "bool" -> v is WBool; "dur" -> v is WDur; "list" -> v is WList
            "rec" -> v is WRec; "node", "nodes" -> v is WNode; "action" -> v is WAction; "fn" -> v is WFn
            "date" -> v is WDate; "datetime" -> v is WDateTime; "time" -> v is WTime
            else -> false
        }
        is TypeSpec.Enum -> (v is WSym && v.name in t.values) || (v is WStr && v.v in t.values)
        is TypeSpec.Tok -> v is WSym && Tokens.isMember(t.set, v.name)
        is TypeSpec.Union -> t.options.any { exactly(v, it) }
    }

    fun num(v: WValue): Coerced = when (v) {
        is WNum -> Coerced(v, true)
        WNull -> Coerced(WNull, true)
        is WStr -> parseNumber(v.v)?.let { Coerced(WNum.of(it), true) } ?: Coerced(WNull, false)
        is WBool -> Coerced(WNum(if (v.v) 1.0 else 0.0), true)
        is WDur -> Coerced(WNum.of(v.ms), true)
        else -> Coerced(WNull, false)
    }

    fun str(v: WValue): Coerced = when (v) {
        WNull -> Coerced(WNull, true)
        is WStr -> Coerced(v, true)
        is WImage, is WAction, is WFn -> Coerced(WNull, false)
        else -> Coerced(WStr(f.auto(v)), true)
    }

    fun list(v: WValue): Coerced = when (v) {
        is WList -> Coerced(v, true)
        WNull -> Coerced(WList.EMPTY, true)
        else -> Coerced(WList(listOf(v)), true)
    }

    fun dur(v: WValue): Coerced = when (v) {
        is WDur -> Coerced(v, true)
        WNull -> Coerced(WNull, true)
        is WStr -> parseDuration(v.v)?.let { Coerced(WDur(it), true) } ?: Coerced(WNull, false)
        else -> Coerced(WNull, false)
    }

    fun date(v: WValue): Coerced = when (v) {
        is WDate -> Coerced(v, true)
        is WDateTime -> Coerced(WDate(LocalDateTime.ofInstant(v.v, f.zone).toLocalDate()), true)
        WNull -> Coerced(WNull, true)
        is WStr -> when (val p = parseDate(v.v, f.zone)) { is WDate -> Coerced(p, true); is WDateTime -> date(p); else -> Coerced(WNull, false) }
        else -> Coerced(WNull, false)
    }

    fun datetime(v: WValue): Coerced = when (v) {
        is WDateTime -> Coerced(v, true)
        is WDate -> Coerced(WDateTime(v.v.atStartOfDay(f.zone).toInstant()), true)
        WNull -> Coerced(WNull, true)
        is WStr -> parseDate(v.v, f.zone)?.let { datetime(it) } ?: Coerced(WNull, false)
        else -> Coerced(WNull, false)
    }

    fun time(v: WValue): Coerced = when (v) {
        is WTime -> Coerced(v, true)
        WNull -> Coerced(WNull, true)
        is WStr -> parseTime(v.v)?.let { Coerced(WTime(it), true) } ?: Coerced(WNull, false)
        else -> Coerced(WNull, false)
    }

    companion object {
        // no overlapping digit runs, so a long non-number fails in linear time
        private val NUM = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?%?")

        /** 03 §2.4. */
        fun parseNumber(s0: String): Double? {
            if (s0.length > 400) return null
            val s = s0.trim().replace(",", "").replace("_", "")
            if (!NUM.matches(s)) return null
            val pct = s.endsWith("%")
            val d = s.removeSuffix("%").toDoubleOrNull() ?: return null
            val v = if (pct) d / 100 else d
            return if (v.isNaN() || v.isInfinite()) null else v
        }

        private val DUR_PART = Regex("(\\d+(?:\\.\\d+)?)\\s*(ms|s|m|h|d)(?![A-Za-z0-9_])")

        /** 03 §2.5: `"1h 30m"` → 5,400,000. */
        fun parseDuration(s0: String): Long? {
            val s = s0.trim()
            // a real duration is short; a long string is rejected before any matching
            if (s.isEmpty() || s.length > MAX_DURATION_CHARS) return null
            var total = 0.0
            var pos = 0
            // each part must start exactly where the last one ended (after spaces), so a failed
            // match is never retried at later positions
            val m = DUR_PART.toPattern().matcher(s)
            while (pos < s.length) {
                while (pos < s.length && s[pos].isWhitespace()) pos++
                if (pos == s.length) break
                m.region(pos, s.length)
                if (!m.lookingAt()) return null
                val n = m.group(1).toDouble()
                total += n * when (m.group(2)) { "ms" -> 1.0; "s" -> 1e3; "m" -> 6e4; "h" -> 3.6e6; else -> 8.64e7 }
                pos = m.end()
            }
            if (pos == 0 || total == 0.0 && !s.any { it.isDigit() }) return null
            return Math.floor(total + 0.5).toLong()
        }

        private const val MAX_DURATION_CHARS = 64

        private val TIME24 = Regex("(\\d{1,2}):(\\d{2})")
        private val TIME12 = Regex("(\\d{1,2}):(\\d{2})\\s*([aApP][mM])")

        fun parseTime(s0: String): LocalTime? {
            val s = s0.trim()
            TIME12.matchEntire(s)?.let { m ->
                var h = m.groupValues[1].toInt() % 12
                if (m.groupValues[3].lowercase() == "pm") h += 12
                val min = m.groupValues[2].toInt()
                return if (min < 60) LocalTime.of(h, min) else null
            }
            TIME24.matchEntire(s)?.let { m ->
                val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
                return if (h < 24 && min < 60) LocalTime.of(h, min) else null
            }
            return null
        }

        /** `date(s)`: `YYYY-MM-DD` → date; with a time → datetime (device zone, or the given offset). */
        fun parseDate(s0: String, zone: ZoneId): WValue? {
            val s = s0.trim()
            return runCatching {
                when {
                    s.length == 10 -> WDate(LocalDate.parse(s))
                    s.endsWith("Z") || Regex(".*[+-]\\d{2}:\\d{2}$").matches(s) -> WDateTime(OffsetDateTime.parse(s.replace(" ", "T")).toInstant())
                    else -> WDateTime(LocalDateTime.parse(s.replace(" ", "T")).atZone(zone).toInstant())
                }
            }.getOrNull()
        }

        fun instantOf(v: WValue, zone: ZoneId): Instant? = when (v) {
            is WDateTime -> v.v
            is WDate -> v.v.atStartOfDay(zone).toInstant()
            else -> null
        }
    }
}
