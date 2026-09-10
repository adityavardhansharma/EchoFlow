package com.echoflow.data

/**
 * Vision for Sarvam chat models, which are text-only and reject `image_url` parts.
 *
 * Instead of sending pixels, an attached image is read on-device with ML Kit OCR
 * ([com.echoflow.data.extract.ImageTextRecognizer.ocrUri]) and the recognized text
 * is folded into the turn the model sees — the same request-only injection pattern
 * as [LocalLlmPrompting.historyWithInjectedDocs], minus the URI stripping (the
 * Sarvam router already drops raw attachments via `textOnlyHistory`).
 *
 * Pure formatting so it unit-tests without ML Kit or a `ContentResolver`.
 */
internal object SarvamVisionBridge {
    /**
     * Request-only history where [ocrBlocks] (one per readable image, in attach
     * order) are appended to the latest user turn. Returns the input unchanged
     * when there is nothing to inject. Never mutates stored rows.
     */
    fun historyWithOcr(history: List<ChatMessage>, ocrBlocks: List<String>): List<ChatMessage> {
        val blocks = ocrBlocks.map { it.trim() }.filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return history
        val index = history.indexOfLast { it.role == "user" }
        if (index == -1) return history
        val joined = blocks.joinToString("\n\n")
        val target = history[index]
        val content = if (target.content.isBlank()) joined else "${target.content}\n\n$joined"
        return history.toMutableList().also { it[index] = target.copy(content = content) }
    }

    /**
     * One image's OCR text as a labeled block. [ocrText] is capped so a dense
     * screenshot cannot crowd out the user's question.
     */
    fun ocrBlock(imageName: String?, ocrText: String): String {
        val label = imageName?.trim().takeUnless { it.isNullOrEmpty() } ?: "Attached image"
        val body = ocrText.trim().take(MAX_OCR_CONTEXT_CHARS)
        val truncatedMark = if (ocrText.trim().length > MAX_OCR_CONTEXT_CHARS) "\n[…image text truncated…]" else ""
        return "--- $label (text read from the image on this device) ---\n$body$truncatedMark\n--- End of $label ---"
    }

    /** Combined OCR budget injected from one turn's images into a Sarvam prompt. */
    internal const val MAX_OCR_CONTEXT_CHARS = 6_000
}
