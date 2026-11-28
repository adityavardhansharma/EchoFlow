package com.echoflow.weave.runtime

import com.echoflow.weave.core.eval.HostEffect
import com.echoflow.weave.core.eval.Place
import com.echoflow.weave.core.eval.WBool
import com.echoflow.weave.core.eval.WDateTime
import com.echoflow.weave.core.eval.WList
import com.echoflow.weave.core.eval.WNull
import com.echoflow.weave.core.eval.WRec
import com.echoflow.weave.core.eval.WStr
import com.echoflow.weave.core.eval.WValue
import com.echoflow.weave.core.program.Compiler
import com.echoflow.weave.core.program.Edits
import com.echoflow.weave.core.program.ModelResponses
import com.echoflow.weave.core.program.StateCodec
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.HiddenPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Instant

/** What the model loop needs from the app: hidden completions and tools (05 §8, §8.7). */
interface HiddenModel {
    /** The streamed text of one hidden request, or null on failure. */
    suspend fun complete(systemPrompt: String, userText: String, timeoutMs: Long = 60_000, onPartial: (String) -> Unit = {}): String?
    /** Whether hidden requests are possible now (a cloud model is selected). */
    val available: Boolean
}

/** A block-safe tool (05 §8.7). */
interface WeaveTool {
    val name: String
    fun consentText(args: WValue): String
    suspend fun run(args: WValue): WValue
}

/**
 * The model loop (05 §8): `!ask`, remix, `!regen`, `lazy`, repair and `query`/`!tool`, with the
 * cost guards of §8.4 — one hidden request per block in flight, 20 per block per hour — and the
 * quality record for auto-downgrade (05 §10.4).
 */
