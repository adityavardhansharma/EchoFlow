package com.echoflow.weave.core.eval

import com.echoflow.weave.core.Limits
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.check.Diag
import com.echoflow.weave.core.check.Diagnostic
import com.echoflow.weave.core.parse.Binding
import com.echoflow.weave.core.parse.BinaryOp
import com.echoflow.weave.core.parse.Call
import com.echoflow.weave.core.parse.CallKind
import com.echoflow.weave.core.parse.Effect
import com.echoflow.weave.core.parse.Expr
import com.echoflow.weave.core.parse.Index
import com.echoflow.weave.core.parse.InterpPart
import com.echoflow.weave.core.parse.Lambda
import com.echoflow.weave.core.parse.ListLit
import com.echoflow.weave.core.parse.Member
import com.echoflow.weave.core.parse.Name
import com.echoflow.weave.core.parse.Qualified
import com.echoflow.weave.core.parse.RecLit
import com.echoflow.weave.core.parse.Span
import com.echoflow.weave.core.parse.StateRef
import com.echoflow.weave.core.parse.Str
import com.echoflow.weave.core.parse.Ternary
import com.echoflow.weave.core.parse.Unary
import com.echoflow.weave.core.program.EditResult
import com.echoflow.weave.core.program.WeaveProgram

/** Something a deferred or outbound action asked the host to do (recorded; 03 §7.4). */
data class HostEffect(val kind: String, val args: List<WValue>, val named: Map<String, WValue>)

/**
 * A pure, single-threaded block runtime for the JVM: the part of `weave-runtime` (05 §4) that
 * needs no Android — state, transactions, local actions, history, change/appear/every effects with
 * the 8-firing cap, and a virtual clock for debounce, `every` and `!after`. Deferred actions are
 * recorded in [outbox] for the host. Used by the conformance tests and the eval harness.
 */
