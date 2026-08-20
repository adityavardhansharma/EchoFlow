package com.echoflow.data.extract

import com.echoflow.data.DefaultChatModels

/**
 * Whether the active model can accept raw files/images on a chat turn (Tier 3).
 *
 * Only OpenRouter models get extras attached today. On-device (`local/`) and
 * custom/direct-cloud (`custom/`) send paths drop PDFs or all extra parts, so
 * they must not be advertised as file-capable — the Files row would otherwise
 * say the file was sent when it was not.
 *
 * [DefaultChatModels.ECHO_LUMEN_MODEL_ID] uses on-device anydoc (Tier 1) instead — see
 * [extractsDocsLocally].
 */
object ModelFileCapability {
    fun readsFiles(modelId: String): Boolean =
        modelId.isNotBlank() &&
            !modelId.startsWith("local/") &&
            !modelId.startsWith("custom/") &&
            modelId != DefaultChatModels.ECHO_LUMEN_MODEL_ID

    /**
     * Tier 1: parse supported doc files on-device (anydoc → Markdown) before send.
     * Images are not supported on this path.
     */
    fun extractsDocsLocally(modelId: String): Boolean =
        modelId.startsWith("local/") || modelId == DefaultChatModels.ECHO_LUMEN_MODEL_ID
}
