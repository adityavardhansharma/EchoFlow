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
 * Deliberately separate from [carryImageIntoVideoDecision]. "Is this the moment" is decided by a
 * switch the user just flipped and is known instantly; "is there anything to hand over" depends
 * on the model directory and the message history, which arrive over the network and out of a
 * database. Judging both in one pass meant that switching to Video before either had loaded
 * answered "no" once and never asked again — the transition was gone, and the first frame
 * silently never appeared.
 */
fun armsCarryIntoVideo(from: ImagineMedia, to: ImagineMedia): Boolean =
    from == ImagineMedia.Image && to == ImagineMedia.Video

/**
 * What is known about the selected video model's ability to start from an image.
 *
 * [Unknown] and [Unsupported] were once the same `false`, and collapsing them is what let a
 * refusal masquerade as a delay: the hand-off stayed armed against a model that had already
 * said no, so later picking a model that *could* take a first frame silently attached an old
 * image to the next request. Changing model is not a request for a first frame.
 */
enum class FirstFrameSupport { Unknown, Supported, Unsupported }

/** What to do with an armed hand-off right now. */
enum class CarryDecision {
    /** Attach the image as a first frame. */
    Carry,

    /** Nothing has answered yet — stay armed and ask again when something does. */
    Wait,

    /** Answered, and the answer is no. Disarm; this transition is spent. */
    Drop,
}

/**
 * Whether an armed hand-off should go ahead, wait, or be abandoned.
 *
 * A pure rule rather than a few `if`s inside the effect that runs it, because every clause is
 * a way the feature turns from helpful into presumptuous, and each deserves to be pinned down
 * by a test rather than re-argued the next time somebody edits the composer.
 *
 * The three-way answer is the substance. A predicate can only say "no", which a caller has to
 * interpret — and interpreting every "no" as "not yet" leaves the hand-off armed indefinitely,
 * waiting to fire on an unrelated change.
 */
fun carryImageIntoVideoDecision(
    lastImagePath: String?,
    firstFrame: FirstFrameSupport,
    composerAlreadyHasAttachment: Boolean,
): CarryDecision = when {
    // A deliberate choice outranks a guess, and clearing the chip has to mean something.
    composerAlreadyHasAttachment -> CarryDecision.Drop
    // A model that cannot take a first frame would silently drop the file, leaving the user
    // with a chip in the composer and no idea it did nothing.
    firstFrame == FirstFrameSupport.Unsupported -> CarryDecision.Drop
    firstFrame == FirstFrameSupport.Unknown -> CarryDecision.Wait
    lastImagePath == null -> CarryDecision.Wait
    else -> CarryDecision.Carry
}
