@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.legacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Artifact
import com.echoflow.ui.theme.Spacing

/**
 * The pre-redesign artifact card, kept alive verbatim so existing conversations keep rendering
 * exactly as they were written.
 *
 * **This file is frozen.** Nothing in `ui/components` may depend on it, and it is reached from a
 * single dispatch point: assistant messages whose persisted [com.echoflow.data.ArtifactRef] is
 * still stamped [com.echoflow.data.ArtifactRef.UI_VERSION_LEGACY] (every row written before the
 * card redesign, which omits `uiVersion` and deserializes to the legacy default). Artifacts
 * produced from now on write [com.echoflow.data.ArtifactRef.UI_VERSION_CURRENT] and are drawn by
 * `ArtifactCard` in `ui/components` instead.
 *
 * Don't "improve" anything here — a change to these composables retroactively rewrites how old
 * chats look, which is the exact failure this split exists to prevent. The one thing it does still
 * inherit is the design system itself (theme colours, typography, [Spacing]): old messages should
 * keep following palette and font changes, or they would look broken sitting next to new ones.
 * Composition is frozen; skin is not.
 */

/**
 * Pre-redesign in-chat artifact card: tertiary-tinted surface, "ARTIFACT · type" pill, version
 * subtitle, and an explicit Open button. No version-diff chips, no HTML preview thumbnail.
 */
@Composable
fun LegacyArtifactCard(
    title: String,
    artifactType: String,
    version: Int,
    building: Boolean,
    charCount: Int,
    truncated: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, typeLabel) = legacyArtifactGlyph(artifactType)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.40f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            if (building) "BUILDING ARTIFACT" else "ARTIFACT · $typeLabel",
                            Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiary,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        title.ifBlank { typeLabel },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            building && charCount > 0 -> "Writing… ${charCount} characters"
                            building -> "Starting…"
                            truncated -> "Version $version · incomplete (stream cut off)"
                            else -> "Version $version · tap Open to view"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!building) {
                    Spacer(Modifier.width(Spacing.s))
                    FilledTonalButton(onClick = onOpen) {
                        Icon(Icons.Default.OpenInFull, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open")
                    }
                }
            }
            if (building) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun legacyArtifactGlyph(type: String): Pair<ImageVector, String> = when (type) {
    Artifact.TYPE_MARKDOWN -> Icons.AutoMirrored.Filled.Article to "Document"
    Artifact.TYPE_LATEX -> Icons.Default.PictureAsPdf to "Report"
    else -> Icons.Default.Code to "Web page"
}
