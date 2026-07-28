package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-attaching is only good while it is restrained. Each case here is a way it would either
 * put a file into the composer the user did not ask for, or silently fail to.
 */
class CarryImageIntoVideoTest {

    private fun decide(
        lastImagePath: String? = "/data/last.png",
        firstFrame: FirstFrameSupport = FirstFrameSupport.Supported,
        composerAlreadyHasAttachment: Boolean = false,
    ) = carryImageIntoVideoDecision(lastImagePath, firstFrame, composerAlreadyHasAttachment)

    @Test fun `everything ready carries the last image`() {
        assertEquals(CarryDecision.Carry, decide())
    }

    @Test fun `only image to video arms the hand-off`() {
        assertTrue(armsCarryIntoVideo(ImagineMedia.Image, ImagineMedia.Video))
        // The effect re-runs on recomposition; without the transition check, clearing the chip
        // would be undone on the next frame and the attachment could not be removed at all.
        assertFalse(armsCarryIntoVideo(ImagineMedia.Video, ImagineMedia.Video))
        assertFalse(armsCarryIntoVideo(ImagineMedia.Image, ImagineMedia.Image))
        assertFalse(armsCarryIntoVideo(ImagineMedia.Video, ImagineMedia.Image))
    }

    @Test fun `an unanswered capability set waits rather than refusing`() {
        // Capabilities come off the network. Treating "not loaded" as "no" spent the
        // transition and the first frame silently never appeared.
        assertEquals(CarryDecision.Wait, decide(firstFrame = FirstFrameSupport.Unknown))
        assertEquals(CarryDecision.Wait, decide(lastImagePath = null))
    }

    @Test fun `a model that says no ends the hand-off instead of parking it`() {
        // The bug this separation exists for. Held as Wait, the transition stayed armed
        // against a model that had already refused, so later switching to a model that *can*
        // take a first frame attached an old image to the next request — long after the
        // switch, and in response to picking a model rather than to any request for it.
        assertEquals(CarryDecision.Drop, decide(firstFrame = FirstFrameSupport.Unsupported))
    }

    @Test fun `an attachment the user chose is never displaced, and ends the wait`() {
        assertEquals(CarryDecision.Drop, decide(composerAlreadyHasAttachment = true))
        // Even while other answers are still outstanding: there is nothing left to wait for.
        assertEquals(
            CarryDecision.Drop,
            decide(composerAlreadyHasAttachment = true, firstFrame = FirstFrameSupport.Unknown),
        )
    }

    @Test fun `a refusal outranks a missing image`() {
        assertEquals(
            CarryDecision.Drop,
            decide(lastImagePath = null, firstFrame = FirstFrameSupport.Unsupported),
        )
    }

    @Test fun `waiting resolves into a carry once the answer arrives`() {
        assertEquals(CarryDecision.Wait, decide(firstFrame = FirstFrameSupport.Unknown))
        assertEquals(CarryDecision.Carry, decide(firstFrame = FirstFrameSupport.Supported))
    }
}
