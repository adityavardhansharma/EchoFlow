package com.echoflow.ui.screens

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusMenuPositionProviderTest {
  private val gapPx = 8
  private val popupSize = IntSize(260, 420)
  private val windowSize = IntSize(1080, 1920)

  @Test
  fun ltr_opensAboveAnchorWhenThereIsRoom() {
    var flippedDown: Boolean? = null
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = { flippedDown = it })
    val anchor = IntRect(left = 100, top = 1400, right = 144, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    assertFalse(flippedDown!!)
    assertEquals(100, position.x)
    assertEquals(anchor.top - popupSize.height - gapPx, position.y)
  }

  @Test
  fun rtl_alignsTrailingEdgeAndOpensAbove() {
    var flippedDown: Boolean? = null
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = { flippedDown = it })
    val anchor = IntRect(left = 900, top = 1400, right = 944, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Rtl, popupSize)

    assertFalse(flippedDown!!)
    assertEquals(anchor.right - popupSize.width, position.x)
    assertEquals(anchor.top - popupSize.height - gapPx, position.y)
  }

  @Test
  fun flipsBelowAnchorWhenThereIsNoRoomAbove() {
    var flippedDown: Boolean? = null
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = { flippedDown = it })
    val anchor = IntRect(left = 100, top = 24, right = 144, bottom = 68)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    assertTrue(flippedDown!!)
    assertEquals(anchor.bottom + gapPx, position.y)
  }

  @Test
  fun clampsHorizontallyInsideWindow() {
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = {})
    val anchor = IntRect(left = 0, top = 1400, right = 44, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    assertEquals(gapPx, position.x)
  }

  @Test
  fun pivotIsZeroWhenLeadingEdgeAligns() {
    var pivot = -1f
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = {}, onPivotFractionX = { pivot = it })
    val anchor = IntRect(left = 100, top = 1400, right = 144, bottom = 1444)

    provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    assertEquals(0f, pivot, 0.0001f)
  }

  @Test
  fun pivotTracksAnchorWhenClampedNearRightEdge() {
    var pivot = -1f
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = {}, onPivotFractionX = { pivot = it })
    // Anchor near the right edge: the popup can't align its left edge to it, so it clamps left.
    val anchor = IntRect(left = 950, top = 1400, right = 994, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    // Origin must land on the "+" (anchor.left), not the popup's shifted left edge.
    val expected = (anchor.left - position.x).toFloat() / popupSize.width
    assertEquals(expected, pivot, 0.0001f)
    assertTrue("pivot should be pulled inward, not 0", pivot > 0f)
  }

  @Test
  fun rtl_clampsTrailingEdgeInsideWindow() {
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = {})
    val anchor = IntRect(left = 1036, top = 1400, right = 1080, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Rtl, popupSize)

    val maxX = windowSize.width - popupSize.width - gapPx
    assertEquals(maxX, position.x)
  }

  @Test
  fun shadowPad_offsetsSoTheCardAlignsWithTheAnchor() {
    val pad = 20
    val provider = PlusMenuPositionProvider(gapPx = gapPx, shadowPadPx = pad, onFlipDown = {})
    val anchor = IntRect(left = 40, top = 1400, right = 84, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    assertEquals(anchor.left - pad, position.x)
    assertEquals(anchor.top - popupSize.height - gapPx, position.y)
  }

  @Test
  fun shadowPad_clampsFlushWhenTheAnchorSitsOnTheLeftEdge() {
    val pad = 20
    val provider = PlusMenuPositionProvider(gapPx = gapPx, shadowPadPx = pad, onFlipDown = {})
    val anchor = IntRect(left = 10, top = 1400, right = 54, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Ltr, popupSize)

    // Pad itself is the margin, so the window may sit at x = 0 and still keep the card on-screen.
    assertEquals(0, position.x)
  }
}
