package com.echoflow.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PlusMenuScreenshotTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun plus_menu_open_settled() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(expanded = true, anchorAlignment = Alignment.BottomStart)
      }
    }
    composeRule.waitForIdle()

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_open_settled.png",
    )
  }

  @Test
  fun plus_menu_open_mid_enter() {
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

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_open_mid_enter.png",
    )
  }

  @Test
  fun plus_menu_closing_mid_exit() {
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

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_closing_mid_exit.png",
    )
  }

  @Test
  fun plus_menu_reduced_motion_open() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(
          expanded = true,
          reducedMotion = true,
          anchorAlignment = Alignment.BottomStart,
        )
      }
    }

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_reduced_motion_open.png",
    )
  }

  @Test
  fun plus_menu_rtl_open() {
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

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_rtl_open.png",
    )
  }

  @Test
  fun plus_menu_flipped_below_anchor() {
    composeRule.setContent {
      EchoFlowTheme {
        PlusMenuHarness(
          expanded = true,
          anchorAlignment = Alignment.TopStart,
        )
      }
    }
    composeRule.waitForIdle()

    composeRule.onRoot().captureRoboImage(
      filePath = "src/test/screenshots/plus_menu_flipped_below_anchor.png",
    )
  }
}
