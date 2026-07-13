package com.echoflow.data

/** Runtime used by an installed on-device image model. Values are persisted in Room. */
enum class LocalImageRuntime(val id: String) {
    MEDIAPIPE("mediapipe"),
    STABLE_DIFFUSION_CPP("stable-diffusion-cpp");

    companion object {
        fun fromId(id: String): LocalImageRuntime =
            entries.firstOrNull { it.id == id } ?: MEDIAPIPE
    }
}

/** Shape of the pinned artifact downloaded by [LocalImageModelDownloadWorker]. */
enum class LocalImageArtifactFormat {
    /** ZIP entries are the MediaPipe model directory and are extracted under install/bins/. */
    MEDIAPIPE_ROOT_ZIP,
    /** A single stable-diffusion.cpp safetensors checkpoint. */
    SAFETENSORS,
    /** A single stable-diffusion.cpp PyTorch checkpoint. */
    CHECKPOINT,
}

/**
 * One immutable curated artifact. URLs always pin a model revision (or a Civitai model-version
 * endpoint), and every artifact has an exact byte count and SHA-256 before it can be offered.
 */
data class LocalImageCatalogEntry(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val runtime: LocalImageRuntime,
    val artifactFormat: LocalImageArtifactFormat,
    val artifactUrl: String?,
    val downloadBytes: Long?,
    val installedBytes: Long?,
    val artifactRevision: String?,
    val artifactSha256: String?,
    val modelFileName: String?,
    val experimental: Boolean,
    val licenseId: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val activationPhrase: String?,
    val defaultNegativePrompt: String,
    val minRamBytes: Long,
    val bundleFormatVersion: Int = LocalImageModelCatalog.BUNDLE_FORMAT_VERSION,
) {
    val artifactAvailable: Boolean
        get() = !artifactUrl.isNullOrBlank() &&
            SHA256_REGEX.matches(artifactSha256.orEmpty()) &&
            !artifactRevision.isNullOrBlank() &&
            (downloadBytes ?: 0L) > 0L &&
            (installedBytes ?: 0L) > 0L &&
            when (artifactFormat) {
                LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP -> modelFileName == null
                LocalImageArtifactFormat.SAFETENSORS ->
                    modelFileName?.endsWith(".safetensors", ignoreCase = true) == true
                LocalImageArtifactFormat.CHECKPOINT ->
                    modelFileName?.endsWith(".ckpt", ignoreCase = true) == true
            }

    fun canDownload(experimentalEnabled: Boolean): Boolean =
        artifactAvailable && (!experimental || experimentalEnabled)

    /** Compatibility for the existing settings row: experimental artifacts stay disabled. */
    val available: Boolean get() = canDownload(experimentalEnabled = false)

    // Compatibility aliases for the original MediaPipe-only installer/tests.
    val bundleUrl: String? get() = artifactUrl
    val sourceRevision: String? get() = artifactRevision
    val sourceCheckpointSha256: String? get() = artifactSha256
    val bundleSha256: String? get() = artifactSha256

    companion object {
        val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    }
}

object LocalImageModelCatalog {
    const val LICENSE_OPENRAIL_M = "creativeml-openrail-m"
    const val LICENSE_OPENRAIL_M_URL =
        "https://huggingface.co/spaces/CompVis/stable-diffusion-license"
    const val LICENSE_CIVITAI_MODEL_TERMS = "civitai-model-terms"
    const val DREAMSHAPER_TERMS_URL = "https://civitai.com/models/4384/dreamshaper"
    const val BUNDLE_FORMAT_VERSION = 1

    private const val MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024

