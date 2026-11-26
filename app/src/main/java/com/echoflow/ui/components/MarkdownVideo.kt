package com.echoflow.ui.components

/**
 * Android Enriched Markdown 0.3.0 has no video node. A block-level `<video>` makes the
 * native parser fail, and the reply draws empty. Code is left untouched.
 */
internal fun neutralizeUnsupportedVideo(source: String): String {
    if (source.indexOf("video", ignoreCase = true) < 0) return source
    val code = markdownCodeMask(source)
    val out = StringBuilder(source.length)
    var i = 0
    while (i < source.length) {
        if (code[i]) {
            out.append(source[i])
            i++
            continue
        }
        if (isVideoOpenTag(source, i)) {
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
        if (isVideoCloseTag(source, i)) {
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

/** The value of the tag's own `src` attribute, read attribute by attribute so `data-src` or a quoted value cannot match. */
private fun videoSrc(source: String, start: Int, end: Int): String? {
    var i = start + 1 + "video".length
    while (i < end) {
        while (i < end && (source[i].isWhitespace() || source[i] == '/')) i++
        val nameStart = i
        while (i < end && !source[i].isWhitespace() && source[i] !in "\"'>/=") i++
        if (i == nameStart) break
        val name = source.substring(nameStart, i)
        while (i < end && source[i].isWhitespace()) i++
        if (i >= end || source[i] != '=') continue
        i++
        while (i < end && source[i].isWhitespace()) i++
        val value: String
        if (i < end && (source[i] == '"' || source[i] == '\'')) {
            val quote = source[i]
            val close = source.indexOf(quote, i + 1).takeIf { it in 0 until end } ?: end
            value = source.substring(i + 1, close)
            i = close + 1
        } else {
            val valueStart = i
            while (i < end && !source[i].isWhitespace() && source[i] != '>') i++
            value = source.substring(valueStart, i)
        }
        if (name.equals("src", ignoreCase = true)) {
            return value.replace('\n', ' ').replace('\r', ' ').trim().ifEmpty { null }
        }
    }
    return null
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
