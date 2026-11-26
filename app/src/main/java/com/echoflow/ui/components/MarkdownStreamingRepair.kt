package com.echoflow.ui.components

/**
 * Makes the unfinished end of a streaming reply render as what it is becoming, never as raw
 * syntax: open emphasis, code spans, and math are closed; a half-typed link shows its text; a
 * half-typed image, HTML tag, or fence marker is held back; a table header gets its separator
 * row early; and a lone "-" or "=" under a paragraph is held back so the paragraph does not flash
 * as a setext heading. Only the last line changes, except for the table header. Text inside an
 * open code fence is left alone, because the parser already draws an open fence as code.
 */
internal fun repairStreamingMarkdown(source: String): String {
    if (source.isEmpty()) return source
    val lines = source.split('\n').toMutableList()
    if (unclosedFence(source) != null || markdownCodeBlockMask(source)[source.length - 1]) {
        return normalizeMath(source, streaming = true)
    }
    val last = lines.last()
    when {
        PARTIAL_FENCE.matches(last) -> lines.removeAt(lines.lastIndex)
        SETEXT_UNDERLINE.matches(last) && lines.size >= 2 && isParagraphLine(lines[lines.size - 2]) ->
            lines.removeAt(lines.lastIndex)
    }
    repairTableHeader(lines)
    val math = normalizeMath(lines.joinToString("\n"), streaming = true)
    val cut = math.lastIndexOf('\n') + 1
    val tail = math.substring(cut)
    if (HORIZONTAL_RULE.matches(tail)) return math
    return math.substring(0, cut) + closeInline(repairLinks(dropPartialTag(tail)))
}

/** True when [range] touches a code span in [line], including one still open at the end. */
private fun touchesCode(line: String, range: IntRange): Boolean {
    var i = 0
    while (i < line.length && i <= range.last) {
        when (line[i]) {
            '\\' -> i += 2
            '`' -> {
                val run = line.runLength(i, '`')
                val close = closingRun(line, i + run, '`', run)
                val end = if (close < 0) line.length else close + run
                if (range.first < end && range.last >= i) return true
                i = end
            }
            else -> i++
        }
    }
    return false
}

private fun dropPartialTag(line: String): String {
    val match = PARTIAL_TAG.find(line) ?: return line
    return if (touchesCode(line, match.range)) line else line.substring(0, match.range.first)
}

private val PARTIAL_FENCE = Regex("""^ *(?:`{1,2}|~{1,2})$""")
private val SETEXT_UNDERLINE = Regex("""^ {0,3}(?:-+|=+) *$""")
private val HORIZONTAL_RULE = Regex("""^ {0,3}([-*_])(?: *\1){2,} *$""")
private val PARTIAL_TAG = Regex("""<(?:/?[A-Za-z][A-Za-z0-9]{0,9}|/|(?:video|img|a|span|div|details|summary|sup|sub|mark|br|b|i|u|s|em|strong|del|ins)\b[^<>]*)$""", RegexOption.IGNORE_CASE)
private val TABLE_SEPARATOR = Regex("""^ *\|?(?: *:?-+:? *\|)* *:?-+:? *\|? *$""")
private val PARTIAL_TABLE_SEPARATOR = Regex("""^ *\|?[ :|-]*$""")

private fun isParagraphLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    val first = trimmed.first()
    if (first == '#' || first == '>' || first == '|') return false
    return !Regex("""^(?:[*+-]|\d{1,9}[.)])(?:\s|$)""").containsMatchIn(trimmed)
}

/** Gives a table header its separator row before the separator has fully arrived. */
private fun repairTableHeader(lines: MutableList<String>) {
    val start = lines.indexOfLast { it.isBlank() } + 1
    if (start > lines.lastIndex) return
    val header = lines[start]
    if (!header.trimStart().startsWith('|') || header.count { it == '|' } < 2) return
    val columns = tableCells(header).size
    if (columns == 0) return
    when (lines.lastIndex - start) {
        0 -> lines += tableSeparator(columns, emptyList())
        1 -> {
            val separator = lines[start + 1]
            val complete = TABLE_SEPARATOR.matches(separator) && tableCells(separator).size == columns
            if (!complete && PARTIAL_TABLE_SEPARATOR.matches(separator)) {
                lines[start + 1] = tableSeparator(columns, tableCells(separator))
            }
        }
    }
}

private fun tableCells(row: String): List<String> {
    var trimmed = row.trim().removePrefix("|")
    if (trimmed.endsWith('|') && !trimmed.endsWith("\\|")) trimmed = trimmed.dropLast(1)
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split(Regex("""(?<!\\)\|"""))
}

private fun tableSeparator(columns: Int, partial: List<String>): String =
    (0 until columns).joinToString(" | ", prefix = "| ", postfix = " |") { column ->
        partial.getOrNull(column)?.trim()?.takeIf { it.contains('-') } ?: "---"
    }

