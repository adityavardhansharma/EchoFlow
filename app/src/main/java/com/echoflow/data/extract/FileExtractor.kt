package com.echoflow.data.extract

import com.echoflow.data.ExtractionStatus
import com.echoflow.data.ExtractionTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decides the extraction tier and returns durable text (or a status that defers to the
 * provider). Called from [com.echoflow.data.ProjectManager] after a file is copied and from
 * the lazy backfill sweep.
 */
class FileExtractor(
    private val parser: DocumentParser = Anydoc,
    private val ocr: ImageTextRecognizer? = null,
) {
    data class Result(
        val text: String?,
        val status: ExtractionStatus,
        val tier: ExtractionTier?,
    )

    suspend fun extract(file: File, mime: String, name: String): Result = withContext(Dispatchers.IO) {
        val ext = name.substringAfterLast('.', "").lowercase()
        val lowerMime = mime.lowercase()

        if (lowerMime.startsWith("image/")) {
            val text = ocr?.ocrImage(file)
            return@withContext if (!text.isNullOrBlank()) {
                Result(text, ExtractionStatus.EXTRACTED, ExtractionTier.OCR)
            } else {
                Result(null, ExtractionStatus.NEEDS_PROVIDER, null)
            }
        }

        if (ext in ANYDOC_EXTS || lowerMime == "application/pdf") {
            val bytes = readCapped(file) ?: return@withContext Result(null, ExtractionStatus.FAILED, null)
            when (val parsed = parser.convert(bytes, name)) {
                is Anydoc.Result.Text ->
                    return@withContext Result(parsed.markdown, ExtractionStatus.EXTRACTED, ExtractionTier.ANYDOC)
                is Anydoc.Result.Failed ->
                    return@withContext Result(null, ExtractionStatus.FAILED, null)
                Anydoc.Result.TryOcr -> Unit
            }
        }

        if (lowerMime == "application/pdf" || ext == "pdf") {
            val text = ocr?.ocrPdf(file)
            if (!text.isNullOrBlank()) {
                return@withContext Result(text, ExtractionStatus.EXTRACTED, ExtractionTier.OCR)
            }
        }

        legacyPlainText(file, lowerMime, ext)?.let {
            return@withContext Result(it, ExtractionStatus.EXTRACTED, ExtractionTier.LEGACY_TEXT)
        }

        Result(null, ExtractionStatus.NEEDS_PROVIDER, null)
    }

    /**
     * Extract text for context injection. Reads text-shaped formats only; peak allocation is
     * capped so a huge log/csv cannot OOM the process.
     */
    private fun legacyPlainText(file: File, mime: String, ext: String): String? {
        val looksTextual = mime.startsWith("text/") ||
            mime in TEXTUAL_MIME_TYPES ||
            ext in TEXTUAL_EXTENSIONS
        if (!looksTextual) return null
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_EXTRACT_CHARS)
                var filled = 0
                while (filled < buffer.size) {
                    val read = reader.read(buffer, filled, buffer.size - filled)
                    if (read < 0) break
                    filled += read
                }
                if (filled <= 0) null else String(buffer, 0, filled)
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun readCapped(file: File): ByteArray? {
        if (file.length() > MAX_PARSE_BYTES) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    companion object {
        private const val MAX_PARSE_BYTES = 25L * 1024 * 1024
        private const val MAX_EXTRACT_CHARS = 200_000

        private val ANYDOC_EXTS = setOf(
            "doc", "docx", "docm", "ppt", "pptx", "xls", "xlsx", "xlsm", "xlsb",
            "odt", "ods", "odp", "rtf", "epub", "csv", "pdf",
        )

        private val TEXTUAL_MIME_TYPES = setOf(
            "application/json", "application/xml", "application/xhtml+xml",
            "application/javascript", "application/x-yaml", "application/yaml",
            "application/markdown", "application/x-sh", "application/csv",
        )

        private val TEXTUAL_EXTENSIONS = setOf(
            "txt", "md", "markdown", "json", "xml", "yaml", "yml", "csv", "tsv",
            "html", "htm", "css", "js", "ts", "kt", "java", "py", "rb", "go",
            "rs", "c", "cpp", "h", "sh", "toml", "ini", "cfg", "log", "sql",
        )
    }
}
