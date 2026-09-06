package com.echoflow.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response

/** Cancellation closes both a pending connect and a blocking response-body read. */
internal suspend fun <T> Call.useCancellable(block: suspend (Response) -> T): T = coroutineScope {
    val call = this@useCancellable
    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { call.cancel() }
    }
    try {
        withContext(Dispatchers.IO) {
            try { call.execute().use { block(it) } }
            catch (e: Exception) {
                // Closing the socket often reports IOException first. Preserve cancellation
                // instead of allowing that I/O error to fail the parent scope.
                currentCoroutineContext().ensureActive()
                throw e
            }
        }
    } finally {
        cancellation.cancel()
    }
}
