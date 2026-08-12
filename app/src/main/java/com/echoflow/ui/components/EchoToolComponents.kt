@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.FusionAnalysis
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

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

/** Honest process phases for a fusion turn. No fake per-model clocks mid-flight. */
private enum class FusionStepState { Pending, Active, Done, Failed }

private fun fusionRevealEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(300)) + fadeIn(tween(200))

private fun fusionRevealExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(240)) + fadeOut(tween(120))

/**
 * Echo Fusion process shell — Thinking header + Task rows + Tool chips, in EchoFlow chrome.
 *
 * While the panel is out ([active]) or the judge is about to write ([isStreaming] with analysis
 * but no answer text yet), the shell stays open and steps advance honestly. Once the final
 * answer has started ([answerStarted]) or the turn settles, it collapses like [ReasoningSection]
 * so the chat body is only the synthesized answer. Expand anytime for the step replay,
 * comparison digest, and per-model answers.
 *
 * @param active panel still waiting on the server tool (no analysis yet)
 * @param isStreaming the assistant turn is still live
 * @param answerStarted a [com.echoflow.ui.StreamSegment.Text] has appeared after this segment
 */
@Composable
fun FusionCard(
    panelName: String,
    models: List<String>,
    analysis: FusionAnalysis?,
    active: Boolean,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    answerStarted: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val roster = analysis?.models?.takeIf { it.isNotEmpty() } ?: models
    // Live process: panel waiting, or tool returned but final answer not on screen yet.
    val processLive = active || (analysis != null && isStreaming && !answerStarted)

    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: processLive
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "fusion-process-chevron",
    )
    val toggleInteraction = remember { MutableInteractionSource() }

    // Elapsed clock while live; freezes when the process settles (Reasoning-weight meta).
    val startMs = remember { System.currentTimeMillis() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(processLive) {
        if (!processLive) return@LaunchedEffect
        while (true) {
            elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
            delay(250)
        }
    }
    // Capture final duration once when leaving live.
    var settledMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(processLive, elapsedMs) {
        if (!processLive && elapsedMs > 0L && settledMs == 0L) settledMs = elapsedMs
    }
    val durationLabel = when {
        processLive && elapsedMs >= 1000L -> formatResearchDuration(elapsedMs)
        !processLive && settledMs >= 1000L -> formatResearchDuration(settledMs)
        else -> null
    }

    val panelState = when {
        analysis == null && processLive -> FusionStepState.Active
        analysis == null -> FusionStepState.Pending
        analysis.failedModels.isNotEmpty() &&
            analysis.responses.isEmpty() &&
            analysis.failedModels.size >= roster.size.coerceAtLeast(1) -> FusionStepState.Failed
        else -> FusionStepState.Done
    }
    val compareState = when {
        analysis == null -> FusionStepState.Pending
        processLive && !answerStarted -> FusionStepState.Done // comparison is tool result; answer is next
        else -> FusionStepState.Done
    }
    val answerState = when {
        answerStarted || (!processLive && analysis != null) -> FusionStepState.Done
        analysis != null && processLive -> FusionStepState.Active
        else -> FusionStepState.Pending
    }

    val statusLine = when {
        analysis == null && processLive -> "Panel answering in parallel"
        analysis != null && processLive && !answerStarted -> "Writing final answer"
        else -> null
    }

    val headerTitle = when {
        processLive -> {
            val name = panelName.ifBlank { "Fusion" }
            if (panelName.isNotBlank()) "Fusion · $name" else "Fusion"
        }
        else -> {
            val n = roster.size.coerceAtLeast(models.size)
            buildString {
                append("Fused · $n model")
                if (n != 1) append('s')
            }
        }
    }

    val panelMeta = when (panelState) {
        FusionStepState.Active -> "${roster.size} model${if (roster.size == 1) "" else "s"}"
        FusionStepState.Done -> {
            val returned = analysis?.responses?.size ?: roster.size
            val failed = analysis?.failedModels?.size ?: 0
            buildString {
                append("$returned returned")
                if (failed > 0) append(" · $failed failed")
            }
        }
        FusionStepState.Failed -> "Did not respond"
        FusionStepState.Pending -> null
    }
    val compareMeta = analysis?.let { a ->
        if (a.isEmpty && a.responses.isEmpty()) null
        else buildString {
            val parts = mutableListOf<String>()
            if (a.consensus.isNotEmpty()) parts += "${a.consensus.size} agreed"
            if (a.contradictions.isNotEmpty()) parts += "${a.contradictions.size} disagreed"
            if (parts.isEmpty() && a.responses.isNotEmpty()) parts += "compared"
            append(parts.joinToString(" · "))
        }.takeIf { it.isNotBlank() }
    }
    val answerMeta = when (answerState) {
        FusionStepState.Active -> "writing…"
        else -> null
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            // Thinking-style header — open while live, collapses when the answer owns the turn.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = toggleInteraction,
                        indication = null,
                        onClick = { userToggled = !expanded },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.AccountTree,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    headerTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (processLive) {
                    Spacer(Modifier.width(Spacing.s))
                    LoadingIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                durationLabel?.let { dur ->
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        dur,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    if (expanded) "Collapse" else "Expand",
                    Modifier.size(20.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (statusLine != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    statusLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fusionRevealEnter(reducedMotion),
                exit = fusionRevealExit(reducedMotion),
            ) {
                Column(
                    Modifier.padding(top = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    // Task-row steps with Research mark grammar (secondary accent).
                    FusionProcessStep(
                        label = "Panel",
                        state = panelState,
                        meta = panelMeta,
                    )
                    if (roster.isNotEmpty()) {
                        FusionModelChips(
                            models = roster,
                            failed = analysis?.failedModels.orEmpty(),
                            resolved = analysis != null,
                        )
                    }
                    FusionProcessStep(
                        label = "Compare",
                        state = compareState,
                        meta = compareMeta,
                    )
                    if (analysis != null && !analysis.isEmpty) {
                        FusionCountChips(analysis)
                    }
                    FusionProcessStep(
                        label = "Answer",
                        state = answerState,
                        meta = answerMeta,
                    )

                    // Settled expand only: full comparison + per-model answers stay optional depth.
                    if (!processLive && analysis != null) {
                        Spacer(Modifier.height(Spacing.xs))
                        FusionDeliberation(analysis)
                    }
                }
            }
        }
    }
}

@Composable
private fun FusionProcessStep(
    label: String,
    state: FusionStepState,
    meta: String?,
) {
    val dimmed = state == FusionStepState.Pending
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FusionStepMark(state)
        Spacer(Modifier.width(Spacing.s))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (dimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
            Spacer(Modifier.width(Spacing.s))
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FusionStepMark(state: FusionStepState) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when (state) {
            FusionStepState.Active -> LoadingIndicator(
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
            )
            FusionStepState.Done -> Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Done",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            }
            FusionStepState.Failed -> Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Failed",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onError,
                )
            }
            FusionStepState.Pending -> Box(
                Modifier
                    .size(14.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        }
    }
}

/** Tool-chip density for the panel roster — labels only; checks only after resolve. */
@Composable
private fun FusionModelChips(
    models: List<String>,
    failed: List<String>,
    resolved: Boolean,
) {
    val failedSet = remember(failed) { failed.map { it.lowercase() }.toSet() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp + Spacing.s)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        models.forEach { model ->
            val isFailed = resolved && (
                failedSet.contains(model.lowercase()) ||
                    failed.any { model.endsWith(it) || it.endsWith(model) }
                )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    Modifier.padding(start = Spacing.s, end = Spacing.m, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (resolved) {
                        Icon(
                            if (isFailed) Icons.Default.Close else Icons.Default.Check,
                            null,
                            Modifier.size(12.dp),
                            tint = if (isFailed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        shortModel(model),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Compact count chips after compare (agreed / disagreed) — not a full report. */
@Composable
private fun FusionCountChips(analysis: FusionAnalysis) {
    val chips = buildList {
        if (analysis.consensus.isNotEmpty()) add("${analysis.consensus.size} agreed")
        if (analysis.contradictions.isNotEmpty()) add("${analysis.contradictions.size} disagreed")
        if (analysis.uniqueInsights.isNotEmpty()) add("${analysis.uniqueInsights.size} unique")
        if (analysis.blindSpots.isNotEmpty()) add("${analysis.blindSpots.size} gaps")
    }
    if (chips.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp + Spacing.s)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        chips.forEach { label ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Expanded settled depth: comparison digest + per-model answers (not the default surface). */
@Composable
private fun FusionDeliberation(analysis: FusionAnalysis) {
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
