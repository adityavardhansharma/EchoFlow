package com.echoflow.weave.core.eval

import com.echoflow.weave.core.Limits
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.catalog.ParamSpec
import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.check.TypeSpec
import com.echoflow.weave.core.parse.BinaryOp
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.CallKind
import com.echoflow.weave.core.parse.Expr
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
import com.echoflow.weave.core.parse.StateRef
import com.echoflow.weave.core.parse.Str
import com.echoflow.weave.core.parse.Ternary
import com.echoflow.weave.core.parse.TextPart
import com.echoflow.weave.core.parse.Unary
import com.echoflow.weave.core.program.WeaveProgram
import java.time.LocalDate

/** Context values, 03 §4.3. Everything that changes over time is explicit here (golden rule 2). */
class EvalContext(
    val formatter: Formatter = Formatter(),
    val deviceWidth: String = "compact",
    val landscape: Boolean = false,
    val dark: Boolean = false,
    val reducedMotion: Boolean = false,
    val fontScale: Double = 1.0,
    val blockExpanded: Boolean = false,
    val blockVisible: Boolean = true,
    val userFirstName: String? = null,
    /** Reads `id::name` from earlier blocks' exports; null = not found. */
    val exports: (block: String, name: String) -> WValue? = { _, _ -> null },
    /**
     * Animation time in ms while the block is visible and motion is allowed, else null. `loop`
     * reads it (04 §13.1); with null (tests, reduced motion, off-screen) `loop` returns `from`.
     */
    val animationTimeMs: () -> Long? = { null },
    /** Binding names with a hidden request in flight (`pending(name)`, 03 §9.2). */
    val pendingNames: () -> Set<String> = { emptySet() },
    /**
     * The current value of `query(tool, args, every)` declared in statement [stmt] (03 §13.1), as
     * the record `{value, loading, error, at}`; null when the host runs no queries.
     */
    val queryValue: (stmt: String, tool: String, args: WValue, everyMs: Long?) -> WValue? = { _, _, _, _ -> null },
) {
    fun device() = WRec.of(
        "width" to WSym(deviceWidth), "landscape" to WBool.of(landscape), "dark" to WBool.of(dark),
        "locale" to WStr(formatter.locale.toLanguageTag()), "currency" to WStr(formatter.currency),
        "reducedMotion" to WBool.of(reducedMotion), "fontScale" to WNum.of(fontScale),
    )

    fun block(id: String, version: Int) = WRec.of(
        "expanded" to WBool.of(blockExpanded), "visible" to WBool.of(blockVisible), "id" to WStr(id), "version" to WNum.of(version),
    )

    fun user() = WRec.of("firstName" to (userFirstName?.let { WStr(it) } ?: WNull))
    fun today(): LocalDate = java.time.LocalDateTime.ofInstant(formatter.now(), formatter.zone).toLocalDate()
}

internal class BudgetExceeded : RuntimeException(null, null, false, false)
internal class LimitExceeded(val what: String) : RuntimeException(null, null, false, false)
internal class CycleSignal(val start: String) : RuntimeException(null, null, false, false)

/**
 * The tree-walking evaluator (03 §4–6). One instance evaluates one snapshot of state: bindings
 * are memoised until a new evaluator is made for the next snapshot. Never throws (golden rule 1).
 */
