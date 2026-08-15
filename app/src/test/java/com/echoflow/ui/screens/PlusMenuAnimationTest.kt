package com.echoflow.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import com.echoflow.ui.theme.EchoFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlusMenuAnimationTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun opening_midEnter_keepsMenuSurfaceComposed() {
    var expanded by mutableStateOf(false)
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(expanded = expanded, anchorAlignment = Alignment.BottomStart)
      }
    }

    expanded = true
    composeRule.runOnIdle {}
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeBy(40)

    composeRule.onAllNodesWithTag("plus_menu_surface").assertCountEquals(1)
    composeRule.onNodeWithText("Web search").assertIsDisplayed()
  }

  @Test
  fun closing_midExit_keepsSurfaceUntilAnimationEnds() {
    var expanded by mutableStateOf(true)
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(expanded = expanded, anchorAlignment = Alignment.BottomStart)
      }
    }

    composeRule.mainClock.advanceTimeBy(100)
    expanded = false
    composeRule.runOnIdle {}
    composeRule.mainClock.advanceTimeByFrame()
    composeRule.mainClock.advanceTimeBy(30)

    composeRule.onAllNodesWithTag("plus_menu_surface").assertCountEquals(1)
  }

  @Test
  fun reducedMotion_showsSettledMenuImmediately() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(
          expanded = true,
          reducedMotion = true,
          anchorAlignment = Alignment.BottomStart,
        )
      }
    }
    composeRule.waitForIdle()

    composeRule.onAllNodesWithTag("plus_menu_surface").assertCountEquals(1)
    composeRule.onNodeWithText("Web search").assertIsDisplayed()
  }

  @Test
  fun rtl_openMenu_exposesRows() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(
          expanded = true,
          layoutDirection = LayoutDirection.Rtl,
          anchorAlignment = Alignment.BottomEnd,
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("plus_menu_surface").assertIsDisplayed()
    composeRule.onNodeWithText("Artifact").assertIsDisplayed()
  }

  @Test
  fun flippedPlacement_stillRendersMenuRows() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(
          expanded = true,
          anchorAlignment = Alignment.TopStart,
        )
      }
    }

    composeRule.waitForIdle()
    composeRule.onNodeWithTag("plus_menu_surface").assertIsDisplayed()
    composeRule.onNodeWithText("Capabilities").assertIsDisplayed()
  }
}
