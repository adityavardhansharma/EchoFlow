package com.echoflow.weave.core.check

import com.echoflow.weave.core.catalog.ActionSpec
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.catalog.ChildrenSpec
import com.echoflow.weave.core.catalog.ComponentSpec
import com.echoflow.weave.core.catalog.ParamSpec
import com.echoflow.weave.core.catalog.Tokens
import com.echoflow.weave.core.eval.WDur
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WNum
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.parse.AutoFix
import com.echoflow.weave.core.parse.BinaryOp
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.CallKind
import com.echoflow.weave.core.parse.Effect
import com.echoflow.weave.core.parse.Expr
import com.echoflow.weave.core.parse.FnDecl
import com.echoflow.weave.core.parse.HexLit
import com.echoflow.weave.core.parse.Index
import com.echoflow.weave.core.parse.InterpPart
import com.echoflow.weave.core.parse.Lambda
import com.echoflow.weave.core.parse.ListLit
import com.echoflow.weave.core.parse.Lit
import com.echoflow.weave.core.parse.Member
import com.echoflow.weave.core.parse.Name
import com.echoflow.weave.core.parse.Qualified
import com.echoflow.weave.core.parse.RecLit
import com.echoflow.weave.core.parse.Span
import com.echoflow.weave.core.parse.StateDecl
import com.echoflow.weave.core.parse.StateRef
import com.echoflow.weave.core.parse.Stmt
import com.echoflow.weave.core.parse.Str
import com.echoflow.weave.core.parse.Ternary
import com.echoflow.weave.core.parse.TextPart
import com.echoflow.weave.core.parse.Unary

/**
 * The static checker (05 §2). Runs per statement after parsing and applies the call-level
 * structural auto-fixes (02 §8.2 S2–S8, S10) with the slot types from the catalog, then resolves
 * names, calls and arguments, emitting 02 §9 diagnostics and removing the smallest failing unit.
 *
 * [catalog] is the build catalog (tier, phase, registry); [BuiltinCatalog] tells an unavailable
 * entry (E209) from an unknown one (E2xx).
 */
