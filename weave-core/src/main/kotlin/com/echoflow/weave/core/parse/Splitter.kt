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
        val nextOf = nextNonNewlineIndex(tokens)
        var cur = ArrayList<Token>()
        val openers = ArrayDeque<String>()
        var unbalanced = false
        fun flush() {
            if (cur.isEmpty()) return
            val notes = ArrayList<LexNote>()
            // A9: `;` at the end of a logical line is removed.
            while (cur.isNotEmpty() && cur.last().isPunct(";")) {
                val t = cur.removeAt(cur.size - 1)
                notes += LexNote(t.start, t.line, "removed \";\" at the end of a line")
            }
            if (cur.isNotEmpty()) out += LogicalLine(cur, unbalanced || openers.isNotEmpty(), notes)
            cur = ArrayList()
            openers.clear()
            unbalanced = false
        }
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when (t.kind) {
                TK.NEWLINE -> {
                    val last = cur.lastOrNull { !it.isPunct(";") } ?: cur.lastOrNull()
                    val next = nextOf[i]?.let { tokens[it] }
                    val ends = openers.isEmpty() &&
                        (last == null || !last.isContinuation()) &&
                        (next == null || !next.isLeadingContinuation())
                    if (ends) flush()
                }
                TK.EOF -> flush()
                else -> {
                    if (t.isOpenBracket()) openers.addLast(t.text)
                    if (t.isCloseBracket()) {
                        val want = when (t.text) { ")" -> "("; "]" -> "["; else -> "{" }
                        if (openers.lastOrNull() == want) openers.removeLast() else {
                            // 02 §3.2: a bracket with no match drops this statement only, and the
                            // splitter continues with depth clamped at zero.
                            unbalanced = true
                            openers.clear()
                        }
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

    /** Index of the next non-newline token after each position, computed once. */
    private fun nextNonNewlineIndex(tokens: List<Token>): Array<Int?> {
        val next = arrayOfNulls<Int>(tokens.size)
        var nxt: Int? = null
        for (k in tokens.indices.reversed()) {
            next[k] = nxt
            if (tokens[k].kind != TK.NEWLINE && tokens[k].kind != TK.EOF) nxt = k
            else if (tokens[k].kind == TK.EOF) nxt = null
        }
        return next
    }
}
