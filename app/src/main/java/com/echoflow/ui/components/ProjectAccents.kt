@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.ui.theme.RoundedPolygonShape

/**
 * A project's colour identity, resolved from the active [MaterialTheme.colorScheme] rather than
 * hardcoded hex — so every accent adapts to light/dark and to each palette (and dynamic colour)
 * exactly like the rest of the app. [colorIndex] is stored on the project and mapped here.
 */
data class ProjectAccentColors(
    val container: Color,
    val onContainer: Color,
    val solid: Color,
)

/** How many distinct accents [projectAccent] cycles through — the colour picker offers these. */
const val PROJECT_ACCENT_COUNT = 5

@Composable
fun projectAccent(colorIndex: Int): ProjectAccentColors {
    val cs = MaterialTheme.colorScheme
    return when (((colorIndex % PROJECT_ACCENT_COUNT) + PROJECT_ACCENT_COUNT) % PROJECT_ACCENT_COUNT) {
        0 -> ProjectAccentColors(cs.primaryContainer, cs.onPrimaryContainer, cs.primary)
        1 -> ProjectAccentColors(cs.secondaryContainer, cs.onSecondaryContainer, cs.secondary)
        2 -> ProjectAccentColors(cs.tertiaryContainer, cs.onTertiaryContainer, cs.tertiary)
        3 -> ProjectAccentColors(cs.errorContainer, cs.onErrorContainer, cs.error)
        else -> ProjectAccentColors(cs.surfaceVariant, cs.onSurfaceVariant, cs.outline)
    }
}

/**
 * The [MaterialShapes] polygon paired with each accent. A project's identity is a shape *and* a
 * colour — the same trick [ProviderIdentity] uses to keep a wall of medallions distinct — so
 * projects read apart at a glance instead of being five folders in five tints. Indexed in lockstep
 * with [projectAccent], so picking a colour picks a silhouette with it.
 */
private val PROJECT_SHAPES: List<RoundedPolygon> = listOf(
    MaterialShapes.Sunny,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Pentagon,
    MaterialShapes.Gem,
)

fun projectShape(colorIndex: Int): RoundedPolygon =
    PROJECT_SHAPES[((colorIndex % PROJECT_SHAPES.size) + PROJECT_SHAPES.size) % PROJECT_SHAPES.size]

/**
 * The project identity mark — its glyph set in its own [projectShape] medallion, tinted with its
 * accent pair. This is the project's face: reused in the list, the home header and the colour
 * picker so one project wears one mark everywhere it appears.
 */
@Composable
fun ProjectMedallion(
    colorIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    glyph: ImageVector = Icons.Default.FolderOpen,
) {
    val accent = projectAccent(colorIndex)
    val shape = remember(colorIndex) { RoundedPolygonShape(projectShape(colorIndex)) }
    Box(
        modifier.size(size).clip(shape).background(accent.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, null, Modifier.size(size * 0.44f), tint = accent.onContainer)
    }
}
