package com.echoflow.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource

/*
 * The request-and-read steps every direct provider call repeats: run the call, turn a failed
 * status into the provider's error text, and read a streamed body line by line.
 */

/**
 * Runs [request] and hands the open body to [read]. A non-2xx status throws with the text
 * [error] builds from its code and body.
 */
internal inline fun OkHttpClient.readStream(
    request: Request,
    error: (code: Int, body: String) -> String,
    read: (BufferedSource) -> Unit,
) {
    newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception(error(response.code, response.body?.string().orEmpty()))
        val body = response.body ?: throw Exception("Empty stream body received.")
        body.source().use { read(it) }
    }
}

/** Runs [request] and returns its body text, or throws with the text [error] builds. */
internal inline fun OkHttpClient.fetchText(request: Request, error: (code: Int, body: String) -> String): String =
    newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw Exception(error(response.code, body))
        body
    }

/** Calls [onData] with each `data:` payload, skipping blank ones and the `[DONE]` marker. */
internal inline fun BufferedSource.forEachSseData(onData: (String) -> Unit) {
    while (!exhausted()) {
        val line = readUtf8Line() ?: break
        val data = SseDecoder.dataLine(line) ?: continue
        if (data.isBlank() || data == "[DONE]") continue
        onData(data)
    }
}

/** Calls [onLine] with each non-blank line, for newline-delimited JSON streams (Ollama). */
internal inline fun BufferedSource.forEachJsonLine(onLine: (String) -> Unit) {
    while (!exhausted()) {
        val line = readUtf8Line() ?: break
        if (line.isBlank()) continue
        onLine(line)
    }
}
