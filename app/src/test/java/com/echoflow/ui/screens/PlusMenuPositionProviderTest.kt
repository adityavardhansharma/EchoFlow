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
  fun rtl_clampsTrailingEdgeInsideWindow() {
    val provider = PlusMenuPositionProvider(gapPx = gapPx, onFlipDown = {})
    val anchor = IntRect(left = 1036, top = 1400, right = 1080, bottom = 1444)

    val position = provider.calculatePosition(anchor, windowSize, LayoutDirection.Rtl, popupSize)

    val maxX = windowSize.width - popupSize.width - gapPx
    assertEquals(maxX, position.x)
  }
}
