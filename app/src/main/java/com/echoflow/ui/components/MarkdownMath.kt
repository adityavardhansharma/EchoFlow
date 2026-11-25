package com.echoflow.ui.components

/**
 * Rewrites the math delimiters models emit into the two the native parser reads: `$…$` inline
 * and `$$…$$` display. `\(…\)` becomes inline math and `\[…\]` becomes display math. A dollar
 * that does not open real math is escaped, so prices such as "$5 and $10" stay text: an opener
 * must be followed by a non-space, a closer must follow a non-space and must not be followed by a
 * digit, and the content must look like math.
 *
 * With [streaming], math still open at the end of the text is closed and its LaTeX balanced, so a
 * half-written formula draws as a formula instead of flashing raw source. Code is never touched.
 */
internal fun normalizeMath(source: String, streaming: Boolean = false): String {
    if (source.indexOf('$') < 0 && source.indexOf("\\(") < 0 && source.indexOf("\\[") < 0) return source
    val protected = markdownProtectedCharacters(source)
    val paragraphs = MathParagraphs(source)
    val out = StringBuilder(source.length + 16)
    var lineStart = 0
    var blankAfterDisplay = false
    while (lineStart <= source.length) {
        val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
        val line = source.substring(lineStart, lineEnd)
        if (blankAfterDisplay && line.isNotBlank()) out.append('\n')
        blankAfterDisplay = false
        val display = displayMath(source, lineStart, lineEnd, protected, streaming)
        if (display != null) {
            if (out.isNotEmpty() && !out.endsWith("\n\n")) out.append('\n')
            if (display.latex.isNotEmpty()) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                out.append(indent).append("$$\n")
                display.latex.lines().forEach { out.append(indent).append(it).append('\n') }
                out.append(indent).append("$$")
            }
            if (display.end >= source.length) break
            out.append('\n')
            blankAfterDisplay = true
            lineStart = display.end + 1
            continue
        }
        val consumedEnd = appendInlineMath(out, source, lineStart, lineEnd, protected, streaming, paragraphs)
        if (consumedEnd >= source.length) break
        out.append('\n')
        lineStart = consumedEnd + 1
    }
    return out.toString()
}

private class DisplayMath(val latex: String, val end: Int)

/** A display block opening at the start of the line, ending at [DisplayMath.end] (a line end). */
private fun displayMath(source: String, lineStart: Int, lineEnd: Int, protected: BooleanArray, streaming: Boolean): DisplayMath? {
    var start = lineStart
    while (start < lineEnd && (source[start] == ' ' || source[start] == '\t')) start++
    if (start >= lineEnd || protected[start]) return null
    val closer = when {
        source.startsWith("$$", start) -> "$$"
        source.startsWith("\\[", start) -> "\\]"
        else -> return null
    }
    var searchFrom = start + 2
    var lineEndOfClose = lineEnd
    while (true) {
        val close = source.indexOf(closer, searchFrom).takeIf { it in searchFrom until lineEndOfClose }
            ?.takeUnless { protected[it] }
        if (close != null) {
            val after = source.substring(close + closer.length, lineEndOfClose)
            if (after.isNotBlank()) return null
            val latex = cleanDisplayLatex(source.substring(start + 2, close))
            if (latex.isEmpty()) return null
            return DisplayMath(latex, lineEndOfClose)
        }
        if (lineEndOfClose >= source.length) break
        val nextStart = lineEndOfClose + 1
        val nextEnd = source.indexOf('\n', nextStart).let { if (it < 0) source.length else it }
        searchFrom = nextStart
        lineEndOfClose = nextEnd
    }
    if (!streaming) return null
    return DisplayMath(cleanDisplayLatex(balancePartialLatex(source.substring(start + 2))), source.length)
}

private fun cleanDisplayLatex(latex: String): String =
    latex.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

/**
 * Appends one line with its inline math rewritten. A formula may wrap onto the following lines of
 * the same paragraph; the line end actually consumed is returned.
 */
