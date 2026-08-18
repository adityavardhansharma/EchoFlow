package dev.firecrawl.anydoc.android

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.firecrawl.anydoc.AnyDoc
import dev.firecrawl.anydoc.ConvertException
import dev.firecrawl.anydoc.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class DocumentBytes(
    val bytes: ByteArray,
    val displayName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentBytes) return false
        return displayName == other.displayName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + displayName.hashCode()
}

fun ContentResolver.displayName(uri: Uri): String {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            val name = cursor.getString(index)
            if (!name.isNullOrBlank()) return name
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
}

/**
 * Read a `content://` or `file://` URI, capped at [AnyDoc.MAX_DOCUMENT_BYTES].
 * Android scoped storage does not give the Rust crate a usable filesystem path.
 */
fun ContentResolver.readDocumentBytes(
    uri: Uri,
    maxBytes: Int = AnyDoc.MAX_DOCUMENT_BYTES,
): DocumentBytes {
    val name = displayName(uri)
    val bytes = openInputStream(uri)?.use { it.readCapped(maxBytes) }
        ?: throw ConvertException("io", "io error: unable to open document")
    return DocumentBytes(bytes, name)
}

fun ContentResolver.toMarkdown(uri: Uri, format: Format? = null): String {
    val document = readDocumentBytes(uri)
    return AnyDoc.toMarkdownBytes(document.bytes, format, document.displayName)
}

suspend fun ContentResolver.toMarkdownAsync(uri: Uri, format: Format? = null): String {
    val document = withContext(Dispatchers.IO) { readDocumentBytes(uri) }
    return withContext(Dispatchers.Default) {
        AnyDoc.toMarkdownBytes(document.bytes, format, document.displayName)
    }
}

internal fun InputStream.readCapped(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val out = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        if (total > maxBytes) {
            throw ConvertException(
                "resourceLimit",
                "resource limit exceeded (max_document_bytes): $total > $maxBytes",
            )
        }
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
