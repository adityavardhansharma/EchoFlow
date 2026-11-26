package com.echoflow.weave.core.parse

import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.check.WType
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WSym
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.lex.LexNote
import com.echoflow.weave.core.lex.Lexer
import com.echoflow.weave.core.lex.TK
import com.echoflow.weave.core.lex.Token

/** The result of parsing one logical line. [stmt] is null when the statement was dropped. */
data class ParsedLine(
    val stmt: Stmt?,
    val diags: List<Diagnostic>,
    val start: Int,
    val end: Int,
    /** Binding written with a capitalised name (auto-fix S9 decides what happens to it). */
    val capitalisedName: Boolean = false,
)

private class ParseError(val diag: Diagnostic) : RuntimeException(null, null, false, false)

/**
 * Recursive-descent parser for one logical line (02 §4). Applies S1 (`=` as `==`) and S11
 * (`key = value` in records and calls) while parsing, and the pipe rewrite (02 §6.5).
 */
class Parser private constructor(private val toks: List<Token>) {
    private var p = 0
    private val diags = ArrayList<Diagnostic>()

    private fun peek(o: Int = 0): Token? = toks.getOrNull(p + o)
    private fun next(): Token = toks[p++]
    private fun atEnd() = p >= toks.size
    private fun isPunct(s: String, o: Int = 0) = peek(o)?.isPunct(s) == true
    private fun isKw(s: String, o: Int = 0) = peek(o)?.isKeyword(s) == true

    private fun span(a: Token, b: Token = a) = Span(a.line, a.start, b.end)
    private fun spanFrom(a: Token) = Span(a.line, a.start, toks[maxOf(0, p - 1)].end)

    private fun fail(t: Token?): Nothing {
        if (t == null) {
            val last = toks.last()
            throw ParseError(Diag.syntax(span(last), last.text))
        }
        throw ParseError(Diag.syntax(span(t), t.text))
    }

    private fun unexpected(t: Token): Nothing = throw ParseError(Diag.unexpected(span(t), t.text))

    private fun expectPunct(s: String): Token {
        val t = peek() ?: fail(null)
        if (!t.isPunct(s)) fail(t)
        return next()
    }

    private fun fix(t: Token, description: String) {
        diags += Diag.autofix(span(t), description)
    }

    // ---- statements ----------------------------------------------------------------------------

    private fun statement(): Pair<Stmt, Boolean> {
        val t = peek()!!
        val stmt: Stmt
        var cap = false
        when {
            t.kind == TK.HEADER -> stmt = header()
            t.isKeyword("fn") -> stmt = fnDecl(js = false)
            t.isKeyword("js") && isKw("fn", 1) -> stmt = fnDecl(js = true)
            t.isKeyword("on") -> stmt = effect()
            t.isKeyword("export") -> stmt = export()
            t.isPunct("-") -> stmt = delete()
            t.kind == TK.STATE && (isPunct("=", 1) || isPunct(":", 1)) -> stmt = stateDecl()
            t.kind == TK.IDENT && isPunct("=", 1) -> {
                next(); next()
                val e = expr()
                stmt = Binding(t.text, e, spanFrom(t))
            }
            t.kind == TK.CAP_IDENT && isPunct("=", 1) -> {
                next(); next()
                val e = expr()
                stmt = Binding(t.text, e, spanFrom(t))
                cap = true
            }
            t.kind == TK.KEYWORD && isPunct("=", 1) -> throw ParseError(Diag.reserved(span(t), t.text))
            else -> fail(t)
        }
        if (!atEnd()) unexpected(peek()!!)
        return stmt to cap
    }

