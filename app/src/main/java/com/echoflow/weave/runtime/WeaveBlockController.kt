package com.echoflow.weave.runtime

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.echoflow.weave.core.eval.EvalContext
import com.echoflow.weave.core.eval.Place
import com.echoflow.weave.core.eval.Session
import com.echoflow.weave.core.eval.WNode
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.parse.IncrementalParser
import com.echoflow.weave.core.program.EditResult
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.core.program.WeaveProgram
import com.echoflow.weave.runtime.storage.WeaveBlockRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One block instance (05 §4.1): parses its fence as it streams (02 §10) off the main thread,
 * owns the [Session] (state, transactions, effects, history) on the main thread, and persists
 * state to `weave_blocks` (05 §9.3). Compose observes [program] and [version]; every committed
 * transaction bumps [version], which re-evaluates the tree (05 §4.3).
 */
@Stable
class WeaveBlockController internal constructor(
    val messageId: String?,
    chatId: String?,
    val blockKey: Int,
    private val runtime: WeaveRuntime,
) {
    var chatId: String? = chatId
        internal set
    private val parser = IncrementalParser(runtime.catalog)
    private val parseLock = Mutex()

    var session: Session? = null
        private set
    var program by mutableStateOf<WeaveProgram?>(null)
        private set
    /** Bumped after every state change; readers of the tree depend on it. */
    var version by mutableIntStateOf(0)
        private set
    /** A short inline chip ("This block is full", 05 §13.3). */
    var chip by mutableStateOf<String?>(null)
    /** Whether the block shows as plain text (05 §7.2 "Show as plain text"). */
    var plain by mutableStateOf(false)
        private set
    /** The block's version number (edits, 05 §9.3). */
    var blockVersion by mutableIntStateOf(1)
        private set

    /** `!expand` / `!collapse` (05 §7.3); the block surface observes and clears it. */
    var expandRequest by mutableStateOf<Boolean?>(null)
    /** Overlays opened with `!open(name)` (04 §11), most recent last. */
    var openOverlays by mutableStateOf(listOf<String>())
        private set
    /** `!focus(name)` and `!scrollTo(name)` targets, consumed by the renderer. */
    var focusRequest by mutableStateOf<String?>(null)
    var scrollRequest by mutableStateOf<String?>(null)
    /** Bumped by `!confetti`. */
    var confetti by mutableIntStateOf(0)

    // ---- model loop state (05 §8) --------------------------------------------------------------
    /** Names with a hidden request in flight; `pending(name)` reads them (03 §9.2). */
    var pendingNames by mutableStateOf(emptySet<String>())
        private set
    /** "Stop" on the hourly budget banner: no more hidden requests until restart (05 §8.4). */
    var modelStopped = false
    var extraBudget = 0
    var budgetBanner by mutableStateOf(false)
    var repairedVersion = 0
    /** The last 5 `!ask` exchanges: prompt → names updated. */
    var askLog: List<Pair<String, List<String>>> = emptyList()
        private set
    /** Lazy parts: binding → (cache key, fragment source) (05 §8.5). */
    private var lazyParts = mutableMapOf<String, Pair<String, String>>()
    var lazyInFlight: Set<String> = emptySet()
    /** Query values by statement (03 §13.1) and the args they ran with. */
    private val queries = mutableMapOf<String, Pair<WValue, WValue>>()
    private val queryStarted = mutableSetOf<String>()
    /** A tool waiting for consent (05 §8.7): the consent strip shows it. */
    var consentRequest by mutableStateOf<ConsentRequest?>(null)
    private var consentJson = "{}"

    class ConsentRequest(val tool: String, val text: String, val answer: (granted: Boolean, always: Boolean) -> Unit)

    private var row: WeaveBlockRow? = null
    private var rowLoaded = false
    private var lastBody: String? = null
    private var lastComplete = false
    private var saveJob: Job? = null
    private var ctx: EvalContext? = null
    private var firedJson = "{}"
    private var uiJson = "{}"
    private var lastRejected = 0

    val blockId: String? get() = row?.blockId ?: program?.header?.id
    val title: String? get() = program?.header?.title

    /** Called on every recomposition of the fence (05 §6.1); cheap when nothing changed. */
    suspend fun update(body: String, complete: Boolean, context: EvalContext) {
        if (ctx == null) ctx = context
        val mid = messageId
        if (mid != null && !rowLoaded) {
            row = runCatching { runtime.loadRow(mid, blockKey) }.getOrNull()
            rowLoaded = true
            row?.let { r ->
                firedJson = r.firedJson; uiJson = r.uiStateJson; blockVersion = r.version; consentJson = r.consentJson
                askLog = (StateCodec.decode(r.askLogJson) as? com.echoflow.weave.core.eval.WList)?.items?.mapNotNull { e ->
                    val rec = e as? com.echoflow.weave.core.eval.WRec ?: return@mapNotNull null
                    (rec["p"] as? com.echoflow.weave.core.eval.WStr)?.v?.let { p -> p to ((rec["n"] as? com.echoflow.weave.core.eval.WList)?.items?.mapNotNull { (it as? com.echoflow.weave.core.eval.WStr)?.v } ?: emptyList()) }
                } ?: emptyList()
                (StateCodec.decode(r.lazyJson) as? com.echoflow.weave.core.eval.WRec)?.fields?.forEach { (k, v) ->
                    val rec = v as? com.echoflow.weave.core.eval.WRec ?: return@forEach
                    val key = (rec["key"] as? com.echoflow.weave.core.eval.WStr)?.v ?: return@forEach
                    val src = (rec["source"] as? com.echoflow.weave.core.eval.WStr)?.v ?: return@forEach
                    lazyParts[k] = key to src
                }
                (StateCodec.decode(r.queryJson) as? com.echoflow.weave.core.eval.WRec)?.fields?.forEach { (k, v) -> queries[k] = v to WNull }
                plain = WeaveRuntime.hasFlag(r.uiStateJson, "plain")
            }
        }
        // A stored row wins over the fence text: it holds the current version (05 §9.3).
        val src = row?.source ?: body
        val done = row != null || complete
        if (src == lastBody && done == lastComplete) return
        lastBody = src; lastComplete = done
        val next = parseLock.withLock { withContext(Dispatchers.Default) { parser.update(src, done) } }
        publish(next)
        if (done && row == null && messageId != null) {
            row = runtime.createRow(this, next, src)
            row?.let { blockVersion = it.version }
            // a block that arrived with errors may get one repair round (05 §8.9)
            (runtime.modelLoop as? ModelLoop)?.maybeRepair(this)
        }
    }

    private fun publish(next: WeaveProgram) {
        val s = session
        if (s == null) {
            val base = ctx ?: EvalContext()
            val withExtras = EvalContext(
                formatter = base.formatter, deviceWidth = base.deviceWidth, landscape = base.landscape, dark = base.dark,
                reducedMotion = base.reducedMotion, fontScale = base.fontScale, blockExpanded = base.blockExpanded,
                blockVisible = base.blockVisible, userFirstName = base.userFirstName,
                exports = { block, name -> runtime.exportOf(chatId, block, name) },
                animationTimeMs = { if (base.reducedMotion) null else animationMs },
                pendingNames = { pendingNames },
                queryValue = { stmt, tool, args, every -> queryFor(stmt, tool, args, every) },
            )
            val created = Session(next, withExtras, runtime.catalog)
            row?.let { r ->
                created.restore(StateCodec.decodeState(next, r.stateJson, created.ctx.formatter))
                if (WeaveRuntime.hasFlag(r.firedJson, "appear")) created.markAppeared()
            }
            session = created
            // stored lazy parts come back as bindings (03 §9.3)
            if (next.complete) lazyParts.forEach { (name, part) -> applyLazySource(name, part.second) }
        } else {
            s.sync(next)
        }
        program = next
        version++
    }

    /** The evaluated tree for the current version (05 §4.3). Never throws. */
    fun root(): WNode? = runCatching { session?.root() }.getOrNull()

    fun binding(name: String): WValue? = runCatching { session?.binding(name) }.getOrNull()

    /** A gesture (03 §7.2). */
    fun fire(action: WValue, data: WValue? = null) {
        val s = session ?: return
        runCatching { s.fire(action, data) }
        afterEvent()
    }

    /** An input writing its bound place, then its `onChange` (04 §7). */
    fun input(place: Place, value: WValue, onChange: WValue? = null, drag: Boolean = false) {
        val s = session ?: return
        runCatching { s.input(place, value, onChange, drag) }
        afterEvent()
    }

    /** A continuous drag ended (one history entry per drag, 03 §7.5). */
    fun endDrag() { session?.endDrag() }

    /** Calls a block function value (`render`, `onRow`), never throwing. */
    fun callFn(fn: WValue, args: List<WValue>): WValue? {
        val s = session ?: return null
        val f = fn as? com.echoflow.weave.core.eval.WFn ?: return null
        return runCatching { s.evaluator().callFn(f, args) }.getOrNull()
    }

    fun callItem(fn: WValue, item: WValue, index: Int): WValue? {
        val s = session ?: return null
        return runCatching { s.evaluator().callItem(fn, item, index) }.getOrNull()
    }

    /** The text a `!say` handler would send (Button long-press tooltip, 04 §7). */
    fun sayPreview(action: WValue?): String? {
        val a = action as? com.echoflow.weave.core.eval.WAction ?: return null
        if (a.kind != "say") return null
        val s = session ?: return null
        val e = a.args.firstOrNull() ?: return null
        return runCatching { s.ctx.formatter.auto(s.evaluator().topLevel(e, a.scope)) }.getOrNull()
    }

    /** Fires `appear` once per block lifetime (03 §8.2) when the complete block is first shown. */
    fun appear() {
        val s = session ?: return
        if (program?.complete != true) return
        val first = !s.hasAppeared
        runCatching { s.appear() }
        if (first) {
            firedJson = WeaveRuntime.addFlag(firedJson, "appear")
            row?.let { runtime.saveFired(it.id, firedJson) }
        }
        afterEvent()
    }

    /** Animation time for `loop` (04 §13.1), advanced by [frame]. */
    @Volatile private var animationMs = 0L
    private var frameEpoch = 0L

    /** Whether the block needs per-frame work: `frame` effects or `loop(...)` values. */
    val animated: Boolean get() = session?.hasFrameEffects == true || (lastBody?.contains("loop(") == true)

    val hasMotionEffects: Boolean get() = session?.hasMotionEffects == true

    /** One display frame: advance `loop` time, run `frame` effects, re-render. */
    fun frame(dtMs: Long) {
        val s = session ?: return
        animationMs += dtMs
        if (s.hasFrameEffects) runCatching { s.frame(dtMs) }
        if (++frameEpoch % 1L == 0L) version++
        if (s.outbox.isNotEmpty()) afterEvent()
    }

    fun animationTime(): Long = animationMs

    fun tilt(x: Double, y: Double) { session?.let { runCatching { it.tilt(x, y) }; version++ } }
    fun shake() { session?.let { runCatching { it.shake() }; afterEvent() } }

    /** Advances the block clock by real elapsed time; only called while timers are pending. */
    fun tick(ms: Long) {
        val s = session ?: return
        if (!s.hasTimers) return
        runCatching { s.advance(ms) }
        afterEvent()
    }

    val hasTimers: Boolean get() = session?.hasTimers == true

    /** A host effect a renderer raises itself (a link tap, a Timer's haptic). Links always confirm. */
    fun hostEffect(kind: String, args: List<WValue>) {
        runtime.dispatch(this, com.echoflow.weave.core.eval.HostEffect(kind, args, emptyMap()))
    }

    fun requestExpanded(on: Boolean) { expandRequest = on }

    /** Local navigation actions (03 §7.3): overlays, tab/page switches, focus and scrolling. */
    fun localNavigation(kind: String, args: List<WValue>) {
        val name = (args.firstOrNull() as? com.echoflow.weave.core.eval.WStr)?.v
        when (kind) {
            "open" -> if (name != null && name !in openOverlays) openOverlays = openOverlays + name
            "close" -> openOverlays = if (name == null) emptyList() else openOverlays - name
            "go" -> {
                val target = name?.let { binding(it) } as? WNode ?: return
                val place = target.places["index"] ?: target.places["step"] ?: return
                val i = (args.getOrNull(1) as? com.echoflow.weave.core.eval.WNum) ?: return
                input(place, i)
            }
            "focus" -> focusRequest = name
            "scrollTo" -> scrollRequest = name
        }
        version++
    }

    fun closeOverlay(name: String) { openOverlays = openOverlays - name }

    /** `!ask`, `!refresh`, `!regen`, `!tool`: hidden model requests (05 §8), handled by the model loop. */
    fun modelAction(e: com.echoflow.weave.core.eval.HostEffect) { runtime.modelLoop?.handle(this, e) }

    fun requestUrl(href: String) = hostEffect("url", listOf(com.echoflow.weave.core.eval.WStr(href)))

    private fun afterEvent() {
        val s = session ?: return
        if (s.rejectedTaps != lastRejected) {
            lastRejected = s.rejectedTaps
            runtime.host?.rejectHaptic()
        }
        if (s.diagnostics.any { it.code == "E304" } && chip == null) {
            chip = "This block is full"
            runtime.scope.launch { delay(4_000); chip = null }
        }
        val effects = s.outbox.toList()
        s.outbox.clear()
        effects.forEach { runtime.dispatch(this, it) }
        version++
        scheduleSave()
    }

    /** Debounced 500 ms per block (05 §9.3); [flush] writes immediately (ON_STOP). */
    private fun scheduleSave() {
        val r = row ?: return
        saveJob?.cancel()
        saveJob = runtime.scope.launch {
            delay(500)
            save(r)
        }
    }

    private fun save(r: WeaveBlockRow) {
        val s = session ?: return
        val p = program ?: return
        runtime.saveState(r.id, StateCodec.encodeState(p, s.state))
    }

    fun flush() {
        saveJob?.cancel()
        row?.let { save(it) }
    }

    /** An accepted edit from a later reply (02 §11.3). */
    fun applyEdit(r: EditResult, newVersion: Int, fired: String) {
        val s = session ?: return
        runCatching { s.applyEdit(r) }
        firedJson = fired
        program = s.program
        blockVersion = newVersion
        row = row?.copy(source = r.program.print(runtime.catalog), version = newVersion)
        lastBody = row?.source
        version++
        scheduleSave()
    }

    fun setPending(names: Set<String>) { pendingNames = names; version++ }

    fun flashChip(text: String) {
        chip = text
        runtime.scope.launch { delay(6_000); if (chip == text) chip = null }
    }

    fun askFailed(name: String) = flashChip("Couldn't update $name")

    fun recordAsk(prompt: String, names: List<String>) {
        askLog = (askLog + (prompt to names)).takeLast(5)
        row?.let { r ->
            val json = StateCodec.encode(com.echoflow.weave.core.eval.WList(askLog.map { (p, n) ->
                com.echoflow.weave.core.eval.WRec.of("p" to com.echoflow.weave.core.eval.WStr(p), "n" to com.echoflow.weave.core.eval.WList(n.map { com.echoflow.weave.core.eval.WStr(it) }))
            }))
            runtime.saveAskLog(r.id, json)
        }
    }

    /** An accepted `!ask`, remix or repair edit: applied live and saved as a new version (05 §9.3). */
    fun acceptModelEdit(r: EditResult, cause: String) {
        val s = session ?: return
        runCatching { s.applyEdit(r) }
        program = s.program
        val next = blockVersion + 1
        val src = WeaveRuntime.withHeaderId(r.program.print(runtime.catalog), blockId ?: "b1")
        row?.let { runtime.saveVersion(it.id, next, src, cause); row = it.copy(source = src, version = next) }
        blockVersion = next
        lastBody = src
        version++
        scheduleSave()
    }

    /** `!regen`: a whole new block as version n+1; states follow edit rules (03 §9.4). */
    suspend fun replaceProgram(source: String, cause: String) {
        val p = parseLock.withLock { withContext(Dispatchers.Default) { com.echoflow.weave.core.program.Compiler.compile(source, runtime.catalog) } }
        val s = session ?: return
        val r = EditResult(p, true, null, emptyList(), emptySet())
        runCatching { s.applyEdit(r) }
        program = s.program
        val next = blockVersion + 1
        row?.let { runtime.saveVersion(it.id, next, source, cause); row = it.copy(source = source, version = next) }
        blockVersion = next
        lastBody = source
        version++
    }

    // ---- lazy (03 §9.3) ----

    /** The node to show for a lazy part, or null while it is generated. */
    fun lazyNode(name: String, prompt: WValue, context: com.echoflow.weave.core.eval.WRec): WNode? {
        val key = ModelLoop.lazyKey(prompt, context)
        val part = lazyParts[name]
        if (part == null && program?.complete == true) (runtime.modelLoop as? ModelLoop)?.lazy(this, name, (prompt as? com.echoflow.weave.core.eval.WStr)?.v ?: "", context, key)
        if (part == null) return null
        return binding(com.echoflow.weave.core.program.ModelResponses.lazyName(name)) as? WNode
    }

    /** True when the cached part was made for other context values: show the "Update" chip. */
    fun lazyStale(name: String, prompt: WValue, context: com.echoflow.weave.core.eval.WRec): Boolean =
        lazyParts[name]?.first?.let { it != ModelLoop.lazyKey(prompt, context) } == true

    fun refreshLazy(name: String) {
        val node = (program?.bindings?.get(name))?.let { binding(name) } as? WNode ?: return
        val prompt = node.args["prompt"] ?: return
        val ctx = node.args["context"] as? com.echoflow.weave.core.eval.WRec ?: com.echoflow.weave.core.eval.WRec(LinkedHashMap())
        (runtime.modelLoop as? ModelLoop)?.lazy(this, name, (prompt as? com.echoflow.weave.core.eval.WStr)?.v ?: "", ctx, ModelLoop.lazyKey(prompt, ctx))
    }

    fun lazyPartial(name: String, key: String, body: String) {
        if (applyLazySource(name, body, keep = false)) version++
    }

    fun lazyResult(name: String, key: String, body: String): Boolean {
        if (!applyLazySource(name, body)) return false
        lazyParts[name] = key to body
        row?.let { r ->
            val json = StateCodec.encode(com.echoflow.weave.core.eval.WRec(LinkedHashMap(lazyParts.mapValues { (_, v) ->
                com.echoflow.weave.core.eval.WRec.of("key" to com.echoflow.weave.core.eval.WStr(v.first), "source" to com.echoflow.weave.core.eval.WStr(v.second))
            })))
            runtime.saveLazy(r.id, json)
        }
        version++
        return true
    }

    fun lazyFailed(name: String) = flashChip("Couldn't load $name")

    private fun applyLazySource(name: String, body: String, keep: Boolean = true): Boolean {
        val s = session ?: return false
        val edit = com.echoflow.weave.core.program.ModelResponses.lazyEdit(name, body, runtime.catalog) ?: return false
        val r = com.echoflow.weave.core.program.Edits.apply(s.program, edit, runtime.catalog)
        if (!r.accepted) return false
        runCatching { s.applyEdit(r) }
        if (keep) program = s.program
        return true
    }

    // ---- queries and consent (03 §13, 05 §8.7) ----

    private fun queryFor(stmt: String, tool: String, args: WValue, every: Long?): WValue {
        val known = queries[stmt]
        val argsChanged = known != null && known.second != WNull && !com.echoflow.weave.core.eval.weaveEquals(known.second, args)
        if ((stmt !in queryStarted || argsChanged) && program?.complete == true && queryStarted.size < com.echoflow.weave.core.Limits.QUERIES_PER_BLOCK) {
            queryStarted += stmt
            queries[stmt] = (known?.first ?: ModelLoop.queryRecord(WNull, true, null)) to args
            runtime.scope.launch { delay(if (argsChanged) 400 else 0); (runtime.modelLoop as? ModelLoop)?.runQuery(this@WeaveBlockController, stmt, tool, args, every) }
        }
        return queries[stmt]?.first ?: ModelLoop.queryRecord(WNull, true, null)
    }

    fun setQuery(stmt: String, value: WValue) {
        queries[stmt] = value to (queries[stmt]?.second ?: WNull)
        version++
        row?.let { r -> runtime.saveQueries(r.id, StateCodec.encode(com.echoflow.weave.core.eval.WRec(LinkedHashMap(queries.mapValues { it.value.first })))) }
    }

    fun queryValueOf(stmt: String): WValue = ((queries[stmt]?.first as? com.echoflow.weave.core.eval.WRec)?.get("value")) ?: WNull
    fun queryArgsStill(stmt: String, args: WValue) = queries[stmt]?.second?.let { com.echoflow.weave.core.eval.weaveEquals(it, args) } != false

    fun requestConsent(tool: WeaveTool, args: WValue, then: (Boolean) -> Unit) {
        if (WeaveRuntime.hasFlag(consentJson, tool.name)) { then(true); return }
        consentRequest = ConsentRequest(tool.name, tool.consentText(args)) { granted, always ->
            consentRequest = null
            if (granted && always) {
                consentJson = WeaveRuntime.addFlag(consentJson, tool.name)
                row?.let { runtime.saveConsent(it.id, consentJson) }
            }
            then(granted)
        }
    }

    fun revokeConsents() { consentJson = "{}"; row?.let { runtime.saveConsent(it.id, consentJson) } }

    /** Restores an earlier version from the Versions sheet (05 §7.2). */
    suspend fun restoreVersion(source: String) {
        val r = row ?: return
        val next = blockVersion + 1
        runtime.saveVersion(r.id, next, source, "restore")
        row = r.copy(source = source, version = next)
        blockVersion = next
        val p = parseLock.withLock { withContext(Dispatchers.Default) { com.echoflow.weave.core.program.Compiler.compile(source, runtime.catalog) } }
        publish(p)
        lastBody = source; lastComplete = true
    }

    suspend fun versions() = row?.let { runtime.versions(it.id) } ?: emptyList()

    fun showPlain(on: Boolean) {
        plain = on
        uiJson = if (on) WeaveRuntime.addFlag(uiJson, "plain") else uiJson.replace("\"plain\":true", "\"plain\":false")
        row?.let { runtime.saveUiState(it.id, uiJson) }
    }

    /** Plain text of the whole block (04 §2.4), for "Show as plain text", copy and history. */
    fun plainText(): String {
        val s = session ?: return ""
        val n = root() ?: return ""
        return runCatching { s.plainText(n) }.getOrDefault("")
    }

    /** Canonical source of the current version (View source). */
    fun source(): String = row?.source ?: program?.print(runtime.catalog) ?: lastBody.orEmpty()

    fun undo() { session?.let { fire(com.echoflow.weave.core.eval.WAction("undo", emptyList(), emptyList(), com.echoflow.weave.core.eval.Scope.EMPTY, com.echoflow.weave.core.parse.Span.NONE)) } }

    val canUndo: Boolean get() = (session?.historySize ?: 0) > 0

    fun resetAll() { session?.let { fire(com.echoflow.weave.core.eval.WAction("resetAll", emptyList(), emptyList(), com.echoflow.weave.core.eval.Scope.EMPTY, com.echoflow.weave.core.parse.Span.NONE)) } }

    fun close() {
        flush()
    }
}
