package com.echoflow.data.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBitmapBudgetTest {
    @Test fun `small images are not downsampled`() {
        assertEquals(1, OcrBitmapBudget.sampleSize(800, 600))
    }

    @Test fun `huge images downsample until they fit the pixel budget`() {
        val sample = OcrBitmapBudget.sampleSize(40_000, 40_000)
        assertNotNull(sample)
        assertTrue(sample!! > 1)
        val pixels = (40_000L / sample) * (40_000L / sample)
        assertTrue(pixels <= OcrBitmapBudget.MAX_PIXELS)
    }

    @Test fun `dimensions that still exceed the budget after 1024x are rejected`() {
        assertNull(OcrBitmapBudget.sampleSize(3_000_000, 3_000_000))
        assertNull(OcrBitmapBudget.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE))
        assertFalse(OcrBitmapBudget.fits(3_000_000, 3_000_000))
    }

    @Test fun `invalid image dimensions are rejected`() {
        assertNull(OcrBitmapBudget.sampleSize(0, 100))
        assertNull(OcrBitmapBudget.sampleSize(-1, 10))
        assertFalse(OcrBitmapBudget.fits(0, 100))
    }

    @Test fun `pdf pages at 2x stay within the budget`() {
        val size = OcrBitmapBudget.pdfRenderSize(612, 792)
        assertNotNull(size)
        assertEquals(1224, size!!.first)
        assertEquals(1584, size.second)
    }

    @Test fun `huge pdf page boxes are scaled instead of overflowing`() {
        val size = OcrBitmapBudget.pdfRenderSize(100_000, 100_000)
        assertNotNull(size)
        val pixels = size!!.first.toLong() * size.second.toLong()
        assertTrue(pixels <= OcrBitmapBudget.MAX_PIXELS)
    }

    @Test fun `invalid dimensions are skipped`() {
        assertNull(OcrBitmapBudget.pdfRenderSize(0, 100))
        assertNull(OcrBitmapBudget.pdfRenderSize(-1, 10))
    }
}