private val IMAGE_PARTIAL = Regex("""!\[[^\]]*(?:\]\([^)]*)?$""")
private val LINK_PARTIAL_URL = Regex("""\[([^\[\]]*)\]\([^)]*$""")
private val LINK_PARTIAL_TEXT = Regex("""\[([^\[\]]*)$""")
private val LINK_AWAITING_URL = Regex("""\[([^\[\]]+)\]$""")
private val TASK_PREFIX = Regex("""^\s*(?:[*+-]|\d{1,9}[.)])\s+$""")

/** A link or image still being written: an image is held back, a link shows its text. */
private fun repairLinks(line: String): String {
    fun Regex.repairable(escapeIndex: (MatchResult) -> Int) =
        find(line)?.takeIf { !isEscaped(line, escapeIndex(it)) && !touchesCode(line, it.range) }
    IMAGE_PARTIAL.repairable { it.range.first + 1 }?.let {
        return line.substring(0, it.range.first).trimEnd()
    }
    LINK_PARTIAL_URL.repairable { it.range.first }?.let {
        return line.substring(0, it.range.first) + it.groupValues[1]
    }
    LINK_PARTIAL_TEXT.repairable { it.range.first }?.let {
        val before = line.substring(0, it.range.first)
        if (TASK_PREFIX.matches(before) && it.groupValues[1].length <= 1) return before
        return before + it.groupValues[1]
    }
    LINK_AWAITING_URL.repairable { it.range.first }?.let {
        val before = line.substring(0, it.range.first)
        if (TASK_PREFIX.matches(before) && it.groupValues[1] in TASK_BOXES) return line
        return before + it.groupValues[1]
    }
    return line
}

private val TASK_BOXES = setOf(" ", "x", "X")

private class Delimiter(val token: String, val index: Int)

/** Closes emphasis, strikethrough, highlight, and code spans left open at the end of [line]. */
private fun closeInline(line: String): String {
    val stack = ArrayList<Delimiter>()
    var codeStart = -1
    var codeRun = 0
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '\\' -> i += 2
            c == '`' -> {
                val run = line.runLength(i, '`')
                val close = closingRun(line, i + run, '`', run)
                if (close < 0) {
                    codeStart = i
                    codeRun = run
                    break
                }
                i = close + run
            }
            c == '$' -> i = skipMath(line, i)
            c == ']' && line.getOrNull(i + 1) == '(' ->
                i = findLinkDestinationClose(line, i + 1, line.length)?.plus(1) ?: (i + 1)
            c == '*' || c == '_' || c == '~' || c == '=' -> {
                val run = line.runLength(i, c)
                trackDelimiter(line, stack, c, run, i)
                i += run
            }
            else -> i++
        }
    }
    var out = line
    if (codeStart >= 0) {
        out = if (out.substring(codeStart + codeRun).isBlank()) out.substring(0, codeStart).trimEnd() else out + "`".repeat(codeRun)
    }
    while (stack.isNotEmpty() && out.substring(minOf(out.length, stack.last().index + stack.last().token.length)).isBlank()) {
        out = out.substring(0, minOf(out.length, stack.removeAt(stack.lastIndex).index)).trimEnd()
    }
    if (stack.isEmpty()) return out
    out = out.trimEnd()
    for (index in stack.indices.reversed()) out += stack[index].token
    return out
}

private fun trackDelimiter(line: String, stack: MutableList<Delimiter>, c: Char, run: Int, index: Int) {
    if ((c == '~' && run > 2) || (c == '=' && run != 2)) return
    val token = c.toString().repeat(minOf(run, 3))
    val before = line.getOrNull(index - 1)
    val after = line.getOrNull(index + run)
    val canClose = before != null && !before.isWhitespace()
    val canOpen = after == null || !after.isWhitespace()
    if (c == '_' && before != null && before.isLetterOrDigit()) {
        if (canClose) closeDelimiter(stack, c, token)
        return
    }
    if (canClose && closeDelimiter(stack, c, token)) return
    if (canOpen) stack += Delimiter(token, index)
}

private fun closeDelimiter(stack: MutableList<Delimiter>, c: Char, token: String): Boolean {
    val open = stack.indexOfLast { it.token[0] == c }
    if (open < 0) return false
    if (stack[open].token.length != token.length && c != '*' && c != '_') return false
    while (stack.size > open) stack.removeAt(stack.lastIndex)
    return true
}

private fun closingRun(line: String, from: Int, c: Char, length: Int): Int {
    var index = line.indexOf(c, from)
    while (index >= 0) {
        val run = line.runLength(index, c)
        if (run == length) return index
        index = line.indexOf(c, index + run)
    }
    return -1
}

/** Index after the math span opening at [start]; math was already closed by [normalizeMath]. */
private fun skipMath(line: String, start: Int): Int {
    if (isEscaped(line, start)) return start + 1
    val delimiter = if (line.startsWith("$$", start)) "$$" else "$"
    var index = line.indexOf(delimiter, start + delimiter.length)
    while (index >= 0 && isEscaped(line, index)) index = line.indexOf(delimiter, index + 1)
    return if (index < 0) line.length else index + delimiter.length
}
