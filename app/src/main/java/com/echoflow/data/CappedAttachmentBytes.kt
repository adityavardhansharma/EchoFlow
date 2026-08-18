package com.echoflow.data

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/**
 * Shared bound for reading a file/URI into memory before Base64. Matches the
 * project-import cap so a fallback attachment cannot grow past what we already
 * allow on disk.
 */
internal object CappedAttachmentBytes {
    const val MAX_BYTES: Long = 25L * 1024 * 1024

    fun read(pathOrUri: String, openUri: (Uri) -> InputStream?): ByteArray? {
        val asFile = File(pathOrUri.removePrefix("file://"))
        if (asFile.isFile) {
            if (asFile.length() > MAX_BYTES) return null
            return asFile.inputStream().use { it.readCapped(MAX_BYTES) }
        }
        return openUri(Uri.parse(pathOrUri))?.use { it.readCapped(MAX_BYTES) }
    }

    fun InputStream.readCapped(maxBytes: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n.toLong()
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
