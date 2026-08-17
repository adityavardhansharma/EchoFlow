package com.echoflow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
