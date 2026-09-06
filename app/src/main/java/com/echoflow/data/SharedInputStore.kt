package com.echoflow.data

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.echoflow.data.extract.FileExtractor
import com.echoflow.data.extract.OcrExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class SharedInputStore(private val context: Context) {
    companion object {
        const val MAX_FILES = 3
        const val MAX_FILE_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
        fun accepts(intent: Intent): Boolean = intent.action in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_PROCESS_TEXT)
        @Suppress("DEPRECATION")
        fun sanitize(intent: Intent): Intent {
            require(accepts(intent)) { "Unsupported share action." }
            val key = if (intent.action == Intent.ACTION_PROCESS_TEXT) Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT
            val text = (intent.getCharSequenceExtra(key) ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text)?.toString().orEmpty()
            require(text.length <= 100_000) { "Share a smaller text selection (up to 100,000 characters)." }
            val uris = buildList<Uri> {
                if (intent.action == Intent.ACTION_SEND_MULTIPLE) addAll(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
                else (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(::add)
                intent.clipData?.let { for (i in 0 until it.itemCount) it.getItemAt(i).uri?.let(::add) }
            }.distinct()
            require(uris.size <= MAX_FILES && uris.all { it.toString().length <= 8192 }) { "Share up to three files at a time." }
            return Intent(intent.action).setType(intent.type).apply {
                putExtra(key, text)
                if (intent.action == Intent.ACTION_SEND_MULTIPLE) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                else uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) }
                // Preserve all ClipData files even when a sender uses SEND with multiple URIs.
                if (uris.isNotEmpty()) clipData = android.content.ClipData.newRawUri("Shared files", uris.first()).apply {
                    uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
                }
            }
        }
        fun validExternalUri(uri: Uri, packageName: String): Boolean =
            uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.authority != "$packageName.fileprovider"
    }
    @Suppress("DEPRECATION")
    suspend fun import(intent: Intent): SharedInput = withContext(Dispatchers.IO) {
        require(accepts(intent)) { "Unsupported share action." }
        val text = (intent.getCharSequenceExtra(if (intent.action == Intent.ACTION_PROCESS_TEXT) Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text)?.toString().orEmpty()
        require(text.length <= 100_000) { "Shared text is too long. Share a smaller selection." }
        val streams = buildList<Uri> {
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) addAll(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
            else (intent.getParcelableExtra<android.os.Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(::add)
            intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(::add) }
        }.distinct()
        require(streams.size <= MAX_FILES) { "Share up to $MAX_FILES files at a time." }
        require(text.isNotBlank() || streams.isNotEmpty()) { "This share has no readable text or files." }
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "shared_inputs/$id").apply { mkdirs() }
        try {
            var total = 0L
            val files = streams.mapIndexed { index, uri ->
                require(validExternalUri(uri, context.packageName)) { "This file cannot be imported from that location." }
                val resolver = context.contentResolver
                val mime = (resolver.getType(uri) ?: intent.type).orEmpty().lowercase()
                require(mime == "application/pdf" || mime.startsWith("text/") || mime in setOf("image/png", "image/jpeg", "image/webp", "image/gif", "image/heic", "image/heif")) {
                    "Share text, a PDF, or a supported image."
                }
                var name = if (mime == "application/pdf") "Shared.pdf" else if (mime.startsWith("text/")) "Shared.txt" else "Shared image"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.let { name = it.take(160) }
                }
                val suffix = when (mime) { "application/pdf" -> ".pdf"; "image/png" -> ".png"; "image/jpeg" -> ".jpg";
                    "image/webp" -> ".webp"; "image/gif" -> ".gif"; "image/heic" -> ".heic"; "image/heif" -> ".heif"; else -> ".txt" }
                val safeName = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.', name).take(100).replace(Regex("[^\\p{L}\\p{N} _-]"), "_")
                val destination = File(directory, "$index-$safeName$suffix")
                var copied = 0L
                requireNotNull(resolver.openInputStream(uri)) { "The sharing app did not grant access to this file." }.use { source ->
                    destination.outputStream().use { out ->
                        val buffer = ByteArray(16384)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            copied += count; total += count
                            require(copied <= (if (mime.startsWith("image/")) 10L * 1024 * 1024 else MAX_FILE_BYTES) && total <= MAX_TOTAL_BYTES) { "Shared files exceed the size limit (10 MB per image, 25 MB per document, 50 MB total)." }
                            out.write(buffer, 0, count)
                        }
                    }
                }
                require(copied > 0) { "$name is empty." }
                val extracted = if (mime.startsWith("image/")) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(destination.path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "$name is not a readable image." }
                    null
                } else FileExtractor(ocr = OcrExtractor(context)).extract(destination, mime, name).text
                SharedFile(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination).toString(), name, mime, extracted)
            }
            SharedInput(id, text, files)
        } catch (e: Throwable) { directory.deleteRecursively(); throw e }
    }
    suspend fun persist(input: SharedInput) = withContext(Dispatchers.IO) {
        require(runCatching { UUID.fromString(input.id) }.isSuccess)
        val dir = File(context.filesDir, "shared_inputs/${input.id}").apply { mkdirs() }
        val atomic = android.util.AtomicFile(File(dir, "input.json"))
        val stream = atomic.startWrite()
        try { stream.write(QuickTaskJson.input(input).toByteArray()); atomic.finishWrite(stream) }
        catch (e: Throwable) { atomic.failWrite(stream); throw e }
    }
    suspend fun load(id: String): SharedInput = withContext(Dispatchers.IO) {
        require(runCatching { UUID.fromString(id) }.isSuccess)
        val file = File(context.filesDir, "shared_inputs/$id/input.json")
        require(file.length() <= 8 * 1024 * 1024) { "The shared draft is too large." }
        QuickTaskJson.input(file.readText())
    }
    suspend fun discard(input: SharedInput) = withContext(Dispatchers.IO) {
        if (runCatching { UUID.fromString(input.id) }.isSuccess) File(context.filesDir, "shared_inputs/${input.id}").deleteRecursively()
        Unit
    }
}
