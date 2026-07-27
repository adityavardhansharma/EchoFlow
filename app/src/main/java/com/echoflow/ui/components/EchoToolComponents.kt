@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.FusionAnalysis
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Short, human label for an OpenRouter model id, e.g. "anthropic/claude-opus-4.8" → "claude-opus-4.8". */
private fun shortModel(id: String): String = id.substringAfterLast('/').ifBlank { id }

/** A tiny uppercase brand chip ("ECHO ADVISER" / "ECHO FUSION") so the mode is unmistakable. */
@Composable
private fun ModeBadge(label: String, container: Color, onContainer: Color) {
    Surface(shape = CircleShape, color = container) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = onContainer,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = 3.dp),
        )
    }
}

// ── Echo Adviser ────────────────────────────────────────────────────────────────────────

/**
 * One Echo Adviser consultation in the reply timeline — a tertiary-accented side-channel that
 * is visually unmistakable from a normal reasoning trace. While [active] it shows a branded
 * "consulting…" header with a wavy indicator; once resolved it reveals (expanded by default)
 * the question the answering model asked and the advisor's full advice. This is the
 * transparency win: the user watches a model escalate to a stronger mind and sees what it said.
 */
@Composable
fun AdvisorCard(
    advisorName: String,
    advisorModel: String,
    prompt: String,
    advice: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasAdvice = !advice.isNullOrBlank()
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    // Default: open while consulting and once advice lands (the advice is the whole point).
    val expanded = userToggled ?: (active || hasAdvice)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "advisor-chevron")
    val canToggle = hasAdvice || prompt.isNotBlank()

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f),
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
                        Modifier.size(38.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie7Sided))
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Psychology, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                    Spacer(Modifier.width(Spacing.m))
                    Column(Modifier.weight(1f)) {
                        ModeBadge("ECHO ADVISER", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (active) "Consulting $advisorName…" else "$advisorName advised",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (advisorModel.isNotBlank()) {
                            Text(
                                shortModel(advisorModel),
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
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(visible = expanded && canToggle, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.m)) {
                    if (prompt.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(Spacing.m)) {
                                Icon(Icons.Default.QuestionAnswer, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(Spacing.s))
                                Column {
                                    Text("What the model asked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(2.dp))
                                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    if (hasAdvice) {
                        if (prompt.isNotBlank()) Spacer(Modifier.height(Spacing.m))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(Spacing.xs))
                            Text("Advice", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                        RichMarkdown(advice!!, Modifier.fillMaxWidth())
                    } else if (!active) {
                        Text(
                            "The advisor was consulted; its written advice wasn't returned separately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

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

// ── Echo Fusion ─────────────────────────────────────────────────────────────────────────

/**
 * One Echo Fusion deliberation in the reply timeline. While [active] it shows the real panel
 * roster as a travelling shimmer wave under a branded "deliberating" header. Once resolved it
 * becomes the headline ("synthesized from N models by <judge>") plus the judge's structured
 * comparison — consensus, disagreements, unique insights, blind spots — and an accordion of
 * every model's full answer. Fusion never picks a single winner; the judge blends them, which
 * the framing makes explicit.
 */
@Composable
fun FusionCard(
    panelName: String,
    models: List<String>,
    analysis: FusionAnalysis?,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val roster = analysis?.models?.takeIf { it.isNotEmpty() } ?: models
    val judge = analysis?.judgeModel

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(RoundedPolygonShape(MaterialShapes.Clover4Leaf))
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.AccountTree, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondary) }
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    ModeBadge("ECHO FUSION", MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        if (panelName.isNotBlank()) panelName else "Fusion panel",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (active) "${roster.size} models deliberating…" else "Synthesized from ${roster.size} models",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.m))
            PanelRoster(roster, shimmer = active)

            if (active) {
                Spacer(Modifier.height(Spacing.m))
                LinearWavyProgressIndicator(
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Panel deliberating in parallel · the judge will synthesize their answers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (analysis != null) {
                Spacer(Modifier.height(Spacing.m))
                // Honest framing: the final answer below is a synthesis, not one model's reply.
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Gavel, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            buildString {
                                append("Final answer synthesized from ${roster.size} models")
                                judge?.takeIf { it.isNotBlank() }?.let { append(" by ${shortModel(it)}") }
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                FusionDeliberation(analysis)
            }
        }
    }
}

/**
 * The panel members as a horizontal strip of model chips. While [shimmer] they pulse as a
 * travelling wave (each chip phase-offset) so the panel reads as alive; once done each shows
 * a check. We can't observe true per-model completion in one request, so the wave is honest
 * "work in progress", not a fake per-model clock.
 */
@Composable
private fun PanelRoster(models: List<String>, shimmer: Boolean) {
    val transition = rememberInfiniteTransition(label = "roster")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "roster-phase",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        models.forEachIndexed { index, model ->
            val wave = 0.4f + 0.6f * abs(sin(PI * (phase + index * 0.18f)).toFloat())
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.alpha(if (shimmer) wave else 1f),
            ) {
                Row(
                    Modifier.padding(start = Spacing.s, end = Spacing.m, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (shimmer) Icons.Default.Bolt else Icons.Default.CheckCircle,
                        null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(shortModel(model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                }
            }
        }
    }
}

/** The resolved fusion body: a collapsible deliberation digest + a per-model answers accordion. */
@Composable
private fun FusionDeliberation(analysis: FusionAnalysis) {
    Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (!analysis.isEmpty) {
            var open by remember { mutableStateOf(true) }
            val chevron by animateFloatAsState(if (open) 180f else 0f, label = "fusion-delib-chevron")
            Surface(
                onClick = { open = !open },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.CompareArrows, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(Spacing.s))
                    Text("How the panel compared", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, if (open) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (analysis.consensus.isNotEmpty()) {
                        FusionSection("Consensus", Icons.Default.CheckCircle, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                            BulletList(analysis.consensus, MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    if (analysis.contradictions.isNotEmpty()) {
                        FusionSection("Disagreements", Icons.AutoMirrored.Filled.CompareArrows, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                analysis.contradictions.forEach { c ->
                                    Column {
                                        Text(c.topic, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onErrorContainer)
                                        c.stances.forEach { stance ->
                                            Row(Modifier.padding(top = 2.dp)) {
                                                Text("⟂  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                Text(stance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (analysis.uniqueInsights.isNotEmpty()) {
                        FusionSection("Unique insights", Icons.Default.Lightbulb, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                analysis.uniqueInsights.forEach { i ->
                                    Column {
                                        if (i.model.isNotBlank()) {
                                            Text(shortModel(i.model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                        Text(i.insight, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }
                    if (analysis.blindSpots.isNotEmpty()) {
                        FusionSection("Blind spots", Icons.Default.VisibilityOff, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface) {
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
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "fusion-resp-chevron")
    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    "Each model's answer · ${analysis.responses.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.size(20.dp).rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    analysis.responses.forEachIndexed { index, resp ->
                        Column {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Row(
                                    Modifier.padding(start = Spacing.s, end = Spacing.m, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Bolt, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(Modifier.width(4.dp))
                                    Text(shortModel(resp.model), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
