package com.echoflow.data.extract

/**
 * Whether the active model can accept raw files/images on a chat turn (Tier 3).
 * On-device models cannot; OpenRouter and typical cloud endpoints can. Unknown
 * is treated as "no" only for the local prefix — matching the existing user-attachment
 * path, which already sends PDFs to any OpenRouter model.
 */
object ModelFileCapability {
    fun readsFiles(modelId: String): Boolean = !modelId.startsWith("local/")
}