    val entries: List<LocalImageCatalogEntry> = listOf(
        LocalImageCatalogEntry(
            id = "local-image/stable-diffusion-1.5",
            name = "Stable Diffusion 1.5",
            category = "Balanced",
            description = "Reliable general-purpose image generation",
            runtime = LocalImageRuntime.MEDIAPIPE,
            artifactFormat = LocalImageArtifactFormat.MEDIAPIPE_ROOT_ZIP,
            artifactUrl = "https://huggingface.co/na5h13/stable-diffusion-v1-5-mediapipe/resolve/" +
                "645265e20ab21ec6d4b93b030d87178017c04c7e/sd15-mediapipe.zip",
            downloadBytes = 1_906_219_565L,
            installedBytes = 2_067_362_572L,
            artifactRevision = "645265e20ab21ec6d4b93b030d87178017c04c7e",
            artifactSha256 = "0e17f95821b6a247d01807b20920206052fc32483af2af42f03ff61954397758",
            modelFileName = null,
            experimental = false,
            licenseId = LICENSE_OPENRAIL_M,
            licenseUrl = LICENSE_OPENRAIL_M_URL,
            sourceUrl = "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5",
            activationPhrase = null,
            defaultNegativePrompt = "low quality, distorted, deformed",
            minRamBytes = MIN_RAM_BYTES,
        ),
        LocalImageCatalogEntry(
            id = "local-image/dreamshaper-8",
            name = "DreamShaper 8",
            category = "Creative",
            description = "Illustration, fantasy and expressive artwork",
            runtime = LocalImageRuntime.STABLE_DIFFUSION_CPP,
            artifactFormat = LocalImageArtifactFormat.SAFETENSORS,
            artifactUrl = "https://civitai.com/api/download/models/128713",
            downloadBytes = 2_132_625_894L,
            installedBytes = 2_132_625_894L,
            artifactRevision = "civitai-model-version-128713",
            artifactSha256 = "879db523c30d3b9017143d56705015e15a2cb5628762c11d086fed9538abd7fd",
            modelFileName = "dreamshaper_8.safetensors",
            experimental = true,
            licenseId = LICENSE_CIVITAI_MODEL_TERMS,
            licenseUrl = DREAMSHAPER_TERMS_URL,
            sourceUrl = "https://civitai.com/models/4384/dreamshaper",
            activationPhrase = null,
            defaultNegativePrompt = "low quality, distorted, deformed",
            minRamBytes = MIN_RAM_BYTES,
        ),
        LocalImageCatalogEntry(
            id = "local-image/analog-diffusion",
            name = "Analog Diffusion",
            category = "Photography",
            description = "Portraits and analogue film photography",
            runtime = LocalImageRuntime.STABLE_DIFFUSION_CPP,
            artifactFormat = LocalImageArtifactFormat.SAFETENSORS,
            artifactUrl = "https://huggingface.co/wavymulder/Analog-Diffusion/resolve/" +
                "211449c273875dedc683fdb5a95d8a0ff9d76484/analog-diffusion-1.0.safetensors",
            downloadBytes = 2_132_625_462L,
            installedBytes = 2_132_625_462L,
            artifactRevision = "211449c273875dedc683fdb5a95d8a0ff9d76484",
            artifactSha256 = "51f6fff5088a9c5f5aa7cefa0a5a859d0424fc68fdc440e0ee5608a2b82e5ff9",
            modelFileName = "analog-diffusion-1.0.safetensors",
            experimental = true,
            licenseId = LICENSE_OPENRAIL_M,
            licenseUrl = LICENSE_OPENRAIL_M_URL,
            sourceUrl = "https://huggingface.co/wavymulder/Analog-Diffusion",
            activationPhrase = "analog style",
            defaultNegativePrompt = "blur, haze, distorted, low quality",
            minRamBytes = MIN_RAM_BYTES,
        ),
        LocalImageCatalogEntry(
            id = "local-image/openjourney-v4",
            name = "OpenJourney v4",
            category = "Concept art",
            description = "Detailed concept art and illustration",
            runtime = LocalImageRuntime.STABLE_DIFFUSION_CPP,
            artifactFormat = LocalImageArtifactFormat.CHECKPOINT,
            artifactUrl = "https://huggingface.co/prompthero/openjourney-v4/resolve/" +
                "b195ed2d503f3eb29637050a886d77bd81d35f0e/openjourney-v4.ckpt",
            downloadBytes = 2_132_910_209L,
            installedBytes = 2_132_910_209L,
            artifactRevision = "b195ed2d503f3eb29637050a886d77bd81d35f0e",
            artifactSha256 = "02e37aad9f74f574808ad456043b89e8c6b24e22828743fcf002168f76493d9b",
            modelFileName = "openjourney-v4.ckpt",
            experimental = true,
            licenseId = LICENSE_OPENRAIL_M,
            licenseUrl = LICENSE_OPENRAIL_M_URL,
            sourceUrl = "https://huggingface.co/prompthero/openjourney-v4",
            activationPhrase = null,
            defaultNegativePrompt = "low quality, distorted, deformed",
            minRamBytes = MIN_RAM_BYTES,
        ),
    )

    fun entryById(id: String): LocalImageCatalogEntry? = entries.firstOrNull { it.id == id }

    fun directoryNameFor(id: String): String = id.substringAfterLast('/')
}
