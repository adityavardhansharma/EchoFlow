package com.echoflow.ui.components

/**
 * Source-level scanning shared by the markdown preprocessors. Everything here works on raw
 * markdown text, before the native parser sees it, so a rewrite never touches code.
 */

internal data class MarkdownFence(val marker: Char, val length: Int, val info: String)

/** A fence opener with at most [maxIndent] leading spaces, or null. */
internal fun openingFence(line: String, maxIndent: Int = 3): MarkdownFence? {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > maxIndent || indent == line.length) return null
    val marker = line[indent]
    if (marker != '`' && marker != '~') return null
    val length = line.runLength(indent, marker)
    if (length < 3) return null
    val suffix = line.substring(indent + length)
    if (marker == '`' && '`' in suffix) return null
    return MarkdownFence(marker, length, suffix.trim())
}

internal fun closesFence(line: String, fence: MarkdownFence): Boolean {
    val indent = line.takeWhile { it == ' ' }.length
    if (line.getOrNull(indent) != fence.marker) return false
    val length = line.runLength(indent, fence.marker)
    return length >= fence.length && line.substring(indent + length).isBlank()
}

/** Characters inside fenced code, indented code, and code spans. */
internal fun markdownCodeMask(source: String): BooleanArray = scanMarkdown(source, includeLinksAndTags = false)

/** [markdownCodeMask] plus link destinations, autolinks, and HTML tags. */
internal fun markdownProtectedCharacters(source: String): BooleanArray = scanMarkdown(source, includeLinksAndTags = true)

private fun scanMarkdown(source: String, includeLinksAndTags: Boolean): BooleanArray {
    val protected = BooleanArray(source.length)
    var fence: MarkdownFence? = null
    var previousBlank = true
    var listContentIndent: Int? = null
    var lineStart = 0
    while (lineStart < source.length) {
        val lineEnd = source.indexOf('\n', lineStart).takeIf { it >= 0 } ?: source.length
        val line = source.substring(lineStart, lineEnd)
        val activeFence = fence
        val newFence = if (activeFence == null) openingFence(line, maxIndent = (listContentIndent ?: 0) + 3) else null
        val listIndent = markdownListIndent(line)
        val newListContentIndent = listIndent?.takeIf {
            it.marker <= 3 || (listContentIndent != null && it.marker >= listContentIndent)
        }?.content
        val codeIndent = (listContentIndent?.plus(4) ?: 4)
        val indentedCode = activeFence == null && newFence == null &&
            previousBlank && newListContentIndent == null &&
            line.leadingIndentColumns() >= codeIndent
        if (activeFence != null || newFence != null || indentedCode) {
            for (index in lineStart until minOf(lineEnd + 1, source.length)) protected[index] = true
        }
        fence = when {
            activeFence != null && closesFence(line, activeFence) -> null
            activeFence != null -> activeFence
            newFence != null -> newFence
            else -> null
        }
        listContentIndent = when {
            activeFence != null || newFence != null -> listContentIndent
            newListContentIndent != null -> newListContentIndent
            line.isBlank() -> listContentIndent
            listContentIndent != null && line.leadingIndentColumns() >= listContentIndent -> listContentIndent
            else -> null
        }
        previousBlank = line.isBlank()
        lineStart = lineEnd + 1
    }

    var i = 0
    while (i < source.length) {
        if (protected[i]) {
            i++
            continue
        }
        val c = source[i]
        if (c == '\\') {
            i += 2
            continue
        }
        if (c == '`') {
            val length = source.runLength(i, '`')
            val close = source.findClosingCodeSpan(i + length, length, protected)
            if (close >= 0) {
                for (index in i until close + length) protected[index] = true
                i = close + length
                continue
            }
            i += length
            continue
        }
        if (includeLinksAndTags && c == ']' && source.getOrNull(i + 1) == '(') {
            val close = findLinkDestinationClose(source, i + 1, source.length)
            if (close != null) {
                for (index in i + 1..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        if (includeLinksAndTags && c == '<') {
            val close = source.findAngleConstructEnd(i)
            if (close >= 0 && isHtmlTagOrAutolink(source.substring(i, close + 1))) {
                for (index in i..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        i++
    }
    return protected
}

private val MARKDOWN_LIST_ITEM = Regex("""^([ \t]*)([*+-]|\d{1,9}[.)])([ \t]{1,4}).*$""")
private val HTML_TAG = Regex("""</?[A-Za-z][A-Za-z0-9-]*(?:\s+[^<>]*?)?\s*/?>""")
private val URI_AUTOLINK = Regex("""<[A-Za-z][A-Za-z0-9+.-]{1,31}:[^<>\s]*>""")
private val EMAIL_AUTOLINK = Regex("""<[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?>""")

private fun isHtmlTagOrAutolink(candidate: String): Boolean =
    HTML_TAG.matches(candidate) ||
        URI_AUTOLINK.matches(candidate) ||
        EMAIL_AUTOLINK.matches(candidate) ||
        (candidate.startsWith("<!--") && candidate.endsWith("-->"))

private data class MarkdownListIndent(val marker: Int, val content: Int)

private fun markdownListIndent(line: String): MarkdownListIndent? {
    val match = MARKDOWN_LIST_ITEM.matchEntire(line) ?: return null
    val markerColumn = match.groupValues[1].leadingIndentColumns()
    val markerEnd = markerColumn + match.groupValues[2].length
    val contentColumn = match.groupValues[3].endIndentColumn(markerEnd)
    return MarkdownListIndent(marker = markerColumn, content = contentColumn)
}

internal fun String.leadingIndentColumns(): Int {
    var columns = 0
    for (char in this) {
        when (char) {
            ' ' -> columns++
            '\t' -> columns += 4 - (columns % 4)
            else -> return columns
        }
    }
    return columns
}

private fun String.endIndentColumn(startColumn: Int): Int {
    var columns = startColumn
    for (char in this) {
        columns += if (char == '\t') 4 - (columns % 4) else 1
    }
    return columns
}

private fun String.findAngleConstructEnd(start: Int): Int {
    if (startsWith("<!--", start)) return indexOf("-->", start + 4).let { if (it < 0) -1 else it + 2 }
    var quote: Char? = null
    var index = start + 1
    while (index < length) {
        val char = this[index]
        when {
            char == '\n' && quote == null -> return -1
            quote != null && char == quote -> quote = null
            quote == null && (char == '\'' || char == '"') -> quote = char
            quote == null && char == '>' -> return index
        }
        index++
    }
    return -1
}

private fun String.findClosingCodeSpan(from: Int, length: Int, protected: BooleanArray): Int {
    var index = indexOf('`', from)
    while (index >= 0) {
        val runLength = runLength(index, '`')
        if (!protected[index] && runLength == length) return index
        index = indexOf('`', index + runLength)
    }
    return -1
}

internal fun String.runLength(start: Int, char: Char): Int {
    var end = start
    while (end < length && this[end] == char) end++
    return end - start
}
