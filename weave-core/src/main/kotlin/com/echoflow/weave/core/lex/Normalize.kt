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
                    if (c == '"' || c == '\n') inStr = false
                    out.append(c)
                }
                inComment -> {
                    if (c == '\n') inComment = false
                    out.append(if (c == '\t') ' ' else c)
                }
                else -> when {
                    s.startsWith("\"\"\"", i) -> { inRaw = true; out.append("\"\"\""); i += 3; continue }
                    c == '"' -> { inStr = true; out.append(c) }
                    c == '#' -> { inComment = true; out.append(c) }
                    c == '\t' -> out.append(' ')
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }
}
