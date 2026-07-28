package com.echoflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-attaching is only good while it is restrained. Each case here is a way it would start
 * putting files into the composer that the user did not ask for and cannot explain.
 */
class CarryImageIntoVideoTest {

    private fun carry(
        from: ImagineMedia = ImagineMedia.Image,
        to: ImagineMedia = ImagineMedia.Video,
        lastImagePath: String? = "/data/last.png",
        modelTakesFirstFrame: Boolean = true,
        composerAlreadyHasAttachment: Boolean = false,
    ) = shouldCarryImageIntoVideo(
        from, to, lastImagePath, modelTakesFirstFrame, composerAlreadyHasAttachment,
    )

    @Test fun `switching from image to video carries the last image`() {
        assertTrue(carry())
    }

    @Test fun `staying on the same medium carries nothing`() {
        // The effect re-runs on recomposition; without this, clearing the chip would be undone
        // on the very next frame and the attachment would be impossible to remove.
        assertFalse(carry(from = ImagineMedia.Video, to = ImagineMedia.Video))
        assertFalse(carry(from = ImagineMedia.Image, to = ImagineMedia.Image))
    }

    @Test fun `switching back to image carries nothing`() {
        assertFalse(carry(from = ImagineMedia.Video, to = ImagineMedia.Image))
    }

    @Test fun `nothing is carried when there is no image behind you`() {
        assertFalse(carry(lastImagePath = null))
    }

    @Test fun `a model that cannot take a first frame gets nothing`() {
        // Otherwise the request drops the file and the user is left with a chip that lied.
        assertFalse(carry(modelTakesFirstFrame = false))
    }

    @Test fun `an attachment the user chose is never displaced`() {
        assertFalse(carry(composerAlreadyHasAttachment = true))
    }
}
