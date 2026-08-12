@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.echoflow.data.Artifact
import com.echoflow.data.ArtifactVersion
import com.echoflow.data.VersionDelta
import com.echoflow.data.versionDeltas
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.diffAdded
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

// The most recent versions carried as chips; older ones fold into a single "+N" marker so the row
// never wraps past a couple of lines on a phone.
private const val MAX_VERSION_CHIPS = 4
private const val CARD_RADIUS_DP = 22
private const val MARK_SIZE_DP = 24
private const val ROW_MIN_DP = 48

/**
 * The in-chat artifact card for **current** artifacts ([ArtifactRef.UI_VERSION_CURRENT]).
 *
 * Hybrid of the coding-agent tool-chip reference and EchoFlow's research-capsule grammar:
 * - **Building** is a live tool-row trace (mark + label + mono filename chip + size), not a
 *   greyed-out copy of the old tertiary card.
 * - **Settled** is a result object in the same anatomy as [ResearchResultCard] (filled mark,
 *   title, type meta, open action) with **file-diff-style version chips** at the bottom —
 *   `v1 84 lines`, `v2 +74 −41` — the glanceable edit history from the reference.
 *
 * No live HTML WebView thumbnail: that path was janky (white flash + fade) and not in the
 * reference language. Preview lives in the fullscreen workspace the user deliberately opens.
 *
 * Pre-redesign messages are not drawn here — see `ui/legacy/LegacyArtifactComponents.kt`.
 */
@Composable
fun ArtifactCard(
    artifactId: String?,
    title: String,
    artifactType: String,
    version: Int,
    building: Boolean,
    charCount: Int,
    truncated: Boolean,
    onOpen: (artifactId: String, version: Int) -> Unit,
    modifier: Modifier = Modifier,
    observeVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
) {
    if (building) {
        BuildingArtifactCard(
            title = title,
            artifactType = artifactType,
            charCount = charCount,
            modifier = modifier,
        )
        return
    }

    // All versions of this lineage up to and including the one this card was written at — a card in
    // scrolled-back history shows its own era's chips, not the lineage's latest state.
    val versionsFlow = remember(artifactId) {
        if (artifactId != null) observeVersions(artifactId) else flowOf(emptyList())
    }
    val versions by versionsFlow.collectAsState(initial = emptyList())
    val lineage = remember(versions, version) {
        versions.filter { it.versionNumber <= version }.sortedBy { it.versionNumber }
    }
    val deltas by produceState(initialValue = emptyList<VersionDelta>(), lineage) {
        value = withContext(Dispatchers.Default) { versionDeltas(lineage) }
    }

    SettledArtifactCard(
        title = title,
        artifactType = artifactType,
        truncated = truncated,
        deltas = deltas,
        onOpen = { artifactId?.let { onOpen(it, version) } },
        modifier = modifier,
    )
}

/**
 * Settled result — same weight as [ResearchResultCard]: primary container, filled check mark,
 * title + type, open affordance, then file-diff chips for the version lineage.
 */
@Composable
private fun SettledArtifactCard(
    title: String,
    artifactType: String,
    truncated: Boolean,
    deltas: List<VersionDelta>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (_, typeLabel) = artifactGlyph(artifactType)
    val fileName = artifactFileLabel(title, typeLabel, artifactType)

    Surface(
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_DP.dp)
                    .padding(horizontal = Spacing.m, vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Same 24dp filled mark as the research result card — one system, not a 40dp tile.
                FilledArtifactMark(
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    icon = Icons.Default.Check,
                    description = "Artifact ready",
                )
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (truncated) "$typeLabel · incomplete" else typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Default.OpenInFull,
                    "Open artifact",
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                )
            }

            // File-diff chip row from the reference: mono pills with green + / red − counts.
            if (deltas.isNotEmpty()) {
                VersionChipRow(
                    deltas = deltas,
                    fileHint = fileName,
                    modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.m),
                )
            }
        }
    }
}

/** The edit history as glanceable file-diff chips — newest few, older ones folded into "+N". */
@Composable
private fun VersionChipRow(
    deltas: List<VersionDelta>,
    fileHint: String,
    modifier: Modifier = Modifier,
) {
    val shown = remember(deltas) { deltas.takeLast(MAX_VERSION_CHIPS) }
    val hidden = deltas.size - shown.size
    // Hairline above the chips, like the reference's separator before the file pills.
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
        )
        Spacer(Modifier.height(Spacing.m))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (hidden > 0) MoreChip("+$hidden earlier")
            // Leading file pill (reference puts the filename on the diff chips).
            FileNameChip(fileHint)
            shown.forEachIndexed { index, delta ->
                VersionChip(delta = delta, staggerIndex = index)
            }
        }
    }
}

