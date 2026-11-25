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

/** A closer indented at most [maxIndent] spaces; callers that know the container pass its indent + 3. */
internal fun closesFence(line: String, fence: MarkdownFence, maxIndent: Int = Int.MAX_VALUE): Boolean {
    val indent = line.takeWhile { it == ' ' }.length
    if (indent > maxIndent || line.getOrNull(indent) != fence.marker) return false
    val length = line.runLength(indent, fence.marker)
    return length >= fence.length && line.substring(indent + length).isBlank()
}

/** The fence still open at the end of [source], by the same container rules as [markdownCodeMask], or null. */
internal fun unclosedFence(source: String): MarkdownFence? = scanCodeBlocks(source, BooleanArray(source.length))

/** Characters inside fenced code, indented code, and code spans. */
internal fun markdownCodeMask(source: String): BooleanArray = scanMarkdown(source, includeLinks = false, includeTags = false)

/** [markdownCodeMask] plus link destinations and titles. */
internal fun markdownCodeAndLinkMask(source: String): BooleanArray = scanMarkdown(source, includeLinks = true, includeTags = false)

/** [markdownCodeMask] plus link destinations, autolinks, and HTML tags. */
internal fun markdownProtectedCharacters(source: String): BooleanArray = scanMarkdown(source, includeLinks = true, includeTags = true)

private fun scanMarkdown(source: String, includeLinks: Boolean, includeTags: Boolean): BooleanArray {
    val protected = BooleanArray(source.length)
    scanCodeBlocks(source, protected)
    protectInlineConstructs(source, protected, includeLinks, includeTags)
    return protected
}

/** Marks fenced and indented code lines in [protected]; returns the fence still open at the end. */
private fun scanCodeBlocks(source: String, protected: BooleanArray): MarkdownFence? {
    var fence: MarkdownFence? = null
    var fenceQuoteDepth = 0
    var fenceContainerIndent = 0
    var quoteDepth = 0
    var previousBlank = true
    var inIndentedCode = false
    var listContentIndent: Int? = null
    var lineStart = 0
    while (lineStart < source.length) {
        val lineEnd = source.indexOf('\n', lineStart).takeIf { it >= 0 } ?: source.length
        val rawLine = source.substring(lineStart, lineEnd)
        var activeFence = fence
        val quote = blockQuotePrefix(rawLine, maxDepth = if (activeFence != null) fenceQuoteDepth else Int.MAX_VALUE)
        // A fence ends with the block quote that contains it.
        if (activeFence != null && quote.depth < fenceQuoteDepth) activeFence = null
        if (quote.depth != quoteDepth) {
            if (quote.depth > quoteDepth) previousBlank = true
            listContentIndent = null
            inIndentedCode = false
            quoteDepth = quote.depth
        }
        val line = rawLine.substring(quote.offset)
        val containerIndent = listContentIndent ?: 0
        val newFence = if (activeFence == null) openingFence(line, maxIndent = containerIndent + 3) else null
        val listIndent = markdownListIndent(line)
        val newListContentIndent = listIndent?.takeIf {
            it.marker <= 3 || (listContentIndent != null && it.marker >= listContentIndent)
        }?.content
        val indentedCode = activeFence == null && newFence == null && newListContentIndent == null &&
            line.isNotBlank() && (previousBlank || inIndentedCode) &&
            line.leadingIndentColumns() >= containerIndent + 4
        if (activeFence != null || newFence != null || indentedCode) {
            for (index in lineStart until minOf(lineEnd + 1, source.length)) protected[index] = true
        }
        fence = when {
            activeFence != null && closesFence(line, activeFence, maxIndent = fenceContainerIndent + 3) -> null
            activeFence != null -> activeFence
            newFence != null -> {
                fenceQuoteDepth = quoteDepth
                fenceContainerIndent = containerIndent
                newFence
            }
            else -> null
        }
        listContentIndent = when {
            activeFence != null || newFence != null -> listContentIndent
            newListContentIndent != null -> newListContentIndent
            line.isBlank() -> listContentIndent
            listContentIndent != null && line.leadingIndentColumns() >= listContentIndent -> listContentIndent
            else -> null
        }
        inIndentedCode = indentedCode || (inIndentedCode && line.isBlank())
        previousBlank = line.isBlank()
        lineStart = lineEnd + 1
    }
    return fence
}

private fun protectInlineConstructs(source: String, protected: BooleanArray, includeLinks: Boolean, includeTags: Boolean) {
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
        if (includeLinks && c == ']' && source.getOrNull(i + 1) == '(') {
            val close = findLinkDestinationClose(source, i + 1, source.length)
            if (close != null) {
                for (index in i + 1..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        if (includeTags && c == '<') {
            val close = source.findAngleConstructEnd(i)
            if (close >= 0 && isHtmlTagOrAutolink(source.substring(i, close + 1))) {
                for (index in i..close) protected[index] = true
                i = close + 1
                continue
            }
        }
        i++
    }
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

private class BlockQuotePrefix(val depth: Int, val offset: Int)

/** Up to [maxDepth] leading `>` markers, each with up to three spaces before and one after. */
private fun blockQuotePrefix(line: String, maxDepth: Int): BlockQuotePrefix {
    var depth = 0
    var offset = 0
    while (depth < maxDepth) {
        var i = offset
        while (i < line.length && i - offset < 3 && line[i] == ' ') i++
        if (line.getOrNull(i) != '>') break
        i++
        if (line.getOrNull(i) == ' ') i++
        depth++
        offset = i
    }
    return BlockQuotePrefix(depth, offset)
}

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
