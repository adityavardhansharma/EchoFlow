package com.echoflow.ui.chat

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * What another app handed EchoFlow through the share sheet (ACTION_SEND / ACTION_SEND_MULTIPLE)
 * or the text-selection menu (ACTION_PROCESS_TEXT). Text goes into the composer; files are staged
 * as attachments on a fresh chat.
 */
data class IncomingShare(
    val text: String?,
    val files: List<Uri>,
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && files.isEmpty()

    companion object {
        /** Reads a share or process-text intent; null for any other intent or an empty share. */
        fun from(intent: Intent?): IncomingShare? {
            intent ?: return null
            val share = when (intent.action) {
                Intent.ACTION_PROCESS_TEXT -> IncomingShare(
                    text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
                    files = emptyList(),
                )
                Intent.ACTION_SEND -> IncomingShare(
                    text = sharedText(intent),
                    files = listOfNotNull(intent.streamExtra()),
                )
                Intent.ACTION_SEND_MULTIPLE -> IncomingShare(
                    text = sharedText(intent),
                    files = intent.streamListExtra(),
                )
                else -> return null
            }
            return share.takeUnless { it.isEmpty }
        }

        // Links are often shared as EXTRA_TEXT with the page title in EXTRA_SUBJECT; keep both.
        private fun sharedText(intent: Intent): String? {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
            return when {
                text.isNullOrEmpty() -> subject?.takeIf { it.isNotEmpty() }
                subject.isNullOrEmpty() || text.contains(subject) -> text
                else -> "$subject\n$text"
            }
        }

        @Suppress("DEPRECATION")
        private fun Intent.streamExtra(): Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }

        @Suppress("DEPRECATION")
        private fun Intent.streamListExtra(): List<Uri> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            } else {
                getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            }
    }
}
