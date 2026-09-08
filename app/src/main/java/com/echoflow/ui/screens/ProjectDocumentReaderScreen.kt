package com.echoflow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ArtifactExport
import com.echoflow.data.ExtractionTier
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.components.RichMarkdown
import com.echoflow.ui.theme.Spacing

/**
 * The in-app reader for "Open as Markdown" — the on-device extraction of a project file, rendered
 * with the same native Markdown engine and slide-up chrome as the Artifact/report workspace so the
 * two read as one family. Structured files (PDF/Word/Excel) open in the user's own viewer instead;
 * this surface only exists for the text we produced locally.
 *
 * The **EchoOCR** wordmark lives here and nowhere else: it appears only when anydoc
 * ([ExtractionTier.ANYDOC]) parsed the file. Text from ML Kit OCR or a plain-text read carries no
 * badge — we only brand the reading we did with our own engine.
 */
@Composable
fun ProjectDocumentReaderScreen(
    document: ProjectDocument,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val content = document.extractedText.orEmpty()
    val showBrand = document.tier == ExtractionTier.ANYDOC

    // Slide up from the bottom and fade in on first appearance, mirroring the Artifact workspace so
    // opening a document feels like the same move the app already makes for reports.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "doc-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.fillMaxSize()) {
            ReaderTopBar(
                title = document.name,
                showBrand = showBrand,
                onClose = onClose,
                onCopy = { clipboard.setText(AnnotatedString(content)) },
                onExport = { ArtifactExport.printArtifact(context, document.name, "markdown", content) },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.base),
                ) {
                    RichMarkdown(content, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.xxl))
                }
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    showBrand: Boolean,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onExport: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Close document", Modifier.size(26.dp))
                }
                Text(
                    title.ifBlank { "Document" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, "Copy text", Modifier.size(20.dp))
                }
                FilledTonalIconButton(onClick = onExport) {
                    Icon(Icons.Default.PictureAsPdf, "Export PDF", Modifier.size(20.dp))
                }
            }
            if (showBrand) EchoOcrBadge()
        }
    }
}

/**
 * The one place the EchoOCR wordmark is shown: a small tonal pill under the title, marking that this
 * text was read from the file by the app's own on-device parser.
 */
@Composable
private fun EchoOcrBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                null,
                Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                "EchoOCR",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
