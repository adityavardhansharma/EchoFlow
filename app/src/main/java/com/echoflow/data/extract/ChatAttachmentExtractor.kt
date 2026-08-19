package com.echoflow.data.extract

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * On-device text extraction for a chat attachment (a `content://` URI picked in the composer).
 *
 * This is the local-model path: a doc file (PDF/Word/Excel/PowerPoint/ODF/RTF/EPUB/CSV) is run
 * through the bundled [Anydoc] parser — the same one the Projects pipeline uses — and its Markdown
 * is injected into the prompt the on-device model receives, since local models can't accept raw
 * files. There is no OCR tier here: a scanned/image-only PDF that anydoc can't read is reported as
 * [Result.Failed] so the composer chip offers retry/remove rather than silently attaching nothing.
 *
 * Never throws — every failure is a [Result.Failed]. Runs on [Dispatchers.IO].
 */
class ChatAttachmentExtractor(
    private val resolver: ContentResolver,
    private val parser: DocumentParser = Anydoc,
) {
    sealed interface Result {
        data class Text(val markdown: String) : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun extract(uri: Uri, name: String): Result = withContext(Dispatchers.IO) {
        val bytes = readCapped(uri) ?: return@withContext Result.Failed("unreadable")
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)?.lowercase().orEmpty()

        if (ext in ANYDOC_EXTS || mime == "application/pdf") {
            when (val parsed = parser.convert(bytes, name)) {
                is Anydoc.Result.Text ->
                    return@withContext if (parsed.markdown.isNotBlank()) {
                        Result.Text(parsed.markdown.take(MAX_EXTRACT_CHARS))
                    } else {
                        Result.Failed("empty")
                    }
                is Anydoc.Result.Failed -> return@withContext Result.Failed(parsed.reason)
                // Scanned / unsupported. Fall through so txt/csv that anydoc declined can
                // still be read as UTF-8 — same split FileExtractor uses.
                Anydoc.Result.TryOcr -> Unit
            }
        }

        plaintextFromBytes(bytes, mime, ext)?.let { return@withContext Result.Text(it) }
        Result.Failed("no readable text")
    }

    /** Reads the URI up to [MAX_BYTES]; returns null on an I/O error or when the file is too big. */
    private fun readCapped(uri: Uri): ByteArray? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_BYTES) return@runCatching null
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun plaintextFromBytes(bytes: ByteArray, mime: String, ext: String): String? {
        val looksTextual = mime.startsWith("text/") ||
            mime in TEXTUAL_MIME_TYPES ||
            ext in TEXTUAL_EXTENSIONS
        if (!looksTextual) return null
        return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_EXTRACT_CHARS)
    }

    companion object {
        private const val MAX_BYTES = 25L * 1024 * 1024
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
