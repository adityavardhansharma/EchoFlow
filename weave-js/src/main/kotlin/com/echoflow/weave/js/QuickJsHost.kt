package com.echoflow.weave.js

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.echoflow.weave.core.eval.JsHost
import com.echoflow.weave.core.eval.JsResult
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A block's `js fn` runtime (03 §10.12, 01 §15.5): one QuickJS runtime per block, used from one
 * thread of its own.
 *
 * - Each call runs `function(params) { body }` in strict mode with the arguments parsed from JSON,
 *   and returns `JSON.stringify` of the result.
 * - No host objects are defined: no `fetch`, `require`, timers or I/O.
 * - [timeoutMs] of wall time and [memoryBytes] per runtime; over either is [JsResult.Limit]. A
 *   runtime that ran out of memory is replaced before the next call.
 * - The functions are pure, so results are cached by (name, source, arguments) for repeated
 *   evaluation (a slider drag re-reads the same binding many times).
 */
class QuickJsHost(
    private val timeoutMs: Long = TIMEOUT_MS,
    private val memoryBytes: Long = MEMORY_BYTES,
    private val cacheSize: Int = CACHE_SIZE,
) : JsHost {

    companion object {
        const val TIMEOUT_MS = 50L
        const val MEMORY_BYTES = 16L * 1024 * 1024
        const val CACHE_SIZE = 64
        /** Longest JSON accepted in or out of a call (05 §13.3 `STRING_CHARS` × 10). */
        const val MAX_JSON_CHARS = 1_000_000

        /** Globals some QuickJS builds add that a pure function must not reach. */
        private val REMOVED_GLOBALS = listOf("setTimeout", "clearTimeout", "setInterval", "clearInterval", "fetch", "require", "std", "os")
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "weave-js").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var runtime: QuickJs? = null
    private val cache = object : LinkedHashMap<String, JsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsResult>?) = size > cacheSize
    }

    @Volatile private var closed = false

    override fun call(name: String, params: List<String>, source: String, argsJson: String): JsResult {
        if (closed) return JsResult.Error("closed")
        if (argsJson.length > MAX_JSON_CHARS) return JsResult.Limit("arguments")
        val key = name + '\u0000' + source + '\u0000' + argsJson
        synchronized(cache) { cache[key]?.let { return it } }
        val result = try {
            runBlocking(dispatcher) { evaluate(params, source, argsJson) }
        } catch (e: Exception) {
            JsResult.Error(e.message ?: e.javaClass.simpleName)
        }
        // Limits depend on the moment (a cold runtime, a busy device), so only answers are cached.
        if (result is JsResult.Ok) synchronized(cache) { cache[key] = result }
        return result
    }

    private suspend fun evaluate(params: List<String>, source: String, argsJson: String): JsResult {
        val js = runtime ?: QuickJs.create(dispatcher).also { created ->
            created.memoryLimit = memoryBytes
            created.evaluationTimeoutMillis = timeoutMs
            created.evaluate<Any?>(REMOVED_GLOBALS.joinToString(";") { "delete globalThis.$it" } + ";undefined")
            runtime = created
        }
        return try {
            val out = js.evaluate<Any?>(program(params, source, argsJson), "weave-js-fn")
            val json = out as? String ?: return JsResult.Error("result is not JSON")
            if (json.length > MAX_JSON_CHARS) JsResult.Limit("result") else JsResult.Ok(json)
        } catch (e: QuickJsInterruptedException) {
            JsResult.Limit("time")
        } catch (e: QuickJsException) {
            val message = e.message.orEmpty()
            if ("out of memory" in message.lowercase()) {
                // A runtime that hit its limit may hold garbage it can't free; start fresh.
                runCatching { js.close() }
                runtime = null
                JsResult.Limit("memory")
            } else {
                JsResult.Error(message)
            }
        }
    }

    /**
     * The script for one call. Parameters `_` are never bound (02 §5.4); arguments are passed as
     * one JSON array literal, which is valid JavaScript.
     */
    private fun program(params: List<String>, source: String, argsJson: String): String {
        val names = params.mapIndexed { i, p -> if (p == "_") "__unused$i" else p }
        return buildString {
            append("(function () {\n\"use strict\";\n")
            append("const __fn = function (").append(names.joinToString(", ")).append(") {\n")
            append(source)
            append("\n};\n")
            append("const __out = __fn.apply(undefined, ").append(argsJson).append(");\n")
            append("const __json = JSON.stringify(__out);\n")
            append("return __json === undefined ? \"null\" : __json;\n")
            append("})()")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { runBlocking(dispatcher) { runtime?.close(); runtime = null } }
        executor.shutdown()
    }
}
