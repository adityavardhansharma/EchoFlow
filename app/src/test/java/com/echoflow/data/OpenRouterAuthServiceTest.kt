package com.echoflow.data

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.Socket

@RunWith(RobolectricTestRunner::class)
class OpenRouterAuthServiceTest {
    @Test fun `browser return is restricted to EchoFlow package`() {
        val intent = android.content.Intent.parseUri(OpenRouterAuthService.RETURN_TO_APP_URI, android.content.Intent.URI_INTENT_SCHEME)
        assertEquals(com.echoflow.BuildConfig.APPLICATION_ID, intent.`package`)
        assertEquals("com.echoflow.openrouter://return", intent.dataString)
        assertNull(intent.selector)
    }
    @Test fun `PKCE uses fresh verifiers and unpadded base64url challenges`() {
        val verifier = OpenRouterAuthService.randomToken()
        assertEquals(64, verifier.length)
        assertTrue(OpenRouterAuthService.challenge(verifier).matches(Regex("[A-Za-z0-9_-]{43}")))
        assertNotEquals(OpenRouterAuthService.randomToken(), OpenRouterAuthService.randomToken())
    }

    @Test fun `callback rejects foreign state duplicate code and non GET requests`() {
        fun code(line: String) = OpenRouterAuthService.callbackCode(line, 1234, "expected-state")
        assertEquals("abc", code("GET /callback/expected-state?code=abc HTTP/1.1"))
        assertNull(code("GET /callback/wrong-state?code=abc HTTP/1.1"))
        assertNull(code("GET /callback/expected-state?code=abc&code=def HTTP/1.1"))
        assertNull(code("POST /callback/expected-state?code=abc HTTP/1.1"))
        assertNull(code("GET /callback/expected-state?code= HTTP/1.1"))
        assertNull(code("GET /callback/expected-state?error=access_denied HTTP/1.1"))
        assertNull(code("GET http://evil.test/callback/expected-state?code=abc HTTP/1.1"))
    }

    @Test fun `browser callback exchanges authorization with original verifier`() = runBlocking {
        var exchanged = false
        var expectedChallenge = ""
        val browser = CompletableDeferred<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://openrouter.ai/api/v1/auth/keys", request.url.toString())
            assertEquals("POST", request.method)
            assertNull(request.header("Authorization"))
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            val body = org.json.JSONObject(buffer.readUtf8())
            assertEquals("authorization-code", body.getString("code"))
            assertEquals("S256", body.getString("code_challenge_method"))
            assertEquals(expectedChallenge,
                OpenRouterAuthService.challenge(body.getString("code_verifier")))
            exchanged = true
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{\"key\":\"sk-or-v1-returned-user-key\"}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val signIn = async { OpenRouterAuthService(client).signIn({
            expectedChallenge = it.toHttpUrl().queryParameter("code_challenge").orEmpty()
            browser.complete(it)
        }, {}) }
        val callback = browser.await().toHttpUrl().queryParameter("callback_url")!!.toHttpUrl()
        val requestUrl = callback.newBuilder().addQueryParameter("code", "authorization-code").build()
        // Synchronous socket request uses a real loopback listener; no external account required.
        Socket("127.0.0.1", callback.port).use { socket ->
            socket.soTimeout = 5000
            socket.getOutputStream().write("GET ${requestUrl.encodedPath}?${requestUrl.encodedQuery} HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
            val page = socket.getInputStream().bufferedReader().readText()
            assertTrue(page.contains("Return to EchoFlow"))
            assertTrue(page.contains("OpenRouter approved"))
            assertTrue(page.contains("prefers-color-scheme:dark"))
            assertTrue(page.contains("This page is from EchoFlow on this phone"))
            assertTrue(page.contains("location.href="))
            assertTrue(page.contains(OpenRouterAuthService.RETURN_TO_APP_URI))
            assertFalse(page.contains("href=\"com.echoflow.openrouter:"))
            assertFalse(page.contains("authorization-code"))
            assertFalse(page.contains("sk-or-"))
        }
        assertEquals("sk-or-v1-returned-user-key", signIn.await())
        assertTrue(exchanged)
    }

    @Test fun `callback pages cover success cancel and invalid without leaking secrets`() {
        val success = OpenRouterAuthService.callbackHtml(accepted = true, declined = false)
        val cancelled = OpenRouterAuthService.callbackHtml(accepted = true, declined = true)
        val invalid = OpenRouterAuthService.callbackHtml(accepted = false, declined = false)
        assertTrue(success.contains("Return to EchoFlow"))
        assertTrue(success.contains("location.href="))
        assertFalse(cancelled.contains("<script>"))
        assertTrue(cancelled.contains("Sign-in cancelled"))
        assertFalse(invalid.contains("<script>"))
        assertTrue(invalid.contains("Couldn’t finish sign-in"))
        listOf(success, cancelled, invalid).forEach { page ->
            assertTrue(page.contains(OpenRouterAuthService.RETURN_TO_APP_URI))
            assertTrue(page.contains("color-scheme"))
            assertFalse(page.contains("sk-or-"))
        }
    }

    @Test fun `cancel closes listener without exchanging a key`() = runBlocking {
        val browser = CompletableDeferred<String>()
        val signIn = async { OpenRouterAuthService().signIn({ browser.complete(it) }, { fail("Must not exchange") }) }
        val callback = browser.await().toHttpUrl().queryParameter("callback_url")!!.toHttpUrl()
        signIn.cancelAndJoin()
        assertThrows(java.net.ConnectException::class.java) { Socket("127.0.0.1", callback.port).close() }
        Unit
    }
}
