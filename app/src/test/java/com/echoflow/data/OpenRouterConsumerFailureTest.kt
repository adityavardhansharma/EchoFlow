package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouterConsumerFailureTest {
    @Test fun `consumer failure escapes instead of silently losing a chunk`() = runBlocking {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val transport = OpenRouterStreamTransport(moshi.adapter(Any::class.java),
            moshi.adapter(OpenRouterService.OpenRouterStreamEvent::class.java),
            { _, _ -> Request.Builder().url("https://example.invalid/chat").build() },
            { _, _ -> }, { null })
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("data:{\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: [DONE]\n\n".toResponseBody()).build()
        }.build()
        val failure = IllegalStateException("consumer failed")
        val thrown = runCatching {
            transport.streamCompletion("test", "test/model", listOf(mapOf("role" to "user", "content" to "hi")),
                tools = null, params = null, httpClient = client) { throw failure }
        }.exceptionOrNull()
        assertEquals(failure.javaClass, thrown?.javaClass)
        assertEquals(failure.message, thrown?.message)
    }
}
