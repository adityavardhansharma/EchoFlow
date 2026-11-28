package com.echoflow.weave.runtime

import com.echoflow.BuildConfig
import com.echoflow.data.AppDatabase
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.eval.HostEffect
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.Edits
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.core.program.WeaveProgram
import com.echoflow.weave.runtime.storage.WeaveBlockRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Where a block's deferred and outbound actions go (03 §7.4). Implemented by the chat screen's
 * Compose host: toasts, haptics, the clipboard, the confirm sheet for outbound actions, `!say`
 * as a user message, and full-screen mode.
 */
/** Hidden model requests for `!ask`, `lazy`, remix, repair and `!regen` (05 §8). */
interface WeaveModelLoop {
    fun handle(block: WeaveBlockController, effect: HostEffect)
}

interface WeaveHost {
    fun perform(block: WeaveBlockController, effect: HostEffect)
    fun rejectHaptic() {}
}

/**
 * One per chat screen (05 §4.1). Holds the block controllers in an LRU of 64 keyed by
 * `(messageId, blockKey)`, the catalog this build renders, and storage. Thread scope (01 §6.5):
 * blocks read each other's exports through [exportOf].
 */
class WeaveRuntime(
    private val db: AppDatabase,
    val registry: Set<String>,
    var host: WeaveHost? = null,
    /** One-shot markers (`appear`) of kept blocks, which have no `weave_blocks` row to hold them. */
    private val libraryFired: android.content.SharedPreferences? = null,
) {
    var modelLoop: WeaveModelLoop? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO
    /**
     * Storage writes outlive the screen: [close] flushes every controller and then cancels [scope],
     * so the final writes must not run on it or leaving within the save delay would drop them.
     */
    private val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao = db.weaveBlockDao()
    private val storage = db.weaveStorageDao()
    /**
     * Block-id allocation is serialised for the whole process, not per runtime: two screens can
     * hold runtimes for the same chat (a navigation transition), and both must see each other's ids.
     */
    private val idLock = ID_LOCK

    /** The catalog this build can render: phase-filtered and limited to registered renderers (05 §3.3). */
    val catalog: Catalog = BuiltinCatalog.restrictedTo(4, BuildConfig.ECHO_UI_PHASE).registeredOnly(registry)

    private val controllers = object : LinkedHashMap<String, WeaveBlockController>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeaveBlockController>): Boolean {
            val evict = size > 64
            if (evict) { eldest.value.close(); forgetStored(eldest.value.chatId) }
            return evict
        }
    }

    /** The same controller across recompositions while it stays in the LRU (05 §4.1). */
    fun controller(messageId: String?, chatId: String?, blockKey: Int): WeaveBlockController {
        val key = "${messageId ?: "live"}:$blockKey"
        return controllers.getOrPut(key) { WeaveBlockController(messageId, chatId, blockKey, this) }.also { c ->
            if (chatId != null && c.chatId == null) c.chatId = chatId
        }
    }

    /** Live controllers of a chat, for exports and edits. */
    private fun live(chatId: String) = controllers.values.filter { it.chatId == chatId }

    /**
     * `id::name` from an earlier block's `export` (01 §6.5). A block on screen answers from its live
     * session; one that isn't composed (scrolled away, evicted, or not reached yet when a long chat
     * opens at the bottom) answers from its stored row, loaded once per chat.
     */
    fun exportOf(chatId: String?, blockId: String, name: String): WValue? {
        chatId ?: return null
        val c = live(chatId).firstOrNull { it.blockId == blockId }
        val s = c?.session ?: storedSession(chatId, blockId) ?: return null
        val p = s.program
        return when {
            name.startsWith("$") && name.removePrefix("$") in p.exports.map { it.removePrefix("$") } -> s.state[name.removePrefix("$")]
            name in p.exports -> runCatching { s.binding(name) }.getOrNull()
            else -> null
        }
    }

    /** Stored rows per chat, for exports of blocks that aren't composed. */
    private val storedRows = HashMap<String, List<WeaveBlockRow>>()
    private val loadingRows = HashSet<String>()
    /** Sessions rebuilt from stored rows, by row id, with the row's `updatedAt`; least recently used go first. */
    private val storedSessions = object : LinkedHashMap<String, Pair<Long, com.echoflow.weave.core.eval.Session>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, com.echoflow.weave.core.eval.Session>>) = size > 32
    }
    /** Exports being resolved from stored rows, so `a::x` ↔ `b::y` can't recurse forever. */
    private val resolving = HashSet<String>()

    private fun storedSession(chatId: String, blockId: String): com.echoflow.weave.core.eval.Session? {
        val rows = storedRows[chatId]
        if (rows == null) {
            if (loadingRows.add(chatId)) scope.launch {
                val loaded = runCatching { withContext(io) { dao.forChat(chatId) } }.getOrDefault(emptyList())
                storedRows[chatId] = loaded
                loadingRows.remove(chatId)
                exportsChanged(chatId, null)
            }
            return null
        }
        val row = rows.lastOrNull { it.blockId == blockId } ?: return null
        storedSessions[row.id]?.let { (at, s) -> if (at == row.updatedAt) return s }
        val program = Compiler.compile(row.source, catalog)
        // this block's own `id::name` reads resolve like a live block's, through the runtime
        val ctx = com.echoflow.weave.core.eval.EvalContext(exports = { b, n -> exportGuarded(chatId, b, n) })
        val s = runCatching {
            com.echoflow.weave.core.eval.Session(program, ctx, catalog).also { it.restore(StateCodec.decodeState(program, row.stateJson)) }
        }.getOrNull() ?: return null
        storedSessions[row.id] = row.updatedAt to s
        return s
    }

    private fun exportGuarded(chatId: String, blockId: String, name: String): WValue? {
        val key = "$chatId/$blockId::$name"
        if (!resolving.add(key)) return null
        try { return exportOf(chatId, blockId, name) } finally { resolving.remove(key) }
    }

    /**
     * An exporting block changed (or stored rows arrived): blocks of the chat that read `id::name`
     * re-evaluate. [from] is the block that changed, skipped.
     */
    fun exportsChanged(chatId: String?, from: WeaveBlockController?) {
        chatId ?: return
        live(chatId).forEach { if (it !== from) it.exportsChanged() }
    }

    /** A controller left the cache: its stored row is now the latest copy, so reload rows. */
    private fun forgetStored(chatId: String?) {
        chatId ?: return
        storedRows.remove(chatId)?.forEach { storedSessions.remove(it.id) }
    }

    private val library = db.weaveLibraryDao()

    /**
     * The stored row for a block. A `library:<id>` key is a kept block (01 §23.5): it comes back
     * as an in-memory row whose state saves go to `weave_library`.
     */
    suspend fun loadRow(messageId: String, blockKey: Int): WeaveBlockRow? = if (isArchived(messageId)) null else withContext(io) {
        if (messageId.startsWith(LIBRARY)) {
            val l = library.get(messageId.removePrefix(LIBRARY)) ?: return@withContext null
            WeaveBlockRow(
                id = messageId, chatId = "", messageId = messageId, blockKey = blockKey, blockId = "b1", version = 1, form = "source",
                source = l.source, stateJson = l.stateJson, historyJson = "[]", lazyJson = l.lazyJson, queryJson = "{}", consentJson = "{}",
                firedJson = libraryFired?.getString(messageId, null) ?: "{}", askLogJson = "[]", uiStateJson = "{}",
                createdAt = l.createdAt, updatedAt = l.updatedAt,
            )
        } else dao.get(messageId, blockKey)
    }

    /** "Keep in Library" (01 §23.5): source and state of the current version. */
    fun keep(controller: WeaveBlockController) {
        val p = controller.program ?: return
        val s = controller.session ?: return
        val now = System.currentTimeMillis()
        val row = com.echoflow.weave.runtime.storage.WeaveLibraryRow(
            id = java.util.UUID.randomUUID().toString(), title = p.header.title ?: "Block", icon = p.header.icon ?: "widgets",
            // generated lazy parts go with it: the Library has no model to make them again
            source = controller.source(), stateJson = StateCodec.encodeState(p, s.state), lazyJson = controller.lazyJson(), pinnedWidgetId = null,
            createdAt = now, updatedAt = now,
        )
        writes.launch { library.upsert(row) }
    }

    fun observeLibrary() = library.observeAll()
    fun deleteFromLibrary(id: String) { writes.launch { library.delete(id) } }

    /** Inserts the row for a block whose fence just closed (05 §9.3); returns the stored row. */
    suspend fun createRow(controller: WeaveBlockController, program: WeaveProgram, source: String): WeaveBlockRow? {
        val messageId = controller.messageId ?: return null
        // The reply row may not be written yet when the fence closes: wait for it (FK, 05 §9.1).
        repeat(20) { attempt ->
            val chatId = controller.chatId
            if (chatId != null) {
                val row = runCatching { insertRow(controller, messageId, chatId, program, source) }.getOrNull()
                if (row != null) return row
            }
            kotlinx.coroutines.delay(500L + attempt * 250L)
        }
        return null
    }

    private suspend fun insertRow(controller: WeaveBlockController, messageId: String, chatId: String, program: WeaveProgram, source: String): WeaveBlockRow? {
        return idLock.withLock {
            withContext(io) {
                dao.get(messageId, controller.blockKey)?.let { return@withContext it }
                val used = dao.blockIds(chatId).toSet()
                val id = program.header.id?.takeIf { it !in used } ?: generateSequence(1) { it + 1 }.map { "b$it" }.first { it !in used }
                val now = System.currentTimeMillis()
                val row = WeaveBlockRow(
                    id = java.util.UUID.randomUUID().toString(), chatId = chatId, messageId = messageId,
                    blockKey = controller.blockKey, blockId = id, version = 1, form = "source",
                    source = withHeaderId(source, id),
                    stateJson = StateCodec.encodeState(program, controller.session?.state ?: emptyMap()),
                    historyJson = "[]", lazyJson = "{}", queryJson = "{}", consentJson = "{}",
                    firedJson = "{}", askLogJson = "[]", uiStateJson = "{}", createdAt = now, updatedAt = now,
                )
                if (storage.createBlock(row)) row else dao.get(messageId, controller.blockKey)
            }
        }
    }

    fun saveState(rowId: String, stateJson: String) {
        writes.launch {
            if (rowId.startsWith(LIBRARY)) library.updateState(rowId.removePrefix(LIBRARY), stateJson, System.currentTimeMillis())
            else dao.updateState(rowId, stateJson, "[]", System.currentTimeMillis())
        }
    }

    fun saveFired(rowId: String, firedJson: String) {
        if (rowId.startsWith(LIBRARY)) { libraryFired?.edit()?.putString(rowId, firedJson)?.apply(); return }
        writes.launch { dao.updateFired(rowId, firedJson, System.currentTimeMillis()) }
    }

    fun saveUiState(rowId: String, uiJson: String) {
        writes.launch { dao.updateUiState(rowId, uiJson, System.currentTimeMillis()) }
    }

    fun saveAskLog(rowId: String, json: String) { writes.launch { dao.updateAskLog(rowId, json, System.currentTimeMillis()) } }
    fun saveLazy(rowId: String, json: String) {
        writes.launch {
            if (rowId.startsWith(LIBRARY)) library.updateLazy(rowId.removePrefix(LIBRARY), json, System.currentTimeMillis())
            else dao.updateLazy(rowId, json, System.currentTimeMillis())
        }
    }
    fun saveQueries(rowId: String, json: String) { writes.launch { dao.updateQueries(rowId, json, System.currentTimeMillis()) } }
    fun saveConsent(rowId: String, json: String) { writes.launch { dao.updateConsent(rowId, json, System.currentTimeMillis()) } }

    fun saveVersion(rowId: String, version: Int, source: String, cause: String) {
        if (rowId.startsWith(LIBRARY)) { writes.launch { library.updateSource(rowId.removePrefix(LIBRARY), source, System.currentTimeMillis()) }; return }
        writes.launch { storage.newVersion(rowId, version, source, cause, System.currentTimeMillis()) }
    }

    suspend fun versions(rowId: String) = withContext(io) { db.weaveVersionDao().forBlock(rowId) }

    /**
     * Applies an edit fence (02 §11, 05 §5.2) from message [fromMessageId] to the chat's block
     * [targetId] once, when the fence closes. Returns the target's title, or null when no block
     * matched or the edit was rejected.
     */
    suspend fun applyEdit(chatId: String, targetId: String, body: String, fromMessageId: String, fenceKey: Int): EditOutcome {
        val mark = "edit:$fromMessageId:$fenceKey"
        val row = withContext(io) { dao.forChat(chatId).lastOrNull { it.blockId == targetId } } ?: return EditOutcome.NoTarget
        val title = Compiler.compile(row.source, catalog).header.title ?: targetId
        if (row.firedJson.contains("\"$mark\"")) return EditOutcome.Applied(title)
        val liveCtl = live(chatId).firstOrNull { it.blockId == targetId }
        val base = liveCtl?.session?.program ?: Compiler.compile(row.source, catalog)
        val r = withContext(Dispatchers.Default) { Edits.apply(base, body, catalog) }
        val fired = addFlag(row.firedJson, mark)
        withContext(io) { dao.updateFired(row.id, fired, System.currentTimeMillis()) }
        if (!r.accepted) return EditOutcome.Rejected(title, r.rejection ?: "rejected")
        val source = withHeaderId(r.program.print(catalog), targetId)
        withContext(io) { storage.newVersion(row.id, row.version + 1, source, "edit", System.currentTimeMillis()) }
        // `reset` applies once, when the edit is accepted (02 §11.3): clear the stored values now,
        // so a block that isn't on screen re-initialises them and later changes are kept
        // (also when the block's controller exists but hasn't loaded its session yet)
        if (liveCtl?.session == null && r.resetStates.isNotEmpty()) {
            val stored = StateCodec.decode(row.stateJson) as? com.echoflow.weave.core.eval.WRec
            if (stored != null && r.resetStates.any { it in stored.fields }) {
                val kept = com.echoflow.weave.core.eval.WRec(LinkedHashMap(stored.fields.filterKeys { it !in r.resetStates }))
                withContext(io) { dao.updateState(row.id, StateCodec.encode(kept), row.historyJson, System.currentTimeMillis()) }
            }
        }
        liveCtl?.applyEdit(r, row.version + 1, fired)
        // stored copies of this chat's rows are now stale: reload them, and let readers of the
        // edited block's exports re-evaluate
        forgetStored(chatId)
        exportsChanged(chatId, null)
        return EditOutcome.Applied(title)
    }

    sealed interface EditOutcome {
        data object NoTarget : EditOutcome
        data class Applied(val title: String) : EditOutcome
        data class Rejected(val title: String, val why: String) : EditOutcome
    }

    fun dispatch(controller: WeaveBlockController, effect: HostEffect) {
        // hidden model requests go to the model loop; everything else to the UI host
        if (effect.kind in MODEL_ACTIONS) {
            val loop = modelLoop
            // the Library (and any host without a model) can't run hidden requests: say so
            if (loop == null) controller.flashChip("Live updates work in chat") else loop.handle(controller, effect)
        } else host?.perform(controller, effect)
    }

    fun close() {
        controllers.values.forEach { it.close() }
        controllers.clear()
        scope.cancel()
    }

    companion object {
        private val ID_LOCK = Mutex()
        private const val ARCHIVED = "#version:"

        /** The controller key for an older answer version: it never gets or reads a stored row. */
        fun archivedKey(messageId: String, version: Int) = "$messageId$ARCHIVED$version"
        fun isArchived(messageId: String?) = messageId?.contains(ARCHIVED) == true
        const val LIBRARY = "library:"
        private val MODEL_ACTIONS = setOf("ask", "refresh", "regen", "tool")
        private val HEADER = Regex("""^@weave\s+(\d+)([^\n]*)""")

        /** Ensures the stored source's header carries the thread id, so later edits can target it. */
        fun withHeaderId(source: String, id: String): String {
            val m = HEADER.find(source)
            if (m == null) return "@weave 1 id: $id\n$source"
            val attrs = m.groupValues[2]
            val existing = headerIdRange(attrs)
            // the thread may have given this block a different id than the model wrote (a reused
            // id); the stored header must name the id the row is stored under, or later edits
            // would target the other block
            if (existing != null) {
                if (attrs.substring(existing) == id) return source
                val fixed = attrs.replaceRange(existing, id)
                return source.replaceRange(m.range, "@weave ${m.groupValues[1]}$fixed")
            }
            return source.replaceRange(m.range, "@weave ${m.groupValues[1]} id: $id$attrs")
        }

        /** The range of the `id:` value in header attributes, skipping quoted text (a title saying "id: x"). */
        private fun headerIdRange(attrs: String): IntRange? {
            var i = 0
            var quote: Char? = null
            while (i < attrs.length) {
                val c = attrs[i]
                when {
                    quote != null -> { if (c == '\\') i++ else if (c == quote) quote = null }
                    c == '"' || c == '\'' -> quote = c
                    attrs.startsWith("id", i) && (i == 0 || !attrs[i - 1].isLetterOrDigit() && attrs[i - 1] != '_') -> {
                        var k = i + 2
                        while (k < attrs.length && attrs[k] == ' ') k++
                        if (k < attrs.length && attrs[k] == ':') {
                            k++
                            while (k < attrs.length && attrs[k] == ' ') k++
                            val start = k
                            while (k < attrs.length && (attrs[k].isLetterOrDigit() || attrs[k] == '_' || attrs[k] == '-')) k++
                            if (k > start) return start until k
                        }
                    }
                }
                i++
            }
            return null
        }

        fun addFlag(json: String, flag: String): String {
            val rec = StateCodec.decode(json) as? com.echoflow.weave.core.eval.WRec ?: com.echoflow.weave.core.eval.WRec(LinkedHashMap())
            val m = LinkedHashMap(rec.fields); m[flag] = com.echoflow.weave.core.eval.WBool.TRUE
            return StateCodec.encode(com.echoflow.weave.core.eval.WRec(m))
        }

        fun hasFlag(json: String, flag: String) = (StateCodec.decode(json) as? com.echoflow.weave.core.eval.WRec)?.fields?.get(flag) == com.echoflow.weave.core.eval.WBool.TRUE
    }
}