private fun appendInlineMath(
    out: StringBuilder,
    source: String,
    lineStart: Int,
    firstLineEnd: Int,
    protected: BooleanArray,
    streaming: Boolean,
    paragraphs: MathParagraphs,
): Int {
    val searchEnd = paragraphs.continuationEnd(firstLineEnd)
    var lineEnd = firstLineEnd
    var openAtEnd = streaming && lineEnd == source.length
    fun appendMath(delimiter: String, latex: String, close: Int) {
        out.append(delimiter).append(latex.replace('\n', ' ')).append(delimiter)
        if (close > lineEnd) {
            lineEnd = source.indexOf('\n', close).let { if (it < 0) source.length else it }
            openAtEnd = streaming && lineEnd == source.length
        }
    }
    var i = lineStart
    while (i < lineEnd) {
        val c = source[i]
        if (protected[i]) {
            out.append(c)
            i++
            continue
        }
        if (c == '\\') {
            val next = source.getOrNull(i + 1)?.takeIf { i + 1 < lineEnd }
            if (next == '(' || next == '[') {
                val closer = if (next == '(') "\\)" else "\\]"
                val open = if (next == '(') "$" else "$$"
                val close = source.indexOfUnprotected(closer, i + 2, searchEnd, protected)
                if (close >= 0) {
                    val latex = source.substring(i + 2, close).trim()
                    // `\[12\]` is more often an escaped citation than a formula.
                    if (latex.isNotEmpty() && (next == '(' || !latex.all { it.isDigit() })) {
                        appendMath(open, latex, close)
                        i = close + 2
                        continue
                    }
                } else if (openAtEnd) {
                    val latex = balancePartialLatex(source.substring(i + 2, lineEnd).trim())
                    if (latex.isNotEmpty()) out.append(open).append(latex).append(open)
                    return lineEnd
                }
            }
            if (next == null) {
                if (!openAtEnd) out.append(c)
                i++
                continue
            }
            out.append(c).append(next)
            i += 2
            continue
        }
        if (c != '$') {
            out.append(c)
            i++
            continue
        }
        if (source.getOrNull(i + 1) == '$' && i + 1 < lineEnd) {
            val close = source.indexOfUnprotected("$$", i + 2, searchEnd, protected)
            val latex = if (close >= 0) source.substring(i + 2, close).trim() else ""
            if (latex.isNotEmpty()) {
                appendMath("$$", latex, close)
                i = close + 2
                continue
            }
            if (close < 0 && openAtEnd) {
                val partial = balancePartialLatex(source.substring(i + 2, lineEnd).trim())
                if (partial.isNotEmpty()) out.append("$$").append(partial).append("$$")
                return lineEnd
            }
            out.append("\\$\\$")
            i += 2
            continue
        }
        val next = source.getOrNull(i + 1)?.takeIf { i + 1 < lineEnd }
        if (next == null) {
            if (!openAtEnd) out.append("\\$")
            i++
            continue
        }
        if (next.isWhitespace()) {
            out.append("\\$")
            i++
            continue
        }
        val close = findInlineDollarClose(source, i + 1, searchEnd, protected)
        if (close >= 0) {
            val latex = source.substring(i + 1, close)
            if (looksLikeMath(latex, source.getOrNull(i - 1), source.getOrNull(close + 1))) {
                appendMath("$", latex, close)
                i = close + 1
                continue
            }
        } else if (openAtEnd && !next.isDigit()) {
            val rest = source.substring(i + 1, lineEnd)
            if (looksLikePartialMath(rest)) {
                val latex = balancePartialLatex(rest)
                if (latex.isNotEmpty()) out.append('$').append(latex).append('$')
                return lineEnd
            }
        }
        out.append("\\$")
        i++
    }
    return lineEnd
}

private val BLOCK_START = Regex("""^ {0,3}(?:#|>|\||[*+-](?:[ \t]|$)|\d{1,9}[.)](?:[ \t]|$)|```|~~~|\$\$|\\\[)""")

/**
 * Where inline math may search for its closer: to the end of the paragraph holding a line. Math
 * never wraps into or out of a table row or code. Masks are built once and each paragraph's end
 * is cached, so a chunk is scanned in linear time.
 */
private class MathParagraphs(private val source: String) {
    private val table = markdownTableMask(source)
    private val code = markdownCodeBlockMask(source)
    private var cachedEnd = -1

    private fun isBoundary(lineStart: Int): Boolean =
        lineStart < source.length && (table[lineStart] || code[lineStart])

