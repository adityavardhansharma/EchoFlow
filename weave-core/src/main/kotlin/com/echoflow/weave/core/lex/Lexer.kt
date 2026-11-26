package com.echoflow.weave.core.lex

/**
 * The Weave lexer (02 §2), including the lexical auto-fix rules A1–A8 (02 §8.1). A9 (trailing `;`)
 * is applied by the splitter because it depends on logical lines. Never throws: anything it can't
 * read becomes an [TK.ERROR] token that drops its statement.
 */
class Lexer(private val src: String, private val baseOffset: Int = 0, private val baseLine: Int = 1) {
    private var i = 0
    private var line = baseLine
    private var space = false
    private val tokens = ArrayList<Token>()
    val notes = ArrayList<LexNote>()

    /** True when the input ended inside a normal string (streaming tail, 02 §10.2). */
    var endedInString = false; private set
    /** True when the input ended inside a raw string. */
    var endedInRaw = false; private set

    fun lex(): List<Token> {
        while (i < src.length) {
            val c = src[i]
            when {
                c == ' ' || c == '\t' -> { space = true; i++ }
                c == '\n' -> { add(TK.NEWLINE, i, i + 1); line++; i++; space = false }
                c == '#' -> lexHash()
                c == '"' && src.startsWith("\"\"\"", i) -> lexRaw()
                c == '"' || c == '“' || c == '”' || c == '„' || c == '‟' -> lexString(c)
                c == '\'' || c == '‘' || c == '’' -> lexSingle(c)
                c.isDigit() -> lexNumber(i)
                c == '.' && i + 1 < src.length && src[i + 1].isDigit() && !prevIsOperand() -> {
                    note(i, "\".${src[i + 1]}…\" written as \"0.${src[i + 1]}…\"")
                    lexNumber(i, leadingDot = true)
                }
                c == '$' -> lexState()
                c == '!' -> lexBang()
                c == '@' -> lexAt()
                c in 'a'..'z' || c == '_' -> lexIdent()
                c in 'A'..'Z' -> lexIdent()
                else -> lexPunct()
            }
        }
        add(TK.EOF, src.length, src.length)
        return tokens
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun add(
        kind: TK, s: Int, e: Int, text: String = src.substring(s, e), num: Double = 0.0, ms: Long = 0,
        str: String? = null, unterminated: Boolean = false, errCode: String? = null, startLine: Int = line,
    ) {
        tokens += Token(kind, text, baseOffset + s, baseOffset + e, startLine, space, num, ms, str, unterminated, errCode)
        space = false
    }

    private fun note(at: Int, description: String) {
        notes += LexNote(baseOffset + at, line, description)
    }

    private fun isIdentChar(c: Char) = c.isLetterOrDigit() && c.code < 128 || c == '_'
    private fun charAt(k: Int): Char = if (k < src.length) src[k] else '\u0000'

    private fun prevIsOperand(): Boolean {
        val t = tokens.lastOrNull() ?: return false
        if (t.kind == TK.NEWLINE) return false
        return when (t.kind) {
            TK.IDENT, TK.STATE, TK.NUMBER, TK.STRING, TK.PERCENT, TK.DURATION, TK.CAP_IDENT -> true
            TK.PUNCT -> t.text == ")" || t.text == "]" || t.text == "}"
            else -> false
        }
    }

    // ---- comments and hex ------------------------------------------------------------------------

    private fun lexHash() {
        // 02 §2.10: a HEX token only after `:`, `(`, `,`, `=` or `[` on the same line.
        var k = i + 1
        while (k < src.length && src[k].isHexDigitAscii()) k++
        val n = k - i - 1
        val prev = tokens.lastOrNull()
        val prevOk = prev != null && prev.kind == TK.PUNCT && prev.text in setOf(":", "(", ",", "=", "[")
        if (n in setOf(3, 4, 6, 8) && !isIdentChar(charAt(k)) && prevOk) {
            add(TK.HEX, i, k)
            i = k
            return
        }
        while (i < src.length && src[i] != '\n') i++
    }

    private fun Char.isHexDigitAscii() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    // ---- numbers ---------------------------------------------------------------------------------

    private fun lexNumber(s: Int, leadingDot: Boolean = false) {
        var k = s
        val sb = StringBuilder()
        var bad = false
        if (leadingDot) {
            sb.append("0")
        } else {
            while (k < src.length && (src[k].isDigit() || src[k] == '_')) k++
            val digits = src.substring(s, k)
            if (digits.contains("__") || digits.endsWith("_")) bad = true
            sb.append(digits.replace("_", ""))
        }
        // fraction: `.` followed by a digit (never `..`, never `.letter`)
        if (charAt(k) == '.' && charAt(k + 1).isDigit()) {
            val fs = k + 1
            k = fs
            while (k < src.length && (src[k].isDigit() || src[k] == '_')) k++
            val frac = src.substring(fs, k)
            if (frac.contains("__") || frac.endsWith("_")) bad = true
            sb.append('.').append(frac.replace("_", ""))
        }
        // exponent
        if ((charAt(k) == 'e' || charAt(k) == 'E')) {
            var x = k + 1
            if (charAt(x) == '+' || charAt(x) == '-') x++
            if (charAt(x).isDigit()) {
                val es = k
                k = x
                while (k < src.length && src[k].isDigit()) k++
                sb.append(src, es, k)
            }
        }
        val text = src.substring(s, k)
        if (bad) {
            add(TK.ERROR, s, k, errCode = "E104")
            i = k
            return
        }
        val v = sb.toString().toDoubleOrNull()
        if (v == null || v.isInfinite() || v.isNaN()) {
            add(TK.ERROR, s, k, errCode = "E104"); i = k; return
        }
        // percent, 02 §2.5
        if (charAt(k) == '%') {
            val after = charAt(k + 1)
            val modulo = after.isLetterOrDigit() || after == '$' || after == '_' || after == '('
            if (!modulo) {
                add(TK.PERCENT, s, k + 1, num = v / 100.0)
                i = k + 1
                return
            }
        }
        // durations 02 §2.6, A7 words, A6 unit suffixes
        for ((unit, mult) in DURATION_UNITS) {
            if (src.startsWith(unit, k) && !isIdentChar(charAt(k + unit.length))) {
                add(TK.DURATION, s, k + unit.length, ms = roundHalfUp(v * mult))
                i = k + unit.length
                return
            }
        }
        for ((word, mult) in A7_WORDS) {
            if (src.startsWith(word, k) && !isIdentChar(charAt(k + word.length))) {
                note(s, "\"$text$word\" written as a duration")
                add(TK.DURATION, s, k + word.length, ms = roundHalfUp(v * mult))
                i = k + word.length
                return
            }
        }
        for (u in listOf("dp", "px", "sp")) {
            if (src.startsWith(u, k) && !isIdentChar(charAt(k + u.length))) {
                note(s, "removed \"$u\" from \"$text$u\"")
                add(TK.NUMBER, s, k, num = v)
                i = k + u.length
                return
            }
        }
        add(TK.NUMBER, s, k, num = v)
        i = k
    }

    private fun roundHalfUp(d: Double): Long = Math.floor(d + 0.5).toLong()

    // ---- strings ---------------------------------------------------------------------------------

    private fun lexString(open: Char) {
        val s = i
        val startLine = line
        val smart = open != '"'
        if (smart) note(i, "smart quotes replaced with \"")
        val content = StringBuilder()
        var k = i + 1
        var closed = false
        while (k < src.length) {
            val c = src[k]
            if (c == '\\' && k + 1 < src.length && src[k + 1] != '\n') {
                content.append(c).append(src[k + 1]); k += 2; continue
            }
            if (c == '"' || (smart && (c == '”' || c == '“' || c == '„' || c == '‟'))) { closed = true; k++; break }
            if (!smart && (c == '”' || c == '“')) { content.append(c); k++; continue }
            if (c == '\n') break
            if (c == '{') {
                if (charAt(k + 1) == '{') { content.append("{{"); k += 2; continue }
                val end = scanInterp(k)
                if (end < 0) { // ran off the end inside an interpolation
                    content.append(src, k, src.length)
                    k = src.length
                    break
                }
                content.append(src, k, end)
                for (x in k until end) if (src[x] == '\n') line++
                k = end
                continue
            }
            content.append(c)
            k++
        }
        if (!closed) {
            if (k >= src.length) endedInString = true
            add(TK.ERROR, s, k, str = content.toString(), unterminated = true, errCode = "E102", startLine = startLine)
            i = k
            return
        }
        add(TK.STRING, s, k, str = content.toString(), startLine = startLine)
        i = k
    }

    /** From a `{` at [k], returns the index just after the matching `}`, or -1 at end of input. */
    private fun scanInterp(k: Int): Int {
        var depth = 0
        var x = k
        while (x < src.length) {
            val c = src[x]
            when {
                c == '"' -> {
                    // nested string inside the interpolation
                    x++
                    while (x < src.length && src[x] != '"') {
                        if (src[x] == '\\') x++
                        else if (src[x] == '{' && charAt(x + 1) != '{') {
                            val e = scanInterp(x)
                            if (e < 0) return -1
                            x = e
                            continue
                        } else if (src[x] == '{') x++
                        x++
                    }
                    if (x >= src.length) return -1
                    x++
                    continue
                }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> {
                    depth--
                    if (depth == 0 && c == '}') return x + 1
                }
            }
            x++
        }
        return -1
    }

    /** A1: `'…'` on one line (also `‘…’`) becomes a double-quoted string, inner `"` escaped. */
    private fun lexSingle(open: Char) {
        val s = i
        var k = i + 1
        val content = StringBuilder()
        while (k < src.length && src[k] != '\n') {
            val c = src[k]
            if (c == '\\' && k + 1 < src.length && src[k + 1] == '\'') { content.append('\''); k += 2; continue }
            if (c == '\\' && k + 1 < src.length) { content.append(c).append(src[k + 1]); k += 2; continue }
            if (c == '\'' || c == '’' || (open == '‘' && c == '’')) break
            if (c == '"') content.append("\\\"") else content.append(c)
            k++
        }
        if (k >= src.length || src[k] == '\n') {
            // not a single-quoted string: an apostrophe is not valid Weave
            add(TK.ERROR, s, s + 1, errCode = "E105")
            i = s + 1
            return
        }
        note(s, "single quotes replaced with double quotes")
        add(TK.STRING, s, k + 1, str = content.toString())
        i = k + 1
    }

    private fun lexRaw() {
        val s = i
        val startLine = line
        val close = src.indexOf("\"\"\"", i + 3)
        if (close < 0) {
            endedInRaw = true
            val body = src.substring(i + 3)
            for (c in body) if (c == '\n') line++
            add(TK.ERROR, s, src.length, str = body, unterminated = true, errCode = "E102", startLine = startLine)
            i = src.length
            return
        }
        val body = src.substring(i + 3, close)
        for (c in body) if (c == '\n') line++
        add(TK.RAW_STRING, s, close + 3, str = rawText(body), startLine = startLine)
        i = close + 3
    }

    // ---- names -----------------------------------------------------------------------------------

    private fun lexState() {
        val s = i
        var k = i + 1
        if (k < src.length && (src[k].isLetter() && src[k].code < 128 || src[k] == '_')) {
            while (k < src.length && isIdentChar(src[k])) k++
            add(TK.STATE, s, k, text = src.substring(s + 1, k))
            i = k
        } else {
            add(TK.ERROR, s, s + 1, errCode = "E105"); i = s + 1
        }
    }

    private fun lexBang() {
        val s = i
        val n = charAt(i + 1)
        when {
            src.startsWith("!==", i) -> { note(i, "\"!==\" written as \"!=\""); add(TK.PUNCT, s, s + 3, text = "!="); i += 3 }
            n == '=' -> { add(TK.PUNCT, s, s + 2, text = "!="); i += 2 }
            n in 'a'..'z' -> {
                var k = i + 1
                while (k < src.length && isIdentChar(src[k])) k++
                add(TK.ACTION, s, k, text = src.substring(s + 1, k))
                i = k
            }
            n == ' ' || n == '(' || n == '$' || n == '!' || n in 'A'..'Z' -> {
                note(i, "\"!\" written as \"not\"")
                add(TK.KEYWORD, s, s + 1, text = "not")
                i++
            }
            else -> { add(TK.ERROR, s, s + 1, errCode = "E105"); i++ }
        }
    }

    private fun lexAt() {
        val s = i
        var k = i + 1
        while (k < src.length && isIdentChar(src[k])) k++
        val word = src.substring(s, k)
        if (word == "@weave") add(TK.HEADER, s, k) else add(TK.ERROR, s, maxOf(k, s + 1), errCode = "E105")
        i = maxOf(k, s + 1)
    }

    private fun lexIdent() {
        val s = i
        var k = i
        while (k < src.length && isIdentChar(src[k])) k++
        val w = src.substring(s, k)
        i = k
        val a8 = A8[w]
        if (a8 != null) {
            note(s, "\"$w\" written as \"$a8\"")
            add(TK.KEYWORD, s, k, text = a8)
            return
        }
        when {
            w[0].isUpperCase() -> add(TK.CAP_IDENT, s, k)
            w in Token.KEYWORDS -> add(TK.KEYWORD, s, k)
            else -> add(TK.IDENT, s, k)
        }
    }

    // ---- punctuation -----------------------------------------------------------------------------

    private fun lexPunct() {
        val s = i
        if (src.startsWith("===", i)) { note(i, "\"===\" written as \"==\""); add(TK.PUNCT, s, s + 3, text = "=="); i += 3; return }
        if (src.startsWith("&&", i)) { note(i, "\"&&\" written as \"and\""); add(TK.KEYWORD, s, s + 2, text = "and"); i += 2; return }
        if (src.startsWith("||", i)) { note(i, "\"||\" written as \"or\""); add(TK.KEYWORD, s, s + 2, text = "or"); i += 2; return }
        for (p in PUNCT) {
            if (src.startsWith(p, i)) {
                add(TK.PUNCT, s, s + p.length)
                i += p.length
                return
            }
        }
        // Unicode or other characters outside strings
        val cp = src.codePointAt(i)
        val len = Character.charCount(cp)
        add(TK.ERROR, s, s + len, errCode = "E105")
        i += len
    }

    companion object {
        private val PUNCT = listOf(
            "::", "..", "??", "==", "!=", "<=", ">=", "|>", "=>", "->",
            "(", ")", "[", "]", "{", "}", ",", ":", ".", "=", "<", ">", "+", "-", "*", "/", "%", "?", ";",
        )
        private val DURATION_UNITS = listOf("ms" to 1.0, "s" to 1_000.0, "m" to 60_000.0, "h" to 3_600_000.0, "d" to 86_400_000.0)
        private val A7_WORDS = listOf(
            "mins" to 60_000.0, "min" to 60_000.0, "secs" to 1_000.0, "sec" to 1_000.0,
            "hrs" to 3_600_000.0, "hr" to 3_600_000.0, "days" to 86_400_000.0, "day" to 86_400_000.0,
        )
        private val A8 = mapOf(
            "True" to "true", "False" to "false", "None" to "null", "nil" to "null",
            "undefined" to "null", "NULL" to "null",
        )

        /** 02 §2.9 raw string processing. Not `trimIndent()`. */
        fun rawText(body: String): String {
            var b = body
            if (b.startsWith("\n")) b = b.substring(1)
            val lines = b.split('\n')
            val nonBlank = lines.filter { it.isNotBlank() }
            var common = nonBlank.firstOrNull()?.takeWhile { it == ' ' || it == '\t' } ?: ""
            for (l in nonBlank) {
                var n = 0
                while (n < common.length && n < l.length && l[n] == common[n]) n++
                common = common.substring(0, n)
            }
            return lines.joinToString("\n") { l ->
                when {
                    l.isBlank() -> ""
                    l.startsWith(common) -> l.substring(common.length)
                    else -> l
                }
            }
        }
    }
}
