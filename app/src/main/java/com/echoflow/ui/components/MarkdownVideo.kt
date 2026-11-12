package com.echoflow.ui.components

/**
 * Android Enriched Markdown 0.3.0 has no video node. A block-level `<video>` makes the
 * native parser fail, and the reply draws empty. Code spans are left untouched.
 */
internal fun neutralizeUnsupportedVideo(source: String): String {
    if (source.indexOf("video", ignoreCase = true) < 0) return source
    val out = StringBuilder(source.length)
    var i = 0
    while (i < source.length) {
        val fenceEnd = fenceBlockEnd(source, i)
        if (fenceEnd > i) {
            out.append(source, i, fenceEnd)
            i = fenceEnd
            continue
        }
        val codeEnd = inlineCodeEnd(source, i)
        if (codeEnd > i) {
            out.append(source, i, codeEnd)
            i = codeEnd
            continue
        }
        if (isVideoOpenTag(source, i) && !isIndentedCodeLine(source, i)) {
            val tagEnd = openTagEnd(source, i)
            if (tagEnd < 0) {
                out.append(source[i])
                i++
                continue
            }
            val src = videoSrc(source, i, tagEnd)
            out.append(if (src.isNullOrEmpty()) "Video" else "Video: $src")
            i = skipVideoClose(source, tagEnd)
            continue
        }
        if (isVideoCloseTag(source, i) && !isIndentedCodeLine(source, i)) {
            val tagEnd = openTagEnd(source, i)
            i = if (tagEnd < 0) i + 1 else tagEnd
            continue
        }
        out.append(source[i])
        i++
    }
    return out.toString()
}

private fun isVideoOpenTag(source: String, index: Int): Boolean {
    if (source[index] != '<') return false
    if (!source.regionMatches(index + 1, "video", 0, 5, ignoreCase = true)) return false
    val next = source.getOrNull(index + 6) ?: return false
    return next.isWhitespace() || next == '>' || next == '/'
}

private fun isVideoCloseTag(source: String, index: Int): Boolean {
    if (!source.regionMatches(index, "</video", 0, 7, ignoreCase = true)) return false
    val next = source.getOrNull(index + 7) ?: return true
    return next.isWhitespace() || next == '>'
}

/** Index after `>`, or -1 when the tag is still incomplete. */
private fun openTagEnd(source: String, start: Int): Int {
    var i = start + 1
    var quote: Char? = null
    while (i < source.length) {
        val c = source[i]
        when {
            quote != null -> if (c == quote) quote = null
            c == '"' || c == '\'' -> quote = c
            c == '>' -> return i + 1
        }
        i++
    }
    return -1
}

private fun videoSrc(source: String, start: Int, end: Int): String? {
    val match = SRC.find(source.substring(start, end)) ?: return null
    val value = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return null
    val cleaned = value.replace('\n', ' ').replace('\r', ' ').trim()
    return cleaned.ifEmpty { null }
}

private fun skipVideoClose(source: String, start: Int): Int {
    var i = start
    while (i < source.length && source[i].isWhitespace()) i++
    if (i < source.length && isVideoCloseTag(source, i)) {
        val end = openTagEnd(source, i)
        if (end >= 0) return end
    }
    return start
}

private fun isIndentedCodeLine(source: String, index: Int): Boolean {
    var line = index
    while (line > 0 && source[line - 1] != '\n') line--
    var spaces = 0
    var i = line
    while (i < source.length && source[i] == ' ') {
        spaces++
        i++
    }
    if (i < source.length && source[i] == '\t') return true
    return spaces >= 4
}

private fun fenceBlockEnd(source: String, index: Int): Int {
    if (!isLineStart(source, index)) return index
    val open = fenceMarker(source, index, closing = false) ?: return index
    var line = source.indexOf('\n', index).let { if (it < 0) source.length else it + 1 }
    while (line < source.length) {
        val close = fenceMarker(source, line, closing = true)
        if (close != null && close.char == open.char && close.count >= open.count) {
            val end = source.indexOf('\n', line)
            return if (end < 0) source.length else end + 1
        }
        val next = source.indexOf('\n', line)
        if (next < 0) return source.length
        line = next + 1
    }
    return source.length
}

private data class Fence(val char: Char, val count: Int)

private fun fenceMarker(source: String, lineStart: Int, closing: Boolean): Fence? {
    var i = lineStart
    var spaces = 0
    while (i < source.length && source[i] == ' ' && spaces < 4) {
        spaces++
        i++
    }
    if (i >= source.length) return null
    val char = source[i]
    if (char != '`' && char != '~') return null
    var count = 0
    while (i < source.length && source[i] == char) {
        count++
        i++
    }
    if (count < 3) return null
    val lineEnd = source.indexOf('\n', i).let { if (it < 0) source.length else it }
    val rest = source.substring(i, lineEnd)
    if (closing) {
        if (rest.any { it != ' ' && it != '\t' }) return null
    } else if (char == '`' && rest.contains('`')) {
        return null
    }
    return Fence(char, count)
}

private fun inlineCodeEnd(source: String, index: Int): Int {
    if (source[index] != '`') return index
    var count = 0
    while (index + count < source.length && source[index + count] == '`') count++
    var i = index + count
    while (i < source.length) {
        if (source[i] == '`') {
            var run = 0
            while (i + run < source.length && source[i + run] == '`') run++
            if (run == count) return i + run
            i += run
        } else {
            i++
        }
    }
    return index
}

private fun isLineStart(source: String, index: Int): Boolean =
    index == 0 || source[index - 1] == '\n'

private val SRC = Regex("""(?i)\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
