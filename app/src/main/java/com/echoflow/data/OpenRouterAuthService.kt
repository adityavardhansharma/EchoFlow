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
        val body = callbackHtml(accepted, declined)
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = if (accepted) "200 OK" else "400 Bad Request"
        val csp = "default-src 'none'; style-src 'unsafe-inline'; " +
            (if (accepted && !declined) "script-src 'unsafe-inline'; " else "") +
            "frame-ancestors 'none'"
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Cache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n" +
                "Content-Security-Policy: $csp\r\n" +
                "Connection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray(Charsets.US_ASCII))
            write(bytes)
            flush()
        }
    }

    companion object {
        // Package binding prevents another app that registered our scheme from claiming the return.
        internal val RETURN_TO_APP_URI = "intent://return#Intent;scheme=com.echoflow.openrouter;" +
            "package=${com.echoflow.BuildConfig.APPLICATION_ID};end"
        // Hex is within RFC 7636's verifier alphabet and carries 256 bits of entropy.
        internal fun randomToken(): String = ByteArray(32).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it) }

        internal fun challenge(verifier: String): String = android.util.Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )

        internal fun callbackHtml(accepted: Boolean, declined: Boolean): String = when {
            !accepted -> page(
                heading = "Couldn’t finish sign-in",
                status = "Try again in the app",
                message = "This return link isn’t valid. Continue sign-in from EchoFlow.",
                autoOpen = false,
            )
            declined -> page(
                heading = "Sign-in cancelled",
                status = "Nothing was changed",
                message = "OpenRouter authorization was declined. Your saved connection has not changed.",
                autoOpen = false,
            )
            else -> page(
                heading = "Continue in EchoFlow",
                status = "OpenRouter approved",
                message = "Your authorization was received. Return to EchoFlow to finish connecting.",
                autoOpen = true,
            )
        }

        private fun page(heading: String, status: String, message: String, autoOpen: Boolean): String {
            val href = RETURN_TO_APP_URI
            val script = if (autoOpen) {
                """<script>setTimeout(function(){location.href=${JSONObject.quote(href)};},400);</script>"""
            } else ""
            return """
                <!doctype html><html lang="en"><head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light dark">
                <title>Return to EchoFlow</title>
                <style>
                :root{--bg:#f4f4f5;--card:#fff;--text:#18181b;--muted:#52525b;--btn:#18181b;--on-btn:#fafafa;--ring:#e4e4e7;--accent:#3f3f46}
                @media (prefers-color-scheme:dark){:root{--bg:#09090b;--card:#18181b;--text:#fafafa;--muted:#a1a1aa;--btn:#fafafa;--on-btn:#18181b;--ring:#3f3f46;--accent:#d4d4d8}}
                *{box-sizing:border-box}html,body{height:100%;margin:0}
                body{font:16px/1.45 system-ui,-apple-system,sans-serif;background:var(--bg);color:var(--text);display:flex;align-items:center;justify-content:center;padding:24px}
                main{width:min(100%,400px);background:var(--card);border:1px solid var(--ring);border-radius:28px;padding:28px 24px 24px;text-align:center}
                .mark{width:56px;height:56px;margin:0 auto 18px;border-radius:16px;background:var(--bg);color:var(--text);display:grid;place-items:center}
                .status{margin:0 0 8px;color:var(--accent);font-size:.75rem;font-weight:650;letter-spacing:.06em;text-transform:uppercase}
                h1{margin:0 0 10px;font-size:1.35rem;letter-spacing:-.02em;font-weight:650}
                p{margin:0;color:var(--muted)}
                a.btn{display:flex;align-items:center;justify-content:center;min-height:52px;margin:22px 0 14px;padding:14px 24px;border-radius:999px;background:var(--btn);color:var(--on-btn);text-decoration:none;font-weight:650}
                a.btn:focus-visible{outline:3px solid var(--accent);outline-offset:3px}
                .hint{font-size:.875rem}
                .local{margin-top:16px;font-size:.75rem}
                </style></head><body><main>
                <div class="mark" aria-hidden="true"><svg viewBox="0 0 48 48" width="36" height="36">
                <path d="M16 31c6.2-7.4 9.8-7.4 16 0" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/>
                <path d="M12 25c9-11.5 15-11.5 24 0" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"/>
                <circle cx="24" cy="35.5" r="2.3" fill="currentColor"/></svg></div>
                <p class="status">$status</p>
                <h1>$heading</h1>
                <p>$message</p>
                <a class="btn" href="$href">Return to EchoFlow</a>
                <p class="hint">If the button doesn’t open the app, switch to EchoFlow from Recents.</p>
                <p class="local">This page is from EchoFlow on this phone. It never shows your key.</p>
                </main>$script</body></html>
            """.trimIndent()
        }

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
