@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoflow.data.FusionAnalysis
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

/** Expanded settled depth: comparison digest + per-model answers (not the default surface). */
@Composable
internal fun FusionDeliberation(analysis: FusionAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (!analysis.isEmpty) {
            var open by remember { mutableStateOf(false) }
            val reducedMotion = rememberReducedMotion()
            val chevron by animateFloatAsState(
                targetValue = if (open) 180f else 0f,
                animationSpec = tween(if (reducedMotion) 0 else 300),
                label = "fusion-delib-chevron",
            )
            Surface(
                onClick = { open = !open },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.CompareArrows,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "How the panel compared",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        if (open) "Collapse" else "Expand",
                        Modifier.size(20.dp).rotate(chevron),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = fusionRevealEnter(reducedMotion),
                exit = fusionRevealExit(reducedMotion),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (analysis.consensus.isNotEmpty()) {
                        FusionSection(
                            "Consensus",
                            Icons.Default.CheckCircle,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            BulletList(analysis.consensus, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    if (analysis.contradictions.isNotEmpty()) {
                        FusionSection(
                            "Disagreements",
                            Icons.AutoMirrored.Filled.CompareArrows,
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                analysis.contradictions.forEach { c ->
                                    Column {
                                        Text(
                                            c.topic,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        c.stances.forEach { stance ->
                                            Row(Modifier.padding(top = 2.dp)) {
                                                Text(
                                                    "⟂  ",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Text(
                                                    stance,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (analysis.uniqueInsights.isNotEmpty()) {
                        FusionSection(
                            "Unique insights",
                            Icons.Default.Lightbulb,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                analysis.uniqueInsights.forEach { i ->
                                    Column {
                                        if (i.model.isNotBlank()) {
                                            Text(
                                                shortModel(i.model),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary,
                                            )
                                        }
                                        Text(
                                            i.insight,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (analysis.blindSpots.isNotEmpty()) {
                        FusionSection(
                            "Blind spots",
                            Icons.Default.VisibilityOff,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface,
                        ) {
                            BulletList(analysis.blindSpots, MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (analysis.responses.isNotEmpty()) {
            ModelResponsesDisclosure(analysis)
        }
        if (analysis.failedModels.isNotEmpty()) {
            Text(
                "Did not respond: ${analysis.failedModels.joinToString(", ") { shortModel(it) }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun FusionSection(
    title: String,
    icon: ImageVector,
    container: Color,
    onContainer: Color,
    content: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(16.dp), tint = onContainer)
                Spacer(Modifier.width(Spacing.s))
                Text(title, style = MaterialTheme.typography.labelLarge, color = onContainer)
            }
            Spacer(Modifier.height(Spacing.s))
            content()
        }
    }
}

@Composable
private fun BulletList(items: List<String>, color: Color) {
    Column {
        items.forEach { item ->
            Row(Modifier.padding(vertical = 1.dp)) {
                Text("•  ", style = MaterialTheme.typography.bodySmall, color = color)
                Text(item, style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

/** Collapsed accordion holding each panel model's full answer, rendered as markdown. */
@Composable
private fun ModelResponsesDisclosure(analysis: FusionAnalysis) {
    var expanded by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "fusion-resp-chevron",
    )
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "Each model's answer · ${analysis.responses.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    Modifier.size(20.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fusionRevealEnter(reducedMotion),
                exit = fusionRevealExit(reducedMotion),
            ) {
                Column(
                    Modifier.padding(top = Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    analysis.responses.forEachIndexed { index, resp ->
                        Column {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(
                                    Modifier.padding(start = Spacing.s, end = Spacing.m, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        null,
                                        Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        shortModel(resp.model),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            RichMarkdown(resp.content, Modifier.fillMaxWidth())
                        }
                        if (index != analysis.responses.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}
