package com.echoflow.data

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

class CancellableHttpTest {
    @Test fun `cancellation interrupts a stalled response body`() = runBlocking {
        ServerSocket(0).use { server ->
            val peer = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* request headers */ }
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                    socket.soTimeout = 5000
                    runCatching { socket.getInputStream().read() }
                }
            }
            val call = OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}").build())
            val reading = CompletableDeferred<Unit>()
            val job = launch { call.useCancellable { response ->
                reading.complete(Unit)
                response.body!!.source().readByte()
            } }
            withTimeout(5000) { reading.await() }
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(call.isCanceled())
            peer.join(1000)
        }
    }
}
