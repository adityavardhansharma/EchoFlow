package com.echoflow.weave.core.check

import com.echoflow.weave.core.parse.Span

enum class Severity { ERROR, WARNING }

/**
 * A problem found in a block, 02 §9. [message] is the exact template text (used verbatim in
 * repair prompts, 06 §5). [statement] is the name of the statement it belongs to, when known.
 */
data class Diagnostic(
    val code: String,
    val severity: Severity,
    val statement: String?,
    val span: Span,
    val message: String,
) {
    override fun toString() = "$code $message"
}

/** Message templates from 02 §9. */
object Diag {
    fun suggestion(closest: String?) = if (closest == null) "" else " Did you mean \"$closest\"?"

    private fun e(code: String, span: Span, stmt: String?, msg: String) = Diagnostic(code, Severity.ERROR, stmt, span, msg)
    private fun w(code: String, span: Span, stmt: String?, msg: String) = Diagnostic(code, Severity.WARNING, stmt, span, msg)

    fun syntax(span: Span, near: String, stmt: String? = null) = e("E101", span, stmt, "Line ${span.line}: can't read this statement near \"$near\".")
    fun unterminated(span: Span, stmt: String? = null) = e("E102", span, stmt, "Line ${span.line}: a string is missing its closing quote.")
    fun unbalanced(span: Span, stmt: String? = null) = e("E103", span, stmt, "Line ${span.line}: brackets don't match.")
    fun badNumber(span: Span, text: String, stmt: String? = null) = e("E104", span, stmt, "Line ${span.line}: \"$text\" is not a valid number.")
    fun unexpected(span: Span, token: String, stmt: String? = null) = e("E105", span, stmt, "Line ${span.line}: unexpected \"$token\".")
    fun reserved(span: Span, name: String, stmt: String? = null) = e("E106", span, stmt, "\"$name\" is reserved and can't be used as a name.")
    fun badName(span: Span, name: String, stmt: String? = null) = e("E107", span, stmt, "\"$name\" is not a valid name here.")
    fun argOrder(span: Span, callee: String, stmt: String? = null) = e("E108", span, stmt, "$callee: positional arguments must come before named ones.")
    fun tooManyLeads(span: Span, callee: String, stmt: String? = null) = e("E109", span, stmt, "$callee: too many unnamed arguments; name them.")
    fun headerPosition(span: Span, stmt: String? = null) = e("E110", span, stmt, "\"@weave\" must be the first line.")
    fun unknownComponent(span: Span, name: String, closest: String?, stmt: String? = null) = e("E201", span, stmt, "Unknown component \"$name\".${suggestion(closest)}")
    fun unknownFunction(span: Span, name: String, closest: String?, stmt: String? = null) = e("E202", span, stmt, "Unknown function \"$name\".${suggestion(closest)}")
    fun unknownAction(span: Span, name: String, closest: String?, stmt: String? = null) = e("E203", span, stmt, "Unknown action \"!$name\".${suggestion(closest)}")
    fun missingRequired(span: Span, callee: String, param: String, stmt: String? = null) = e("E204", span, stmt, "$callee needs \"$param\".")
    fun badType(span: Span, callee: String, param: String, expected: String, actual: String, stmt: String? = null) = e("E205", span, stmt, "$callee.$param expects $expected, got $actual.")
    fun badToken(span: Span, callee: String, param: String, value: String, allowed: String, stmt: String? = null) = e("E206", span, stmt, "$callee.$param: \"$value\" is not one of $allowed.")
    fun notCallable(span: Span, name: String, stmt: String? = null) = e("E207", span, stmt, "\"$name\" can't be called.")
    fun childrenNotAllowed(span: Span, callee: String, stmt: String? = null) = e("E208", span, stmt, "$callee doesn't take children.")
    fun tier(span: Span, feature: String, stmt: String? = null) = e("E209", span, stmt, "$feature isn't available at this level.")
    fun bindNotState(span: Span, callee: String, stmt: String? = null) = e("E210", span, stmt, "$callee needs a state variable like \$value as its first argument.")
    fun missingRef(span: Span, name: String, stmt: String? = null) = e("E301", span, stmt, "\"$name\" is used but never defined.")
    fun cycle(span: Span, names: String, stmt: String? = null) = e("E302", span, stmt, "$names depend on each other in a loop.")
    fun budget(span: Span, name: String, stmt: String? = null) = e("E303", span, stmt, "\"$name\" took too long to compute.")
    fun limit(span: Span, what: String, stmt: String? = null) = e("E304", span, stmt, "This block is too large ($what).")
    fun effectLoop(span: Span, stmt: String? = null) = e("E306", span, stmt, "Effects triggered each other too many times.")

    fun unknownArg(span: Span, callee: String, param: String, stmt: String? = null) = w("W401", span, stmt, "$callee: ignored unknown \"$param\".")
    fun tokenSnapped(span: Span, callee: String, param: String, token: String, value: String, stmt: String? = null) = w("W402", span, stmt, "$callee.$param: used \"$token\" for $value.")
    fun autofix(span: Span, description: String, stmt: String? = null) = w("W403", span, stmt, "Fixed: $description.")
    fun duplicate(span: Span, name: String, stmt: String? = null) = w("W404", span, stmt, "\"$name\" was defined twice; using the later one.")
    fun noRoot(span: Span, name: String) = w("W405", span, null, "No \"root\"; using \"$name\".")
    fun unresolved(span: Span, name: String, stmt: String? = null) = w("W407", span, stmt, "\"$name\" isn't defined; treated as a plain word.")
    fun deprecated(span: Span, callee: String, param: String, replacement: String, stmt: String? = null) = w("W408", span, stmt, "$callee.$param is deprecated; use $replacement.")
}

/** Damerau–Levenshtein (optimal string alignment) distance. */
fun damerauLevenshtein(a: String, b: String): Int {
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
    }
    return d[a.length][b.length]
}

/** The closest name within distance 2 (ties: alphabetical first), 02 §9.1. */
fun closest(name: String, candidates: Collection<String>): String? =
    candidates.map { it to damerauLevenshtein(name, it) }
        .filter { it.second <= 2 }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .firstOrNull()?.first