    private fun header(): HeaderStmt {
        val h = next()
        var version = 1
        if (peek()?.kind == TK.NUMBER) version = next().num.toInt()
        else diags += Diag.autofix(span(h), "missing version after \"@weave\" read as 1")
        val attrs = LinkedHashMap<String, Expr>()
        while (!atEnd()) {
            val k = next()
            if (!(k.kind == TK.IDENT || k.kind == TK.KEYWORD) || !isPunct(":")) unexpected(k)
            next()
            // Collect the value tokens up to the next `key:` pair.
            val vs = ArrayList<Token>()
            while (!atEnd() && !((peek()!!.kind == TK.IDENT || peek()!!.kind == TK.KEYWORD) && isPunct(":", 1))) vs += next()
            if (vs.isEmpty()) fail(k)
            val value: Expr = if (vs.size == 1) {
                val v = vs[0]
                when (v.kind) {
                    TK.STRING -> stringExpr(v)
                    TK.NUMBER -> Lit(WNum.of(v.num), span(v))
                    TK.IDENT, TK.CAP_IDENT -> Lit(WSym(v.text), span(v))
                    TK.KEYWORD -> when (v.text) {
                        "true" -> Lit(WBool.TRUE, span(v)); "false" -> Lit(WBool.FALSE, span(v))
                        else -> Lit(WSym(v.text), span(v))
                    }
                    else -> Lit(WSym(v.text), span(v))
                }
            } else {
                // e.g. `id: Loan-1`: not a valid value; kept as text so the checker can replace it
                Lit(WSym(vs.joinToString("") { it.text }), span(vs.first(), vs.last()))
            }
            if (attrs.containsKey(k.text)) diags += Diag.duplicate(span(k), k.text)
            attrs.remove(k.text)
            attrs[k.text] = value
        }
        return HeaderStmt(version, attrs, spanFrom(h))
    }

    private fun stateDecl(): StateDecl {
        val s = next()
        var type: WType? = null
        if (isPunct(":")) {
            next()
            val tn = next()
            type = WType.of(tn.text) ?: fail(tn)
        }
        expectPunct("=")
        val init = expr()
        var transient = false
        var reset = false
        while (!atEnd()) {
            val f = peek()!!
            when {
                f.isKeyword("transient") -> { next(); if (transient) fix(f, "repeated \"transient\" ignored"); transient = true }
                f.isKeyword("reset") -> { next(); if (reset) fix(f, "repeated \"reset\" ignored"); reset = true }
                else -> unexpected(f)
            }
        }
        return StateDecl(s.text, type, init, transient, reset, spanFrom(s))
    }

    private fun fnDecl(js: Boolean): FnDecl {
        val start = next()
        if (js) next()
        val n = next()
        if (n.kind == TK.KEYWORD) throw ParseError(Diag.reserved(span(n), n.text))
        if (n.kind != TK.IDENT) fail(n)
        expectPunct("(")
        val params = paramList(")")
        expectPunct(")")
        expectPunct("=")
        return if (js) {
            val r = next()
            if (r.kind != TK.RAW_STRING) fail(r)
            FnDecl(n.text, params, null, r.str, spanFrom(start))
        } else {
            FnDecl(n.text, params, expr(), null, spanFrom(start))
        }
    }

    private fun paramList(close: String): List<String> {
        val out = ArrayList<String>()
        while (!isPunct(close)) {
            val t = next()
            if (t.kind == TK.KEYWORD) throw ParseError(Diag.reserved(span(t), t.text))
            if (t.kind != TK.IDENT) fail(t)
            out += t.text
            if (isPunct(",")) next() else break
        }
        return out
    }

    private fun effect(): Effect {
        val on = next()
        val trig = next()
        if (trig.kind != TK.IDENT) fail(trig)
        val trigger = if (isPunct("(")) {
            next()
            val (leads, named) = argList(trig.text, ")")
            expectPunct(")")
            Call(CallKind.FUNCTION, trig.text, leads, named, null, spanFrom(trig))
        } else Call(CallKind.FUNCTION, trig.text, emptyList(), emptyList(), null, span(trig))
        var cond: Expr? = null
        if (isKw("when")) { next(); cond = expr() }
        expectPunct("->")
        val action = expr()
        return Effect(trigger, cond, action, "", spanFrom(on))
    }

