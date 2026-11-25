package com.echoflow.ui.components

/**
 * The native parser draws no raw HTML, so the small inline vocabulary models actually emit is
 * rewritten to its markdown equivalent: bold, italic, strikethrough, highlight, and line breaks.
 * Underline, subscript, and superscript keep their text. Code is never rewritten.
 */
internal fun normalizeInlineHtml(source: String): String {
    if (source.indexOf('<') < 0) return source
    val code = markdownCodeAndLinkMask(source)
    val out = StringBuilder(source.length)
    var i = 0
    while (i < source.length) {
        val c = source[i]
        if (c != '<' || code[i] || isEscaped(source, i)) {
            out.append(c)
            i++
            continue
        }
        val match = INLINE_TAG.matchAt(source, i)
        if (match == null) {
            out.append(c)
            i++
            continue
        }
        val name = match.groupValues[1].lowercase()
        val end = match.range.last + 1
        out.append(
            when (name) {
                "b", "strong" -> "**"
                "i", "em" -> "*"
                "s", "del", "strike" -> "~~"
                "mark" -> "=="
                "br" -> when {
                    isTableRow(source, i) -> " "
                    source.getOrNull(end) == '\n' || end == source.length -> "  "
                    else -> "  \n"
                }
                else -> ""
            }
        )
        i = end
    }
    return out.toString()
}

private val INLINE_TAG = Regex("""</?(b|strong|i|em|s|del|strike|mark|u|ins|sub|sup|br)\s*/?>""", RegexOption.IGNORE_CASE)

/** Whether [index] sits in a GFM table row: a leading pipe, or a line of a table block. */
private fun isTableRow(source: String, index: Int): Boolean {
    val lineStart = source.lastIndexOf('\n', index - 1) + 1
    return source.substring(lineStart, index).trimStart().startsWith("|") || isInTable(source, lineStart)
}
