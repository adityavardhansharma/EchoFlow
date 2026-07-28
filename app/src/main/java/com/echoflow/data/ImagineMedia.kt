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
 * The moment that arms the hand-off: you were making images, now you are making video.
 *
 * Deliberately separate from [shouldCarryImageIntoVideo]. "Is this the moment" is decided by a
 * switch the user just flipped and is known instantly; "is there anything to hand over" depends
 * on the model directory and the message history, which arrive over the network and out of a
 * database. Judging both in one pass meant that switching to Video before either had loaded
 * answered "no" once and never asked again — the transition was gone, and the first frame
 * silently never appeared.
 */
fun armsCarryIntoVideo(from: ImagineMedia, to: ImagineMedia): Boolean =
    from == ImagineMedia.Image && to == ImagineMedia.Video

/**
 * Whether an armed hand-off should now go ahead.
 *
 * A pure rule rather than a few `if`s inside the effect that runs it, because every clause is
 * a way the feature turns from helpful into presumptuous, and each deserves to be pinned down
 * by a test rather than re-argued the next time somebody edits the composer.
 */
fun shouldCarryImageIntoVideo(
    armed: Boolean,
    lastImagePath: String?,
    modelTakesFirstFrame: Boolean,
    composerAlreadyHasAttachment: Boolean,
): Boolean = armed &&
    lastImagePath != null &&
    // A model that cannot take a first frame would silently drop the file, leaving the user
    // with a chip in the composer and no idea it did nothing.
    modelTakesFirstFrame &&
    // A deliberate choice outranks a guess, and clearing the chip has to mean something.
    !composerAlreadyHasAttachment
