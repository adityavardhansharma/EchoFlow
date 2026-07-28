package com.echoflow.data

/**
 * What Imagine is currently making. Image and video are one surface with a media switch
 * rather than two modes: they share a prompt, a framing, a result to keep and a refine loop,
 * and the differences (how long it takes, whether it can be edited, whether it has audio) are
 * settings, not separate features.
 */
enum class ImagineMedia(val storageKey: String) {
    Image("image"),
    Video("video");

    companion object {
        fun fromStorage(value: String?): ImagineMedia =
            entries.firstOrNull { it.storageKey == value } ?: Image
    }
}