    private fun export(): Export {
        val e = next()
        val names = ArrayList<String>()
        while (true) {
            val t = next()
            when (t.kind) {
                TK.IDENT -> names += t.text
                TK.STATE -> names += "$" + t.text
                else -> fail(t)
            }
            if (atEnd()) break
            expectPunct(",")
        }
        return Export(names, spanFrom(e))
    }

    private fun delete(): Delete {
        val d = next()
        val t = peek() ?: fail(null)
        return when {
            t.kind == TK.IDENT -> { next(); Delete(t.text, spanFrom(d)) }
            t.kind == TK.STATE -> { next(); Delete("$" + t.text, spanFrom(d)) }
            t.isKeyword("on") -> {
                next()
                val trig = next()
                if (trig.kind != TK.IDENT) fail(trig)
                val call = if (isPunct("(")) {
                    next()
                    val (leads, named) = argList(trig.text, ")")
                    expectPunct(")")
                    Call(CallKind.FUNCTION, trig.text, leads, named, null, spanFrom(trig))
                } else Call(CallKind.FUNCTION, trig.text, emptyList(), emptyList(), null, span(trig))
                Delete("on " + CanonicalPrinter.triggerKey(call), spanFrom(d))
            }
            else -> fail(t)
        }
    }

    // ---- expressions ---------------------------------------------------------------------------

    fun expr(): Expr {
        if (lambdaAhead()) return lambda()
        return pipe()
    }

    private fun lambdaAhead(): Boolean {
        val t = peek() ?: return false
        if (t.kind == TK.IDENT && isPunct("=>", 1)) return true
        if (!t.isPunct("(")) return false
        // scan to the matching `)` and check for `=>` and a valid param list
        var depth = 0
        var k = p
        while (k < toks.size) {
            val x = toks[k]
            if (x.isOpenBracket()) depth++
            if (x.isCloseBracket()) { depth--; if (depth == 0) break }
            k++
        }
        if (k >= toks.size || toks.getOrNull(k + 1)?.isPunct("=>") != true) return false
        // between p+1 and k: IDENT { , IDENT } [,]
        var j = p + 1
        var expectName = true
        while (j < k) {
            val x = toks[j]
            if (expectName) { if (x.kind != TK.IDENT) return false; expectName = false }
            else { if (!x.isPunct(",")) return false; expectName = true }
            j++
        }
        return true
    }

    private fun lambda(): Expr {
        val start = peek()!!
        val params = if (start.kind == TK.IDENT) listOf(next().text) else {
            next(); val ps = paramList(")"); expectPunct(")"); ps
        }
        expectPunct("=>")
        val body = expr()
        return Lambda(params, body, spanFrom(start))
    }

    private fun pipe(): Expr {
        val start = peek() ?: fail(null)
        var left = conditional()
        while (isPunct("|>")) {
            val op = next()
            val target = postfix()
            left = when (target) {
                is Call -> target.copy(leads = listOf(left) + target.leads, span = spanFrom(start))
                is Name -> Call(CallKind.FUNCTION, target.name, listOf(left), emptyList(), null, spanFrom(start))
                else -> unexpected(op)
            }
        }
        return left
    }

    private fun conditional(): Expr {
        val start = peek() ?: fail(null)
        val c = coalesce()
        if (isPunct("?")) {
            next()
            val a = expr()
            expectPunct(":")
            val b = expr()
            return Ternary(c, a, b, spanFrom(start))
        }
        return c
    }

    private fun coalesce(): Expr {
        val start = peek() ?: fail(null)
        val l = orExpr()
        if (isPunct("??")) { next(); return BinaryOp("??", l, coalesce(), spanFrom(start)) }
        return l
    }

