package com.echoflow.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.echoflow.data.Artifact
import com.echoflow.ui.theme.EchoFlowTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ArtifactsGalleryOverflowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val artifact = Artifact(
        id = "a1",
        chatId = "c1",
        title = "Pricing page",
        type = Artifact.TYPE_MARKDOWN,
        currentVersion = 1,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun gallery_block_uses_overflow_not_expand() {
        var opens = 0
        setBlock(onOpen = { opens++ })

        composeRule.onAllNodesWithContentDescription("Open").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Artifact options").assertIsDisplayed()
        composeRule.onNodeWithText("Pricing page").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun overflow_menu_does_not_open_artifact() {
        var opens = 0
        setBlock(onOpen = { opens++ })

        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.onNodeWithText("Remove from list").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
        assertEquals(0, opens)
    }

    @Test
    fun remove_from_list_confirms_then_hides() {
        var removed = 0
        var deleted = 0
        setBlock(onRemoveFromList = { removed++ }, onDelete = { deleted++ })

        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.onNodeWithText("Remove from list").performClick()
        composeRule.onNodeWithText("Remove from list?").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
        assertEquals(0, deleted)
    }

    @Test
    fun delete_confirms_then_deletes() {
        var removed = 0
        var deleted = 0
        setBlock(onRemoveFromList = { removed++ }, onDelete = { deleted++ })

        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.onNodeWithText("Delete artifact?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(0, removed)
        assertEquals(1, deleted)
    }

    @Test
    fun gallery_block_overflow_affordance() {
        setBlock()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/artifacts_gallery_block.png",
        )
    }

    @Test
    fun gallery_block_overflow_menu() {
        setBlock()
        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(isRoot()).onLast().captureRoboImage(
            filePath = "src/test/screenshots/artifacts_gallery_overflow_menu.png",
        )
    }

    @Test
    fun gallery_remove_from_list_dialog() {
        setBlock()
        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.onNodeWithText("Remove from list").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(isRoot()).onLast().captureRoboImage(
            filePath = "src/test/screenshots/artifacts_gallery_remove_dialog.png",
        )
    }

    @Test
    fun gallery_delete_dialog() {
        setBlock()
        composeRule.onNodeWithContentDescription("Artifact options").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(isRoot()).onLast().captureRoboImage(
            filePath = "src/test/screenshots/artifacts_gallery_delete_dialog.png",
        )
    }

    private fun setBlock(
        onOpen: () -> Unit = {},
        onRemoveFromList: () -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    ArtifactBlock(
                        artifact = artifact,
                        loadContent = { "# Pricing\n\nA short document preview." },
                        onOpen = onOpen,
                        onOpenVersion = {},
                        onRemoveFromList = onRemoveFromList,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}
