package com.echoflow.weave.core.lex

/** 02 §1.2 text normalisation. Idempotent. */
object Normalize {
    private val strayFence = Regex("^ *`{3,} *$")

    fun body(raw: String): String {
        var s = raw
        if (s.startsWith('﻿')) s = s.substring(1)
        s = s.replace("\r\n", "\n").replace('\r', '\n')
        s = replaceTabsOutsideStrings(s)
        if (s.contains("```")) {
            s = s.split('\n').filterNot { strayFence.matches(it) }.joinToString("\n")
        }
        return s
    }

    private fun replaceTabsOutsideStrings(s: String): String {
        if (!s.contains('\t')) return s
        val out = StringBuilder(s.length)
        var i = 0
        var inStr = false
        var strOpen = '"'
        var inRaw = false
        var inComment = false
        while (i < s.length) {
            val c = s[i]
            when {
                inRaw -> {
                    if (s.startsWith("\"\"\"", i)) { inRaw = false; out.append("\"\"\""); i += 3; continue }
                    out.append(c)
                }
                inStr -> {
                    if (c == '\\' && i + 1 < s.length) { out.append(c).append(s[i + 1]); i += 2; continue }
                    if (c == '"' || (strOpen != '"' && (c == '”' || c == '“' || c == '„' || c == '‟'))) inStr = false
                    else if (c == '\n') inStr = false
                    else if (c == '{') {
                        val end = scanInterpolation(s, i)
                        if (end < 0) { out.append(s, i, s.length); return out.toString() }
                        out.append(s, i, end)
                        i = end
                        continue
                    }
                    out.append(c)
                }
                inComment -> {
                    if (c == '\n') inComment = false
                    out.append(if (c == '\t') ' ' else c)
                }
                else -> when {
                    s.startsWith("\"\"\"", i) -> { inRaw = true; out.append("\"\"\""); i += 3; continue }
                    c == '"' || c == '“' || c == '„' || c == '‟' -> { inStr = true; strOpen = c; out.append(c) }
                    c == '#' && !isHexToken(s, i) -> { inComment = true; out.append(c) }
                    c == '\t' -> out.append(' ')
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }

    /** 02 §2.10: `#` followed by 3, 4, 6 or 8 hex digits after `:`, `(`, `,`, `=` or `[` is a colour, not a comment. */
    private fun isHexToken(s: String, i: Int): Boolean {
        var k = i + 1
        while (k < s.length && s[k].isHexDigit()) k++
        if (k - i - 1 !in setOf(3, 4, 6, 8) || (k < s.length && isIdentChar(s[k]))) return false
        var p = i - 1
        while (p >= 0 && s[p] == ' ') p--
        return p >= 0 && s[p] in setOf(':', '(', ',', '=', '[')
    }

    /** From a `{` at [k], the index just after the matching `}`, or -1 at end of input. */
    private fun scanInterpolation(s: String, k: Int): Int {
        var depth = 0
        var x = k
        while (x < s.length) {
            val c = s[x]
            when {
                c == '"' || c == '“' || c == '„' || c == '‟' -> {
                    val open = c
                    x++
                    while (x < s.length) {
                        if (s[x] == '\\' && x + 1 < s.length && s[x + 1] != '\n') { x += 2; continue }
                        if (s[x] == '\n') break
                        if (s[x] == '{' && x + 1 < s.length && s[x + 1] != '{') {
                            val e = scanInterpolation(s, x)
                            if (e < 0) return -1
                            x = e
                            continue
                        }
                        if (s[x] == '"' || (open != '"' && (s[x] == '”' || s[x] == '“' || s[x] == '„' || s[x] == '‟'))) { x++; break }
                        x++
                    }
                    continue
                }
                c == '(' || c == '[' || c == '{' -> depth++
                c == ')' || c == ']' || c == '}' -> { depth--; if (depth == 0 && c == '}') return x + 1 }
            }
            x++
        }
        return -1
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    private fun isIdentChar(c: Char) = c.isLetterOrDigit() || c == '_'
}
