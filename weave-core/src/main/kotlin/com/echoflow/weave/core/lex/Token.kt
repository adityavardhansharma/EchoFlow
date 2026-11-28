package com.echoflow.weave.core.lex

/** Token kinds, 02 §2.2. [ERROR] carries a lexical error that drops its statement. */
enum class TK {
    NEWLINE, IDENT, CAP_IDENT, STATE, ACTION, KEYWORD, NUMBER, PERCENT, DURATION,
    STRING, RAW_STRING, HEX, HEADER, PUNCT, ERROR, EOF,
}

/**
 * One token. Offsets index the normalised body. For [TK.STRING], [str] holds the raw content
 * between the quotes (escapes and interpolations not yet processed); for [TK.RAW_STRING] the
 * final processed text. [num] holds NUMBER/PERCENT values, [ms] DURATION values.
 */
data class Token(
    val kind: TK,
    val text: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val spaceBefore: Boolean,
    val num: Double = 0.0,
    val ms: Long = 0,
    val str: String? = null,
    val unterminated: Boolean = false,
    /** For ERROR tokens: diagnostic code and its message substitution. */
    val errCode: String? = null,
) {
    fun isPunct(p: String) = kind == TK.PUNCT && text == p
    fun isKeyword(k: String) = kind == TK.KEYWORD && text == k

    /** 02 §3.1 rule 2. */
    fun isContinuation(): Boolean = when (kind) {
        TK.PUNCT -> text in CONTINUATION_PUNCT
        TK.KEYWORD -> text in CONTINUATION_KEYWORDS
        else -> false
    }

    /** 02 §3.1 rule 3. */
    fun isLeadingContinuation(): Boolean = when (kind) {
        TK.PUNCT -> text in LEADING_PUNCT
        TK.KEYWORD -> text == "and" || text == "or"
        else -> false
    }

    fun isOpenBracket() = kind == TK.PUNCT && (text == "(" || text == "[" || text == "{")
    fun isCloseBracket() = kind == TK.PUNCT && (text == ")" || text == "]" || text == "}")

    companion object {
        val CONTINUATION_PUNCT = setOf(
            "+", "-", "*", "/", "%", "=", "==", "!=", "<", "<=", ">", ">=", "?", "??", ":", "::",
            ".", "..", ",", "|>", "=>", "->",
        )
        val CONTINUATION_KEYWORDS = setOf("and", "or", "not", "in", "when", "on")
        val LEADING_PUNCT = setOf("|>", ".", "?", "??", ":", "]", ")", "}")
        val KEYWORDS = setOf(
            "fn", "js", "on", "when", "export", "and", "or", "not", "in", "true", "false", "null",
            "transient", "reset",
        )
    }
}

/** A lexical auto-fix (A-rule) or error, located by body offset. */
data class LexNote(val offset: Int, val line: Int, val description: String)
