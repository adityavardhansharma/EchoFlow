package com.echoflow.data

/** A model the user can download in-app from HuggingFace (AI Edge Gallery style). */
data class CatalogEntry(
    val id: String, // "local/<slug>" — becomes LocalModel.id and the selected_model id
    val name: String,
    val fileName: String,
    val url: String,
    val approxSizeBytes: Long,
    val requiresAuth: Boolean, // gated on HuggingFace: needs an access token + accepted license
    val maxTokens: Int, // must not exceed the kv-cache the file was exported with
    val description: String
)

/**
 * Curated LiteRT models verified against the HuggingFace repos (file names drift when
 * quantization/kv-cache parameters change — re-verify the `resolve/main` URLs if a
 * download starts returning 404). Ordered smallest first; RAM needs grow with size.
 */
object LocalModelCatalog {

    val entries: List<CatalogEntry> = listOf(
        CatalogEntry(
            id = "local/gemma3-1b-it",
            name = "Gemma 3 1B",
            fileName = "gemma3-1b-it-int4.task",
            url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
            approxSizeBytes = 555L * 1024 * 1024,
            requiresAuth = true,
            maxTokens = 2048,
            description = "Small Gemma instruction model. Fast on most phones; requires accepting the Gemma license on Hugging Face."
        ),
        CatalogEntry(
            id = "local/qwen3-0.6b",
            name = "Qwen3 0.6B",
            fileName = "qwen3_0_6b_mixed_int4.litertlm",
            url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
            approxSizeBytes = 497_664_000L,
            requiresAuth = false,
            maxTokens = 2048,
            description = "Tiny Qwen3 LiteRT-LM build for everyday phones. Mixed INT4, no login needed."
        ),
        CatalogEntry(
            id = "local/qwen2.5-1.5b-instruct",
            name = "Qwen 2.5 1.5B",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
            approxSizeBytes = 1700L * 1024 * 1024,
            requiresAuth = false,
            maxTokens = 4096,
            description = "Capable all-rounder with a 4K context, no login needed (~2.5 GB RAM)."
        ),
        CatalogEntry(
            id = "local/deepseek-r1-distill-1.5b",
            name = "DeepSeek R1 Distill 1.5B",
            fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            approxSizeBytes = 1800L * 1024 * 1024,
            requiresAuth = false,
            maxTokens = 4096,
            description = "Reasoning-tuned distill that thinks before answering, no login needed (~2.5 GB RAM)."
        )
    )

    fun entryById(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    /** Safe context budget for a model: catalog value, or a conservative default for imports. */
    fun maxTokensFor(modelId: String): Int = entryById(modelId)?.maxTokens ?: 1280
}