@Composable
private fun FileNameChip(name: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            name,
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VersionChip(delta: VersionDelta, staggerIndex: Int) {
    val reducedMotion = rememberReducedMotion()
    var visible by remember(delta.version) { mutableStateOf(reducedMotion) }
    LaunchedEffect(delta.version) {
        if (!visible) {
            delay(staggerIndex * 45L)
            visible = true
        }
    }
    val appear by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = 520f, dampingRatio = 0.75f),
        label = "chip-appear",
    )
    // Chip sits on a slightly lifted surface so green/red meta read on primaryContainer.
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.graphicsLayer {
            alpha = appear
            val s = 0.92f + 0.08f * appear
            scaleX = s
            scaleY = s
        },
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "v${delta.version}",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                delta.isFirst -> ChipMeta(
                    "${delta.lineCount} lines",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                delta.delta.isEmpty -> ChipMeta("±0", MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    if (delta.delta.added > 0) {
                        ChipMeta("+${delta.delta.added}", MaterialTheme.colorScheme.diffAdded)
                    }
                    if (delta.delta.removed > 0) {
                        ChipMeta("−${delta.delta.removed}", MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipMeta(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
        color = color,
    )
}

@Composable
private fun MoreChip(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            text,
            Modifier.padding(horizontal = Spacing.s, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Live build as a **tool-chip row**, not a greyscale clone of the old card.
 *
 * Anatomy matches the reference and [ResearchCapsule]: 24dp mark slot, action label, mono
 * filename chip, right-aligned size meta, wavy progress under the row. Building → settled
 * keeps the mark slot so the object resolves in place.
 */
@Composable
private fun BuildingArtifactCard(
    title: String,
    artifactType: String,
    charCount: Int,
    modifier: Modifier = Modifier,
) {
    val (_, typeLabel) = artifactGlyph(artifactType)
    val fileName = artifactFileLabel(title, typeLabel, artifactType)
    val phaseLabel = if (charCount > 0) "Writing" else "Starting"
    val sizeMeta = when {
        charCount <= 0 -> null
        charCount < 1000 -> "$charCount chars"
        else -> "${charCount / 1000}k chars"
    }

    Surface(
        shape = RoundedCornerShape(CARD_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.m)) {
            // Primary tool row — the reference's "Write 204 lines  [ChurnSchedule.tsx]" line.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ROW_MIN_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(MARK_SIZE_DP.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MARK_SIZE_DP.dp),
                    )
                }
                Spacer(Modifier.width(Spacing.m))
                Text(
                    phaseLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(Spacing.s))
                // Mono filename chip — the reference's grey pill next to the action.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        fileName,
                        Modifier.padding(horizontal = Spacing.s, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (sizeMeta != null) {
                    Text(
                        sizeMeta,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Quiet second beat once content is flowing — echoes the expanded "detail" lines
            // under a Write row without dumping code into the bubble.
            if (charCount > 0) {
                Spacer(Modifier.height(Spacing.s))
                Row(
                    Modifier.padding(start = MARK_SIZE_DP.dp + Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Spacer(Modifier.width(Spacing.m))
                    Text(
                        "Streaming into $typeLabel…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.m))
            LinearWavyProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilledArtifactMark(
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    description: String,
) {
    Box(
        Modifier
            .size(MARK_SIZE_DP.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, Modifier.size(MARK_SIZE_DP.dp * 0.6f), tint = content)
    }
}

private fun artifactGlyph(type: String): Pair<ImageVector, String> = when (type) {
    Artifact.TYPE_MARKDOWN -> Icons.AutoMirrored.Filled.Article to "Document"
    Artifact.TYPE_LATEX -> Icons.Default.PictureAsPdf to "Report"
    else -> Icons.Default.Code to "Web page"
}

/** A short mono filename-style label for chips — `Pricing.html`, `Report.md`. */
private fun artifactFileLabel(title: String, typeLabel: String, artifactType: String): String {
    val base = title.trim().ifBlank { typeLabel }.replace(Regex("\\s+"), "-")
    val ext = when (artifactType) {
        Artifact.TYPE_MARKDOWN -> "md"
        Artifact.TYPE_LATEX -> "tex"
        else -> "html"
    }
    // Avoid double extensions if the model already put one in the title.
    return if (base.contains('.')) base else "$base.$ext"
}
