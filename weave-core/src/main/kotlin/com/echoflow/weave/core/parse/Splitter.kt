package com.echoflow.weave.core.parse

import com.echoflow.weave.core.lex.LexNote
import com.echoflow.weave.core.lex.TK
import com.echoflow.weave.core.lex.Token

/** One logical line (one statement), 02 §3. */
data class LogicalLine(
    val tokens: List<Token>,
    /** A close bracket without an open one was seen: the statement is dropped with E103. */
    val unbalanced: Boolean,
    /** Auto-fix notes produced by the splitter (A9). */
    val notes: List<LexNote>,
) {
    val start get() = tokens.first().start
    val end get() = tokens.last().end
    val line get() = tokens.first().line
}

object Splitter {
    /** 02 §3.2. [tokens] must end with EOF. */
    fun split(tokens: List<Token>): List<LogicalLine> {
        val out = ArrayList<LogicalLine>()
        var cur = ArrayList<Token>()
        var depth = 0
        var unbalanced = false
        fun flush() {
            if (cur.isEmpty()) return
            val notes = ArrayList<LexNote>()
            // A9: `;` at the end of a logical line is removed.
            while (cur.isNotEmpty() && cur.last().isPunct(";")) {
                val t = cur.removeAt(cur.size - 1)
                notes += LexNote(t.start, t.line, "removed \";\" at the end of a line")
            }
            if (cur.isNotEmpty()) out += LogicalLine(cur, unbalanced || depth > 0, notes)
            cur = ArrayList()
            depth = 0
            unbalanced = false
        }
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when (t.kind) {
                TK.NEWLINE -> {
                    val last = cur.lastOrNull { !it.isPunct(";") } ?: cur.lastOrNull()
                    val next = nextNonNewline(tokens, i)
                    val ends = depth == 0 &&
                        (last == null || !last.isContinuation()) &&
                        (next == null || !next.isLeadingContinuation())
                    if (ends) flush()
                }
                TK.EOF -> flush()
                else -> {
                    if (t.isOpenBracket()) depth++
                    if (t.isCloseBracket()) {
                        if (depth == 0) unbalanced = true
                        depth = maxOf(0, depth - 1)
                    }
                    cur += t
                }
            }
            i++
        }
        return out
    }

    fun nextNonNewline(tokens: List<Token>, from: Int): Token? {
        var k = from + 1
        while (k < tokens.size && tokens[k].kind == TK.NEWLINE) k++
        val t = tokens.getOrNull(k) ?: return null
        return if (t.kind == TK.EOF) null else t
    }
}
