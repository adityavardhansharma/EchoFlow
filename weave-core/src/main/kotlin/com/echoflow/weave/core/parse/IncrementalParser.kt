package com.echoflow.weave.core.parse

import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.lex.Lexer
import com.echoflow.weave.core.lex.Normalize
import com.echoflow.weave.core.lex.TK
import com.echoflow.weave.core.lex.Token
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.WeaveProgram

/**
 * The streaming parser, 02 §10. Frozen statements are never re-lexed or re-parsed while the body
 * only grows; the tail after them is parsed speculatively on each update. At fence close the
 * whole body is parsed again from scratch (07 §8 checks it equals frozen + final tail).
 */
class IncrementalParser(private val catalog: Catalog = BuiltinCatalog.forBuild(2)) {
    private var text = ""
    private val frozen = ArrayList<ParsedLine>()
    private var frozenEnd = 0

    /** Frozen statements so far (02 §10.1). */
    val frozenLines: List<ParsedLine> get() = frozen

    /** Names declared by frozen statements (`$x` for state), for state initialisation (03 §7.1). */
    val frozenNames: Set<String>
        get() = frozen.mapNotNull {
            when (val s = it.stmt) {
                is StateDecl -> "$" + s.name; is Binding -> s.name; is FnDecl -> s.name; else -> null
            }
        }.toSet()

    fun update(body: String, complete: Boolean): WeaveProgram {
        val t = Normalize.body(body)
        if (!t.startsWith(text) || t.length < frozenEnd) reset()
        text = t
        if (complete) {
            val lines = Compiler.parseLines(text)
            frozen.clear(); frozen.addAll(lines)
            frozenEnd = text.length
            return Compiler.build(lines, catalog, complete = true, frozenCount = lines.size)
        }
        freeze()
        val spec = speculative(text.substring(frozenEnd), frozenEnd, lineAt(frozenEnd))
        return Compiler.build(frozen + spec, catalog, complete = false, frozenCount = frozen.size)
    }

    private fun reset() {
        text = ""
        frozen.clear()
        frozenEnd = 0
    }

    private fun lineAt(offset: Int): Int {
        var n = 1
        for (i in 0 until offset) if (text[i] == '\n') n++
        return n
    }

    /** Moves confirmed statements from the tail into [frozen] (02 §10.3). */
    private fun freeze() {
        val tail = text.substring(frozenEnd)
        val lexer = Lexer(tail, frozenEnd, lineAt(frozenEnd))
        val tokens = lexer.lex()
        val lines = Splitter.split(tokens)
        if (lines.size < 2) return
        // Every line but the last was ended by a newline after which the next line's first
        // character has arrived (the next line exists), so it is confirmed.
        val nextFirst = lines.last().tokens.first()
        // the next line's first word may still be growing into a continuation (`|` → `|>`,
        // `a` → `and`, `o` → `or`): until it is complete, the previous statement isn't confirmed
        val atEnd = nextFirst.end == text.length
        val ambiguous = atEnd && (
            (nextFirst.kind == TK.ERROR && nextFirst.text == "|") ||
                (nextFirst.text.isNotEmpty() && CONTINUATION_WORDS.any { it.startsWith(nextFirst.text) })
            )
        val confirmed = if (ambiguous) lines.dropLast(2) else lines.dropLast(1)
        if (confirmed.isEmpty()) return
        for (l in confirmed) {
            frozen += Parser.parseLine(l, lexer.notes.filter { it.offset >= l.start && it.offset < l.end })
        }
        val next = lines[confirmed.size]
        frozenEnd = next.start
    }

    /** 02 §10.2: speculative parse of the unconfirmed tail. Statements that don't parse are ignored. */
    private fun speculative(tail: String, base: Int, baseLine: Int): List<ParsedLine> {
        if (tail.isBlank()) return emptyList()
        var src = tail
        var lexer = Lexer(src, base, baseLine)
        lexer.lex()
        if (lexer.endedInRaw) src += "\"\"\"" else if (lexer.endedInString) src += "\""
        lexer = Lexer(src, base, baseLine)
        val toks = lexer.lex().toMutableList()
        toks.removeAt(toks.size - 1) // EOF
        // step 2: drop a partial word touching the end
        val last = toks.lastOrNull()
        if (last != null && last.end == base + tail.length && !src.endsWith(" ") &&
            last.kind in setOf(TK.IDENT, TK.CAP_IDENT, TK.STATE, TK.ACTION, TK.NUMBER, TK.KEYWORD) && src.length == tail.length
        ) toks.removeAt(toks.size - 1)
        // step 3: drop trailing continuation tokens and `name:` without a value
        while (true) {
            while (toks.isNotEmpty() && toks.last().kind == TK.NEWLINE) toks.removeAt(toks.size - 1)
            val t = toks.lastOrNull() ?: break
            if (t.isPunct(":") && toks.size >= 2 && toks[toks.size - 2].kind == TK.IDENT) {
                toks.removeAt(toks.size - 1); toks.removeAt(toks.size - 1); continue
            }
            if (t.isContinuation() || t.kind == TK.ERROR && !t.unterminated) { toks.removeAt(toks.size - 1); continue }
            break
        }
        // step 4: close open brackets, innermost first
        val stack = ArrayList<String>()
        for (t in toks) {
            if (t.isOpenBracket()) stack += t.text
            if (t.isCloseBracket() && stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        }
        val endPos = toks.lastOrNull()?.end ?: base
        val endLine = toks.lastOrNull()?.line ?: baseLine
        for (open in stack.reversed()) {
            val close = when (open) { "(" -> ")"; "[" -> "]"; else -> "}" }
            toks += Token(TK.PUNCT, close, endPos, endPos, endLine, false)
        }
        toks += Token(TK.EOF, "", endPos, endPos, endLine, false)
        return Splitter.split(toks)
            .map { Parser.parseLine(it, lexer.notes.filter { n -> n.offset >= it.start && n.offset < it.end }) }
            .filter { it.stmt != null }
    }

    private companion object {
        val CONTINUATION_WORDS = listOf("and", "or")
    }
}
