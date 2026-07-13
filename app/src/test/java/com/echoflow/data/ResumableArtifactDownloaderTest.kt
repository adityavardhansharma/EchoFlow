package com.echoflow.data

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResumableArtifactDownloaderTest {
    private val bytes = "0123456789".toByteArray()
    private val entry = LocalImageModelCatalog.entries.first().copy(
        artifactUrl = "https://example.invalid/model.zip",
        downloadBytes = bytes.size.toLong(),
        installedBytes = bytes.size.toLong(),
        artifactRevision = "test",
        artifactSha256 = "a".repeat(64),
    )

    @Test
    fun `partial file resumes with validated HTTP Range`() = runBlocking {
        val part = File.createTempFile("range-resume", ".part").apply {
            writeBytes(bytes.copyOfRange(0, 4))
            deleteOnExit()
        }
        var requestedRange: String? = null
        val downloader = ResumableArtifactDownloader(client { request ->
            requestedRange = request.header("Range")
            response(
                request = request,
                code = 206,
                body = bytes.copyOfRange(4, bytes.size),
                contentRange = "bytes 4-9/10",
            )
        })

        downloader.download(entry, part) { _, _ -> }

        assertEquals("bytes=4-", requestedRange)
        assertTrue(bytes.contentEquals(part.readBytes()))
    }

    @Test
    fun `server ignoring Range truncates partial before writing full response`() = runBlocking {
        val part = File.createTempFile("range-ignored", ".part").apply {
            writeBytes(bytes.copyOfRange(0, 4))
            deleteOnExit()
        }
        val downloader = ResumableArtifactDownloader(client { request ->
            assertEquals("bytes=4-", request.header("Range"))
            response(request, 200, bytes)
        })

        downloader.download(entry, part) { _, _ -> }

        assertEquals(bytes.size.toLong(), part.length())
        assertTrue(bytes.contentEquals(part.readBytes()))
    }

    @Test
    fun `invalid Content-Range deletes incompatible partial and requests retry`() = runBlocking {
        val part = File.createTempFile("range-invalid", ".part").apply {
            writeBytes(bytes.copyOfRange(0, 4))
            deleteOnExit()
        }
        val downloader = ResumableArtifactDownloader(client { request ->
            response(
                request = request,
                code = 206,
                body = bytes.copyOfRange(4, bytes.size),
                contentRange = "bytes 3-8/10",
            )
        })

        try {
            downloader.download(entry, part) { _, _ -> }
            throw AssertionError("expected retryable range failure")
        } catch (error: RetryableModelDownloadException) {
            assertTrue(error.message!!.contains("changed") || error.message!!.contains("invalid"))
        }
        assertFalse(part.exists())
    }

    private fun client(block: (okhttp3.Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain -> block(chain.request()) }).build()

    private fun response(
        request: okhttp3.Request,
        code: Int,
        body: ByteArray,
        contentRange: String? = null,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .body(body.toResponseBody())
        .apply { contentRange?.let { header("Content-Range", it) } }
        .build()
}
