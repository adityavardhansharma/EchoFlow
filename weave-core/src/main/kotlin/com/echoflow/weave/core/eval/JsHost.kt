package com.echoflow.weave.core.eval

/**
 * Runs tier 4 `js fn` bodies (01 §15.5, 03 §10.12). `weave-core` has no JavaScript engine; the
 * optional `:weave-js` module implements this with QuickJS, and a host without one leaves
 * [EvalContext.js] null so every `js fn` call is `null`.
 *
 * One host belongs to one block (an isolated runtime per block). Calls are synchronous: the host
 * enforces the per-call limits itself.
 */
interface JsHost {
    /**
     * Calls the function [name] whose body is [source] (the raw string after `=`), with
     * parameters [params] bound to the JSON values in [argsJson] (a JSON array, one per param).
     */
    fun call(name: String, params: List<String>, source: String, argsJson: String): JsResult

    /** Frees the runtime. Later calls return [JsResult.Error]. */
    fun close() {}
}

sealed interface JsResult {
    /** The function returned [json], a JSON text (`null` for `undefined`). */
    data class Ok(val json: String) : JsResult

    /** Over the wall-time or memory limit (01 §15.5). The call is `null` with `E303`. */
    data class Limit(val what: String) : JsResult

    /** The body threw, didn't parse, or returned something that isn't JSON. The call is `null`. */
    data class Error(val message: String) : JsResult
}
