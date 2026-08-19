package com.echoflow.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.echoflow.data.ExtractionStatus
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.components.groupedItemShape
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
class ProjectDocumentRowScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val documents = listOf(
        ProjectDocument(
            id = "1",
            projectId = "p",
            name = "chapter-one.pdf",
            mimeType = "application/pdf",
            sizeBytes = 248_832,
            filePath = "/tmp/chapter-one.pdf",
            extractedText = "Once upon a time",
            addedAt = 1_700_000_000_000,
            extractionStatus = ExtractionStatus.EXTRACTED.name,
        ),
        ProjectDocument(
            id = "2",
            projectId = "p",
            name = "cast.md",
            mimeType = "text/markdown",
            sizeBytes = 4_096,
            filePath = "/tmp/cast.md",
            extractedText = "Names",
            addedAt = 1_700_000_000_100,
            extractionStatus = ExtractionStatus.EXTRACTED.name,
        ),
        ProjectDocument(
            id = "3",
            projectId = "p",
            name = "timeline.xlsx",
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            sizeBytes = 12_288,
            filePath = "/tmp/timeline.xlsx",
            extractedText = "Act 1",
            addedAt = 1_700_000_000_200,
            extractionStatus = ExtractionStatus.EXTRACTED.name,
        ),
    )

    @Test
    fun project_files_grouped_rows() {
        setFileList()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/project_files_grouped_rows.png",
        )
    }

    @Test
    fun project_files_row_pressed_matches_shape() {
        setFileList()
        composeRule.onNodeWithText("chapter-one.pdf").performTouchInput { down(center) }
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/project_files_row_pressed.png",
        )
    }

    @Test
    fun project_files_row_click_opens_menu_without_square_overlay() {
        setFileList()
        composeRule.onNodeWithText("chapter-one.pdf").performClick()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/screenshots/project_files_row_menu_anchor.png",
        )
    }

    private fun setFileList() {
        composeRule.setContent {
            EchoFlowTheme {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        documents.forEachIndexed { index, doc ->
                            DocumentRow(
                                document = doc,
                                shape = groupedItemShape(index, documents.size),
                                modelReadsFiles = true,
                                onOpenExternal = {},
                                onOpenMarkdown = {},
                                onRemove = {},
                            )
                        }
                    }
                }
            }
        }
    }
}
