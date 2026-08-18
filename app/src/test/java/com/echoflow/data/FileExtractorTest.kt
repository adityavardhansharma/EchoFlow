package com.echoflow.data

import com.echoflow.data.extract.Anydoc
import com.echoflow.data.extract.DocumentParser
import com.echoflow.data.extract.FileExtractor
import com.echoflow.data.extract.ImageTextRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FileExtractorTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `plain text uses the legacy reader`() = runBlocking {
        val file = tmp.newFile("notes.txt").apply { writeText("hello project") }
        val extractor = FileExtractor(parser = decliningParser(), ocr = null)
        val result = extractor.extract(file, "text/plain", "notes.txt")
        assertEquals(ExtractionStatus.EXTRACTED, result.status)
        assertEquals(ExtractionTier.LEGACY_TEXT, result.tier)
        assertEquals("hello project", result.text)
    }

    @Test fun `anydoc markdown is stored as extracted`() = runBlocking {
        val file = tmp.newFile("brief.docx").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val extractor = FileExtractor(
            parser = object : DocumentParser {
                override val available = true
                override fun convert(bytes: ByteArray, filename: String) =
                    Anydoc.Result.Text("# Heading\n\nBody")
            },
            ocr = null,
        )
        val result = extractor.extract(file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "brief.docx")
        assertEquals(ExtractionStatus.EXTRACTED, result.status)
        assertEquals(ExtractionTier.ANYDOC, result.tier)
        assertTrue(result.text!!.contains("Heading"))
    }

    @Test fun `encrypted documents fail without ocr`() = runBlocking {
        val file = tmp.newFile("secret.docx").apply { writeBytes(byteArrayOf(1)) }
        val extractor = FileExtractor(
            parser = object : DocumentParser {
                override val available = true
                override fun convert(bytes: ByteArray, filename: String) = Anydoc.Result.Failed("encrypted")
            },
            ocr = recordingOcr(),
        )
        val result = extractor.extract(file, "application/octet-stream", "secret.docx")
        assertEquals(ExtractionStatus.FAILED, result.status)
        assertNull(result.text)
    }

    @Test fun `pdf with a generic mime still goes to anydoc`() = runBlocking {
        val file = tmp.newFile("report.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val extractor = FileExtractor(
            parser = object : DocumentParser {
                override val available = true
                override fun convert(bytes: ByteArray, filename: String) =
                    Anydoc.Result.Text("# From PDF")
            },
            ocr = object : ImageTextRecognizer {
                override suspend fun ocrImage(file: File) = error("image OCR should not run")
                override suspend fun ocrPdf(file: File, maxPages: Int) = error("pdf OCR should not run")
            },
        )
        val result = extractor.extract(file, "application/octet-stream", "report.pdf")
        assertEquals(ExtractionStatus.EXTRACTED, result.status)
        assertEquals(ExtractionTier.ANYDOC, result.tier)
        assertEquals("# From PDF", result.text)
    }

    @Test fun `scanned pdf falls through to ocr`() = runBlocking {
        val file = tmp.newFile("scan.pdf").apply { writeBytes(byteArrayOf(1)) }
        val extractor = FileExtractor(
            parser = object : DocumentParser {
                override val available = true
                override fun convert(bytes: ByteArray, filename: String) = Anydoc.Result.TryOcr
            },
            ocr = object : ImageTextRecognizer {
                override suspend fun ocrImage(file: File) = null
                override suspend fun ocrPdf(file: File, maxPages: Int) = "page one"
            },
        )
        val result = extractor.extract(file, "application/pdf", "scan.pdf")
        assertEquals(ExtractionStatus.EXTRACTED, result.status)
        assertEquals(ExtractionTier.OCR, result.tier)
        assertEquals("page one", result.text)
    }

    @Test fun `images go straight to ocr`() = runBlocking {
        val file = tmp.newFile("shot.png").apply { writeBytes(byteArrayOf(1)) }
        val extractor = FileExtractor(
            parser = decliningParser(),
            ocr = object : ImageTextRecognizer {
                override suspend fun ocrImage(file: File) = "receipt total 12"
                override suspend fun ocrPdf(file: File, maxPages: Int) = error("pdf should not run")
            },
        )
        val result = extractor.extract(file, "image/png", "shot.png")
        assertEquals(ExtractionStatus.EXTRACTED, result.status)
        assertEquals(ExtractionTier.OCR, result.tier)
        assertEquals("receipt total 12", result.text)
    }

    @Test fun `unreadable files ask the provider`() = runBlocking {
        val file = tmp.newFile("mystery.bin").apply { writeBytes(byteArrayOf(0)) }
        val extractor = FileExtractor(parser = decliningParser(), ocr = recordingOcr())
        val result = extractor.extract(file, "application/octet-stream", "mystery.bin")
        assertEquals(ExtractionStatus.NEEDS_PROVIDER, result.status)
        assertNull(result.text)
    }

    private fun decliningParser() = object : DocumentParser {
        override val available = false
        override fun convert(bytes: ByteArray, filename: String) = Anydoc.Result.TryOcr
    }

    private fun recordingOcr() = object : ImageTextRecognizer {
        override suspend fun ocrImage(file: File) = null
        override suspend fun ocrPdf(file: File, maxPages: Int) = null
    }
}
