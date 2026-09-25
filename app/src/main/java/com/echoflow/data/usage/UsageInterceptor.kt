package com.echoflow.data.usage

import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.ByteString.Companion.encodeUtf8
import okio.buffer

/** A finished response the ledger should read, handed off so parsing never runs on the caller. */
class UsageCapture(
    val provider: UsageProvider,
    val keyHash: String,
    val kind: UsageKind,
    val request: Request,
    val finishedAt: Long,
    /** SSE `data:` payloads that mention usage, or the single JSON body. */
    val documents: List<Buffer>,
)

/**
 * Records what each provider reports about a request, for every client it is added to.
 *
 * The response body is passed through untouched: bytes are copied aside as the caller reads them
 * and handed to [sink] once the body is exhausted or closed. Streams keep only the few events that
 * carry usage; JSON bodies are kept whole up to [MAX_JSON_BYTES]. Requests without a provider key,
 * unsuccessful responses and non-JSON bodies (audio, video downloads) are never touched.
 */
class UsageInterceptor(
    private val sink: (UsageCapture) -> Unit = UsageLedger::submit,
    private val clock: () -> Long = System::currentTimeMillis,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val provider = UsageProvider.forUrl(request.url) ?: return chain.proceed(request)
        val key = UsageKeys.from(request) ?: return chain.proceed(request)
        val response = chain.proceed(request)
        val body = response.body ?: return response
        if (!response.isSuccessful) return response
        val type = body.contentType() ?: return response
        val sse = type.subtype.equals("event-stream", ignoreCase = true)
        val json = type.subtype.contains("json", ignoreCase = true)
        if (!sse && !json) return response

        val tap = UsageTap(body.source(), sse) { documents ->
            if (documents.isEmpty()) return@UsageTap
            sink(
                UsageCapture(
                    provider = provider,
                    keyHash = UsageKeys.hash(key),
                    kind = UsageKind.forPath(request.url.encodedPath),
                    request = request,
                    finishedAt = clock(),
                    documents = documents,
                )
            )
        }
        return response.newBuilder()
            .body(tap.buffer().asResponseBody(type, body.contentLength()))
            .build()
    }

    companion object {
        /** Shared instance for every provider client. */
        val shared = UsageInterceptor()
        internal const val MAX_JSON_BYTES = 32L * 1024 * 1024
        private const val MAX_LINE_BYTES = 4L * 1024 * 1024
        private val USAGE_MARKER = "\"usage".encodeUtf8()
    }

    private class UsageTap(
        delegate: Source,
        private val sse: Boolean,
        private val onDone: (List<Buffer>) -> Unit,
    ) : ForwardingSource(delegate) {
        private val done = AtomicBoolean(false)
        private val pending = Buffer()
        private val documents = mutableListOf<Buffer>()
        private var overflowed = false
        private var skippingLine = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            val read = try {
                super.read(sink, byteCount)
            } catch (e: Exception) {
                finish()
                throw e
            }
            if (read == -1L) {
                finish()
                return -1L
            }
            if (sse) {
                sink.copyTo(pending, sink.size - read, read)
                drainLines()
            } else if (!overflowed) {
                sink.copyTo(pending, sink.size - read, read)
                if (pending.size > MAX_JSON_BYTES) {
                    overflowed = true
                    pending.clear()
                }
            }
            return read
        }

        override fun close() {
            finish()
            super.close()
        }

        /** Keeps only `data:` events that mention usage; a huge line (a base64 image) is dropped. */
        private fun drainLines() {
            while (true) {
                val newline = pending.indexOf('\n'.code.toByte())
                if (newline == -1L) {
                    if (pending.size > MAX_LINE_BYTES) {
                        pending.clear()
                        skippingLine = true
                    }
                    return
                }
                if (skippingLine) {
                    pending.skip(newline + 1)
                    skippingLine = false
                    continue
                }
                // Only decode lines that mention usage; content deltas and images are skipped as bytes.
                val usage = pending.indexOf(USAGE_MARKER)
                if (usage in 0 until newline) keepIfUsage(pending.readUtf8(newline)) else pending.skip(newline)
                pending.skip(1)
            }
        }

        private fun keepIfUsage(line: String) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("data:")) return
            val payload = trimmed.removePrefix("data:").trim()
            if (!payload.startsWith("{") || !payload.contains("\"usage")) return
            documents += Buffer().writeUtf8(payload)
        }

        private fun finish() {
            if (!done.compareAndSet(false, true)) return
            // A cancelled call can close the body from another thread; usage is never worth a crash.
            runCatching {
                if (sse) {
                    // A final event without a trailing newline still counts.
                    if (!skippingLine && pending.size > 0) keepIfUsage(pending.readUtf8())
                } else if (!overflowed && pending.size > 0) {
                    documents += pending.copy()
                }
                pending.clear()
                onDone(documents.toList())
            }
        }
    }
}
