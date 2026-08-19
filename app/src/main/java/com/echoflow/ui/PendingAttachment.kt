package com.echoflow.ui

import java.util.UUID

/**
 * A file staged in the composer, before the turn is sent. The composer renders one chip per
 * entry with a state-driven leading slot (loader / tick / retry) and a remove button.
 *
 * Doc files on the local-model path are parsed on-device (anydoc → Markdown) between attach and
 * send; the chip stays [State.Extracting] until [extractedText] lands or it [State.Failed]s. Images
 * and files bound for a cloud/custom model are [State.Ready] immediately (no local parse).
 */
data class PendingAttachment(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val mimeType: String,
    val name: String,
    val kind: Kind,
    val state: State,
    /** On-device Markdown for a parsed doc; null for images and unparsed (cloud) files. */
    val extractedText: String? = null,
) {
    enum class Kind { Image, Doc }
    enum class State { Extracting, Ready, Failed }

    val isImage: Boolean get() = kind == Kind.Image
    val isProcessing: Boolean get() = state == State.Extracting
    val isFailed: Boolean get() = state == State.Failed
    val isPdf: Boolean get() = mimeType.equals("application/pdf", ignoreCase = true)
}

/** What to keep when the model or mode changes, and which chips still need an on-device parse. */
internal object PendingAttachmentPolicy {
    fun keep(
        current: List<PendingAttachment>,
        imageAllowed: Boolean,
        pdfAllowed: Boolean,
        localFilesAllowed: Boolean,
        cap: Int = 3,
    ): List<PendingAttachment> {
        val legal = current.filter { att ->
            when {
                att.isImage -> imageAllowed
                att.isPdf -> pdfAllowed || localFilesAllowed
                else -> localFilesAllowed
            }
        }
        if (legal.isEmpty()) return emptyList()
        return if (localFilesAllowed) {
            legal.take(cap)
        } else {
            // Cloud/custom/Imagine still send a single localAttachmentUri.
            listOf(legal.firstOrNull { it.isImage } ?: legal.first())
        }
    }

    /** Ready doc with no Markdown — a cloud-staged PDF or a legacy edit. Kick off anydoc. */
    fun needsExtraction(att: PendingAttachment): Boolean =
        att.kind == PendingAttachment.Kind.Doc &&
            att.extractedText.isNullOrBlank() &&
            att.state == PendingAttachment.State.Ready
}
