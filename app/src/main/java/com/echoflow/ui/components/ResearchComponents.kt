@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.data.ExaEffort
import com.echoflow.ui.theme.Spacing

/**
 * Composer-side Deep Research controls.
 *
 * The cards that render a run itself live elsewhere: [ResearchTimeline] and [ResearchResultCard]
 * for research produced by the current app, and `ui/legacy/LegacyResearchComponents.kt` for
 * conversations written before the timeline redesign.
 */

/**
 * Compact effort selector shown next to the Deep Research chip when the Exa Agent engine is
 * picked — keeps the effort/cost dial out of the model list. Tapping cycles a dropdown.
 */
@Composable
fun EffortPill(effort: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Row(
                Modifier.padding(start = Spacing.m, end = Spacing.s, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Effort: ${ExaEffort.label(effort)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Icon(Icons.Default.KeyboardArrowDown, "Change effort", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ExaEffort.levels.forEach { level ->
                DropdownMenuItem(
                    text = { Text(ExaEffort.label(level)) },
                    trailingIcon = { if (level == effort) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = { open = false; onSelect(level) },
                )
            }
        }
    }
}

/** A removable capability chip shown above the input (Search / Deep Research / file). */
@Composable
fun CapabilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(start = Spacing.m, end = if (onRemove != null) Spacing.xs else Spacing.m, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}