    private fun orExpr(): Expr {
        val start = peek() ?: fail(null)
        var l = andExpr()
        while (isKw("or")) { next(); l = BinaryOp("or", l, andExpr(), spanFrom(start)) }
        return l
    }

    private fun andExpr(): Expr {
        val start = peek() ?: fail(null)
        var l = notExpr()
        while (isKw("and")) { next(); l = BinaryOp("and", l, notExpr(), spanFrom(start)) }
        return l
    }

    private fun notExpr(): Expr {
        val start = peek() ?: fail(null)
        if (start.isKeyword("not")) { next(); return Unary("not", notExpr(), spanFrom(start)) }
        return comparison()
    }

    private fun compOp(): String? {
        val t = peek() ?: return null
        if (t.kind == TK.PUNCT && t.text in setOf("==", "!=", "<", "<=", ">", ">=")) return t.text
        if (t.isKeyword("in")) return "in"
        if (t.isPunct("=")) return "="
        return null
    }

    private fun comparison(): Expr {
        val start = peek() ?: fail(null)
        val l = rangeExpr()
        var op = compOp() ?: return l
        val opTok = next()
        if (op == "=") { fix(opTok, "\"=\" in an expression written as \"==\""); op = "==" }
        val r = rangeExpr()
        if (compOp() != null) unexpected(peek()!!)
        return BinaryOp(op, l, r, spanFrom(start))
    }

    private fun rangeExpr(): Expr {
        val start = peek() ?: fail(null)
        val l = additive()
        if (isPunct("..")) { next(); return BinaryOp("..", l, additive(), spanFrom(start)) }
        return l
    }

    private fun additive(): Expr {
        val start = peek() ?: fail(null)
        var l = multiplicative()
        while (isPunct("+") || isPunct("-")) {
            val op = next().text
            l = BinaryOp(op, l, multiplicative(), spanFrom(start))
        }
        return l
    }

    private fun multiplicative(): Expr {
        val start = peek() ?: fail(null)
        var l = unary()
        while (isPunct("*") || isPunct("/") || isPunct("%")) {
            val op = next().text
            l = BinaryOp(op, l, unary(), spanFrom(start))
        }
        return l
    }

    private fun unary(): Expr {
        val start = peek() ?: fail(null)
        if (start.isPunct("-")) { next(); return Unary("-", unary(), spanFrom(start)) }
        return postfix()
    }

    private fun postfix(): Expr {
        val start = peek() ?: fail(null)
        var e = primary()
        while (true) {
            when {
                isPunct(".") -> {
                    next()
                    val n = next()
                    if (n.kind != TK.IDENT && n.kind != TK.KEYWORD && n.kind != TK.CAP_IDENT) fail(n)
                    e = Member(e, n.text, spanFrom(start))
                }
                isPunct("[") -> {
                    val takesChildren = e is Call && (e.kind == CallKind.COMPONENT || (e.callee == "group" && e.kind == CallKind.FUNCTION)) && e.children == null
                    next()
                    if (takesChildren) {
                        val items = exprList("]")
                        expectPunct("]")
                        e = (e as Call).copy(children = items, span = spanFrom(start))
                    } else {
                        val ix = expr()
                        expectPunct("]")
                        e = Index(e, ix, spanFrom(start))
                    }
                }
                isPunct("(") && (e is Name) -> {
                    // a call on a name: IDENT(...) — function (marks resolved by the checker)
                    next()
                    val (leads, named) = argList((e as Name).name, ")")
                    expectPunct(")")
                    e = Call(CallKind.FUNCTION, e.name, leads, named, null, spanFrom(start))
                }
                isPunct("(") && e is Qualified -> {
                    throw ParseError(Diag.notCallable(spanFrom(start), "${(e as Qualified).block}::${e.name}"))
                }
                else -> return e
            }
        }
    }

