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
                firedJson = r.firedJson; uiJson = r.uiStateJson; blockVersion = r.version
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
        }
    }

    private fun publish(next: WeaveProgram) {
        val s = session
        if (s == null) {
            val created = Session(next, ctx ?: EvalContext(), runtime.catalog)
            row?.let { r ->
                created.restore(StateCodec.decodeState(next, r.stateJson, created.ctx.formatter))
                if (WeaveRuntime.hasFlag(r.firedJson, "appear")) created.markAppeared()
            }
            session = created
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
