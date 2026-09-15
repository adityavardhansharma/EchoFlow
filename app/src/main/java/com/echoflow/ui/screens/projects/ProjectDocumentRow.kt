@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.ExtractionStatus
import com.echoflow.data.ProjectDocument
import com.echoflow.data.ProjectFileOpener
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

@Composable
internal fun DocumentRow(
    document: ProjectDocument,
    shape: Shape,
    modelReadsFiles: Boolean,
    onOpenExternal: () -> Unit,
    onOpenMarkdown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hint = documentStatusHint(document.status, modelReadsFiles)
    val hintIsError = document.status == ExtractionStatus.FAILED ||
        (document.status == ExtractionStatus.NEEDS_PROVIDER && !modelReadsFiles)
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var menuOpen by remember { mutableStateOf(false) }
    // "Open as Markdown" only when we actually have readable on-device text; a file that goes
    // straight to the model (NEEDS_PROVIDER) or failed extraction has none, so the option is hidden.
    val canOpenMarkdown = document.hasText
    val kind = ProjectFileOpener.kindOf(document)
    // Long-press (or tap) surfaces the open options anchored to the row. A bounded ripple painted a
    // square around the rounded chip, so instead we tint the surface fill on press — that feedback
    // is the container's own colour, so it always follows the grouped rounded shape.
    val reducedMotion = rememberReducedMotion()
    val pressColor by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = if (pressed) 90 else 220),
        label = "docPress",
    )
    Box {
        Surface(
            shape = shape,
            color = pressColor,
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { menuOpen = true },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ),
        ) {
            Row(
                Modifier.padding(start = Spacing.base, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .then(
                            when (document.status) {
                                ExtractionStatus.EXTRACTING ->
                                    Modifier.semantics { contentDescription = "Reading ${document.name}" }
                                ExtractionStatus.PENDING ->
                                    Modifier.semantics { contentDescription = "Waiting to read ${document.name}" }
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = document.status.isExtracting,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "docLeading",
                    ) { busy ->
                        if (busy) {
                            LoadingIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Icon(
                                Icons.Default.Description,
                                null,
                                Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(
                        targetState = hint,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "docHint",
                    ) { currentHint ->
                        Text(
                            buildString {
                                append(formatBytes(document.sizeBytes))
                                if (currentHint != null) {
                                    append(" · ")
                                    append(currentHint)
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hintIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                // The three-dot affordance opens the same menu as a long-press, so the actions are
                // discoverable without knowing the gesture.
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.MoreVert, "File options", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(ProjectFileOpener.openLabel(kind)) },
                leadingIcon = { Icon(kindIcon(kind), null) },
                onClick = { menuOpen = false; onOpenExternal() },
            )
            if (canOpenMarkdown) {
                DropdownMenuItem(
                    text = { Text("Open as Markdown") },
                    leadingIcon = { Icon(Icons.Default.Notes, null) },
                    onClick = { menuOpen = false; onOpenMarkdown() },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
            DropdownMenuItem(
                text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

private fun kindIcon(kind: ProjectFileOpener.Kind): ImageVector = when (kind) {
    ProjectFileOpener.Kind.PDF -> Icons.Default.PictureAsPdf
    ProjectFileOpener.Kind.WORD -> Icons.Default.Description
    ProjectFileOpener.Kind.SPREADSHEET -> Icons.Default.TableChart
    ProjectFileOpener.Kind.SLIDES -> Icons.Default.Slideshow
    ProjectFileOpener.Kind.IMAGE -> Icons.Default.Image
    ProjectFileOpener.Kind.OTHER -> Icons.Default.InsertDriveFile
}

internal fun documentStatusHint(status: ExtractionStatus, modelReadsFiles: Boolean): String? = when (status) {
    ExtractionStatus.PENDING -> "Waiting…"
    ExtractionStatus.EXTRACTING -> "Reading…"
    ExtractionStatus.EXTRACTED -> null
    ExtractionStatus.NEEDS_PROVIDER ->
        if (modelReadsFiles) "Sent to the model as a file" else "This model can't read this file"
    ExtractionStatus.FAILED -> "Couldn't read this file"
    ExtractionStatus.UNKNOWN -> null
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
