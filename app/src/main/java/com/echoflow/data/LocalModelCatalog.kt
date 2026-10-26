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
    val description: String,
    val vision: Boolean = false, // the .litertlm bundle carries a vision encoder
    val gpu: Boolean = true, // LiteRT-LM GPU is verified for this file on Android
)

/** How to open a known LiteRT-LM bundle, so the load skips attempts that cannot work. */
data class LiteRtLoadPlan(val vision: Boolean, val gpu: Boolean)

/**
 * Curated LiteRT models verified against the HuggingFace repos (file names drift when
 * quantization/kv-cache parameters change — re-verify the `resolve/main` URLs if a
 * download starts returning 404). The settings list comes from [recommendationsFor],
 * not from [entries]: everyone sees the current picks, and a model they already
 * downloaded stays on that list with its tick.
 */
object LocalModelCatalog {

    private val gemma3 = CatalogEntry(
        id = "local/gemma3-1b-it",
        name = "Gemma 3 1B",
        fileName = "gemma3-1b-it-int4.task",
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
        approxSizeBytes = 555L * 1024 * 1024,
        requiresAuth = true,
        maxTokens = 2048,
        description = "Small Gemma instruction model. Fast on most phones; requires accepting the Gemma license on Hugging Face."
    )
    private val qwen06 = CatalogEntry(
        id = "local/qwen3-0.6b",
        name = "Qwen3 0.6B",
        fileName = "qwen3_0_6b_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3_0_6b_mixed_int4.litertlm",
        approxSizeBytes = 497_664_000L,
        requiresAuth = false,
        maxTokens = 2048,
        description = "Tiny Qwen3 LiteRT-LM build for everyday phones. Mixed INT4, no login needed."
    )
    private val qwen25 = CatalogEntry(
        id = "local/qwen2.5-1.5b-instruct",
        name = "Qwen 2.5 1.5B",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task",
        approxSizeBytes = 1700L * 1024 * 1024,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Capable all-rounder with a 4K context, no login needed (~2.5 GB RAM)."
    )
    private val deepseek = CatalogEntry(
        id = "local/deepseek-r1-distill-1.5b",
        name = "DeepSeek R1 Distill 1.5B",
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
        approxSizeBytes = 1800L * 1024 * 1024,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Reasoning-tuned distill that thinks before answering, no login needed (~2.5 GB RAM)."
    )
    private val gemma4E2b = CatalogEntry(
        id = "local/gemma-4-e2b-it",
        name = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approxSizeBytes = 2_588_147_712L,
        requiresAuth = false,
        maxTokens = 8192,
        description = "Latest Gemma generation with an effective 2B footprint. Strong quality on flagship phones, no login needed (~3.5 GB RAM).",
        vision = true,
    )
    private val qwen34b = CatalogEntry(
        id = "local/qwen3-4b-instruct-2507",
        name = "Qwen3 4B Instruct 2507",
        fileName = "qwen3_4b_instruct_2507_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3-4B-Instruct-2507/resolve/main/qwen3_4b_instruct_2507_mixed_int4.litertlm",
        approxSizeBytes = 2_659_057_664L,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Newest Qwen3 instruct build (July 2025 refresh), mixed INT4, no login needed (~4 GB RAM)."
    )
    private val gemma4E4b = CatalogEntry(
        id = "local/gemma-4-e4b-it",
        name = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
        approxSizeBytes = 3_659_530_240L,
        requiresAuth = false,
        maxTokens = 8192,
        description = "Largest Gemma 4 mobile bundle — best answers, needs a high-end phone (~5 GB RAM), no login needed.",
        vision = true,
    )

    // Liquid LFM2.5 (2026). int8 1.2B keeps the reference quality and runs on CPU
    // (the int4 GPU pack failed a basic answer check on LiteRT-LM 0.18). The 2.6B
    // int4 pack is the reasoning model and is verified on Android GPU from 0.16 on.
    private val lfm12 = CatalogEntry(
        id = "local/lfm-2.5-1.2b-instruct",
        name = "LFM2.5 1.2B",
        fileName = "LFM2.5-1.2B-Instruct_int8.litertlm",
        url = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int8.litertlm",
        approxSizeBytes = 1_247_091_440L,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Liquid's 2026 instruct model for everyday phones. Int8, no login needed (~2 GB RAM).",
        gpu = false,
    )
    private val lfm26 = CatalogEntry(
        id = "local/lfm-2.5-2.6b",
        name = "LFM2.5 2.6B",
        fileName = "LFM2.5-2.6B_int4.litertlm",
        url = "https://huggingface.co/litert-community/LFM2.5-2.6B/resolve/main/LFM2.5-2.6B_int4.litertlm",
        approxSizeBytes = 1_668_151_680L,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Liquid's 2026 reasoning model. Thinks before answering, mixed INT4, no login needed (~2.5 GB RAM)."
    )
    // Qwen 3.5 4B mixed INT4 is the phone file (2.75 GB). The int8 pack is 4.4 GB.
    // Android CPU is verified on a Pixel 8a; the manifest marks Adreno GPU unverified.
    private val qwen354b = CatalogEntry(
        id = "local/qwen3.5-4b",
        name = "Qwen 3.5 4B",
        fileName = "Qwen3.5-4B_mixed_int4.litertlm",
        url = "https://huggingface.co/litert-community/Qwen3.5-4B/resolve/main/Qwen3.5-4B_mixed_int4.litertlm",
        approxSizeBytes = 2_754_070_624L,
        requiresAuth = false,
        maxTokens = 4096,
        description = "Qwen's 2026 4B text model. Mixed INT4 for 8 GB phones, no login needed (~4.5 GB RAM).",
        gpu = false,
    )

