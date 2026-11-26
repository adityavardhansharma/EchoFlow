package com.echoflow.weave.core.program

import com.echoflow.weave.core.eval.Coercion
import com.echoflow.weave.core.eval.Formatter
import com.echoflow.weave.core.eval.WAction
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDate
import com.echoflow.weave.core.eval.WDateTime
import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WFn
import com.echoflow.weave.core.eval.WImage
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WTime
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.eval.weaveEquals
import com.echoflow.weave.core.parse.CanonicalPrinter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Values to JSON and back (05 §8.6). Used for the stored `stateJson`, the history state line
 * (06 §2.4), `!say(attach:)` and the hidden model requests. Decoding is tolerant and restores
 * typed values through the state's declared type (03 §7.1 step 2).
 */
object StateCodec {

    fun encode(v: WValue, zone: ZoneId = ZoneId.systemDefault()): String = when (v) {
        WNull -> "null"
        is WBool -> v.v.toString()
        is WNum -> CanonicalPrinter.number(v.v)
        is WStr -> Formatter.jsonString(v.v)
        is WSym -> Formatter.jsonString(v.name)
        is WDur -> Formatter.jsonString("${v.ms}ms")
        is WDate -> Formatter.jsonString(v.v.toString())
        is WTime -> Formatter.jsonString(v.v.format(DateTimeFormatter.ofPattern("HH:mm")))
        is WDateTime -> Formatter.jsonString(v.v.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        is WList -> v.items.joinToString(",", "[", "]") { encode(it, zone) }
        is WRec -> v.fields.entries.joinToString(",", "{", "}") { (k, x) -> Formatter.jsonString(k) + ":" + encode(x, zone) }
        is WImage -> "\"[image]\""
        is WNode -> "\"[node]\""
        is WAction -> "\"[action]\""
        is WFn -> "\"[function]\""
        else -> "null"
    }

    /** The states to store: everything except transient ones (03 §7.1 step 3). */
    fun encodeState(program: WeaveProgram, state: Map<String, WValue>, zone: ZoneId = ZoneId.systemDefault()): String {
        val keep = state.filterKeys { k -> program.states[k]?.transient != true }
        return encode(WRec(LinkedHashMap(keep)), zone)
    }

    /**
     * Stored values for declared, non-transient states, coerced to their declared type. A value
     * that fails coercion is left out, so the state re-initialises (03 §7.1 step 2).
     */
    fun decodeState(program: WeaveProgram, json: String, f: Formatter = Formatter()): Map<String, WValue> {
        val rec = decode(json) as? WRec ?: return emptyMap()
        val out = LinkedHashMap<String, WValue>()
        for ((k, raw) in rec.fields) {
            val decl = program.states[k] ?: continue
            if (decl.transient || decl.reset) continue
            val t = decl.type
            if (t == null) { out[k] = raw; continue }
            val c = Coercion(f).toType(revive(raw), t)
            if (c.ok) out[k] = c.value
        }
        return out
    }

    /** Strings that were durations stay strings unless a type asks for more; `"5ms"` → dur. */
    private fun revive(v: WValue): WValue = when {
        v is WStr && DUR.matches(v.v) -> WDur(v.v.removeSuffix("ms").toLong())
        else -> v
    }

    private val DUR = Regex("-?\\d+ms")

    /**
     * The 06 §2.4 state line body: states whose value differs from the initial value, truncated
     * per 05 §8.6 to at most [max] characters. Null when nothing differs.
     */
    fun stateLine(program: WeaveProgram, state: Map<String, WValue>, initial: Map<String, WValue>, max: Int = 2000, zone: ZoneId = ZoneId.systemDefault()): String? {
        val changed = LinkedHashMap<String, WValue>()
        for (k in program.states.keys) {
            val v = state[k] ?: continue
            val i = initial[k] ?: WNull
            if (!weaveEquals(v, i)) changed[k] = v
        }
        if (changed.isEmpty()) return null
        // step 1: long strings and long lists
        fun shrink(v: WValue): WValue = when (v) {
            is WStr -> if (v.v.length > 200) WStr(v.v.take(200) + "…") else v
            is WList -> if (v.items.size > 20) WStr("list(${v.items.size})") else WList(v.items.map { shrink(it) })
            is WRec -> WRec(LinkedHashMap(v.fields.mapValues { shrink(it.value) }))
            else -> v
        }
        val step1 = WRec(LinkedHashMap(changed.mapValues { shrink(it.value) }))
        encode(step1, zone).let { if (it.length <= max) return it }
        // step 2: every first-level value becomes "…"
        val step2 = WRec(LinkedHashMap(changed.mapValues { WStr("…") as WValue }))
        encode(step2, zone).let { if (it.length <= max) return it }
        // step 3: keep keys in declaration order while they fit with the "+n more" entry
        val keys = changed.keys.toList()
        for (keep in keys.size - 1 downTo 0) {
            val m = LinkedHashMap<String, WValue>()
            keys.take(keep).forEach { m[it] = WStr("…") }
            m["…"] = WStr("+${keys.size - keep} more")
            val s = encode(WRec(m), zone)
            if (s.length <= max) return s
        }
        return encode(WRec.of("…" to WStr("+${keys.size} more")), zone)
    }

    // ---- a small tolerant JSON reader ---------------------------------------------------------

    /** Parses JSON into values (objects → rec, arrays → list). Malformed input → null. */
    fun decode(json: String): WValue = runCatching { Reader(json).let { r -> r.value().also { r.ws() } } }.getOrDefault(WNull)

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): WValue {
            ws()
            require(i < s.length)
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> WStr(str())
                't' -> { expect("true"); WBool.TRUE }
                'f' -> { expect("false"); WBool.FALSE }
                'n' -> { expect("null"); WNull }
                else -> num()
            }
        }
        fun expect(w: String) { require(s.startsWith(w, i)); i += w.length }
        fun obj(): WValue {
            i++
            val m = LinkedHashMap<String, WValue>()
            ws()
            if (s[i] == '}') { i++; return WRec(m) }
            while (true) {
                ws(); val k = str(); ws(); require(s[i] == ':'); i++
                m[k] = value(); ws()
                if (s[i] == ',') { i++; continue }
                require(s[i] == '}'); i++; return WRec(m)
            }
        }
        fun arr(): WValue {
            i++
            val l = ArrayList<WValue>()
            ws()
            if (s[i] == ']') { i++; return WList(l) }
            while (true) {
                l += value(); ws()
                if (s[i] == ',') { i++; continue }
                require(s[i] == ']'); i++; return WList(l)
            }
        }
        fun str(): String {
            require(s[i] == '"'); i++
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun num(): WValue {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return WNum.of(s.substring(st, i).toDouble())
        }
    }
}
