package com.echoflow.weave.core.eval

import com.echoflow.weave.core.Limits
import com.echoflow.weave.core.parse.Span
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Built-in functions, 03 §10 (the lazy control functions live in [Evaluator]). Wrong argument
 * types return `null`; nothing throws except the budget/limit signals.
 */
object Builtins {

    fun len(v: WValue): WValue = when (v) {
        is WStr -> WNum.of(v.v.codePointCount(0, v.v.length))
        is WList -> WNum.of(v.items.size)
        is WRec -> WNum.of(v.fields.size)
        else -> WNum(0.0)
    }

    private fun n(v: WValue?): Double? = (v as? WNum)?.v
    private fun num(d: Double?): WValue = d?.let { WNum.of(it) } ?: WNull
    private fun list(v: WValue?): List<WValue>? = (v as? WList)?.items
    private fun nums(l: List<WValue>): List<Double> = l.mapNotNull { (it as? WNum)?.v }

    fun call(name: String, a: List<WValue?>, ev: Evaluator, span: Span): WValue {
        val f = ev.f
        fun arg(i: Int): WValue = a.getOrNull(i) ?: WNull
        fun s(i: Int): String? = when (val v = a.getOrNull(i)) { is WStr -> v.v; is WSym -> v.name; null, WNull -> null; else -> f.auto(v) }
        fun fieldVals(l: List<WValue>, field: WValue?): List<WValue> =
            if (field == null || field == WNull) l else l.mapIndexed { i, it -> ev.callItem(field, it, i) }
        fun chargeItems(k: Int) = ev.charge(k)

        return when (name) {
            // ---- math
            "abs" -> num(n(arg(0))?.let { Math.abs(it) })
            "sign" -> num(n(arg(0))?.let { Math.signum(it) })
            "sqrt" -> num(n(arg(0))?.let { if (it < 0) null else Math.sqrt(it) })
            "exp" -> num(n(arg(0))?.let { Math.exp(it) })
            "log" -> {
                val x = n(arg(0)) ?: return WNull
                if (x <= 0) return WNull
                val b = n(a.getOrNull(1))
                if (b != null) { if (b <= 0 || b == 1.0) WNull else num(Math.log(x) / Math.log(b)) } else num(Math.log(x))
            }
            "log10" -> num(n(arg(0))?.let { if (it <= 0) null else Math.log10(it) })
            "pow" -> { val x = n(arg(0)); val y = n(arg(1)); if (x == null || y == null) WNull else num(Math.pow(x, y)) }
            "floor" -> num(n(arg(0))?.let { Math.floor(it) })
            "ceil" -> num(n(arg(0))?.let { Math.ceil(it) })
            "round" -> { val x = n(arg(0)) ?: return WNull; num(round(x, n(a.getOrNull(1))?.toInt() ?: 0)) }
            "trunc" -> num(n(arg(0))?.let { if (it < 0) Math.ceil(it) else Math.floor(it) })
            "min" -> nums(spread(a)).minOrNull()?.let { WNum.of(it) } ?: WNull
            "max" -> nums(spread(a)).maxOrNull()?.let { WNum.of(it) } ?: WNull
            "clamp" -> {
                val x = n(arg(0)); var lo = n(arg(1)); var hi = n(arg(2))
                if (x == null || lo == null || hi == null) return WNull
                if (lo > hi) { val t = lo; lo = hi; hi = t }
                num(x.coerceIn(lo, hi))
            }
            "lerp" -> { val x = n(arg(0)); val y = n(arg(1)); val t = n(arg(2)); if (x == null || y == null || t == null) WNull else num(x + (y - x) * t) }
            "remap" -> {
                val v = listOf(0, 1, 2, 3, 4).map { n(arg(it)) }
                if (v.any { it == null }) return WNull
                val (x, il, ih, ol, oh) = v.map { it!! }
                if (il == ih) num(ol) else num(ol + (x - il) * (oh - ol) / (ih - il))
            }
            "sin" -> num(n(arg(0))?.let { Math.sin(it) }); "cos" -> num(n(arg(0))?.let { Math.cos(it) })
            "tan" -> num(n(arg(0))?.let { Math.tan(it) }); "asin" -> num(n(arg(0))?.let { Math.asin(it) })
            "acos" -> num(n(arg(0))?.let { Math.acos(it) }); "atan" -> num(n(arg(0))?.let { Math.atan(it) })
            "atan2" -> { val y = n(arg(0)); val x = n(arg(1)); if (x == null || y == null) WNull else num(Math.atan2(y, x)) }
            "deg" -> num(n(arg(0))?.let { Math.toDegrees(it) }); "rad" -> num(n(arg(0))?.let { Math.toRadians(it) })
            "hypot" -> { val x = n(arg(0)); val y = n(arg(1)); if (x == null || y == null) WNull else num(Math.hypot(x, y)) }
            "isNumber" -> WBool.of(arg(0) is WNum)
            "rand" -> WNum.of(WeaveRandom.rand(arg(0), Math.floor((n(a.getOrNull(1)) ?: 0.0) + 0.5).toLong(), f))
            "randInt" -> {
                val lo = n(arg(2)) ?: return WNull; val hi = n(arg(3)) ?: return WNull
                if (lo != Math.rint(lo) || hi != Math.rint(hi)) return WNull
                WNum.of(WeaveRandom.randInt(arg(0), Math.floor((n(arg(1)) ?: 0.0) + 0.5).toLong(), lo, hi, f))
            }

            // ---- lists
            "len" -> len(arg(0))
            "range" -> {
                val x = n(arg(0)) ?: return WNull
                val y = n(a.getOrNull(1))
                val step = n(a.getOrNull(2)) ?: 1.0
                val (lo, hi) = if (y == null) 0.0 to x else x to y
                if (step == 0.0) return WList.EMPTY
                val count = Math.ceil((hi - lo) / step)
                if (count > Limits.RANGE_ITEMS) throw LimitExceeded("range")
                if (count <= 0) return WList.EMPTY
                // Charge before building, so an over-budget range stops the caller instead of
                // returning null and letting the caller keep going.
                val n0 = count.toInt()
                var left = n0
                while (left > 0) { val take = minOf(left, 1000); chargeItems(take); left -= take }
                WList((0 until count.toInt()).map { WNum.of(lo + it * step) })
            }
            "map", "each" -> { val l = list(arg(0)) ?: return WNull; WList(l.mapIndexed { i, it -> ev.callItem(arg(1), it, i) }) }
            "filter" -> { val l = list(arg(0)) ?: return WNull; WList(l.filterIndexed { i, it -> ev.callItem(arg(1), it, i).truthy() }) }
            "reject" -> { val l = list(arg(0)) ?: return WNull; WList(l.filterIndexed { i, it -> !ev.callItem(arg(1), it, i).truthy() }) }
            "find" -> { val l = list(arg(0)) ?: return WNull; l.withIndex().firstOrNull { (i, it) -> ev.callItem(arg(1), it, i).truthy() }?.value ?: WNull }
            "findIndex" -> { val l = list(arg(0)) ?: return WNull; WNum.of(l.withIndex().indexOfFirst { (i, it) -> ev.callItem(arg(1), it, i).truthy() }) }
            "indexOf" -> { val l = list(arg(0)) ?: return WNull; WNum.of(l.indexOfFirst { weaveEquals(it, arg(1)) }) }
            "any" -> { val l = list(arg(0)) ?: return WNull; WBool.of(l.withIndex().any { (i, it) -> ev.callItem(arg(1), it, i).truthy() }) }
            "all" -> { val l = list(arg(0)) ?: return WNull; WBool.of(l.withIndex().all { (i, it) -> ev.callItem(arg(1), it, i).truthy() }) }
            "count" -> { val l = list(arg(0)) ?: return WNull; WNum.of(l.withIndex().count { (i, it) -> ev.callItem(arg(1), it, i).truthy() }) }
            "reduce" -> {
                val l = list(arg(0)) ?: return WNull
                val fn = arg(2) as? WFn ?: return WNull
                var acc = arg(1)
                for ((i, it) in l.withIndex()) { ev.charge(); acc = ev.callFn(fn, listOf(acc, it, WNum.of(i))) }
                acc
            }
            "sum" -> { val l = list(arg(0)) ?: return WNull; chargeItems(l.size); WNum.of(nums(fieldVals(l, a.getOrNull(1))).sum()) }
            "avg" -> { val l = list(arg(0)) ?: return WNull; val v = nums(fieldVals(l, a.getOrNull(1))); if (v.isEmpty()) WNull else WNum.of(v.average()) }
            "median" -> {
                val l = list(arg(0)) ?: return WNull
                val v = nums(fieldVals(l, a.getOrNull(1))).sorted()
                if (v.isEmpty()) WNull else if (v.size % 2 == 1) WNum.of(v[v.size / 2]) else WNum.of((v[v.size / 2 - 1] + v[v.size / 2]) / 2)
            }
            "minOf", "maxOf" -> {
                val l = list(arg(0)) ?: return WNull
                val v = fieldVals(l, a.getOrNull(1)).filter { it != WNull }
                if (v.isEmpty()) return WNull
                var best = v[0]
                for (x in v.drop(1)) {
                    val c = weaveCompare(x, best) ?: continue
                    if (name == "minOf" && c < 0 || name == "maxOf" && c > 0) best = x
                }
                best
            }
            "minBy", "maxBy" -> {
                val l = list(arg(0)) ?: return WNull
                if (l.isEmpty()) return WNull
                val keys = fieldVals(l, arg(1))
                var bi = 0
                for (i in 1 until l.size) {
                    val c = weaveCompare(keys[i], keys[bi]) ?: continue
                    if (name == "minBy" && c < 0 || name == "maxBy" && c > 0) bi = i
                }
                l[bi]
            }
            "sortBy" -> { val l = list(arg(0)) ?: return WNull; sortBy(l, fieldVals(l, a.getOrNull(1)), arg(2).truthy()) }
            "sort" -> { val l = list(arg(0)) ?: return WNull; sortBy(l, l, arg(1).truthy()) }
            "groupBy", "countBy" -> {
                val l = list(arg(0)) ?: return WNull
                val keys = fieldVals(l, arg(1))
                val out = LinkedHashMap<String, MutableList<WValue>>()
                for ((i, it) in l.withIndex()) out.getOrPut(f.auto(keys[i])) { ArrayList() }.add(it)
                WRec(LinkedHashMap(out.mapValues { (_, v) -> if (name == "groupBy") WList(v) else WNum.of(v.size) }))
            }
            "unique" -> {
                val l = list(arg(0)) ?: return WNull
                val keys = fieldVals(l, a.getOrNull(1))
                val seen = ArrayList<WValue>()
                val out = ArrayList<WValue>()
                for ((i, it) in l.withIndex()) if (seen.none { s -> weaveEquals(s, keys[i]) }) { seen += keys[i]; out += it }
                WList(out)
            }
            "take", "drop" -> {
                val l = list(arg(0)) ?: return WNull
                val k = n(arg(1))?.toInt() ?: return WNull
                // Clamp before negating: -Int.MIN_VALUE overflows.
                val m = if (k < 0) minOf(l.size.toLong(), -k.toLong()).toInt() else minOf(k, l.size)
                WList(when {
                    name == "take" && k >= 0 -> l.take(m)
                    name == "take" -> l.takeLast(m)
                    k >= 0 -> l.drop(m)
                    else -> l.dropLast(m)
                })
            }
            "first" -> list(arg(0))?.firstOrNull() ?: WNull
            "last" -> list(arg(0))?.lastOrNull() ?: WNull
            "reverse" -> when (val v = arg(0)) {
                is WList -> WList(v.items.reversed())
                is WStr -> WStr(String(v.v.codePoints().toArray().reversedArray(), 0, v.v.codePointCount(0, v.v.length)))
                else -> WNull
            }
            "flatten" -> { val l = list(arg(0)) ?: return WNull; WList(l.flatMap { if (it is WList) it.items else listOf(it) }) }
            "zip" -> { val x = list(arg(0)) ?: return WNull; val y = list(arg(1)) ?: return WNull; WList(x.zip(y) { p, q -> WList(listOf(p, q)) }) }
            "chunk" -> { val l = list(arg(0)) ?: return WNull; val k = n(arg(1))?.toInt() ?: return WNull; if (k <= 0) WNull else WList(l.chunked(k).map { WList(it) }) }
            "window" -> {
                val l = list(arg(0)) ?: return WNull; val k = n(arg(1))?.toInt() ?: return WNull
                // windows copy k items each, so bound the total copied, not just the window count
                val total = maxOf(0L, l.size.toLong() - k + 1) * k
                if (k <= 0 || total > com.echoflow.weave.core.Limits.LIST_ITEMS) WNull
                else { chargeItems(total.toInt()); WList(l.windowed(k).map { WList(it) }) }
            }
            "cumsum" -> {
                val l = list(arg(0)) ?: return WNull
                var t = 0.0
                WList(fieldVals(l, a.getOrNull(1)).map { t += (it as? WNum)?.v ?: 0.0; WNum.of(t) })
            }
            "pluck" -> { val l = list(arg(0)) ?: return WNull; WList(l.map { ev.member(it, s(1) ?: "") }) }
            "shuffle" -> {
                val l = list(arg(0))?.toMutableList() ?: return WNull
                for (i in l.size - 1 downTo 1) {
                    val j = Math.floor(WeaveRandom.rand(arg(1), (l.size - 1 - i).toLong(), f) * (i + 1)).toInt()
                    val t = l[i]; l[i] = l[j]; l[j] = t
                }
                WList(l)
            }
            "contains" -> when (val x = arg(0)) {
                is WList -> WBool.of(x.items.any { weaveEquals(it, arg(1)) })
                is WStr -> WBool.of(x.v.contains(f.auto(arg(1))))
                else -> WBool.FALSE
            }

            // ---- records
            "keys" -> (arg(0) as? WRec)?.let { WList(it.fields.keys.map { k -> WStr(k) }) } ?: WNull
            "values" -> (arg(0) as? WRec)?.let { WList(it.fields.values.toList()) } ?: WNull
            "entries" -> (arg(0) as? WRec)?.let { r -> WList(r.fields.map { (k, v) -> WRec.of("key" to WStr(k), "value" to v) }) } ?: WNull
            "fromEntries" -> {
                val l = list(arg(0)) ?: return WNull
                val out = LinkedHashMap<String, WValue>()
                for (e in l) when (e) {
                    is WRec -> out[f.auto(e["key"])] = e["value"]
                    is WList -> if (e.items.size >= 2) out[f.auto(e.items[0])] = e.items[1]
                    else -> {}
                }
                WRec(out)
            }
            "get" -> (arg(0) as? WRec)?.fields?.get(s(1) ?: "") ?: a.getOrNull(2) ?: WNull
            "has" -> WBool.of((arg(0) as? WRec)?.fields?.containsKey(s(1) ?: "") == true)
            "merge" -> WRec(LinkedHashMap<String, WValue>().apply { for (r in spread(a)) if (r is WRec) putAll(r.fields) })
            "pick", "omit" -> {
                val r = arg(0) as? WRec ?: return WNull
                val ks = (list(arg(1)) ?: return WNull).map { f.auto(it) }.toSet()
                WRec(LinkedHashMap(r.fields.filterKeys { (it in ks) == (name == "pick") }))
            }
            "set" -> { val r = arg(0) as? WRec ?: return WNull; WRec(LinkedHashMap(r.fields).apply { put(s(1) ?: "", arg(2)) }) }

            // ---- strings
            "str" -> WStr(f.auto(arg(0)))
            "fmt" -> WStr(f.applyText(arg(0), s(1)))
            "upper" -> s(0)?.let { WStr(it.uppercase(f.locale)) } ?: WNull
            "lower" -> s(0)?.let { WStr(it.lowercase(f.locale)) } ?: WNull
            "trim" -> s(0)?.let { WStr(it.trim()) } ?: WNull
            "titleCase" -> s(0)?.let { WStr(f.titleCase(it)) } ?: WNull
            "split" -> {
                val x = s(0) ?: return WNull; val sep = s(1) ?: return WNull
                if (sep.isEmpty()) WList(x.codePoints().toArray().map { WStr(String(Character.toChars(it))) })
                else WList(x.split(sep).map { WStr(it) })
            }
            "join" -> {
                // bounded like concatenation: stop once the result would pass the string limit
                val l = list(arg(0)) ?: return WNull; val sep = s(1) ?: ", "
                val cap = com.echoflow.weave.core.Limits.STRING_CHARS
                val sb = StringBuilder()
                for ((i, item) in l.withIndex()) {
                    chargeItems(1)
                    if (i > 0) { if (sb.length + sep.length > cap) return WNull; sb.append(sep) }
                    sb.append(f.auto(item))
                    if (sb.length > cap) return WNull
                }
                chargeItems(sb.length / 64)
                WStr(sb.toString())
            }
            "replace" -> {
                val x = s(0) ?: return WNull; val find = s(1) ?: return WNull
                if (find.isEmpty()) return WStr(x)
                val with = s(2) ?: ""
                // count matches first: the result size is known before anything is built
                var count = 0L; var at = x.indexOf(find)
                while (at >= 0) { count++; at = x.indexOf(find, at + find.length) }
                val size = x.length + count * (with.length - find.length)
                if (size > com.echoflow.weave.core.Limits.STRING_CHARS) WNull
                else { chargeItems((x.length + size.toInt()) / 64); WStr(x.replace(find, with)) }
            }
            "startsWith" -> WBool.of(s(0)?.startsWith(s(1) ?: return WBool.FALSE) == true)
            "endsWith" -> WBool.of(s(0)?.endsWith(s(1) ?: return WBool.FALSE) == true)
            "slice" -> {
                val x = s(0) ?: return WNull
                val cps = x.codePoints().toArray()
                fun norm(i: Double?, def: Int): Int { val k = i?.toInt() ?: def; return (if (k < 0) cps.size + k else k).coerceIn(0, cps.size) }
                val st = norm(n(a.getOrNull(1)), 0); val en = norm(n(a.getOrNull(2)), cps.size)
                if (st >= en) WStr("") else WStr(String(cps, st, en - st))
            }
            "padStart", "padEnd" -> {
                val x = s(0) ?: return WNull; val k = n(arg(1))?.toInt() ?: return WNull
                val ch = (s(2) ?: " ").ifEmpty { " " }
                val need = k - x.codePointCount(0, x.length)
                // Bounded like repeat(): a source-controlled width must not drive a huge allocation.
                if (need > MAX_PAD) WNull else if (need <= 0) WStr(x) else {
                    chargeItems(need / 64)
                    val pad = ch.repeat((need + ch.length - 1) / ch.length).take(need)
                    WStr(if (name == "padStart") pad + x else x + pad)
                }
            }
            "repeat" -> {
                val x = s(0) ?: return WNull; val k = n(arg(1))?.toInt() ?: return WNull
                // bound the result, not just the count: nested repeats multiply
                if (k !in 0..1000 || x.length.toLong() * k > com.echoflow.weave.core.Limits.STRING_CHARS) WNull
                else { chargeItems(x.length * k / 64); WStr(x.repeat(k)) }
            }
            "words" -> s(0)?.let { WList(it.trim().split(Regex("\\s+")).filter { w -> w.isNotEmpty() }.map { w -> WStr(w) }) } ?: WNull
            "isEmail" -> WBool.of(s(0)?.let { EMAIL.matches(it) } == true)
            "isPhone" -> WBool.of(s(0)?.let { PHONE.matches(it.replace(Regex("[\\s()-]"), "")) } == true)
            "isUrl" -> WBool.of(s(0)?.let { URL.matches(it) } == true)
            "plural" -> if (n(arg(0)) == 1.0) arg(1) else arg(2)

            // ---- dates
            "now" -> WDateTime(f.now().truncatedTo(ChronoUnit.MINUTES))
            "today" -> WDate(ev.ctx.today())
            "date" -> s(0)?.let { Coercion.parseDate(it, f.zone) } ?: (arg(0).takeIf { it is WDate || it is WDateTime } ?: WNull)
            "time" -> s(0)?.let { Coercion.parseTime(it) }?.let { WTime(it) } ?: WNull
            "datetime" -> {
                val d = (ev.coerce.date(arg(0)).value as? WDate)?.v ?: return WNull
                val t = (arg(1) as? WTime)?.v ?: s(1)?.let { Coercion.parseTime(it) } ?: return WNull
                WDateTime(d.atTime(t).atZone(f.zone).toInstant())
            }
            "addDays", "addMonths", "addYears" -> {
                val k = n(arg(1))?.toLong() ?: return WNull
                fun adj(d: LocalDate): LocalDate? = runCatching { when (name) { "addDays" -> d.plusDays(k); "addMonths" -> d.plusMonths(k); else -> d.plusYears(k) } }.getOrNull()
                when (val d = arg(0)) {
                    is WDate -> adj(d.v)?.let { WDate(it) } ?: WNull
                    is WDateTime -> {
                        val l = LocalDateTime.ofInstant(d.v, f.zone)
                        adj(l.toLocalDate())?.let { WDateTime(l.toLocalTime().atDate(it).atZone(f.zone).toInstant()) } ?: WNull
                    }
                    else -> WNull
                }
            }
            "addMinutes", "addHours" -> {
                val i = Coercion.instantOf(arg(0), f.zone) ?: return WNull
                val k = n(arg(1)) ?: return WNull
                WDateTime(i.plusMillis(Math.round(k * if (name == "addMinutes") 60_000 else 3_600_000)))
            }
            "diffDays" -> {
                val x = (ev.coerce.date(arg(0)).value as? WDate)?.v ?: return WNull
                val y = (ev.coerce.date(arg(1)).value as? WDate)?.v ?: return WNull
                WNum.of(ChronoUnit.DAYS.between(x, y))
            }
            "diffMinutes" -> {
                val x = Coercion.instantOf(arg(0), f.zone) ?: return WNull
                val y = Coercion.instantOf(arg(1), f.zone) ?: return WNull
                WNum.of(ChronoUnit.MINUTES.between(x, y))
            }
            "weekday", "day", "month", "year", "startOfWeek", "startOfMonth", "endOfMonth", "daysIn" -> {
                val d = localDate(arg(0), f) ?: return WNull
                when (name) {
                    "weekday" -> WNum.of(d.dayOfWeek.value)
                    "day" -> WNum.of(d.dayOfMonth); "month" -> WNum.of(d.monthValue); "year" -> WNum.of(d.year)
                    "startOfWeek" -> WDate(d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                    "startOfMonth" -> WDate(d.withDayOfMonth(1))
                    "endOfMonth" -> WDate(d.with(TemporalAdjusters.lastDayOfMonth()))
                    else -> WNum.of(d.lengthOfMonth())
                }
            }
            "hour", "minute" -> {
                val t = when (val v = arg(0)) {
                    is WDateTime -> LocalDateTime.ofInstant(v.v, f.zone).toLocalTime()
                    is WTime -> v.v
                    else -> return WNull
                }
                WNum.of(if (name == "hour") t.hour else t.minute)
            }
            "dur" -> n(arg(0))?.let { WDur(Math.round(it)) } ?: WNull
            "seconds" -> n(arg(0))?.let { WDur(Math.round(it * 1e3)) } ?: WNull
            "minutes" -> n(arg(0))?.let { WDur(Math.round(it * 6e4)) } ?: WNull
            "hours" -> n(arg(0))?.let { WDur(Math.round(it * 3.6e6)) } ?: WNull
            "days" -> n(arg(0))?.let { WDur(Math.round(it * 8.64e7)) } ?: WNull
            "ms" -> (arg(0) as? WDur)?.let { WNum.of(it.ms) } ?: WNull
            "secondsOf" -> (arg(0) as? WDur)?.let { WNum.of(it.ms / 1e3) } ?: WNull
            "minutesOf" -> (arg(0) as? WDur)?.let { WNum.of(it.ms / 6e4) } ?: WNull

            // ---- finance
            "pmt" -> { val r = n(arg(0)); val k = n(arg(1)); val pv = n(arg(2)); if (r == null || k == null || pv == null) WNull else num(Finance.pmt(r, k, pv)) }
            "fv" -> {
                val r = n(arg(0)); val k = n(arg(1)); val p = n(arg(2)); val pv = n(a.getOrNull(3)) ?: 0.0
                if (r == null || k == null || p == null) WNull
                else if (r == 0.0) num(pv + p * k) else num(pv * Math.pow(1 + r, k) + p * (Math.pow(1 + r, k) - 1) / r)
            }
            "npv" -> { val r = n(arg(0)) ?: return WNull; val fl = nums(list(arg(1)) ?: return WNull); num(Finance.npv(r, fl)) }
            "irr" -> num(Finance.irr(nums(list(arg(0)) ?: return WNull)))
            "compound" -> {
                val p = n(arg(0)); val r = n(arg(1)); val y = n(arg(2)); val per = n(a.getOrNull(3)) ?: 1.0
                if (p == null || r == null || y == null || per == 0.0) WNull else num(p * Math.pow(1 + r / per, per * y))
            }
            "emi" -> { val p = n(arg(0)); val r = n(arg(1)); val y = n(arg(2)); if (p == null || r == null || y == null) WNull else num(Finance.pmt(r / 12, y * 12, p)) }
            "amortize" -> {
                val p = n(arg(0)); val r = n(arg(1)); val y = n(arg(2))
                if (p == null || r == null || y == null || y <= 0 || y > 100) WNull else Finance.amortize(p, r, y)
            }
            "percentOf" -> { val p = n(arg(0)); val w = n(arg(1)); if (p == null || w == null || w == 0.0) WNull else num(p / w) }
            "pctChange" -> { val x = n(arg(0)); val y = n(arg(1)); if (x == null || y == null || x == 0.0) WNull else num((y - x) / Math.abs(x)) }

            // ---- units and health
            "convert" -> { val v = n(arg(0)) ?: return WNull; num(Units.convert(v, s(1) ?: return WNull, s(2) ?: return WNull)) }
            "bmi" -> { val w = n(arg(0)); val h = n(arg(1)); if (w == null || h == null || h == 0.0) WNull else num(w / Math.pow(h / 100, 2.0)) }
            "bmr" -> {
                val w = n(arg(0)); val h = n(arg(1)); val age = n(arg(2)); val sex = s(3)
                if (w == null || h == null || age == null) WNull
                else num(10 * w + 6.25 * h - 5 * age + if (sex == "f") -161.0 else 5.0)
            }

            // ---- node helpers
            "nodeText" -> (arg(0) as? WNode)?.let { WStr(PlainText.of(it, f)) } ?: WStr("")
            "aspect" -> n(arg(0))?.let { WRec.of("aspect" to WNum.of(it)) } ?: WNull
            "weight" -> n(arg(0))?.let { WRec.of("weight" to WNum.of(it)) } ?: WNull
            "col" -> WRec(LinkedHashMap<String, WValue>().apply {
                put("header", arg(0)); put("value", arg(1))
                listOf("format", "align", "w").forEachIndexed { i, k -> a.getOrNull(2 + i)?.let { put(k, it) } }
            })
            "axis" -> WRec(LinkedHashMap<String, WValue>().apply {
                listOf("title", "format", "min", "max", "ticks", "grid", "log", "hidden").forEachIndexed { i, k -> a.getOrNull(i)?.let { put(k, it) } }
            })
            "pending", "loading", "failed" -> WBool.FALSE
            "spring", "tween" -> arg(0)
            "loop" -> arg(0)
            "stagger" -> { val i = n(arg(0)) ?: return WNull; val base = (a.getOrNull(1) as? WDur)?.ms ?: 40; WDur(Math.round(i * base)) }
            else -> WNull
        }
    }

    private fun spread(a: List<WValue?>): List<WValue> =
        if (a.size == 1 && a[0] is WList) (a[0] as WList).items else a.filterNotNull()

    fun round(x: Double, digits: Int): Double {
        // a double has at most ~17 significant digits and an exponent within ±324: any scale past
        // these bounds changes nothing, but would make setScale build a huge decimal
        val bd = java.math.BigDecimal(x.toString()).setScale(digits.coerceIn(-340, 340), java.math.RoundingMode.HALF_UP)
        return bd.toDouble()
    }

    /** Stable sort; nulls (and incomparable values) last in both directions (03 §3.3). */
    private fun sortBy(l: List<WValue>, keys: List<WValue>, desc: Boolean): WValue {
        val idx = l.indices.toMutableList()
        val good = idx.filter { keys[it] != WNull && comparable(keys[it], keys) }
        val bad = idx.filter { it !in good }
        val sorted = good.sortedWith { x, y ->
            val c = weaveCompare(keys[x], keys[y]) ?: 0
            if (desc) -c else c
        }
        return WList((sorted + bad).map { l[it] })
    }

    private fun comparable(v: WValue, all: List<WValue>) = all.any { it != WNull && weaveCompare(v, it) != null }

    private fun localDate(v: WValue, f: Formatter): LocalDate? = when (v) {
        is WDate -> v.v
        is WDateTime -> LocalDateTime.ofInstant(v.v, f.zone).toLocalDate()
        is WStr -> (Coercion.parseDate(v.v, f.zone) as? WDate)?.v
        else -> null
    }

    /** Widest `padStart`/`padEnd` result, in code points. */
    private const val MAX_PAD = 10_000
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
    private val PHONE = Regex("^\\+?[0-9]{7,15}$")
    private val URL = Regex("^https?://[^/\\s]+\\.[^/\\s]+.*$")

    @Suppress("unused")
    private fun unusedInstant(): Instant = Instant.EPOCH
}