    /** What a new install shows. The first four stay in front; Qwen 3.5 4B follows. */
    private val recommended = listOf(lfm12, lfm26, gemma4E2b, gemma4E4b, qwen354b)

    /** The list this release replaces, in the order people already know. */
    private val previousRecommendations = listOf(
        gemma3, qwen06, qwen25, deepseek, gemma4E2b, qwen34b, gemma4E4b,
    )

    private val removedIds = setOf(gemma3.id, qwen06.id, qwen25.id, deepseek.id, qwen34b.id)

    /**
     * Every model EchoFlow can still resolve by id or file name, including ones that
     * are no longer offered to new installs. File recovery matches on this list.
     */
    val entries: List<CatalogEntry> = (recommended + previousRecommendations).distinctBy { it.id }

    /**
     * Recommendations for someone who already has [installedIds] on disk.
     *
     * The list is always the current picks. Gemma 4 is already on it. A downloaded
     * model we no longer offer stays at the end, in the old order, so its tick remains.
     */
    fun recommendationsFor(installedIds: Collection<String>): List<CatalogEntry> {
        val installed = installedIds.toSet()
        val downloadedRemoved = previousRecommendations.filter { it.id in removedIds && it.id in installed }
        return recommended + downloadedRemoved
    }

    /**
     * The collapsed settings rows. Installed recommendations that would sit past
     * [limit] stay visible, so a downloaded model's tick is not hidden behind Show all.
     */
    fun preview(
        recommendations: List<CatalogEntry>,
        installedIds: Collection<String>,
        limit: Int,
    ): List<CatalogEntry> {
        val head = recommendations.take(limit)
        val installed = installedIds.toSet()
        return head + recommendations.filter { it.id in installed && it !in head }
    }

    /**
     * Show all is a no-op when every installed model past [limit] is already pinned
     * into the collapsed preview, so the control should stay hidden.
     */
    fun previewOmitsRows(
        recommendations: List<CatalogEntry>,
        installedIds: Collection<String>,
        limit: Int,
    ): Boolean = preview(recommendations, installedIds, limit).size < recommendations.size

    fun entryById(id: String): CatalogEntry? = entries.firstOrNull { it.id == id }

    /**
     * Load plan for a catalog .litertlm file, or null for an imported one, which is probed.
     * LiteRT-LM 0.16 opens a text-only bundle with a vision backend and only fails later,
     * when the conversation is created, so the probe alone misses it.
     */
    fun liteRtPlanFor(modelId: String, fileName: String): LiteRtLoadPlan? {
        val entry = entryById(modelId) ?: entries.firstOrNull { it.fileName.equals(fileName, ignoreCase = true) }
        return entry?.takeIf { isLiteRtLm(it.fileName) }?.let { LiteRtLoadPlan(it.vision, it.gpu) }
    }

    /** File-name based runtime detection — kept here so every layer agrees on the rules. */
    fun isGguf(fileName: String): Boolean = fileName.endsWith(".gguf", ignoreCase = true)
    fun isLiteRtLm(fileName: String): Boolean = fileName.endsWith(".litertlm", ignoreCase = true)
    fun isTask(fileName: String): Boolean = fileName.endsWith(".task", ignoreCase = true)

    /** Supported on-device model file extensions, for the import picker and validation. */
    val supportedExtensions = listOf(".task", ".litertlm", ".gguf")

    private val contextHint = Regex("""(?:ekv|ctx|context|nctx)[-_]?(\d+k?|\d+)""", RegexOption.IGNORE_CASE)

    private fun parseContextHint(fileName: String): Int? {
        val raw = contextHint.find(fileName)?.groupValues?.get(1) ?: return null
        return if (raw.endsWith("k", ignoreCase = true)) {
            raw.dropLast(1).toIntOrNull()?.times(1024)
        } else {
            raw.toIntOrNull()
        }
    }

    /**
     * Safe context budget for a model. Catalog entries know their value; imported or
     * recovered files fall back to hints in the file name (ekv kv-cache markers), then to
     * a conservative default so we never exceed the kv-cache the file was exported with.
     */
    fun maxTokensFor(modelId: String, fileName: String? = null): Int {
        entryById(modelId)?.let { return it.maxTokens }
        val file = fileName ?: return 1280
        entries.firstOrNull { it.fileName.equals(file, ignoreCase = true) }?.let { return it.maxTokens }
        val combined = "$modelId/$file"
        parseContextHint(combined)?.let { return it.coerceIn(512, InferenceLimits.LOCAL_MAX_TOKENS_CEIL) }
        return when {
            // Gemma 4 LiteRT-LM mobile bundles advertise larger windows, but EchoFlow
            // caps local inference at 8K to avoid native OOM/crashes on phones.
            isLiteRtLm(file) && combined.contains("gemma-4", ignoreCase = true) -> InferenceLimits.LOCAL_MAX_TOKENS_CEIL
            // GGUF files carry no kv-cache marker in the name; llama.cpp lets us size the
            // context window ourselves, so use a comfortable mobile default.
            isGguf(file) -> 4096
            isLiteRtLm(file) -> 2048
            else -> 1280
        }
    }
}
