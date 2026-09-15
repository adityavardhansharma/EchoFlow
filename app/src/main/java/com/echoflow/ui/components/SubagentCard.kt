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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing

// ── Echo Agent ──────────────────────────────────────────────────────────────────────────

/**
 * One Echo Agent delegation in the reply timeline — a primary-accented card (distinct from the
 * tertiary Adviser and secondary Fusion cards) that makes the orchestrator's task decomposition
 * visible. While [active] it shows the task the model handed off and a wavy "running on the
 * worker" indicator; once resolved it reveals the worker's outcome (or an error). The
 * orchestrator may spin off several of these in one answer, each its own card.
 */
@Composable
fun SubagentCard(
    taskName: String,
    taskDescription: String,
    workerModel: String,
    outcome: String?,
    error: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasOutcome = !outcome.isNullOrBlank()
    val hasError = !error.isNullOrBlank()
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    // Default: open while running and once the result lands (the brief + result are the point).
    val expanded = userToggled ?: (active || hasOutcome || hasError)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "subagent-chevron")
    val canToggle = taskDescription.isNotBlank() || hasOutcome || hasError

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Surface(
                onClick = { if (canToggle) userToggled = !expanded },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedPolygonShape(MaterialShapes.Pentagon))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Hub, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        ModeBadge("ECHO AGENT", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (active) "${taskName.ifBlank { "Delegating" }}…" else taskName.ifBlank { "Delegated task" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        if (workerModel.isNotBlank()) {
                            Text(
                                "Echo Agent · ${shortModel(workerModel)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!active && canToggle) {
                        Icon(
                            Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                            Modifier.size(22.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (active) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = expanded && canToggle, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.m)) {
                    if (taskDescription.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(Spacing.m)) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.s))
                                Column {
                                    Text("Task brief", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Text(taskDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    when {
                        hasError -> {
                            if (taskDescription.isNotBlank()) Spacer(Modifier.height(Spacing.m))
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(Spacing.s))
                                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                        hasOutcome -> {
                            if (taskDescription.isNotBlank()) Spacer(Modifier.height(Spacing.m))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(Spacing.xs))
                                Text("Result", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            RichMarkdown(outcome!!, Modifier.fillMaxWidth())
                        }
                        !active -> {
                            Text(
                                "The Echo Agent ran; no result text was returned separately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Branded waiting state for an Echo Agents run. The request is non-streaming, so this card
 * animates through the whole wait — the user sees "Echo Agents are deploying…" instead of a bare
 * thinking row — and is replaced by the delegation cards, reasoning and answer once they arrive.
 */
@Composable
fun AgentDeployingCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedPolygonShape(MaterialShapes.Pentagon))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Hub, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    ModeBadge("ECHO AGENTS", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Deploying agents…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "The main model is planning, delegating and gathering results",
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
