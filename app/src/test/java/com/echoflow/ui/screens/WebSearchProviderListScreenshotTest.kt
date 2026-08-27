package com.echoflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.theme.EchoFlowTheme
import com.echoflow.ui.theme.Spacing
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
class WebSearchProviderListScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun provider_list_includes_monid_selected() {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(Spacing.base),
                        verticalArrangement = Arrangement.spacedBy(GroupedItemGap),
                    ) {
                        searchProviders.forEachIndexed { index, option ->
                            ProviderRow(
                                option = option,
                                index = index,
                                count = searchProviders.size,
                                selected = option.id == "monid",
                                onSelect = {},
                            )
                        }
                    }
                }
            }
        }

        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/web_search_providers_monid.png",
        )
    }
}
