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
) {
    var modelLoop: WeaveModelLoop? = null

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val io = Dispatchers.IO
    private val dao = db.weaveBlockDao()
    private val storage = db.weaveStorageDao()
    private val idLock = Mutex()

    /** The catalog this build can render: phase-filtered and limited to registered renderers (05 §3.3). */
    val catalog: Catalog = BuiltinCatalog.restrictedTo(4, BuildConfig.ECHO_UI_PHASE).registeredOnly(registry)

    private val controllers = object : LinkedHashMap<String, WeaveBlockController>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WeaveBlockController>): Boolean {
            val evict = size > 64
            if (evict) eldest.value.close()
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

    /** `id::name` from an earlier block's `export` (01 §6.5). */
    fun exportOf(chatId: String?, blockId: String, name: String): WValue? {
        chatId ?: return null
        val c = live(chatId).firstOrNull { it.blockId == blockId } ?: return null
        val s = c.session ?: return null
        val p = s.program
        return when {
            name.startsWith("$") && name.removePrefix("$") in p.exports.map { it.removePrefix("$") } -> s.state[name.removePrefix("$")]
            name in p.exports -> s.binding(name)
            else -> null
        }
    }

    suspend fun loadRow(messageId: String, blockKey: Int): WeaveBlockRow? = withContext(io) { dao.get(messageId, blockKey) }

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
        scope.launch(io) { dao.updateState(rowId, stateJson, "[]", System.currentTimeMillis()) }
    }

    fun saveFired(rowId: String, firedJson: String) {
        scope.launch(io) { dao.updateFired(rowId, firedJson, System.currentTimeMillis()) }
    }

    fun saveUiState(rowId: String, uiJson: String) {
        scope.launch(io) { dao.updateUiState(rowId, uiJson, System.currentTimeMillis()) }
    }

    fun saveVersion(rowId: String, version: Int, source: String, cause: String) {
        scope.launch(io) { storage.newVersion(rowId, version, source, cause, System.currentTimeMillis()) }
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
        liveCtl?.applyEdit(r, row.version + 1, fired)
        return EditOutcome.Applied(title)
    }

    sealed interface EditOutcome {
        data object NoTarget : EditOutcome
        data class Applied(val title: String) : EditOutcome
        data class Rejected(val title: String, val why: String) : EditOutcome
    }

    fun dispatch(controller: WeaveBlockController, effect: HostEffect) {
        host?.perform(controller, effect)
    }

    fun close() {
        controllers.values.forEach { it.close() }
        controllers.clear()
        scope.cancel()
    }

    companion object {
        private val HEADER = Regex("""^@weave\s+(\d+)([^\n]*)""")

        /** Ensures the stored source's header carries the thread id, so later edits can target it. */
        fun withHeaderId(source: String, id: String): String {
            val m = HEADER.find(source)
            if (m == null) return "@weave 1 id: $id\n$source"
            if (Regex("""\bid\s*:""").containsMatchIn(m.groupValues[2])) return source
            return source.replaceRange(m.range, "@weave ${m.groupValues[1]} id: $id${m.groupValues[2]}")
        }

        fun addFlag(json: String, flag: String): String {
            val rec = StateCodec.decode(json) as? com.echoflow.weave.core.eval.WRec ?: com.echoflow.weave.core.eval.WRec(LinkedHashMap())
            val m = LinkedHashMap(rec.fields); m[flag] = com.echoflow.weave.core.eval.WBool.TRUE
            return StateCodec.encode(com.echoflow.weave.core.eval.WRec(m))
        }

        fun hasFlag(json: String, flag: String) = (StateCodec.decode(json) as? com.echoflow.weave.core.eval.WRec)?.fields?.get(flag) == com.echoflow.weave.core.eval.WBool.TRUE
    }
}