    private fun primary(): Expr {
        val t = peek() ?: fail(null)
        when (t.kind) {
            TK.NUMBER, TK.PERCENT -> { next(); return Lit(WNum.of(t.num), span(t)) }
            TK.DURATION -> { next(); return Lit(WDur(t.ms), span(t)) }
            TK.STRING -> { next(); return stringExpr(t) }
            TK.RAW_STRING -> { next(); return Lit(WStr(t.str ?: ""), span(t)) }
            TK.HEX -> { next(); return HexLit(t.text, span(t)) }
            TK.STATE -> { next(); return StateRef(t.text, span(t)) }
            TK.KEYWORD -> when (t.text) {
                "true" -> { next(); return Lit(WBool.TRUE, span(t)) }
                "false" -> { next(); return Lit(WBool.FALSE, span(t)) }
                "null" -> { next(); return Lit(WNull, span(t)) }
                else -> unexpected(t)
            }
            TK.IDENT -> {
                next()
                if (isPunct("::")) {
                    next()
                    val n = next()
                    return when (n.kind) {
                        TK.IDENT -> Qualified(t.text, n.text, spanFrom(t))
                        TK.STATE -> Qualified(t.text, "$" + n.text, spanFrom(t))
                        else -> fail(n)
                    }
                }
                return Name(t.text, span(t))
            }
            TK.CAP_IDENT -> {
                next()
                var leads: List<Expr> = emptyList()
                var named: List<Pair<String, Expr>> = emptyList()
                if (isPunct("(")) {
                    next()
                    val a = argList(t.text, ")")
                    leads = a.first; named = a.second
                    expectPunct(")")
                }
                return Call(CallKind.COMPONENT, t.text, leads, named, null, spanFrom(t))
            }
            TK.ACTION -> {
                next()
                var leads: List<Expr> = emptyList()
                var named: List<Pair<String, Expr>> = emptyList()
                if (isPunct("(")) {
                    next()
                    val a = argList("!" + t.text, ")")
                    leads = a.first; named = a.second
                    expectPunct(")")
                }
                return Call(CallKind.ACTION, t.text, leads, named, null, spanFrom(t))
            }
            TK.PUNCT -> when (t.text) {
                "[" -> {
                    next()
                    val items = exprList("]")
                    expectPunct("]")
                    return ListLit(items, spanFrom(t))
                }
                "{" -> return record()
                "(" -> {
                    next()
                    val e = expr()
                    expectPunct(")")
                    return e
                }
                else -> unexpected(t)
            }
            TK.ERROR -> throw ParseError(lexError(t))
            else -> unexpected(t)
        }
    }

    private fun record(): Expr {
        val open = next()
        val fields = ArrayList<Pair<String, Expr>>()
        while (!isPunct("}")) {
            val k = next()
            val key = when (k.kind) {
                TK.IDENT, TK.CAP_IDENT, TK.KEYWORD -> k.text
                TK.STRING -> k.str ?: ""
                else -> fail(k)
            }
            if (isPunct("=")) {
                fix(peek()!!, "\"$key = …\" written as \"$key: …\"")
                next()
            } else expectPunct(":")
            fields += key to expr()
            if (isPunct(",")) next() else break
        }
        expectPunct("}")
        return RecLit(fields, spanFrom(open))
    }

    private fun exprList(close: String): List<Expr> {
        val out = ArrayList<Expr>()
        while (!isPunct(close)) {
            if (atEnd()) fail(null)
            out += expr()
            if (isPunct(",")) next() else break
        }
        return out
    }

