package com.echoflow.weave.compose

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/** A `.weave` file opened from another app (01 §23.4), waiting for the chat screen. */
object WeaveImport {
    val pending = MutableStateFlow<String?>(null)

    /** Reads an ACTION_VIEW of a `.weave` file; true when the intent was one. */
    fun read(context: Context, intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        val name = uri.lastPathSegment.orEmpty()
        if (!name.endsWith(".weave", ignoreCase = true) && intent.type != "application/x-weave") return false
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().take(64 * 1024).toByteArray().decodeToString() }
        }.getOrNull() ?: return false
        pending.value = text
        return true
    }
}