class Evaluator(
    val program: WeaveProgram,
    /** Current state values. A declared state missing from this map is **waiting** (03 §7.1). */
    val states: Map<String, WValue>,
    val ctx: EvalContext = EvalContext(),
    val catalog: Catalog = BuiltinCatalog,
    val blockId: String = program.header.id ?: "b1",
) {
    val f get() = ctx.formatter
    val coerce = Coercion(ctx.formatter)
    val diagnostics = ArrayList<Diagnostic>()
    private val reported = HashSet<String>()

    /** The Chart/Canvas whose children are being evaluated, so a mark takes that container's signature. */
    internal var markContainer: String? = null

    private val cache = HashMap<String, WValue>()
    private val waitingCache = HashSet<String>()
    private val inProgress = ArrayList<String>()
    private val cycleMembers = HashSet<String>()

    private var steps = 0
    /** Steps already spent by earlier items of the same removal, added back after each one. */
    private var carried = 0
    private var depth = 0
    /** Set while evaluating when a waiting state (or a binding that read one) was read. */
    var readWaiting = false
        private set

    fun report(d: Diagnostic) {
        if (reported.add(d.code + d.message)) diagnostics += d
    }

    // ---- top level ------------------------------------------------------------------------------

    /** The value of binding [name] (memoised). Unknown → null. */
    fun binding(name: String): WValue {
        cache[name]?.let { if (name in waitingCache) readWaiting = true; return it }
        val b = program.bindings[name] ?: return WNull
        if (name in inProgress) {
            val cyc = inProgress.subList(inProgress.indexOf(name), inProgress.size).toList()
            cycleMembers += cyc
            val order = program.bindings.keys.filter { it in cyc }
            report(Diag.cycle(b.span, order.joinToString(", "), name))
            throw CycleSignal(name)
        }
        val savedSteps = steps
        val savedDepth = depth
        val savedWaiting = readWaiting
        readWaiting = false
        inProgress += name
        steps = 0
        var v: WValue
        try {
            v = eval(b.expr, Scope.EMPTY)
            if (v is WNode) v = rekey(v, name, name)
        } catch (c: CycleSignal) {
            inProgress.remove(name)
            cache[name] = WNull
            steps = savedSteps; depth = savedDepth
            if (c.start != name) throw c
            readWaiting = savedWaiting
            return WNull
        } catch (e: BudgetExceeded) {
            report(Diag.budget(b.span, name, name)); v = WNull
        } catch (e: LimitExceeded) {
            report(Diag.limit(b.span, e.what, name)); v = WNull
        } catch (e: StackOverflowError) {
            report(Diag.budget(b.span, name, name)); v = WNull
        } catch (e: RuntimeException) {
            // Golden rule 1 (03): nothing a block does may throw out of the evaluator. A JDK
            // failure on an extreme argument (date overflow, arithmetic) is just a null value.
            v = WNull
        }
        inProgress.remove(name)
        steps = savedSteps
        depth = savedDepth
        if (name in cycleMembers) v = WNull
        cache[name] = v
        if (readWaiting) waitingCache += name
        readWaiting = readWaiting || savedWaiting
        return v
    }

    /**
     * Calls a handler or predicate from outside an evaluation (event handlers, `onChange`,
     * `removeWhere`): budget, limit, recursion and runtime failures become null (03 golden rule 1).
     */
    fun safeCall(fn: WFn, args: List<WValue>): WValue {
        val saved = steps
        steps = 0
        return try { callFn(fn, args) }
        catch (x: BudgetExceeded) { report(Diag.budget(fn.origin ?: Span.NONE, fn.name ?: "fn", fn.name ?: "fn")); WNull }
        catch (x: LimitExceeded) { report(Diag.limit(fn.origin ?: Span.NONE, x.what, fn.name ?: "fn")); WNull }
        catch (x: CycleSignal) { WNull }
        catch (x: StackOverflowError) { report(Diag.budget(fn.origin ?: Span.NONE, fn.name ?: "fn", fn.name ?: "fn")); WNull }
        catch (x: RuntimeException) { WNull }
        finally { steps = saved }
    }

    fun safeCallItem(fn: WValue, item: WValue, index: Int): WValue {
        // One budget for the whole removal: each item starts from what earlier items already spent.
        steps = carried
        return try { callItem(fn, item, index) } catch (x: BudgetExceeded) { throw x } catch (x: LimitExceeded) { throw x } catch (x: StackOverflowError) { WNull } catch (x: RuntimeException) { WNull }
        finally { carried = steps }
    }

    /** Evaluates one top-level expression (action argument, effect condition, state initial value). */
    fun topLevel(e: Expr, scope: Scope = Scope.EMPTY, name: String = "value"): WValue {
        val saved = steps
        steps = 0
        return try {
            eval(e, scope)
        } catch (x: BudgetExceeded) {
            report(Diag.budget(e.span, name, name)); WNull
        } catch (x: LimitExceeded) {
            report(Diag.limit(e.span, x.what, name)); WNull
        } catch (x: CycleSignal) {
            WNull
        } catch (x: StackOverflowError) {
            report(Diag.budget(e.span, name, name)); WNull
        } catch (x: RuntimeException) {
            WNull
        } finally {
            steps = saved
        }
    }

    /** The root node, or null when there is none or it doesn't evaluate to a node. */
    fun root(): WNode? = program.root?.let { binding(it) as? WNode }

    fun charge(n: Int = 1) {
        steps += n
        if (steps > Limits.EVAL_STEPS) throw BudgetExceeded()
    }

    // ---- expressions ----------------------------------------------------------------------------

    fun eval(e: Expr, scope: Scope): WValue {
        charge()
        return when (e) {
            is Lit -> e.value
            is Str -> WStr(interpolate(e, scope))
            is Name -> name(e.name, scope)
            is StateRef -> state(e.name)
            is Qualified -> ctx.exports(e.block, e.name) ?: WNull
            is Member -> member(eval(e.target, scope), e.name)
            is Index -> Places.access(eval(e.target, scope), eval(e.index, scope)) { charge() }
            is Unary -> unary(e.op, eval(e.operand, scope))
            is BinaryOp -> binary(e, scope)
            is Ternary -> if (eval(e.cond, scope).truthy()) eval(e.then, scope) else eval(e.otherwise, scope)
            is ListLit -> WList(e.items.map { eval(it, scope) })
            is RecLit -> WRec(LinkedHashMap<String, WValue>().apply { for ((k, v) in e.fields) put(k, eval(v, scope)) })
            is Lambda -> WFn(e.params, e.body, scope, e.span)
            is HexLit -> WNull
            is Call -> call(e, scope)
        }
    }

    fun state(name: String): WValue {
        if (name !in program.states) return WNull
        val v = states[name]
        if (v == null) { readWaiting = true; return WNull }
        return v
    }

    private fun name(n: String, scope: Scope): WValue {
        scope.lookup(n)?.let { return it }
        if (n in program.bindings) return binding(n)
        program.functions[n]?.let { fd -> return WFn(fd.params, fd.body, null, fd.span, name = fd.name) }
        return when (n) {
            "pi" -> WNum(Math.PI)
            "e" -> WNum(Math.E)
            "device" -> ctx.device()
            "block" -> ctx.block(blockId, program.version)
            "user" -> ctx.user()
            else -> if (catalog.full.function(n) != null) WFn(emptyList(), null, null, null, builtin = n) else WSym(n)
        }
    }

    fun member(v: WValue, k: String): WValue = when {
        k == "len" && (v is WStr || v is WList || v is WRec) -> Builtins.len(v)
        v is WRec -> v[k]
        // each visited item costs a step, so shared nested lists can't expand without bound
        v is WList -> WList(v.items.map { charge(); member(it, k) })
        v is WNode -> v.args[k] ?: WNull
        else -> WNull
    }

    private fun interpolate(s: Str, scope: Scope): String {
        val sb = StringBuilder()
        for (p in s.parts) when (p) {
            is TextPart -> sb.append(p.text)
            is InterpPart -> {
                var v = eval(p.expr, scope)
                var text = f.auto(v)
                for (fs in p.formats) { text = f.apply(v, fs); v = WStr(text) }
                sb.append(text)
            }
        }
        if (sb.length > 1000) charge((sb.length - 1000) / 100)
        return sb.toString()
    }

    // ---- operators, 03 §6 -----------------------------------------------------------------------

    private fun unary(op: String, v: WValue): WValue = when (op) {
        "not" -> WBool.of(!v.truthy())
        else -> when (v) { is WNum -> WNum.of(-v.v); is WDur -> WDur(-v.ms); else -> WNull }
    }

    private fun binary(e: BinaryOp, scope: Scope): WValue {
        when (e.op) {
            "and" -> { val a = eval(e.left, scope); return if (!a.truthy()) a else eval(e.right, scope) }
            "or" -> { val a = eval(e.left, scope); return if (a.truthy()) a else eval(e.right, scope) }
            "??" -> { val a = eval(e.left, scope); return if (a != WNull) a else eval(e.right, scope) }
        }
        val a = eval(e.left, scope)
        val b = eval(e.right, scope)
        return Ops.binary(e.op, a, b, f) { what -> throw LimitExceeded(what) }
    }

    // ---- calls ----------------------------------------------------------------------------------

    private fun call(c: Call, scope: Scope): WValue = when (c.kind) {
        CallKind.ACTION -> WAction(c.callee, c.leads, c.named, scope, c.span)
        CallKind.COMPONENT -> NodeBuilder(this).component(c, scope)
        CallKind.MARK -> NodeBuilder(this).mark(c, scope)
        CallKind.FUNCTION -> function(c, scope)
    }

    private fun function(c: Call, scope: Scope): WValue {
        // locals (lambda/fn params, event vars) first, then user fns, then built-ins (02 §6.1)
        scope.lookup(c.callee)?.let { v ->
            return if (v is WFn) callFn(v, c.leads.map { eval(it, scope) }) else { report(Diag.notCallable(c.span, c.callee)); WNull }
        }
        program.functions[c.callee]?.let { fd ->
            if (fd.body == null) return WNull // js fn: tier 4 only
            return callFn(WFn(fd.params, fd.body, null, fd.span, name = fd.name), c.leads.map { eval(it, scope) })
        }
        if (c.callee in program.bindings) {
            val v = binding(c.callee)
            return if (v is WFn) callFn(v, c.leads.map { eval(it, scope) }) else WNull
        }
        return when (c.callee) {
            // lazy built-ins, 03 §10.1
            "if" -> if (c.leads.isNotEmpty() && eval(c.leads[0], scope).truthy()) c.leads.getOrNull(1)?.let { eval(it, scope) } ?: WNull
            else c.leads.getOrNull(2)?.let { eval(it, scope) } ?: WNull
            "when" -> if (c.leads.isNotEmpty() && eval(c.leads[0], scope).truthy()) c.leads.getOrNull(1)?.let { eval(it, scope) } ?: WNull else WNull
            // `pending(name)` names a binding; it is not evaluated (03 §9.2)
            "pending" -> WBool.of(((c.leads.getOrNull(0) as? Name)?.name ?: (c.leads.getOrNull(0)?.let { eval(it, scope) } as? WStr)?.v)?.let { it in ctx.pendingNames() } == true)
            "lazy" -> {
                val prompt = c.leads.getOrNull(0)?.let { eval(it, scope) } ?: WNull
                val context = LinkedHashMap<String, WValue>()
                c.named.forEach { (k, v) -> context[k] = eval(v, scope) }
                WNode("Lazy", mapOf("prompt" to prompt, "context" to WRec(context), "name" to WStr(currentStmt())), emptyList(), "", currentStmt())
            }
            "query" -> {
                val tool = (c.leads.getOrNull(0)?.let { eval(it, scope) } as? WStr)?.v ?: ""
                val args = c.leads.getOrNull(1)?.let { eval(it, scope) } ?: c.named.firstOrNull { it.first == "args" }?.second?.let { eval(it, scope) } ?: WRec(LinkedHashMap())
                val every = (c.named.firstOrNull { it.first == "every" }?.second?.let { eval(it, scope) } as? WDur)?.ms
                ctx.queryValue(currentStmt(), tool, args, every)
                    ?: WRec.of("value" to WNull, "loading" to WBool.FALSE, "error" to WStr("Unavailable"), "at" to WNull)
            }
            "coalesce" -> { for (a in c.leads) { val v = eval(a, scope); if (v != WNull) return v }; WNull }
            "switch" -> {
                val v = c.leads.getOrNull(0)?.let { eval(it, scope) } ?: WNull
                val casesExpr = c.leads.getOrNull(1) ?: c.named.firstOrNull { it.first == "cases" }?.second
                val key = f.auto(v)
                val hit = (casesExpr as? RecLit)?.fields?.firstOrNull { it.first == key }?.second
                when {
                    hit != null -> eval(hit, scope)
                    casesExpr != null && casesExpr !is RecLit -> (eval(casesExpr, scope) as? WRec)?.fields?.get(key)
                        ?: (c.leads.getOrNull(2) ?: c.named.firstOrNull { it.first == "default" }?.second)?.let { eval(it, scope) } ?: WNull
                    else -> (c.leads.getOrNull(2) ?: c.named.firstOrNull { it.first == "default" }?.second)?.let { eval(it, scope) } ?: WNull
                }
            }
            else -> {
                // While streaming, an unknown callee is a `fn` that hasn't arrived: waiting (03 §7.1).
                val spec = catalog.full.function(c.callee) ?: run { if (!program.complete) readWaiting = true; return WNull }
                val args = c.leads.map { eval(it, scope) }
                val named = c.named.associate { (k, v) -> k to eval(v, scope) }
                Builtins.call(c.callee, bindArgs(spec.params, args, named, c.callee in BuiltinCatalog.VARIADIC), this, c.span)
            }
        }
    }

    /** Positional + named built-in arguments into one positional list (variadic: as given). */
    private fun bindArgs(params: List<ParamSpec>, args: List<WValue>, named: Map<String, WValue>, variadic: Boolean): List<WValue?> {
        if (variadic) return args
        val out = ArrayList<WValue?>()
        for ((i, p) in params.withIndex()) {
            out += when {
                i < args.size -> args[i]
                p.name in named -> named[p.name]
                else -> null
            }
        }
        for (i in params.size until args.size) out += args[i]
        return out
    }

    /** Calls a lambda, user `fn` or built-in value with already-evaluated arguments. */
    fun callFn(fn: WFn, args: List<WValue>): WValue {
        fn.builtin?.let { b ->
            val spec = catalog.full.function(b) ?: return WNull
            return Builtins.call(b, if (b in BuiltinCatalog.VARIADIC) args else args.take(maxOf(spec.params.size, args.size)), this, fn.origin ?: Span.NONE)
        }
        val body = fn.body ?: return WNull
        depth++
        if (depth > Limits.RECURSION) throw BudgetExceeded()
        try {
            val vars = HashMap<String, WValue>()
            for ((i, p) in fn.params.withIndex()) if (p != "_") vars[p] = args.getOrElse(i) { WNull }
            val parent = if (fn.name != null) null else fn.scope
            return eval(body, Scope(vars, parent, isFnBoundary = fn.name != null))
        } finally {
            depth--
        }
    }

    /** Calls a list helper's `fn` argument with (item) or (item, index) — index only if declared. */
    fun callItem(fn: WValue, item: WValue, index: Int): WValue = when (fn) {
        is WFn -> {
            charge()
            if (fn.builtin == null && fn.params.size >= 2) callFn(fn, listOf(item, WNum.of(index))) else callFn(fn, listOf(item))
        }
        is WStr -> member(item, fn.v)
        is WSym -> member(item, fn.name)
        WNull -> item
        else -> WNull
    }

    // ---- node keys, 04 §2.5 ---------------------------------------------------------------------

    private fun rekey(n: WNode, key: String, stmt: String): WNode {
        val kids = n.children.mapIndexed { i, ch ->
            if (ch.sourceStmt == stmt || ch.sourceStmt == "") {
                val seg = ch.args["key"]?.let { keyText(it) } ?: i.toString()
                rekey(ch, "$key/$seg", stmt)
            } else ch
        }
        return WNode(n.type, n.args, kids, key, stmt, n.places, n.waiting, n.error)
    }

    internal fun currentStmt(): String = inProgress.lastOrNull() ?: ""

    /** Whether anything evaluated so far read a waiting state. */
    val sawWaiting: Boolean get() = readWaiting || waitingCache.isNotEmpty()

    internal fun withWaitingTracking(block: () -> Unit): Boolean {
        val saved = readWaiting
        readWaiting = false
        block()
        val w = readWaiting
        readWaiting = saved || w
        return w
    }

    fun slotType(p: ParamSpec?): TypeSpec = p?.type ?: TypeSpec.ANY
}
