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

/**
 * Whether flipping the media switch should hand the image you were just looking at to video
 * as its opening frame.
 *
 * A pure rule rather than a few `if`s inside the effect that runs it, because every clause is
 * a way the feature turns from helpful into presumptuous, and each deserves to be pinned down
 * by a test rather than re-argued the next time somebody edits the composer.
 */
fun shouldCarryImageIntoVideo(
    from: ImagineMedia,
    to: ImagineMedia,
    lastImagePath: String?,
    modelTakesFirstFrame: Boolean,
    composerAlreadyHasAttachment: Boolean,
): Boolean = from == ImagineMedia.Image &&
    to == ImagineMedia.Video &&
    lastImagePath != null &&
    // A model that cannot take a first frame would silently drop the file, leaving the user
    // with a chip in the composer and no idea it did nothing.
    modelTakesFirstFrame &&
    // A deliberate choice outranks a guess, and clearing the chip has to mean something.
    !composerAlreadyHasAttachment
