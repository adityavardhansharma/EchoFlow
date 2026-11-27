package com.echoflow.weave.core.json

/**
 * A tolerant JSON reader for Weave-JSON (02 §12.4): objects keep key order, and unterminated
 * input is repaired by closing open strings, arrays and objects. A value that can't be read is
 * dropped; [parse] never throws.
 */
object TolerantJson {
    sealed interface J
    data class JObj(val fields: LinkedHashMap<String, J>) : J
    data class JArr(val items: List<J>) : J
    data class JStr(val v: String) : J
    data class JNum(val v: Double, val raw: String) : J
    data class JBool(val v: Boolean) : J
    object JNull : J

    fun parse(text: String): J? = runCatching { Reader(text).value() }.getOrNull()

    private class Reader(val s: String) {
        var i = 0
        fun eof() = i >= s.length
        fun ws() { while (!eof() && s[i].isWhitespace()) i++ }
        fun value(): J? {
            ws()
            if (eof()) return null
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> JStr(str())
                't' -> word("true", JBool(true))
                'f' -> word("false", JBool(false))
                'n' -> word("null", JNull)
                else -> if (c == '-' || c.isDigit()) num() else null
            }
        }
        fun word(w: String, v: J): J? {
            val end = minOf(s.length, i + w.length)
            if (!w.startsWith(s.substring(i, end))) return null
            i = end
            return v
        }
        fun obj(): J {
            i++
            val m = LinkedHashMap<String, J>()
            while (true) {
                ws()
                if (eof()) return JObj(m)
                if (s[i] == '}') { i++; return JObj(m) }
                if (s[i] == ',') { i++; continue }
                if (s[i] != '"') { i++; continue }
                val k = str()
                ws()
                if (eof()) return JObj(m)
                if (s[i] == ':') i++
                val v = value() ?: return JObj(m)
                m[k] = v
            }
        }
        fun arr(): J {
            i++
            val l = ArrayList<J>()
            while (true) {
                ws()
                if (eof()) return JArr(l)
                if (s[i] == ']') { i++; return JArr(l) }
                if (s[i] == ',') { i++; continue }
                val v = value() ?: return JArr(l)
                l += v
            }
        }
        fun str(): String {
            i++
            val sb = StringBuilder()
            while (!eof()) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (eof()) return sb.toString()
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                            'u' -> { if (i + 4 <= s.length) { sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: '?'); i += 4 } }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }
        fun num(): J? {
            val st = i
            while (!eof() && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val raw = s.substring(st, i)
            return raw.toDoubleOrNull()?.let { JNum(it, raw) }
        }
    }
}