class Checker(
    private val catalog: Catalog,
    /** Names declared in the block, by namespace. */
    private val bindings: Set<String>,
    private val functions: Set<String>,
    private val states: Set<String>,
    /** Names an edit deleted: references to them are `E301` and evaluate to null (T-106). */
    private val deleted: Set<String> = emptySet(),
    /**
     * The block is still streaming: a call to a function nobody declared yet may be a `fn` that
     * hasn't arrived (03 §7.1), so it is kept, not replaced by null. Fence close checks again.
     */
    private val streaming: Boolean = false,
) {
    private val full = BuiltinCatalog
    val diags = ArrayList<Diagnostic>()
    private var stmtName: String? = null

    private data class Ctx(
        val locals: Set<String> = emptySet(),
        val marksOf: String? = null,
        val tokenSlot: Boolean = false,
        val eventVars: Set<String> = emptySet(),
    )

    // ---- statements ----------------------------------------------------------------------------

    fun checkStmt(s: Stmt, name: String?): Stmt? {
        stmtName = name
        return when (s) {
            is Binding -> {
                if (s.name in RESERVED) { diags += Diag.reserved(s.span, s.name, name); return null }
                s.copy(expr = expr(s.expr, Ctx(), null))
            }
            is StateDecl -> s.copy(init = expr(s.init, Ctx(), null))
            is FnDecl -> {
                if (s.name in RESERVED) { diags += Diag.reserved(s.span, s.name, name); return null }
                val seen = HashSet<String>()
                for (p in s.params) {
                    if (p in RESERVED && p !in PARAM_OK) { diags += Diag.reserved(s.span, p, name); return null }
                    if (p != "_" && !seen.add(p)) { diags += Diag.badName(s.span, p, name); return null }
                }
                if (s.js != null && catalog.tier < 4) { diags += Diag.tier(s.span, "js fn", name) }
                if (full.function(s.name) != null) diags += Diag.duplicate(s.span, s.name, name)
                s.copy(body = s.body?.let { expr(it, Ctx(locals = s.params.toSet())) })
            }
            is Effect -> effect(s)
            else -> s
        }
    }

    private fun effect(s: Effect): Effect? {
        val t = s.trigger
        val spec = catalog.trigger(t.callee)
        if (spec == null) {
            if (full.trigger(t.callee) != null) diags += Diag.tier(t.span, "on ${t.callee}", stmtName)
            else diags += Diag.unknownAction(t.span, t.callee, closest(t.callee, full.triggers.map { it.name }), stmtName)
            return null
        }
        var trigger = t
        if (t.callee == "every") {
            val iv = t.leads.firstOrNull() ?: t.named.firstOrNull { it.first == "interval" }?.second
            if (iv is Lit && iv.value is WDur && (iv.value as WDur).ms < 250) {
                diags += Diag.tokenSnapped(iv.span, "every", "interval", "250ms", com.echoflow.weave.core.parse.CanonicalPrinter.duration((iv.value as WDur).ms), stmtName)
                trigger = t.copy(leads = listOf(Lit(WDur(250), iv.span)), named = emptyList())
            }
        }
        if (t.callee == "change") {
            val x = t.leads.firstOrNull()
            if (x == null) { diags += Diag.missingRequired(t.span, "change", "x", stmtName); return null }
            if (x is StateRef && x.name !in states) diags += Diag.missingRef(x.span, "$" + x.name, stmtName)
            if (x is Name && x.name !in bindings) diags += Diag.missingRef(x.span, x.name, stmtName)
        }
        val ev = when (t.callee) {
            "frame" -> setOf("dt"); "tilt" -> setOf("x", "y"); "change" -> setOf("prev"); else -> emptySet()
        }
        val ctx = Ctx(eventVars = ev)
        return s.copy(trigger = trigger, condition = s.condition?.let { expr(it, ctx) }, action = expr(s.action, ctx))
    }

    // ---- expressions ---------------------------------------------------------------------------

    private fun expr(e: Expr, ctx: Ctx, slot: TypeSpec? = null): Expr = when (e) {
        is Lit -> e
        is Str -> e.copy(parts = e.parts.map { p ->
            if (p is InterpPart) {
                for (f in p.formats) if (f.name !in Tokens.FORMATS) {
                    diags += Diag.unknownFunction(e.span, f.name, closest(f.name, Tokens.FORMATS), stmtName)
                }
                p.copy(expr = expr(p.expr, ctx), formats = p.formats.filter { it.name in Tokens.FORMATS })
            } else p
        })
        is Name -> name(e, ctx)
        is StateRef -> {
            if (e.name !in states) diags += Diag.missingRef(e.span, "$" + e.name, stmtName)
            e
        }
        is Qualified -> e
        is Member -> e.copy(target = if (e.target is Name && (e.target.name in CONSTANTS)) e.target else expr(e.target, ctx.copy(tokenSlot = false)))
        is Index -> lowercaseComponent(e, ctx) ?: e.copy(target = expr(e.target, ctx.copy(tokenSlot = false)), index = expr(e.index, ctx.copy(tokenSlot = false)))
        is Unary -> e.copy(operand = expr(e.operand, ctx.copy(tokenSlot = false)))
        is BinaryOp -> if (e.op == "??") e.copy(left = expr(e.left, ctx, slot), right = expr(e.right, ctx, slot))
        else e.copy(left = expr(e.left, ctx.copy(tokenSlot = false)), right = expr(e.right, ctx.copy(tokenSlot = false)))
        is Ternary -> e.copy(cond = expr(e.cond, ctx.copy(tokenSlot = false)), then = expr(e.then, ctx, slot), otherwise = expr(e.otherwise, ctx, slot))
        is ListLit -> e.copy(items = e.items.map { expr(it, ctx, slot) })
        is RecLit -> e.copy(fields = e.fields.map { (k, v) -> k to expr(v, ctx.copy(tokenSlot = false, marksOf = null)) })
        is Lambda -> {
            for (p in e.params) if (p in RESERVED && p !in PARAM_OK) diags += Diag.reserved(e.span, p, stmtName)
            e.copy(body = expr(e.body, ctx.copy(locals = ctx.locals + e.params, tokenSlot = false, marksOf = null)))
        }
        is HexLit -> {
            diags += Diag.badType(e.span, stmtName ?: "value", "value", "a colour role", e.hex, stmtName)
            Lit(WNull, e.span)
        }
        is Call -> call(e, ctx)
    }

    /** S7 for `card [a, b]`: an undeclared name whose capitalised form is a component. */
    private fun lowercaseComponent(e: Index, ctx: Ctx): Expr? {
        val n = (e.target as? Name)?.name ?: return null
        if (n in ctx.locals || n in ctx.eventVars || n in bindings || n in functions || n in CONSTANTS) return null
        val comp = full.components.firstOrNull { it.name.equals(n, ignoreCase = true) } ?: return null
        diags += Diag.autofix(e.span, "\"$n\" written as \"${comp.name}\"", stmtName)
        val kids = (e.index as? ListLit)?.items ?: listOf(e.index)
        return call(Call(CallKind.COMPONENT, comp.name, emptyList(), emptyList(), kids, e.span), ctx)
    }

    private fun name(e: Name, ctx: Ctx): Expr {
        val n = e.name
        if (n in ctx.locals || n in ctx.eventVars || n in bindings || n in functions || n in CONSTANTS) return e
        if (n in deleted) { diags += Diag.missingRef(e.span, n, stmtName); return Lit(WNull, e.span) }
        if (full.function(n) != null) return e // a built-in used as a value (`map(xs, abs)`)
        if (!ctx.tokenSlot) diags += Diag.unresolved(e.span, n, stmtName)
        return e
    }

    private fun isPlace(e: Expr): Boolean = when (e) {
        is StateRef -> true
        is Member -> isPlace(e.target)
        is Index -> isPlace(e.target)
        else -> false
    }

    private fun call(c0: Call, ctx: Ctx): Expr {
        var c = c0
        // S6: if(c, a, b) → c ? a : b
        if (c.kind == CallKind.FUNCTION && c.callee == "if" && "if" !in functions && "if" !in ctx.locals && c.named.isEmpty() && c.leads.size in 2..3) {
            diags += Diag.autofix(c.span, "if(…) written as \"… ? … : …\"", stmtName)
            return expr(Ternary(c.leads[0], c.leads[1], c.leads.getOrElse(2) { Lit(WNull, c.span) }, c.span), ctx)
        }
        if (c.kind == CallKind.FUNCTION) {
            val n = c.callee
            val isLocalFn = n in ctx.locals || n in functions || n in ctx.eventVars
            if (!isLocalFn) {
                // marks inside Chart/Canvas children
                if (ctx.marksOf != null && full.mark(ctx.marksOf, n) != null) return mark(c, ctx.marksOf, ctx)
                if (full.function(n) == null) {
                    // S7: card(...) → Card(...)
                    val cap = n.replaceFirstChar { it.uppercaseChar() }
                    val comp = full.components.firstOrNull { it.name.equals(cap, ignoreCase = true) }
                    if (comp != null) {
                        diags += Diag.autofix(c.span, "\"$n\" written as \"${comp.name}\"", stmtName)
                        c = c.copy(kind = CallKind.COMPONENT, callee = comp.name)
                    }
                }
            }
            if (c.kind == CallKind.FUNCTION) return function(c, ctx, isLocalFn)
        }
        return when (c.kind) {
            CallKind.ACTION -> action(c, ctx)
            CallKind.COMPONENT -> component(c, ctx)
            else -> c
        }
    }

    private fun function(c: Call, ctx: Ctx, local: Boolean): Expr {
        val argCtx = ctx.copy(tokenSlot = false, marksOf = null)
        if (local) return c.copy(leads = c.leads.map { expr(it, argCtx) }, named = c.named.map { (k, v) -> k to expr(v, argCtx) })
        val spec = catalog.function(c.callee)
        if (spec == null && streaming && full.function(c.callee) == null) {
            return c.copy(leads = c.leads.map { expr(it, argCtx) }, named = c.named.map { (k, v) -> k to expr(v, argCtx) })
        }
        if (spec == null) {
            if (full.function(c.callee) != null) diags += Diag.tier(c.span, c.callee, stmtName)
            else diags += Diag.unknownFunction(c.span, c.callee, closest(c.callee, catalog.functions.map { it.name } + functions), stmtName)
            return Lit(WNull, c.span)
        }
        // token-producing helpers keep bare words as tokens (`col(..., align: end, format: inr)`)
        val tokenArgs = c.callee in TOKEN_HELPERS
        val named = ArrayList<Pair<String, Expr>>()
        for ((k, v) in c.named) {
            if (spec.params.none { it.name == k }) { diags += Diag.unknownArg(v.span, c.callee, k, stmtName); continue }
            named += k to expr(v, argCtx.copy(tokenSlot = tokenArgs))
        }
        return c.copy(leads = c.leads.map { expr(it, argCtx.copy(tokenSlot = tokenArgs)) }, named = named)
    }

    private fun action(c: Call, ctx: Ctx): Expr {
        val spec = catalog.action(c.callee)
        if (spec == null) {
            if (full.action(c.callee) != null) diags += Diag.tier(c.span, "!" + c.callee, stmtName)
            else diags += Diag.unknownAction(c.span, c.callee, closest(c.callee, catalog.actions.map { it.name }), stmtName)
            return Lit(WNull, c.span)
        }
        val label = "!" + c.callee
        val (leads, named) = arrange(c, label, spec.leads, spec.positionalOrder, emptyMap(), variadic = spec.variadic)
        val outLeads = leads.mapIndexed { i, e ->
            val p = spec.leads.getOrElse(i) { spec.leads.lastOrNull() }
            when {
                spec.nameLeads && e is Name -> e
                p?.type == TypeSpec.STATE -> {
                    if (!isPlace(e)) diags += Diag.badType(e.span, label, p.name, "a state like \$x", typeWord(e), stmtName)
                    expr(e, ctx.copy(tokenSlot = false))
                }
                else -> slotValue(e, p, label, ctx) ?: Lit(WNull, e.span)
            }
        }
        val outNamed = named.mapNotNull { (k, v) ->
            val p = spec.param(k)
            if (p == null) { diags += Diag.unknownArg(v.span, label, k, stmtName); null }
            else slotValue(v, p, label, ctx)?.let { k to it }
        }
        for ((i, p) in spec.leads.withIndex()) {
            if (p.required && i >= outLeads.size && outNamed.none { it.first == p.name }) {
                diags += Diag.missingRequired(c.span, label, p.name, stmtName)
                return Lit(WNull, c.span)
            }
        }
        return c.copy(leads = outLeads, named = outNamed)
    }

    private fun mark(c: Call, container: String, ctx: Ctx): Expr {
        val spec = full.mark(container, c.callee)!!
        if (catalog.mark(container, c.callee) == null) {
            diags += Diag.tier(c.span, c.callee, stmtName)
            return Lit(WNull, c.span)
        }
        val leads = ArrayList<Expr>()
        for ((i, e) in c.leads.withIndex()) {
            if (i < spec.leads.size) leads += e
            else diags += Diag.tooManyLeads(e.span, c.callee, stmtName)
        }
        val named = ArrayList<Pair<String, Expr>>()
        for ((k, v) in c.named) {
            val p = spec.param(k)
            if (p == null) { diags += Diag.unknownArg(v.span, c.callee, k, stmtName); continue }
            named.removeAll { it.first == k }
            named += k to (slotValue(v, p, c.callee, ctx) ?: continue)
        }
        for ((i, p) in spec.leads.withIndex()) if (i >= leads.size && named.none { it.first == p.name }) {
            diags += Diag.missingRequired(c.span, c.callee, p.name, stmtName)
            return Lit(WNull, c.span)
        }
        return c.copy(
            kind = CallKind.MARK,
            leads = leads.mapIndexed { i, e -> slotValue(e, spec.leads[i], c.callee, ctx) ?: Lit(WNull, e.span) },
            named = named,
            children = c.children?.map { expr(it, ctx) },
        )
    }

    private fun component(c0: Call, ctx: Ctx): Expr {
        var c = c0
        // S7: case-insensitive match (`LINECHART`)
        if (full.component(c.callee) == null) {
            val m = full.components.firstOrNull { it.name.equals(c.callee, ignoreCase = true) }
            if (m != null) {
                diags += Diag.autofix(c.span, "\"${c.callee}\" written as \"${m.name}\"", stmtName)
                c = c.copy(callee = m.name)
            }
        }
        val spec = catalog.component(c.callee)
        if (spec == null) {
            if (full.component(c.callee) != null) diags += Diag.tier(c.span, c.callee, stmtName)
            else diags += Diag.unknownComponent(c.span, c.callee, closest(c.callee, catalog.components.map { it.name }), stmtName)
            val kids = c.children ?: c.named.firstOrNull { it.first == "children" }?.second?.let { (it as? ListLit)?.items ?: listOf(it) }
            return if (!kids.isNullOrEmpty() && catalog.component("Column") != null) expr(Call(CallKind.COMPONENT, "Column", emptyList(), emptyList(), kids, c.span), ctx)
            else Lit(WNull, c.span)
        }
        // S10: children passed as a named argument
        c.named.firstOrNull { it.first == "children" }?.let { (_, v) ->
            if (spec.children != ChildrenSpec.None) {
                diags += Diag.autofix(v.span, "\"children:\" written as a children list", stmtName)
                val items = (v as? ListLit)?.items ?: listOf(v)
                c = c.copy(named = c.named.filter { it.first != "children" }, children = (c.children ?: emptyList()) + items)
            }
        }
        val (leads, named) = arrange(c, c.callee, spec.leads, spec.positionalOrder, spec.aliases)
        val outLeads = leads.mapIndexed { i, e ->
            val p = spec.leads[i]
            if (p.type == TypeSpec.STATE) {
                if (!isPlace(e)) diags += Diag.bindNotState(e.span, c.callee, stmtName)
                expr(e, ctx.copy(tokenSlot = false, marksOf = null))
            } else slotValue(e, p, c.callee, ctx) ?: Lit(WNull, e.span)
        }
        val outNamed = ArrayList<Pair<String, Expr>>()
        for ((k, v) in named) {
            val p = spec.param(k) ?: BuiltinCatalog.COMMON.firstOrNull { it.name == k }
            if (p == null) { diags += Diag.unknownArg(v.span, c.callee, k, stmtName); continue }
            if (p.type == TypeSpec.STATE && !isPlace(v)) {
                diags += Diag.badType(v.span, c.callee, k, "a state place", typeWord(v), stmtName)
                continue
            }
            val value = slotValue(v, p, c.callee, ctx) ?: continue
            outNamed += k to value
        }
        for ((i, p) in spec.leads.withIndex()) if (p.required && i >= outLeads.size && outNamed.none { it.first == p.name }) {
            diags += Diag.missingRequired(c.span, c.callee, p.name, stmtName)
        }
        for (p in spec.params) if (p.required && outNamed.none { it.first == p.name }) {
            diags += Diag.missingRequired(c.span, c.callee, p.name, stmtName)
        }
        var children = c.children
        if (children != null) {
            children = when (val cs = spec.children) {
                ChildrenSpec.None -> { diags += Diag.childrenNotAllowed(c.span, c.callee, stmtName); null }
                is ChildrenSpec.Marks -> children.map { expr(it, ctx.copy(marksOf = cs.container, tokenSlot = false)) }
                else -> children.map { expr(it, ctx.copy(marksOf = null, tokenSlot = false)) }
            }
        }
        return c.copy(leads = outLeads, named = outNamed, children = children)
    }

    /**
     * Lead/named arrangement: E108 already applied by the parser; S2 maps extra positionals by
     * the positional order, E109 drops the rest; S3 renames aliases; duplicates keep the last (W404).
     */
    private fun arrange(
        c: Call, label: String, leadSpecs: List<ParamSpec>, positional: List<String>, aliases: Map<String, String>,
        variadic: Boolean = false,
    ): Pair<List<Expr>, List<Pair<String, Expr>>> {
        val leads = ArrayList<Expr>()
        val named = ArrayList<Pair<String, Expr>>()
        val extra = ArrayList<Expr>()
        for ((i, e) in c.leads.withIndex()) if (i < leadSpecs.size || variadic) leads += e else extra += e
        // S3 aliases first so positional mapping sees canonical names
        val explicit = c.named.map { (k, v) ->
            val canon = aliases[k]
            if (canon != null) {
                diags += Diag.autofix(v.span, "\"$k\" written as \"$canon\"", stmtName)
                canon to (if (c.callee == "Text" && k == "size" && v is Name && v.name in setOf("s", "m", "l"))
                    Name(mapOf("s" to "bodySmall", "m" to "bodyMedium", "l" to "bodyLarge").getValue(v.name), v.span) else v)
            } else k to v
        }
        if (extra.isNotEmpty()) {
            val free = positional.filter { p -> explicit.none { it.first == p } && leadSpecs.none { l -> l.name == p } }
            if (free.isNotEmpty()) diags += Diag.autofix(c.span, "unnamed arguments of $label named", stmtName)
            for ((i, e) in extra.withIndex()) {
                val p = free.getOrNull(i)
                if (p != null) named += p to e else diags += Diag.tooManyLeads(e.span, label, stmtName)
            }
        }
        for ((k, v) in explicit) {
            val leadIx = leadSpecs.indexOfFirst { it.name == k }
            if (leadIx >= 0) {
                if (leadIx < leads.size) { diags += Diag.duplicate(v.span, k, stmtName); leads[leadIx] = v }
                else if (leadIx == leads.size) leads += v
                else named += k to v
                continue
            }
            if (named.any { it.first == k }) { diags += Diag.duplicate(v.span, k, stmtName); named.removeAll { it.first == k } }
            named += k to v
        }
        return leads to named
    }

    /** Checks one argument value against its slot (S4, S5, S8, E206). Null = argument dropped. */
    private fun slotValue(e: Expr, p: ParamSpec?, callee: String, ctx: Ctx): Expr? {
        if (p == null) return expr(e, ctx.copy(tokenSlot = false))
        val t = p.type
        // format slots take format names as bare words (`format: inr`, 06 §3.8)
        val tokenSlot = t.isTokenSlot() || p.name in FORMAT_PARAMS
        // S5: hex colour in a colour slot
        if (e is HexLit) {
            if (t.isColorSlot()) {
                val role = AutoFix.hexToRole(e.hex, textSlot = callee == "Text" && p.name == "color")
                diags += Diag.autofix(e.span, "\"${e.hex}\" written as \"$role\"", stmtName)
                return Name(role, e.span)
            }
            diags += Diag.badType(e.span, callee, p.name, t.display, "a hex colour", stmtName)
            return null
        }
        // S13: a format written as a call in a format slot (`format: dec(1)`) is that format's text
        if (p.name in FORMAT_PARAMS && e is Call && e.kind == CallKind.FUNCTION && e.callee in Tokens.FORMATS &&
            e.callee !in functions && e.named.isEmpty() && e.leads.all { it is Lit || it is Name || it is Str }
        ) {
            val text = com.echoflow.weave.core.program.WeaveProgram.printer().expr(e)
            diags += Diag.autofix(e.span, "format $text written as \"$text\"", stmtName)
            return Lit(WStr(text), e.span)
        }
        val set = tokenSet(t)
        // S4: a number in a spacing/height/shape slot snaps to the nearest token
        if (e is Lit && e.value is WNum && set != null && Tokens.dpTable(set) != null && !(t is TypeSpec.Union && t.options.contains(TypeSpec.NUM))) {
            val v = (e.value as WNum).v
            val tok = Tokens.snap(set, v)!!
            if (Tokens.dpTable(set)!![tok]!!.toDouble() != v) diags += Diag.tokenSnapped(e.span, callee, p.name, tok, com.echoflow.weave.core.parse.CanonicalPrinter.number(v), stmtName)
            return Name(tok, e.span)
        }
        // S8: a string literal whose text is a token
        if (e is Str && set != null && set != TokenSet.ICON && e.parts.size <= 1 && e.parts.all { it is TextPart }) {
            val text = (e.parts.firstOrNull() as? TextPart)?.text ?: ""
            if (isMember(t, text)) {
                diags += Diag.autofix(e.span, "\"$text\" written as the token $text", stmtName)
                return Name(text, e.span)
            }
        }
        if (e is Lit && e.value is WStr && set != null && set != TokenSet.ICON && isMember(t, (e.value as WStr).v)) {
            return Name((e.value as WStr).v, e.span)
        }
        // E206: a bare word that is not a member of the token set
        if (t.isTokenSlot() && e is Name && e.name !in bindings && e.name !in ctx.locals && e.name !in ctx.eventVars && e.name !in CONSTANTS && !isMember(t, e.name)) {
            if (!(t is TypeSpec.Union && t.options.any { !it.isTokenSlot() && it != TypeSpec.NUM && it != TypeSpec.BOOL })) {
                diags += Diag.badToken(e.span, callee, p.name, e.name, allowedText(t), stmtName)
                return null
            }
        }
        val childCtx = ctx.copy(tokenSlot = tokenSlot, marksOf = null)
        return expr(e, childCtx, t)
    }

    private fun tokenSet(t: TypeSpec): TokenSet? = when (t) {
        is TypeSpec.Tok -> t.set
        is TypeSpec.Union -> t.options.firstNotNullOfOrNull { (it as? TypeSpec.Tok)?.set }
        else -> null
    }

    private fun isMember(t: TypeSpec, w: String): Boolean = when (t) {
        is TypeSpec.Tok -> Tokens.isMember(t.set, w)
        is TypeSpec.Enum -> w in t.values
        is TypeSpec.Union -> t.options.any { isMember(it, w) }
        else -> false
    }

    private fun allowedText(t: TypeSpec): String = when (t) {
        is TypeSpec.Tok -> Tokens.members(t.set).take(12).joinToString(", ")
        is TypeSpec.Enum -> t.values.joinToString(", ")
        is TypeSpec.Union -> t.options.joinToString(", ") { allowedText(it) }.trim(',', ' ')
        else -> t.display
    }

    private fun typeWord(e: Expr) = when (e) {
        is Lit -> e.value.typeName
        is Str -> "str"
        is Name -> e.name
        is Call -> e.callee
        else -> "an expression"
    }

    companion object {
        /** Reserved identifiers, 02 §2.3. */
        val RESERVED = setOf("dt", "device", "block", "user", "item", "index")
        val CONSTANTS = setOf("pi", "e", "device", "block", "user")
        /** Reserved names a `fn` or lambda may still use as a parameter (02 §2.3, spec 1.3). */
        val PARAM_OK = setOf("item", "index")
        val FORMAT_PARAMS = setOf("format", "deltaFormat")
        val TOKEN_HELPERS = setOf("col", "axis", "aspect", "weight", "spring", "tween", "loop", "fmt")
    }
}