class Session(
    program: WeaveProgram,
    val ctx: EvalContext = EvalContext(),
    val catalog: Catalog = BuiltinCatalog,
) {
    var program: WeaveProgram = program
        private set
    val state = LinkedHashMap<String, WValue>()
    val diagnostics = ArrayList<Diagnostic>()
    val outbox = ArrayList<HostEffect>()
    /** History entries: state maps before and after (03 §7.5). */
    private val undo = ArrayDeque<Pair<Map<String, WValue>, Map<String, WValue>>>()
    private val redo = ArrayDeque<Pair<Map<String, WValue>, Map<String, WValue>>>()
    val historySize get() = undo.size
    /** Gesture transactions discarded because they read a waiting state; the host gives the reject haptic. */
    var rejectedTaps = 0
        private set
    private var effectLoopReported = false
    private var appeared = false

    /** Virtual time in ms, advanced by [advance]. */
    var clock = 0L
        private set
    private data class Timer(val at: Long, val id: String, val run: () -> Unit, val repeat: Long = 0)
    private val timers = ArrayList<Timer>()

    init {
        sync(program)
    }

    /**
     * Takes a newer version of a streaming block (05 §4.2 `StateStore.sync`). While the block is
     * incomplete, a declared state is initialised only once everything its initial expression
     * reaches has arrived (03 §7.1); until then it is *waiting* (absent). At fence close every
     * remaining state is initialised and states no longer declared are dropped. During streaming
     * states are only ever added.
     */
    fun sync(next: WeaveProgram) {
        program = next
        if (next.complete) {
            for (n in state.keys.toList()) if (n !in next.states) state.remove(n)
            initStates(next.states.keys.toList())
            return
        }
        while (true) {
            val ready = next.states.keys.filter { it !in state && missingNames(next.states.getValue(it).init).isEmpty() }
            if (ready.isEmpty()) break
            initStates(ready)
            if (ready.none { it in state }) break
        }
    }

    /** States declared but not initialised yet (03 §7.1). */
    val waitingStates: Set<String> get() = program.states.keys - state.keys

    /**
     * Names an expression reaches that haven't arrived: undeclared bindings and functions, and
     * states that are undeclared or still waiting. Empty means the expression is ready.
     */
    fun missingNames(e: Expr): Set<String> {
        val out = HashSet<String>()
        val seen = HashSet<String>()
        fun walk(x: Expr, bound: Set<String>) {
            when (x) {
                is StateRef -> if (x.name !in state) out += "$" + x.name
                is Name -> when {
                    x.name in bound -> {}
                    x.name in program.bindings -> if (seen.add(x.name)) walk(program.bindings.getValue(x.name).expr, emptySet())
                    x.name in program.functions -> if (seen.add(x.name)) program.functions.getValue(x.name).let { f -> f.body?.let { walk(it, f.params.toSet()) } }
                    catalog.full.function(x.name) != null || x.name in CONTEXT_NAMES -> {}
                    else -> out += x.name
                }
                is Call -> {
                    if (x.kind == CallKind.FUNCTION) when {
                        x.callee in bound -> {}
                        x.callee in program.functions -> if (seen.add(x.callee)) program.functions.getValue(x.callee).let { f -> f.body?.let { walk(it, f.params.toSet()) } }
                        catalog.full.function(x.callee) != null -> {}
                        else -> out += x.callee
                    }
                    x.leads.forEach { walk(it, bound) }; x.named.forEach { walk(it.second, bound) }; x.children?.forEach { walk(it, bound) }
                }
                is Member -> walk(x.target, bound)
                is Index -> { walk(x.target, bound); walk(x.index, bound) }
                is Unary -> walk(x.operand, bound)
                is BinaryOp -> { walk(x.left, bound); walk(x.right, bound) }
                is Ternary -> { walk(x.cond, bound); walk(x.then, bound); walk(x.otherwise, bound) }
                is ListLit -> x.items.forEach { walk(it, bound) }
                is RecLit -> x.fields.forEach { walk(it.second, bound) }
                is Lambda -> walk(x.body, bound + x.params)
                is Str -> x.parts.forEach { if (it is InterpPart) walk(it.expr, bound) }
                else -> {}
            }
        }
        walk(e, emptySet())
        return out
    }

    private fun report(d: Diagnostic) {
        if (diagnostics.none { it.code == d.code && it.message == d.message }) diagnostics += d
    }

    fun evaluator(): Evaluator = Evaluator(program, HashMap(state), ctx, catalog).also { ev -> }

    fun root(): WNode? {
        val ev = evaluator()
        val r = ev.root()
        ev.diagnostics.forEach { report(it) }
        return r
    }

    fun binding(name: String): WValue {
        val ev = evaluator()
        return ev.binding(name).also { ev.diagnostics.forEach { d -> report(d) } }
    }

    // ---- state initialisation, 03 §7.1 ----------------------------------------------------------

    /** Initialises [names] (absent states only) in dependency order; cycles start as null (E302). */
    fun initStates(names: List<String>) {
        val todo = names.filter { it !in state && it in program.states }
        if (todo.isEmpty()) return
        val deps = todo.associateWith { stateDeps(program.states.getValue(it).init) }
        val done = HashSet<String>()
        val order = ArrayList<String>()
        val visiting = HashSet<String>()
        val cyclic = HashSet<String>()
        fun visit(n: String, path: List<String>) {
            if (n in done || n !in deps) return
            if (n in visiting) { cyclic += path.subList(path.indexOf(n), path.size); return }
            visiting += n
            for (d in deps.getValue(n).filter { it in deps }.sortedBy { todo.indexOf(it) }) visit(d, path + d)
            visiting -= n
            done += n
            order += n
        }
        for (n in todo) visit(n, listOf(n))
        if (cyclic.isNotEmpty()) {
            val decl = program.states.getValue(cyclic.first())
            report(Diag.cycle(decl.span, program.states.keys.filter { it in cyclic }.joinToString(", ") { "$$it" }))
        }
        for (n in order) {
            val decl = program.states.getValue(n)
            if (n in cyclic) { state[n] = WNull; continue }
            val ev = evaluator()
            var v = ev.topLevel(decl.init, name = "$$n")
            ev.diagnostics.forEach { report(it) }
            decl.type?.let { t ->
                val c = Coercion(ctx.formatter).toType(v, t)
                if (!c.ok) report(Diag.badType(decl.span, "$$n", "type", t.keyword, v.typeName))
                v = if (c.ok) c.value else WNull
            }
            state[n] = v
            if (stateBytes() > Limits.STATE_BYTES) {
                state[n] = WNull
                report(Diag.limit(decl.span, "state"))
            }
        }
    }

    fun stateBytes(): Int = ctx.formatter.json(WRec(LinkedHashMap(state))).toByteArray(Charsets.UTF_8).size

    /** States an expression depends on, through bindings and functions (03 §7.1). */
    fun stateDeps(e: Expr): Set<String> {
        val out = HashSet<String>()
        val seen = HashSet<String>()
        fun walk(x: Expr) {
            when (x) {
                is StateRef -> out += x.name
                is Name -> if (seen.add(x.name)) {
                    program.bindings[x.name]?.let { walk(it.expr) }
                    program.functions[x.name]?.body?.let { walk(it) }
                }
                is Call -> {
                    if (x.kind == CallKind.FUNCTION && seen.add(x.callee)) program.functions[x.callee]?.body?.let { walk(it) }
                    x.leads.forEach { walk(it) }; x.named.forEach { walk(it.second) }; x.children?.forEach { walk(it) }
                }
                is Member -> walk(x.target)
                is Index -> { walk(x.target); walk(x.index) }
                is Unary -> walk(x.operand)
                is BinaryOp -> { walk(x.left); walk(x.right) }
                is Ternary -> { walk(x.cond); walk(x.then); walk(x.otherwise) }
                is ListLit -> x.items.forEach { walk(it) }
                is RecLit -> x.fields.forEach { walk(it.second) }
                is Lambda -> walk(x.body)
                is Str -> x.parts.forEach { if (it is InterpPart) walk(it.expr) }
                else -> {}
            }
        }
        walk(e)
        return out
    }

    // ---- edits, 02 §11.3 ------------------------------------------------------------------------

    fun applyEdit(r: EditResult) {
        if (!r.accepted) return
        val old = program
        program = r.program
        for (n in state.keys.toList()) if (n !in program.states) state.remove(n)
        for ((n, decl) in program.states) {
            if (n in r.resetStates) { state.remove(n); continue }
            val prevType = old.states[n]?.type
            if (n in state && decl.type != null && decl.type != prevType) {
                val c = Coercion(ctx.formatter).toType(state.getValue(n), decl.type)
                if (c.ok) state[n] = c.value else state.remove(n)
            }
        }
        initStates(program.states.keys.toList())
        // Callbacks belong to the version that scheduled them: an edit cancels pending
        // debounce, `!after` and `every` timers, then installs the new version's `every` effects.
        timers.clear()
        if (appeared) startEvery()
    }

    // ---- events and transactions, 03 §7.2 -------------------------------------------------------

    /** A deferred action with its arguments already evaluated inside the transaction (03 §7.2). */
    private class Deferred(val a: WAction, val args: List<WValue>, val named: Map<String, WValue>)

    private class Tx(val work: LinkedHashMap<String, WValue>, val deferred: ArrayList<Deferred>, val fromEffect: Boolean) {
        var undid = false
        val evaluators = ArrayList<Evaluator>()
        /** True when anything this transaction evaluated read a waiting state (03 §7.1). */
        val readWaiting get() = evaluators.any { it.sawWaiting }
    }

    private fun txEvaluator(tx: Tx) = Evaluator(program, HashMap(tx.work), ctx, catalog).also { tx.evaluators += it }

    /** Fires [action] (an action, a list of actions, or a handler fn) with optional event [data]. */
    fun fire(action: WValue, data: WValue? = null) {
        val chain = intArrayOf(0)
        runEvent(action, data, fromEffect = false, chain = chain)
    }

    /** An input writes its bound place (then its `onChange`), in one transaction (04 §7). */
    fun input(place: Place, value: WValue, onChange: WValue? = null) {
        val chain = intArrayOf(0)
        val tx = Tx(LinkedHashMap(state), ArrayList(), false)
        val before = LinkedHashMap(state)
        writePlace(tx, place, value)
        if (onChange != null) {
            val ev = txEvaluator(tx)
            val a = if (onChange is WFn) ev.safeCall(onChange, listOf(Places.read(tx.work[place.state] ?: WNull, place.path))) else onChange
            runIn(tx, a)
        }
        commit(tx, before, chain)
    }

    private fun runEvent(action: WValue, data: WValue?, fromEffect: Boolean, chain: IntArray) {
        val before = LinkedHashMap(state)
        val tx = Tx(LinkedHashMap(state), ArrayList(), fromEffect)
        var a = action
        if (a is WFn) {
            val ev = txEvaluator(tx)
            a = ev.safeCall(a, listOfNotNull(data))
        }
        runIn(tx, a)
        commit(tx, before, chain)
    }

    private fun runIn(tx: Tx, a: WValue) {
        when (a) {
            is WList -> a.items.forEach { runIn(tx, it) }
            is WAction -> perform(tx, a)
            else -> {}
        }
    }

    private fun perform(tx: Tx, a: WAction) {
        if (tx.fromEffect && a.kind in BuiltinCatalog.OUTBOUND) {
            report(Diag.tier(a.origin, "outbound action from effect"))
            return
        }
        // arguments are evaluated now, inside the transaction (they see earlier writes)
        val ev = txEvaluator(tx)
        val nb = NodeBuilder(ev)
        fun arg(i: Int) = a.args.getOrNull(i)?.let { ev.topLevel(it, a.scope) } ?: WNull
        fun named(k: String) = a.named.firstOrNull { it.first == k }?.second?.let { ev.topLevel(it, a.scope) }
        fun place(i: Int): Place? {
            val e = a.args.getOrNull(i) ?: return null
            val p = nb.place(e, a.scope)
            if (p == null || p.state !in program.states) {
                report(Diag.missingRef(e.span, if (e is StateRef) "$" + e.name else "place"))
                return null
            }
            return p
        }
        fun cur(p: Place) = Places.read(tx.work[p.state] ?: WNull, p.path)
        when (a.kind) {
            "set" -> place(0)?.let { writePlace(tx, it, arg(1)) }
            "inc", "dec" -> place(0)?.let { p ->
                val by = (named("by") as? WNum)?.v ?: (a.args.getOrNull(1)?.let { (ev.topLevel(it, a.scope) as? WNum)?.v }) ?: 1.0
                var v = ((cur(p) as? WNum)?.v ?: 0.0) + if (a.kind == "inc") by else -by
                (named("min") as? WNum)?.let { v = maxOf(v, it.v) }
                (named("max") as? WNum)?.let { v = minOf(v, it.v) }
                writePlace(tx, p, WNum.of(v))
            }
            "toggle" -> place(0)?.let { writePlace(tx, it, WBool.of(!cur(it).truthy())) }
            "reset" -> a.args.forEach { e -> (e as? StateRef)?.let { tx.work[it.name] = reinit(it.name, tx) } }
            "resetAll" -> program.states.keys.forEach { tx.work[it] = reinit(it, tx) }
            "push" -> place(0)?.let { p -> val l = cur(p); writePlace(tx, p, if (l is WList) WList(l.items + arg(1)) else WList(listOf(arg(1)))) }
            "insert" -> place(0)?.let { p ->
                val l = (cur(p) as? WList)?.items ?: emptyList()
                val ix = ((named("index") ?: arg(1)) as? WNum)?.v?.toInt()?.coerceIn(0, l.size) ?: l.size
                val item = named("item") ?: arg(2)
                writePlace(tx, p, WList(l.toMutableList().apply { add(ix, item) }))
            }
            "remove" -> place(0)?.let { p ->
                val l = (cur(p) as? WList)?.items ?: return@let
                val ix = Places.listIndex(l.size, ((named("index") ?: arg(1)) as? WNum)?.v ?: return@let) ?: return@let
                writePlace(tx, p, WList(l.toMutableList().apply { removeAt(ix) }))
            }
            "removeWhere" -> place(0)?.let { p ->
                val l = (cur(p) as? WList)?.items ?: return@let
                val pred = named("pred") ?: arg(1)
                val kept = ArrayList<WValue>(l.size)
                var stop: Diagnostic? = null
                // Its own evaluator, so the step budget is one running total for the whole removal.
                val predEv = txEvaluator(tx)
                for ((i, it) in l.withIndex()) {
                    try {
                        if (!predEv.safeCallItem(pred, it, i).truthy()) kept += it
                    } catch (x: BudgetExceeded) {
                        // The rest of the list is untouched once the shared budget runs out.
                        kept += l.subList(i, l.size)
                        stop = Diag.budget(Span.NONE, "removeWhere")
                        break
                    } catch (x: LimitExceeded) {
                        kept += l.subList(i, l.size)
                        stop = Diag.limit(Span.NONE, x.what)
                        break
                    }
                }
                // Keep predEv recorded: a waiting read before the stop must still discard the
                // whole gesture (03 §7.1). The diagnostic itself does not set that flag.
                stop?.let { report(it) }
                writePlace(tx, p, WList(kept))
            }
            "move" -> place(0)?.let { p ->
                val l = (cur(p) as? WList)?.items?.toMutableList() ?: return@let
                if (l.isEmpty()) return@let
                val from = (((named("from") ?: arg(1)) as? WNum)?.v ?: 0.0).toInt().coerceIn(0, l.size - 1)
                val to = (((named("to") ?: arg(2)) as? WNum)?.v ?: 0.0).toInt().coerceIn(0, l.size - 1)
                l.add(to, l.removeAt(from))
                writePlace(tx, p, WList(l))
            }
            "patch" -> place(0)?.let { p ->
                val r = cur(p) as? WRec ?: WRec(LinkedHashMap())
                val add = arg(1) as? WRec ?: return@let
                writePlace(tx, p, WRec(LinkedHashMap(r.fields).apply { putAll(add.fields) }))
            }
            "undo" -> undo.lastOrNull()?.let { (before, _) -> tx.work.clear(); tx.work.putAll(before); redo.addLast(undo.removeLast()); tx.undid = true }
            "redo" -> redo.lastOrNull()?.let { (_, after) -> tx.work.clear(); tx.work.putAll(after); undo.addLast(redo.removeLast()); tx.undid = true }
            "if" -> {
                val cond = arg(0)
                val branch = if (cond.truthy()) a.args.getOrNull(1) else (a.args.getOrNull(2) ?: a.named.firstOrNull { it.first == "else" }?.second)
                branch?.let { runIn(tx, ev.topLevel(it, a.scope)) }
            }
            "open", "close", "go", "focus", "scrollTo", "refresh" -> outbox += HostEffect(a.kind, a.args.map { (it as? Name)?.let { n -> WStr(n.name) } ?: ev.topLevel(it, a.scope) }, emptyMap())
            // arguments are evaluated now, so a waiting read discards the whole transaction (T-279b)
            else -> tx.deferred += deferredArgs(a, ev)
        }
    }

    private fun reinit(name: String, tx: Tx): WValue {
        val decl = program.states[name] ?: return WNull
        val ev = txEvaluator(tx)
        var v = ev.topLevel(decl.init, name = "$$name")
        decl.type?.let { t -> val c = Coercion(ctx.formatter).toType(v, t); v = if (c.ok) c.value else WNull }
        return v
    }

    private fun writePlace(tx: Tx, p: Place, value: WValue) {
        val decl = program.states[p.state] ?: run { report(Diag.missingRef(Span.NONE, "$" + p.state)); return }
        val root = tx.work[p.state] ?: WNull
        var v = value
        if (p.path.isEmpty() && decl.type != null) {
            val c = Coercion(ctx.formatter).toType(v, decl.type)
            if (!c.ok) { report(Diag.badType(decl.span, "$" + p.state, "value", decl.type.keyword, v.typeName)); return }
            v = c.value
        }
        val next = Places.write(root, p.path, v)
        if (next == null) { report(Diag.badType(decl.span, "$" + p.state, "path", "a valid index", "out of range")); return }
        tx.work[p.state] = next
    }

    private fun commit(tx: Tx, before: Map<String, WValue>, chain: IntArray) {
        // A gesture that read a waiting value is discarded whole: no writes, no deferred
        // actions, no history (03 §7.1, T-279b).
        if (!tx.fromEffect && tx.readWaiting) { rejectedTaps++; return }
        // STATE_BYTES: an over-size commit rolls back (05 §13.3)
        val bytes = ctx.formatter.json(WRec(LinkedHashMap(tx.work))).toByteArray(Charsets.UTF_8).size
        if (bytes > Limits.STATE_BYTES) {
            report(Diag.limit(Span.NONE, "state"))
            return
        }
        val changed = tx.work != state
        state.clear(); state.putAll(tx.work)
        if (changed && !tx.undid) {
            undo.addLast(LinkedHashMap(before) to LinkedHashMap(tx.work))
            while (undo.size > Limits.HISTORY_ENTRIES) undo.removeFirst()
            redo.clear()
        }
        // deferred queue, in order (03 §7.2 step 6)
        for (d in tx.deferred) deferred(d, tx.fromEffect)
        if (changed) changeEffects(before, chain)
    }

    private fun deferredArgs(a: WAction, ev: Evaluator): Deferred =
        Deferred(a, a.args.map { ev.topLevel(it, a.scope) }, a.named.associate { (k, v) -> k to ev.topLevel(v, a.scope) })

    private fun deferred(d: Deferred, fromEffect: Boolean) {
        val a = d.a
        val args = d.args
        val named = d.named
        if (a.kind == "after") {
            val delay = ((args.getOrNull(0) as? WDur)?.ms ?: 0L).coerceAtMost(600_000L)
            val act = args.getOrNull(1) ?: named["action"] ?: return
            // The callback keeps the origin of the event that scheduled it, so an effect can't
            // launder an outbound action through a timer (03 §7.4).
            schedule(delay, "after@${a.origin.start}@$clock") { runEvent(act, null, fromEffect = fromEffect, chain = intArrayOf(0)) }
            return
        }
        outbox += HostEffect(a.kind, args, named)
    }

    // ---- effects, 03 §8 -------------------------------------------------------------------------

    private fun triggerTarget(e: Effect): Expr? = if (e.trigger.callee == "change") e.trigger.leads.firstOrNull() else null

    private fun valueOf(target: Expr, snapshot: Map<String, WValue>): WValue {
        val ev = Evaluator(program, HashMap(snapshot), ctx, catalog)
        return when (target) {
            is StateRef -> snapshot[target.name] ?: WNull
            is Name -> ev.binding(target.name)
            is Qualified -> ctx.exports(target.block, target.name) ?: WNull
            else -> ev.topLevel(target)
        }
    }

    private fun changeEffects(before: Map<String, WValue>, chain: IntArray) {
        val firing = ArrayList<Pair<Effect, WValue>>()
        for (e in program.effects.values) {
            val t = triggerTarget(e) ?: continue
            val prev = valueOf(t, before)
            val now = valueOf(t, state)
            if (weaveEquals(prev, now)) continue
            val debounce = e.trigger.named.firstOrNull { it.first == "debounce" }?.second
            if (debounce != null) {
                val ms = (Evaluator(program, HashMap(state), ctx, catalog).topLevel(debounce) as? WDur)?.ms ?: 0L
                schedule(ms, "debounce:${e.key}") { runEffect(e, prev, intArrayOf(0)) }
            } else firing += e to prev
        }
        for ((e, prev) in firing) runEffect(e, prev, chain)
    }

    private fun runEffect(e: Effect, prev: WValue, chain: IntArray) {
        if (chain[0] >= Limits.EFFECT_CHAIN) {
            if (!effectLoopReported) { report(Diag.effectLoop(e.span)); effectLoopReported = true }
            return
        }
        chain[0]++
        val vars = HashMap<String, WValue>()
        if (e.trigger.callee == "change") vars["prev"] = prev
        val scope = Scope(vars, null)
        val ev = Evaluator(program, HashMap(state), ctx, catalog)
        if (e.condition != null && !ev.topLevel(e.condition, scope).truthy()) return
        val action = ev.topLevel(e.action, scope)
        val before = LinkedHashMap(state)
        val tx = Tx(LinkedHashMap(state), ArrayList(), fromEffect = true)
        runIn(tx, action)
        commit(tx, before, chain)
    }

    /** Fires `appear` once per block lifetime (03 §8.2) and starts `every` timers. */
    fun appear() {
        if (!appeared) {
            appeared = true
            program.effects.values.filter { it.trigger.callee == "appear" }.forEach { runEffect(it, WNull, intArrayOf(0)) }
        }
        startEvery()
    }

    private fun startEvery() {
        for (e in program.effects.values.filter { it.trigger.callee == "every" }) {
            val iv = (Evaluator(program, HashMap(state), ctx, catalog).topLevel(e.trigger.leads.firstOrNull() ?: continue) as? WDur)?.ms ?: continue
            val period = maxOf(iv, Limits.MIN_TIMER_MS)
            schedule(period, "every:${e.key}", repeat = period) { runEffect(e, WNull, intArrayOf(0)) }
        }
    }

    private fun schedule(delay: Long, id: String, repeat: Long = 0, run: () -> Unit) {
        timers.removeAll { it.id == id }
        timers += Timer(clock + delay, id, run, repeat)
    }

    /** Advances the virtual clock, firing due timers in time order. */
    fun advance(ms: Long) {
        val end = clock + ms
        while (true) {
            val next = timers.filter { it.at <= end }.minByOrNull { it.at } ?: break
            timers.remove(next)
            clock = next.at
            if (next.repeat > 0) timers += next.copy(at = next.at + next.repeat)
            next.run()
        }
        clock = end
    }

    // ---- queries for tests and the harness ------------------------------------------------------

    /**
     * Every node in the rendered tree: nodes with a falsy `show` are skipped, `List` rows are
     * produced through `render` (or the default `Item`, 04 §8), and only the selected page of
     * `Tabs` is included unless [allPages] (pages the user can reach with a tap).
     */
    fun nodes(allPages: Boolean = false): List<WNode> {
        val out = ArrayList<WNode>()
        val ev = evaluator()
        fun walk(n: WNode) {
            if (n.args.containsKey("show") && !(n.args["show"] ?: WBool.TRUE).truthy()) return
            out += n
            when (n.type) {
                "Tabs" -> if (allPages) n.children.forEach { walk(it) } else {
                    val i = ((n.args["index"] as? WNum)?.v ?: 0.0).toInt()
                    n.children.getOrNull(i.coerceIn(0, maxOf(0, n.children.size - 1)))?.let { walk(it) }
                }
                "List" -> listRows(n, ev).forEach { walk(it) }
                else -> n.children.forEach { walk(it) }
            }
        }
        root()?.let { walk(it) }
        ev.diagnostics.forEach { report(it) }
        return out
    }

    /** The rows a `List` shows (04 §8): `render(item, index)` or an `Item` titled by the item. */
    private fun listRows(n: WNode, ev: Evaluator): List<WNode> {
        val items = (n.args["items"] as? WList)?.items ?: return n.children
        val render = n.args["render"] as? WFn
        return items.mapIndexed { i, item ->
            val v = if (render != null) runCatching { ev.callItem(render, item, i) }.getOrDefault(WNull) else WNull
            val row = v as? WNode
            if (row == null) defaultItem(item, "${n.key}/$i", n.sourceStmt)
            else withKey(row, "${n.key}/${explicitKey(row) ?: i.toString()}")
        }
    }

    /**
     * A row's identity is its `key` prop, or its index (04 §2.5). Descendants are scoped under
     * that key even when they already have one, so two rows never share a key.
     */
    private fun withKey(n: WNode, key: String): WNode {
        val kids = n.children.mapIndexed { i, ch -> withKey(ch, "$key/${explicitKey(ch) ?: i.toString()}") }
        return WNode(n.type, n.args, kids, key, n.sourceStmt, n.places, n.waiting, n.error)
    }

    private fun explicitKey(n: WNode): String? =
        n.args["key"]?.takeIf { it != WNull }?.let { keyText(it) }?.takeIf { it.isNotEmpty() }

    /** 04 §8 default row: title = first string field, subtitle = the second one (spec 1.3). */
    fun defaultItem(item: WValue, key: String, stmt: String): WNode {
        val args = LinkedHashMap<String, WValue>()
        args["title"] = WStr(defaultTitle(item))
        if (item is WRec) item.fields.values.filterIsInstance<WStr>().getOrNull(1)?.let { args["subtitle"] = it }
        return WNode("Item", args, emptyList(), key, stmt)
    }

    private fun defaultTitle(item: WValue): String = when (item) {
        is WRec -> item.fields.values.firstOrNull { it is WStr }?.let { (it as WStr).v } ?: ctx.formatter.auto(item)
        else -> ctx.formatter.auto(item)
    }

    fun plainText(n: WNode) = PlainText.of(n, ctx.formatter)

    @Suppress("unused")
    private fun unused(b: Binding) = b

    private companion object {
        /** Context values (03 §4.3) and event variables that are always defined. */
        val CONTEXT_NAMES = setOf("device", "block", "user", "item", "index", "prev", "event", "value", "pi", "e")
    }
}

/**
 * A key segment (04 §2.5). Only simple values are encoded, with their type, so a number and the
 * same text stay different and a node or list is never walked. Anything else is no key.
 */
internal fun keyText(v: WValue): String {
    val raw = when (v) {
        is WStr -> "s:" + v.v
        is WSym -> "y:" + v.name
        is WNum -> "n:" + com.echoflow.weave.core.parse.CanonicalPrinter.number(v.v)
        is WBool -> "b:" + v.v
        else -> return ""
    }
    if (raw.length <= 200) return raw
    return raw.take(180) + "#" + raw.hashCode().toUInt().toString(16)
}
