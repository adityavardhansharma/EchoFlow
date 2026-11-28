package com.echoflow.weave.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.echoflow.weave.core.Limits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * how [WeaveShare.weaveFile] sends it). Calls [onOther] on the main thread when it is not a
     * `.weave` file. Filename lookup and file reading happen off the main thread, with a bounded
     * stream; one larger than [MAX_FILE_BYTES] is rejected, not cut.
     */
    fun read(context: Context, intent: Intent?, onOther: () -> Unit) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> streamOf(intent)
            else -> null
        } ?: run {
            onOther()
            return
        }
        val declared = intent?.type == "application/x-weave" || uri.lastPathSegment.orEmpty().endsWith(".weave", ignoreCase = true)
        val appContext = context.applicationContext
        scope.launch {
            if (!declared && !displayName(appContext, uri).endsWith(".weave", ignoreCase = true)) {
                withContext(Dispatchers.Main) { onOther() }
                return@launch
            }
            val text = runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
                    if (bytes.size > MAX_FILE_BYTES) null else bytes.decodeToString()
                }
            }.getOrNull() ?: return@launch
            pending.value = text
        }
    }

    @Suppress("DEPRECATION")
    private fun streamOf(intent: Intent): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    private fun displayName(context: Context, uri: Uri): String {
        val segment = uri.lastPathSegment.orEmpty()
        if (segment.endsWith(".weave", ignoreCase = true)) return segment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: segment
    }

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
