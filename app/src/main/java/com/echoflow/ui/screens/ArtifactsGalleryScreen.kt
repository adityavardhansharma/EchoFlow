@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Artifact
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.ArtifactHtmlThumbnail
import com.echoflow.ui.theme.Spacing

/**
 * The Artifacts gallery — a shelf of every chat's latest artifact (one per chat, by the
 * one-lineage-per-chat rule). Each artifact is one full-width block: a live sliver of the rendered
 * page up top (the same sandboxed thumbnail the in-chat card uses) and a meta row below. It is a
 * browse surface, not a second viewer — a block slides the existing workspace up over the gallery,
 * and the version chip drops straight into an earlier version.
 */
@Composable
fun ArtifactsGalleryScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
) {
    val artifacts by chatViewModel.galleryArtifacts.collectAsState()

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "gallery-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            GalleryTopBar(count = artifacts.size, onClose = onClose)
            if (artifacts.isEmpty()) {
                GalleryEmptyState(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(Spacing.base, Spacing.s, Spacing.base, Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.base),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(artifacts, key = { it.id }) { artifact ->
                        ArtifactBlock(
                            artifact = artifact,
                            loadContent = { chatViewModel.galleryArtifactContent(artifact.id) },
                            onOpen = { chatViewModel.openArtifactFromGallery(artifact.id) },
                            onOpenVersion = { v -> chatViewModel.openArtifactFromGallery(artifact.id, v) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryTopBar(count: Int, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = Spacing.xs, end = Spacing.base, top = Spacing.s, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, "Close artifacts", Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Artifacts", style = MaterialTheme.typography.headlineSmall)
                if (count > 0) {
                    Text(
                        if (count == 1) "1 artifact" else "$count artifacts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactBlock(
    artifact: Artifact,
    loadContent: suspend () -> String?,
    onOpen: () -> Unit,
    onOpenVersion: (Int) -> Unit,
) {
    val (glyph, typeLabel) = galleryGlyph(artifact.type)
    // Lazily fetch the rendered body for the thumbnail; recycled off-screen, so cost stays bounded.
    val content by produceState<String?>(initialValue = null, artifact.id, artifact.currentVersion) {
        value = loadContent()
    }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Preview band — a live sliver for HTML, a readable text slice for docs/reports.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(start = Spacing.s, end = Spacing.s, top = Spacing.s)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val body = content
                when {
                    body == null -> PreviewSkeleton(glyph)
                    artifact.isHtml -> {
                        ArtifactHtmlThumbnail(body, Modifier.fillMaxSize())
                        // Card owns the gesture; swallow WebView touches.
                        Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
                    }
                    else -> DocPreview(body, glyph)
                }
            }

            // Meta row.
            Row(
                Modifier.fillMaxWidth().padding(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(glyph, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        artifact.title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (artifact.currentVersion > 1) {
                    VersionChip(artifact.currentVersion, onOpenVersion)
                    Spacer(Modifier.width(Spacing.xs))
                }
                Icon(
                    Icons.Default.OpenInFull, "Open",
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreviewSkeleton(glyph: ImageVector) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun DocPreview(body: String, glyph: ImageVector) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Text(
            body.take(600),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxSize().padding(Spacing.m),
        )
    }
}

/** A "v{n}" chip that opens a menu to drop straight into any earlier version of the lineage. */
@Composable
private fun VersionChip(currentVersion: Int, onOpenVersion: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("v$currentVersion", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.UnfoldMore, "Pick version", Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (currentVersion downTo 1).forEach { v ->
                DropdownMenuItem(
                    text = { Text(if (v == currentVersion) "Version $v (latest)" else "Version $v") },
                    trailingIcon = {
                        if (v == currentVersion) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    onClick = { open = false; onOpenVersion(v) },
                )
            }
        }
    }
}

@Composable
private fun GalleryEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Dashboard, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(
            "No artifacts yet",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.l),
        )
        Text(
            "Web pages, documents and reports you build in Artifact mode collect here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

private fun galleryGlyph(type: String): Pair<ImageVector, String> = when (type) {
    Artifact.TYPE_MARKDOWN -> Icons.AutoMirrored.Filled.Article to "Document"
    Artifact.TYPE_LATEX -> Icons.Default.PictureAsPdf to "Report"
    else -> Icons.Default.Code to "Web page"
}