    /** `arg_list` with E108 (positional after named: dropped) and S11 (`name = value`). */
    private fun argList(callee: String, close: String): Pair<List<Expr>, List<Pair<String, Expr>>> {
        val leads = ArrayList<Expr>()
        val named = ArrayList<Pair<String, Expr>>()
        while (!isPunct(close)) {
            if (atEnd()) fail(null)
            val t = peek()!!
            val isNamed = (t.kind == TK.IDENT || t.kind == TK.KEYWORD && t.text !in setOf("true", "false", "null", "not")) &&
                (isPunct(":", 1) || isPunct("=", 1) && !isPunct("==", 1))
            if (isNamed) {
                next()
                val sep = next()
                if (sep.isPunct("=")) fix(sep, "\"${t.text} = …\" written as \"${t.text}: …\"")
                named += t.text to expr()
            } else {
                val argStart = peek()!!
                val e = expr()
                if (named.isNotEmpty()) diags += Diag.argOrder(spanFrom(argStart), callee)
                else leads += e
            }
            if (isPunct(",")) next() else break
        }
        return leads to named
    }

    // ---- strings -------------------------------------------------------------------------------

    private fun stringExpr(t: Token): Expr {
        val raw = t.str ?: ""
        if (!raw.contains('{') && !raw.contains('}') && !raw.contains('\\')) return Str(listOf(TextPart(raw)), span(t))
        return Str(stringParts(raw, t), span(t))
    }

