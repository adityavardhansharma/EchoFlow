package com.echoflow.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-attaching is only good while it is restrained. Each case here is a way it would start
 * putting files into the composer that the user did not ask for and cannot explain — or, in
 * the loading cases, a way it would silently do nothing at all.
 */
class CarryImageIntoVideoTest {

    private fun carry(
        armed: Boolean = true,
        lastImagePath: String? = "/data/last.png",
        modelTakesFirstFrame: Boolean = true,
        composerAlreadyHasAttachment: Boolean = false,
    ) = shouldCarryImageIntoVideo(
        armed, lastImagePath, modelTakesFirstFrame, composerAlreadyHasAttachment,
    )

    @Test fun `an armed hand-off with everything loaded carries the last image`() {
        assertTrue(carry())
    }

    @Test fun `only image to video arms the hand-off`() {
        assertTrue(armsCarryIntoVideo(ImagineMedia.Image, ImagineMedia.Video))
        // The effect re-runs on recomposition; without the transition check, clearing the chip
        // would be undone on the next frame and the attachment could not be removed at all.
        assertFalse(armsCarryIntoVideo(ImagineMedia.Video, ImagineMedia.Video))
        assertFalse(armsCarryIntoVideo(ImagineMedia.Image, ImagineMedia.Image))
        assertFalse(armsCarryIntoVideo(ImagineMedia.Video, ImagineMedia.Image))
    }

    @Test fun `an unarmed state carries nothing however ready the data is`() {
        assertFalse(carry(armed = false))
    }

    @Test fun `waiting on data is not the same as refusing`() {
        // These two are false because the answer has not arrived yet — capabilities come off
        // the network and the image out of a database. The caller stays armed and asks again,
        // which is the whole reason arming is separate from deciding.
        assertFalse(carry(lastImagePath = null))
        assertFalse(carry(modelTakesFirstFrame = false))
        // ...and once it has arrived, the same armed transition goes through.
        assertTrue(carry(lastImagePath = "/data/last.png", modelTakesFirstFrame = true))
    }

    @Test fun `an attachment the user chose is never displaced`() {
        assertFalse(carry(composerAlreadyHasAttachment = true))
    }
}
