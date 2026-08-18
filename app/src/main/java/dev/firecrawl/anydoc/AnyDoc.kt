package dev.firecrawl.anydoc

import java.io.File

/**
 * Kotlin face for the bundled libanydoc.so.
 *
 * Copy this package plus jniLibs per-ABI libanydoc.so into another Android
 * app. Call [toMarkdownBytes] off the main thread; conversion is CPU work
 * even when it is fast. CSV has no content signature — pass [Format.CSV]
 * or a file name so the extension can name it.
 */
object AnyDoc {
    const val MAX_DOCUMENT_BYTES: Int = 128 * 1024 * 1024

    init {
        System.loadLibrary("anydoc")
    }

    @JvmStatic
    private external fun nativeToMarkdownBytes(data: ByteArray, formatHint: String?): String

    @JvmStatic
    private external fun nativeFormatFromBytes(data: ByteArray): String?

    @JvmStatic
    private external fun nativeFormatFromExtension(extension: String): String?

    @JvmStatic
    private external fun nativeVersion(): String

    /** anydoc crate version baked into the `.so`. */
    @JvmStatic
    fun version(): String = nativeVersion()

    @JvmStatic
    fun formatFromBytes(data: ByteArray): Format? =
        Format.fromWireName(nativeFormatFromBytes(data))

    @JvmStatic
    fun formatFromExtension(extension: String): Format? =
        Format.fromWireName(nativeFormatFromExtension(extension))

    @JvmStatic
    fun formatFromPath(path: String): Format? {
        val ext = path.substringAfterLast('.', missingDelimiterValue = "")
        if (ext.isEmpty() || ext == path) return null
        return formatFromExtension(ext)
    }

    /**
     * Convert in-memory bytes to GitHub-Flavored Markdown.
     *
     * [format] selects the parser. When it is null the format is read from
     * the bytes; [fileName] is the fallback for signature-less inputs (CSV).
     */
    @JvmStatic
    @JvmOverloads
    fun toMarkdownBytes(
        data: ByteArray,
        format: Format? = null,
        fileName: String? = null,
    ): String {
        if (data.size > MAX_DOCUMENT_BYTES) {
            throw ConvertException(
                "resourceLimit",
                "resource limit exceeded (max_document_bytes): ${data.size} > $MAX_DOCUMENT_BYTES",
            )
        }
        val hint = format?.wireName
            ?: fileName?.let { formatFromPath(it)?.wireName }
        return nativeToMarkdownBytes(data, hint)
    }

    /** Convert a real filesystem path. Android content URIs are not paths. */
    @JvmStatic
    fun toMarkdown(path: String): String {
        val file = File(path)
        if (!file.isFile) {
            throw ConvertException("io", "io error: not a file: $path")
        }
        if (file.length() > MAX_DOCUMENT_BYTES) {
            throw ConvertException(
                "resourceLimit",
                "resource limit exceeded (max_document_bytes): ${file.length()} > $MAX_DOCUMENT_BYTES",
            )
        }
        return toMarkdownBytes(file.readBytes(), fileName = file.name)
    }
}
