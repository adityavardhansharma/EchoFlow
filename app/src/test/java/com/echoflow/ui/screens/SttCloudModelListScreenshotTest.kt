package com.echoflow.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.echoflow.data.SttCatalog
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
class SttCloudModelListScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cloud_list_shows_cost_marks_and_best_badge() {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(Modifier.fillMaxWidth()) {
                    SttCloudModelList(
                        models = SttCatalog.CLOUD_MODELS,
                        selectedId = SttCatalog.DEFAULT_MODEL_ID,
                        onSelect = {},
                    )
                }
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/stt_cloud_models.png",
        )
    }
}