    private fun stringParts(raw: String, t: Token): List<StrPart> {
        val parts = ArrayList<StrPart>()
        val sb = StringBuilder()
        var i = 0
        fun flushText() { if (sb.isNotEmpty()) { parts += TextPart(sb.toString()); sb.clear() } }
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' && i + 1 < raw.length -> {
                    val n = raw[i + 1]
                    when (n) {
                        'n' -> { sb.append('\n'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        '"' -> { sb.append('"'); i += 2 }
                        '\\' -> { sb.append('\\'); i += 2 }
                        'u' -> {
                            val close = raw.indexOf('}', i)
                            val hex = if (i + 2 < raw.length && raw[i + 2] == '{' && close > 0) raw.substring(i + 3, close) else null
                            val cp = hex?.takeIf { it.length in 1..6 }?.toIntOrNull(16)
                            if (cp != null && Character.isValidCodePoint(cp)) { sb.appendCodePoint(cp); i = close + 1 }
                            else { sb.append("\\u"); i += 2; fix(t, "unknown escape \"\\u\" kept as text") }
                        }
                        else -> { sb.append('\\').append(n); i += 2; fix(t, "unknown escape \"\\$n\" kept as text") }
                    }
                }
                c == '{' && raw.startsWith("{{", i) -> { sb.append('{'); i += 2 }
                c == '}' && raw.startsWith("}}", i) -> { sb.append('}'); i += 2 }
                c == '{' -> {
                    val end = matchBrace(raw, i)
                    if (end < 0) { sb.append(raw.substring(i)); i = raw.length; continue }
                    val body = raw.substring(i + 1, end)
                    if (body.isBlank()) {
                        sb.append("{}")
                        fix(t, "empty \"{}\" kept as text")
                    } else {
                        flushText()
                        parts += interp(body, t)
                    }
                    i = end + 1
                }
                else -> { sb.append(c); i++ }
            }
        }
        flushText()
        return parts
    }

    private fun matchBrace(s: String, open: Int): Int {
        var depth = 0
        var i = open
        var inStr = false
        while (i < s.length) {
            val c = s[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '(', '[', '{' -> depth++
                ')', ']', '}' -> { depth--; if (depth == 0 && c == '}') return i }
            }
            i++
        }
        return -1
    }

    private fun interp(body: String, t: Token): StrPart {
        // split on top-level `|` not followed by `>` (and not part of `||`)
        val pieces = ArrayList<String>()
        var depth = 0
        var inStr = false
        var last = 0
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (inStr) { if (c == '\\') i++ else if (c == '"') inStr = false }
            else when (c) {
                '"' -> inStr = true
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                '|' -> if (depth == 0) {
                    val n = body.getOrNull(i + 1)
                    if (n == '>' || n == '|') { i += 2; continue }
                    if (i > 0 && body[i - 1] == '|') { i++; continue }
                    pieces += body.substring(last, i); last = i + 1
                }
            }
            i++
        }
        pieces += body.substring(last)
        val lexer = Lexer(pieces[0], t.start + 1, t.line)
        val sub = lexer.lex().filter { it.kind != TK.NEWLINE && it.kind != TK.EOF }
        for (n in lexer.notes) diags += Diag.autofix(Span(n.line, n.offset, n.offset), n.description)
        if (sub.isEmpty()) throw ParseError(Diag.syntax(span(t), t.text))
        val sp = Parser(sub)
        val e = sp.expr()
        if (!sp.atEnd()) sp.unexpected(sp.peek()!!)
        diags += sp.diags
        val formats = pieces.drop(1).map { formatSpec(it.trim(), t) }
        return InterpPart(e, formats)
    }

    private fun formatSpec(s: String, t: Token): FormatSpec {
        val toks = Lexer(s).lex().filter { it.kind != TK.NEWLINE && it.kind != TK.EOF }
        val name = toks.firstOrNull()
        if (name == null || name.kind != TK.IDENT && name.kind != TK.KEYWORD) throw ParseError(Diag.syntax(span(t), s))
        val args = ArrayList<WValue>()
        if (toks.size > 1) {
            if (!toks[1].isPunct("(") || !toks.last().isPunct(")")) throw ParseError(Diag.syntax(span(t), s))
            var k = 2
            while (k < toks.size - 1) {
                val a = toks[k]
                args += when (a.kind) {
                    TK.NUMBER -> WNum.of(a.num)
                    TK.STRING -> WStr(a.str ?: "")
                    TK.IDENT, TK.CAP_IDENT, TK.KEYWORD -> WSym(a.text)
                    else -> throw ParseError(Diag.syntax(span(t), s))
                }
                k++
                if (k < toks.size - 1) { if (!toks[k].isPunct(",")) throw ParseError(Diag.syntax(span(t), s)); k++ }
            }
        }
        return FormatSpec(name.text, args)
    }

    companion object {
        fun lexError(t: Token): Diagnostic {
            val sp = Span(t.line, t.start, t.end)
            return when (t.errCode) {
                "E102" -> Diag.unterminated(sp)
                "E104" -> Diag.badNumber(sp, t.text)
                else -> Diag.unexpected(sp, t.text)
            }
        }

        /** Parse one logical line into a statement (or a dropped statement with its diagnostics). */
        fun parseLine(line: LogicalLine, notes: List<LexNote>): ParsedLine {
            val ds = ArrayList<Diagnostic>()
            for (n in notes) ds += Diag.autofix(Span(n.line, n.offset, n.offset), n.description)
            for (n in line.notes) ds += Diag.autofix(Span(n.line, n.offset, n.offset), n.description)
            val first = line.tokens.first()
            val sp = Span(first.line, line.start, line.end)
            line.tokens.firstOrNull { it.kind == TK.ERROR }?.let { err ->
                return ParsedLine(null, ds + lexError(err), line.start, line.end)
            }
            if (line.unbalanced) return ParsedLine(null, ds + Diag.unbalanced(sp), line.start, line.end)
            val parser = Parser(line.tokens)
            return try {
                val (stmt, cap) = parser.statement()
                ParsedLine(stmt, ds + parser.diags, line.start, line.end, cap)
            } catch (e: ParseError) {
                ParsedLine(null, ds + parser.diags.filter { it.severity == com.echoflow.weave.core.check.Severity.WARNING } + e.diag, line.start, line.end)
            }
        }

        /** Parse a standalone expression (tests, `fmt`, tools). */
        fun parseExpr(src: String): Expr? {
            val toks = Lexer(src).lex().filter { it.kind != TK.NEWLINE && it.kind != TK.EOF }
            if (toks.isEmpty() || toks.any { it.kind == TK.ERROR }) return null
            return try {
                val p = Parser(toks)
                val e = p.expr()
                if (!p.atEnd()) null else e
            } catch (e: ParseError) { null }
        }
    }
}