class ModelLoop(
    private val runtime: WeaveRuntime,
    private val model: HiddenModel,
    private val tools: Map<String, WeaveTool>,
    private val autoFix: () -> Boolean,
    private val tierFor: () -> Int,
) : WeaveModelLoop {

    private val inFlight = HashMap<WeaveBlockController, Job>()
    private val hourly = HashMap<WeaveBlockController, ArrayDeque<Long>>()

    private fun allow(block: WeaveBlockController): Boolean {
        if (block.modelStopped) return false
        val q = hourly.getOrPut(block) { ArrayDeque() }
        val now = System.currentTimeMillis()
        while (q.isNotEmpty() && now - q.first() > 3_600_000) q.removeFirst()
        if (q.size >= 20 + block.extraBudget) { block.budgetBanner = true; return false }
        q.addLast(now)
        return true
    }

    private fun system(): String = EchoUiPrompt.editorSection(tierFor().coerceIn(1, 4), runtime.catalog)

    private fun stateJson(block: WeaveBlockController): String {
        val s = block.session ?: return "{}"
        val p = block.program ?: return "{}"
        return StateCodec.encodeState(p, s.state)
    }

    private fun blockId(block: WeaveBlockController) = block.blockId ?: "b1"

    override fun handle(block: WeaveBlockController, effect: HostEffect) {
        when (effect.kind) {
            "ask" -> ask(block, effect)
            "regen" -> regen(block)
            "refresh" -> (effect.args.firstOrNull() as? WStr)?.v?.let { block.refreshLazy(it) }
            "tool" -> tool(block, effect)
        }
    }

    private fun launch(block: WeaveBlockController, cancelOlder: Boolean, work: suspend () -> Unit) {
        if (!model.available) { block.flashChip("Live updates need a cloud model"); return }
        if (cancelOlder) inFlight[block]?.cancel() else if (inFlight[block]?.isActive == true) return
        inFlight[block] = runtime.scope.launch { try { work() } finally { if (inFlight[block] == coroutineContext[Job]) inFlight.remove(block) } }
    }

    /** 03 §9.2. */
    private fun ask(block: WeaveBlockController, e: HostEffect) {
        val prompt = (e.args.getOrNull(0) as? WStr)?.v ?: return
        val update = ((e.args.getOrNull(1) ?: e.named["update"]) as? WList)?.items?.mapNotNull { (it as? WStr)?.v } ?: emptyList()
        if (update.isEmpty()) return
        val with = (e.args.getOrNull(2) ?: e.named["with"]) ?: WList.EMPTY
        if (!allow(block)) return
        launch(block, cancelOlder = true) {
            block.setPending(update.toSet())
            try {
                val program = block.program ?: return@launch
                val user = HiddenPrompts.ask(blockId(block), block.title, block.source(), stateJson(block), StateCodec.encode(with), block.askLog, prompt, update)
                val text = model.complete(system(), user)
                val body = text?.let { ModelResponses.firstFence(it) }
                val (edit, _) = body?.let { ModelResponses.restrictAsk(program, it, update.toSet(), runtime.catalog) } ?: ("" to emptyList())
                val r = if (edit.isBlank()) null else Edits.apply(program, edit, runtime.catalog)
                if (r == null || !r.accepted) { block.askFailed(update.first()); return@launch }
                block.acceptModelEdit(r, "ask")
                block.recordAsk(prompt, update)
            } finally { block.setPending(emptySet()) }
        }
    }

    /** 05 §8.3: "Change this block…". */
    fun remix(block: WeaveBlockController, userText: String) {
        if (!allow(block)) return
        launch(block, cancelOlder = false) {
            block.setPending(block.program?.bindings?.keys?.toSet() ?: emptySet())
            try {
                val program = block.program ?: return@launch
                val text = model.complete(system(), HiddenPrompts.remix(blockId(block), block.title, block.source(), stateJson(block), userText))
                val r = text?.let { Edits.apply(program, ModelResponses.firstFence(it), runtime.catalog) }
                if (r == null || !r.accepted) { block.flashChip("Couldn't change this block"); return@launch }
                block.acceptModelEdit(r, "remix")
            } finally { block.setPending(emptySet()) }
        }
    }

    /** 03 §9.4. */
    fun regen(block: WeaveBlockController) {
        if (!allow(block)) return
        launch(block, cancelOlder = false) {
            block.setPending(block.program?.bindings?.keys?.toSet() ?: emptySet())
            try {
                val text = model.complete(system(), HiddenPrompts.regen(block.source(), stateJson(block)), timeoutMs = 90_000) ?: run { block.flashChip("Couldn't rewrite this block"); return@launch }
                val body = ModelResponses.firstFence(text)
                val next = Compiler.compile(body, runtime.catalog)
                if (!next.renderable()) { block.flashChip("Couldn't rewrite this block"); return@launch }
                block.replaceProgram(WeaveRuntime.withHeaderId(next.print(runtime.catalog), blockId(block)), "regen")
            } finally { block.setPending(emptySet()) }
        }
    }

    /**
     * 03 §9.3 / 05 §8.5: generate one lazy part, rendering it as it streams. It shares the block's
     * one-request guard with `!ask`, remix and regen; a part skipped while another request runs is
     * asked for again on the next render, which that request's result triggers.
     */
    fun lazy(block: WeaveBlockController, name: String, prompt: String, context: WRec, key: String) {
        if (block.lazyInFlight.contains(key) || inFlight[block]?.isActive == true) return
        if (!model.available) { block.flashChip("Live updates need a cloud model"); return }
        if (!allow(block)) return
        block.lazyInFlight = block.lazyInFlight + key
        launch(block, cancelOlder = false) {
            try {
                val user = HiddenPrompts.lazy(blockId(block), block.title, block.source(), stateJson(block), name, prompt, StateCodec.encode(context))
                val text = model.complete(system(), user) { partial ->
                    // the model streams on a background thread; the session is main-thread only
                    val body = ModelResponses.firstFence(partial)
                    runtime.scope.launch(Dispatchers.Main) { if (key in block.lazyInFlight) block.lazyPartial(name, key, body) }
                }
                val body = text?.let { ModelResponses.firstFence(it) }
                if (body == null || !block.lazyResult(name, key, body)) block.lazyFailed(name)
            } finally { block.lazyInFlight = block.lazyInFlight - key }
        }
    }

    /** 05 §8.9: one repair per block version when errors removed a statement, if enabled. */
    fun maybeRepair(block: WeaveBlockController) {
        val program = block.program ?: return
        val errors = program.errors
        recordQuality(errors.size)
        if (errors.isEmpty() || !autoFix() || block.repairedVersion == block.blockVersion) return
        block.repairedVersion = block.blockVersion
        if (!allow(block)) return
        launch(block, cancelOlder = false) {
            val affected = program.print(runtime.catalog).lines().filter { line ->
                errors.any { d -> line.contains(d.statement ?: "\u0000") } || line.startsWith("root ")
            }.joinToString("\n")
            val text = model.complete(system(), EchoUiPrompt.repair(blockId(block), errors.map { it.message }, affected)) ?: return@launch
            val r = Edits.apply(program, ModelResponses.firstFence(text), runtime.catalog)
            if (r.accepted && r.program.errors.size < errors.size) block.acceptModelEdit(r, "repair")
        }
    }

    // ---- quality record, 05 §10.4 -------------------------------------------------------------

    var qualityStore: ((String) -> String?)? = null
    var qualityWrite: ((String, String?) -> Unit)? = null
    var currentModelId: () -> String = { "" }

    private fun recordQuality(errorCount: Int) {
        val read = qualityStore ?: return; val write = qualityWrite ?: return
        val id = currentModelId().ifEmpty { return }
        val key = "echoui_quality_$id"
        val list = (StateCodec.decode(read(key) ?: "[]") as? WList)?.items?.mapNotNull { (it as? com.echoflow.weave.core.eval.WNum)?.v?.toInt() }.orEmpty()
        val next = (list + errorCount).takeLast(10)
        write(key, StateCodec.encode(WList(next.map { com.echoflow.weave.core.eval.WNum.of(it) })))
        if (next.size >= 5 && next.average() > 2.0) {
            write("echoui_downgrade_until_$id", (System.currentTimeMillis() + 7L * 86_400_000).toString())
            write(key, "[]")
        }
    }

    // ---- query and !tool, 03 §13 --------------------------------------------------------------

    fun toolFor(name: String): WeaveTool? = tools[name]

    private fun tool(block: WeaveBlockController, e: HostEffect) {
        val name = (e.args.getOrNull(0) as? WStr)?.v ?: return
        val args = e.args.getOrNull(1) ?: e.named["args"] ?: WRec(LinkedHashMap())
        val into = ((e.args.getOrNull(2) ?: e.named["into"]) as? WStr)?.v?.removePrefix("$") ?: return
        val place = Place(into, emptyList())
        val t = tools[name]
        if (t == null) { block.input(place, queryRecord(WNull, false, "Unknown tool")); return }
        if (!provenanceOk(block, t, args)) { block.input(place, queryRecord(WNull, false, LINK_NOT_IN_BLOCK)); return }
        block.requestConsent(t, args) { granted ->
            if (!granted) { block.input(place, queryRecord(WNull, false, "Not allowed")); return@requestConsent }
            block.input(place, queryRecord(WNull, true, null))
            runtime.scope.launch {
                val v = runCatching { t.run(args) }
                block.input(place, v.fold({ queryRecord(it, false, null) }, { queryRecord(WNull, false, it.message?.take(120) ?: "Failed") }))
            }
        }
    }

    /**
     * Runs a `query` declared in [stmt] (03 §13.1) after consent; re-runs on `every`. One job per
     * statement: new arguments cancel the old job, a result is accepted only while its arguments
     * are still current, and each repeat re-checks consent (a permission reset stops it) and waits
     * while the block is off screen or the app is in the background.
     */
    fun runQuery(block: WeaveBlockController, stmt: String, tool: String, args: WValue, everyMs: Long?) {
        val t = tools[tool]
        if (t == null) { block.setQuery(stmt, queryRecord(WNull, false, "Unknown tool"), args); return }
        if (!provenanceOk(block, t, args)) { block.setQuery(stmt, queryRecord(WNull, false, LINK_NOT_IN_BLOCK), args); return }
        block.requestConsent(t, args) { granted ->
            if (!granted) { block.setQuery(stmt, queryRecord(block.queryValueOf(stmt), false, "Not allowed"), args); return@requestConsent }
            block.startQueryJob(stmt, runtime.scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                while (true) {
                    if (!block.queryArgsStill(stmt, args)) break
                    block.setQuery(stmt, queryRecord(block.queryValueOf(stmt), true, null), args)
                    val v = runCatching { t.run(args) }
                    if (v.exceptionOrNull() is kotlinx.coroutines.CancellationException) throw v.exceptionOrNull()!!
                    block.setQuery(stmt, v.fold({ queryRecord(it, false, null) }, { queryRecord(block.queryValueOf(stmt), false, it.message?.take(120) ?: "Failed") }), args)
                    if (everyMs == null) break
                    delay(maxOf(everyMs, 60_000L))
                    block.awaitQueryVisible()
                    if (!block.hasConsent(t)) break
                }
            })
        }
    }

    /** 01 §6.5: `web.read` only opens URLs from the block's source or an earlier result. */
    private fun provenanceOk(block: WeaveBlockController, t: WeaveTool, args: WValue) =
        t.name != "web.read" || WeaveTools.readAllowed(args, block.knownText())

    companion object {
        private const val LINK_NOT_IN_BLOCK = "Only links shown in this block can be read"

        fun queryRecord(value: WValue, loading: Boolean, error: String?) = WRec.of(
            "value" to value, "loading" to WBool.of(loading), "error" to (error?.let { WStr(it) } ?: WNull),
            "at" to if (!loading && error == null) WDateTime(Instant.now()) else WNull,
        )

        /** 05 §8.5: SHA-256 of the canonical `{"p": prompt, "c": context}`. */
        fun lazyKey(prompt: WValue, context: WRec): String {
            val sorted = WRec(LinkedHashMap(context.fields.toSortedMap()))
            val json = "{\"p\":" + StateCodec.encode(prompt) + ",\"c\":" + StateCodec.encode(sorted) + "}"
            return MessageDigest.getInstance("SHA-256").digest(json.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
