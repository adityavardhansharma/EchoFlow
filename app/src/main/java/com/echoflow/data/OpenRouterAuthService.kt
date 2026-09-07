package com.echoflow.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Browser-based PKCE with OpenRouter's documented loopback callback.
 * The listener exists only during sign-in, binds IPv4 loopback only, and never receives a key.
 * A process restart invalidates the in-memory verifier: the user simply starts sign-in again.
 */
internal class OpenRouterAuthService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(30, TimeUnit.SECONDS).build(),
) {
    suspend fun signIn(openBrowser: suspend (String) -> Unit, onExchanging: suspend () -> Unit): String =
        withContext(Dispatchers.IO) {
            val verifier = randomToken()
            val state = randomToken()
            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 500
                val callback = "http://127.0.0.1:${server.localPort}/callback/$state"
                openBrowser(authorizationUrl(callback, verifier))
                val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10)
                var code: String? = null
                var declined = false
                while (code == null && !declined) {
                    currentCoroutineContext().ensureActive()
                    check(System.nanoTime() < deadline) { "Sign-in expired. Please try again." }
                    try {
                        server.accept().use { socket ->
                            socket.soTimeout = 1500
                            val line = readRequestLine(socket)
                            code = callbackCode(line, server.localPort, state)
                            declined = callbackUrl(line, server.localPort, state)?.queryParameter("error") != null
                            // Closing the browser must not discard an authorization already received.
                            runCatching { respond(socket, code != null || declined, declined) }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Poll cancellation and expiration, including idle or incomplete requests.
                    } catch (_: IOException) {
                        // Ignore malformed/aborted local connections; the valid callback may follow.
                    }
                }
                currentCoroutineContext().ensureActive()
                check(!declined) { "Sign-in was declined. Your saved connection has not changed." }
                onExchanging()
                exchange(checkNotNull(code), verifier)
            }
        }

    internal fun exchange(code: String, verifier: String): String {
        val body = JSONObject().put("code", code).put("code_verifier", verifier)
            .put("code_challenge_method", "S256").toString()
        val request = Request.Builder().url("https://openrouter.ai/api/v1/auth/keys")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                if (response.code == 400 || response.code == 403) "Sign-in expired or was declined. Please try again."
                else "OpenRouter could not complete sign-in. Please try again."
            }
            // Never surface server bodies or credentials in error messages.
            val key = runCatching { JSONObject(response.body?.string().orEmpty()).optString("key") }.getOrDefault("")
            check(key.startsWith("sk-or-") && key.length > 16 && key.none { it.isWhitespace() }) {
                "OpenRouter returned an invalid connection. Please try again."
            }
            key
        }
    }

    private fun readRequestLine(socket: Socket): String {
        val input = socket.getInputStream()
        val request = StringBuilder()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        repeat(16384) {
            if (System.nanoTime() >= deadline) return ""
            val byte = input.read()
            if (byte == -1) return ""
            request.append(byte.toChar())
            if (request.endsWith("\r\n\r\n")) return request.toString().substringBefore("\r\n")
        }
        return ""
    }

    private fun respond(socket: Socket, accepted: Boolean, declined: Boolean) {
        val message = if (declined) "Sign-in was cancelled. Your saved connection has not changed."
            else "Your authorization was received. Return to EchoFlow to finish connecting."
        val body = if (accepted) {
            """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Return to EchoFlow</title><body style="font:18px system-ui;padding:32px;max-width:440px;margin:auto"><h1>Continue in EchoFlow</h1><p>$message</p><a href="com.echoflow.openrouter://return" style="display:inline-block;padding:16px 24px;background:#222;color:white;border-radius:32px;text-decoration:none">Return to EchoFlow</a></body></html>"""
        } else "Invalid callback. Continue sign-in from EchoFlow."
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = if (accepted) "200 OK" else "400 Bad Request"
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
                "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'\r\n" +
                "Connection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    companion object {
        // Hex is within RFC 7636's verifier alphabet and carries 256 bits of entropy.
        internal fun randomToken(): String = ByteArray(32).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }

        internal fun challenge(verifier: String): String = android.util.Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )

        internal fun authorizationUrl(callback: String, verifier: String): String =
            "https://openrouter.ai/auth".toHttpUrl().newBuilder()
                .addQueryParameter("callback_url", callback)
                .addQueryParameter("code_challenge", challenge(verifier))
                .addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("key_label", "EchoFlow")
                .build().toString()

        internal fun callbackCode(requestLine: String, port: Int, state: String): String? {
            val url = callbackUrl(requestLine, port, state) ?: return null
            if (url.queryParameter("error") != null || url.queryParameterValues("code").size != 1) return null
            return url.queryParameter("code")?.takeIf { it.isNotBlank() && it.length <= 2048 }
        }

        private fun callbackUrl(requestLine: String, port: Int, state: String): okhttp3.HttpUrl? {
            val parts = requestLine.split(' ')
            if (parts.size != 3 || parts[0] != "GET" || parts[2] != "HTTP/1.1") return null
            if (!parts[1].startsWith("/callback/$state?")) return null
            val url = runCatching { "http://127.0.0.1:$port${parts[1]}".toHttpUrl() }.getOrNull() ?: return null
            if (url.encodedPath != "/callback/$state" || url.fragment != null) return null
            return url
        }
    }
}