    fun continuationEnd(lineEnd: Int): Int {
        val lineStart = source.lastIndexOf('\n', lineEnd - 1) + 1
        if (isBoundary(lineStart)) return lineEnd
        if (lineEnd <= cachedEnd) return cachedEnd
        var end = lineEnd
        while (end < source.length) {
            val nextStart = end + 1
            val nextEnd = source.indexOf('\n', nextStart).let { if (it < 0) source.length else it }
            val line = source.substring(nextStart, nextEnd)
            if (line.isBlank() || BLOCK_START.containsMatchIn(line) || isBoundary(nextStart)) break
            end = nextEnd
        }
        cachedEnd = end
        return end
    }
}

private const val MATH_SIGNAL = "\\^_={}+-*/<>|"

/** A closing dollar: not escaped, not doubled, after a non-space, and not before a digit. */
private fun findInlineDollarClose(source: String, from: Int, end: Int, protected: BooleanArray): Int {
    var j = from
    while (j < end) {
        if (source[j] == '$' && !protected[j] && !isEscaped(source, j)) {
            if (source.getOrNull(j + 1) == '$') return -1
            val before = source[j - 1]
            val after = source.getOrNull(j + 1)
            if (!before.isWhitespace() && after?.isDigit() != true) return j
        }
        j++
    }
    return -1
}

private fun String.indexOfUnprotected(needle: String, from: Int, end: Int, protected: BooleanArray): Int {
    var index = indexOf(needle, from)
    while (index in from until end && (protected[index] || isEscaped(this, index))) index = indexOf(needle, index + 1)
    return if (index in from until end) index else -1
}

private fun looksLikePartialMath(content: String): Boolean {
    if (content.isEmpty() || content.length > 160 || content.first().isWhitespace()) return false
    return content.any { it in MATH_SIGNAL } || (content.length <= 3 && content.all { it.isLetterOrDigit() })
}

private val TRAILING_COMMAND = Regex("""\\[A-Za-z]*$""")
private val PARTIAL_ENVIRONMENT = Regex("""\\(?:begin|end)(?:\{[^}]*)?$""")
private val FRACTION_MISSING_DENOMINATOR = Regex("""\\(?:[dt]?frac|binom)\{[^{}]*\}$""")
private val ENVIRONMENT = Regex("""\\(begin|end)\{([^}]*)\}""")
private val LEFT = Regex("""\\left(?![A-Za-z])""")
private val RIGHT = Regex("""\\right(?![A-Za-z])""")

/**
 * Closes LaTeX cut off mid-stream so the math engine can draw it: an unfinished command at the
 * very end is dropped, as are a dangling script and an unfinished environment name; braces,
 * `\left`, and environments are then closed in order.
 */
internal fun balancePartialLatex(partial: String): String {
    var s = partial.trimEnd()
    while (true) {
        val before = s
        val trailingSlashes = s.length - s.trimEnd('\\').length
        s = when {
            trailingSlashes % 2 == 1 -> s.dropLast(1)
            trailingSlashes > 0 -> s
            PARTIAL_ENVIRONMENT.containsMatchIn(s) -> s.replace(PARTIAL_ENVIRONMENT, "")
            TRAILING_COMMAND.containsMatchIn(s) -> s.replace(TRAILING_COMMAND, "")
            s.endsWith('^') || s.endsWith('_') || s.endsWith('&') -> s.dropLast(1)
            else -> s
        }.trimEnd()
        if (s == before) break
    }
    if (s.isEmpty()) return s
    var depth = 0
    var i = 0
    while (i < s.length) {
        when (s[i]) {
            '\\' -> i++
            '{' -> depth++
            '}' -> if (depth > 0) depth--
        }
        i++
    }
    val out = StringBuilder(s)
    repeat(depth) { out.append('}') }
    if (FRACTION_MISSING_DENOMINATOR.containsMatchIn(out)) out.append("{}")
    repeat((LEFT.findAll(s).count() - RIGHT.findAll(s).count()).coerceAtLeast(0)) { out.append("\\right.") }
    val environments = ArrayDeque<String>()
    ENVIRONMENT.findAll(s).forEach { match ->
        if (match.groupValues[1] == "begin") environments.addLast(match.groupValues[2])
        else if (environments.lastOrNull() == match.groupValues[2]) environments.removeLast()
    }
    while (environments.isNotEmpty()) out.append("\\end{").append(environments.removeLast()).append('}')
    return out.toString()
}
