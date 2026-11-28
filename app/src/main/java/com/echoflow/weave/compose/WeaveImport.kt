package com.echoflow.weave.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.echoflow.weave.core.Limits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** A `.weave` file opened from another app (01 §23.4), waiting for the chat screen. */
object WeaveImport {
    val pending = MutableStateFlow<String?>(null)

    /**
     * The largest file we read: a block's largest source plus its largest stored state plus the
     * `# state ` line. [WeaveShare.weaveFile] never writes more, so every export opens again.
     */
    const val MAX_FILE_BYTES = Limits.SOURCE_BYTES + Limits.STATE_BYTES + 1024

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Takes a `.weave` file opened (ACTION_VIEW) or shared (ACTION_SEND with EXTRA_STREAM, which is
     * how [WeaveShare.weaveFile] sends it); true when the intent was one. The file is read off the
     * main thread with a bounded stream; one larger than [MAX_FILE_BYTES] is rejected, not cut.
     */
    fun read(context: Context, intent: Intent?): Boolean {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamOf(intent)
            else -> null
        } ?: return false
        // decided without asking the content provider (that could block the main thread): the
        // type EchoFlow shares with, or a .weave name in the URI
        if (intent?.type != "application/x-weave" && !uri.lastPathSegment.orEmpty().endsWith(".weave", ignoreCase = true)) return false
        val resolver = context.applicationContext.contentResolver
        scope.launch {
            val text = runCatching {
                resolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
                    if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
                }
            }.getOrNull() ?: return@launch
            pending.value = text
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    /** Reads up to [limit] bytes (InputStream.readNBytes needs API 33). */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
