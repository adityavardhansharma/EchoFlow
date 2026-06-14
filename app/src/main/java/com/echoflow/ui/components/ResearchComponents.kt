@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echoflow.data.Citation
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRun
import com.echoflow.data.SearchSource
import com.echoflow.ui.theme.Spacing

private fun faviconFor(url: String): String =
    "https://www.google.com/s2/favicons?domain=${runCatching { android.net.Uri.parse(url).host }.getOrNull() ?: ""}&sz=64"

/**
 * Live Deep Research progress, mirroring the foreground notification: engine + topic, the
 * current phase with a determinate/indeterminate bar, the plan as a checklist, the sources
 * found so far, and a Cancel action. Driven entirely from the persisted [ResearchRun] so it
 * looks identical whether watched live or reopened after the app was killed.
 */
@Composable
fun ResearchProgressCard(
    run: ResearchRun,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = remember(run.planJson) { ResearchJson.stepsFromJson(run.planJson) }
    val sources = remember(run.sourcesJson) { ResearchJson.sourcesFromJson(run.sourcesJson) }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Science, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Deep Research",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        run.engineLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text("Cancel")
                }
            }

            Spacer(Modifier.height(Spacing.m))
            Text(
                run.topic,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text(
                    run.phase ?: "Working…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(Spacing.s))
            if (run.progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { run.progressDone.toFloat() / run.progressTotal },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                steps.forEachIndexed { index, step ->
                    val done = index < run.progressDone
                    val active = index == run.progressDone && !run.isTerminal
                    Row(
                        Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            done -> Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            active -> LoadingIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            else -> Icon(Icons.Default.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            step,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.m))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        sources.take(6).forEach { source ->
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp)) {
                                AsyncImage(faviconFor(source.url), null, Modifier.padding(2.dp).clip(CircleShape))
                            }
                        }
                    }
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        "${sources.size} source${if (sources.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A finished research report: distinct from a normal chat bubble — a titled container with
 * the full markdown body and a sources section, plus copy. The [planSteps] (if any) collapse
 * into a "How this was researched" disclosure so the report leads with the answer.
 */
@Composable
fun ReportCard(
    report: String,
    citations: List<Citation>,
    planSteps: List<String>,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "Research report",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) { Icon(Icons.Default.ContentCopy, "Copy report", Modifier.size(16.dp)) }
            }

            if (planSteps.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                ResearchPlanDisclosure(planSteps)
            }

            Spacer(Modifier.height(Spacing.s))
            RichMarkdown(report, Modifier.fillMaxWidth())

            if (citations.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.base))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(Spacing.base))
                SourcesRow(citations)
            }
        }
    }
}

/** Collapsed "How this was researched" recap of the plan steps. */
@Composable
fun ResearchPlanDisclosure(steps: List<String>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "plan-chevron")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "How this was researched · ${steps.size} steps",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.s)) {
                    steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text("${i + 1}. ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
