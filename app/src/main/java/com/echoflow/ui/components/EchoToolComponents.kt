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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.FusionAnalysis
import com.echoflow.data.FusionResponse
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
 * Echo Fusion process shell — same family as [ReasoningSection]: soft meta block, open while
 * live, collapses when the final answer starts so the chat body is one reply.
 *
 * Multi-line model rows (tool-call style, one spinner each) replace horizontal pills.
 *
 * @param active panel still waiting on the server tool (no analysis yet)
 * @param isStreaming the assistant turn is still live
 * @param answerStarted final answer text has appeared after this segment
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
    val roster = (analysis?.models?.takeIf { it.isNotEmpty() } ?: models).ifEmpty { models }
    // Hard fail: tool ran and reported model failures. Skip: user asked for fusion but panel never ran.
    val panelFailed = analysis?.isHardFailure == true
    val panelDidNotRun = analysis?.panelDidNotRun == true
    val processLive = active || (analysis != null && isStreaming && !answerStarted)

    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    // Stay open when deliberation was skipped so the user sees the disclosure, not a quiet success.
    val expanded = userToggled ?: (processLive || (panelDidNotRun && !answerStarted))
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 300),
        label = "fusion-process-chevron",
    )
    val toggleInteraction = remember { MutableInteractionSource() }

    val startMs = remember { System.currentTimeMillis() }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(processLive) {
        if (!processLive) return@LaunchedEffect
        while (true) {
            elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L)
            delay(250)
        }
    }
    var settledMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(processLive, elapsedMs) {
        if (!processLive && elapsedMs > 0L && settledMs == 0L) settledMs = elapsedMs
    }
    val durationLabel = when {
        processLive && elapsedMs >= 1000L -> formatResearchDuration(elapsedMs)
        !processLive && settledMs >= 1000L -> formatResearchDuration(settledMs)
        else -> null
    }

    val panelActive = active && analysis == null
    val panelState = when {
        panelActive -> FusionStepState.Active
        analysis == null -> FusionStepState.Pending
        panelFailed || panelDidNotRun -> FusionStepState.Failed
        else -> FusionStepState.Done
    }
    val compareState = when {
        analysis == null -> FusionStepState.Pending
        panelFailed || panelDidNotRun -> FusionStepState.Failed
        else -> FusionStepState.Done
    }
    val mergeState = when {
        panelFailed -> FusionStepState.Failed
        panelDidNotRun && !answerStarted -> FusionStepState.Failed
        answerStarted || (!processLive && analysis != null) -> FusionStepState.Done
        analysis != null && processLive -> FusionStepState.Active
        else -> FusionStepState.Pending
    }

    val headerTitle = when {
        processLive -> {
            if (panelName.isNotBlank()) "Fusion · $panelName" else "Fusion"
        }
        panelDidNotRun -> {
            if (panelName.isNotBlank()) "Fusion · $panelName · panel did not run" else "Fusion · panel did not run"
        }
        panelFailed -> {
            if (panelName.isNotBlank()) "Fusion · $panelName" else "Fusion"
        }
        else -> {
            val n = roster.size.coerceAtLeast(1)
            buildString {
                append("Fused · $n model")
                if (n != 1) append('s')
            }
        }
    }

    // Reasoning twin: soft tint, not a solid product slab.
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
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
                if (processLive && !panelFailed) {
                    Spacer(Modifier.width(Spacing.s))
                    FusionBusyIndicator(reducedMotion = reducedMotion, size = 18.dp)
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

            AnimatedVisibility(
                visible = expanded,
                enter = fusionRevealEnter(reducedMotion),
                exit = fusionRevealExit(reducedMotion),
            ) {
                Column(
                    Modifier.padding(top = Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    FusionProcessStep(
                        label = when {
                            panelActive -> "Asking the panel…"
                            panelDidNotRun -> "Panel"
                            panelFailed -> "Panel"
                            else -> "Panel"
                        },
                        state = panelState,
                        meta = when {
                            panelActive -> "${roster.size}"
                            panelDidNotRun -> "not invoked"
                            panelFailed -> {
                                val n = analysis?.failedModels?.size ?: roster.size
                                "$n failed"
                            }
                            analysis != null && analysis.responses.isNotEmpty() -> {
                                val n = analysis.responses.size
                                val f = analysis.failedModels.size
                                if (f > 0) "$n ok · $f failed" else "$n ok"
                            }
                            else -> null
                        },
                        reducedMotion = reducedMotion,
                    )

                    if (roster.isNotEmpty()) {
                        FusionModelRows(
                            models = roster,
                            analysis = analysis,
                            panelActive = panelActive,
                            panelFailed = panelFailed || panelDidNotRun,
                            reducedMotion = reducedMotion,
                        )
                    }

                    FusionProcessStep(
                        label = "Compare",
                        state = compareState,
                        meta = when {
                            panelDidNotRun || panelFailed -> "skipped"
                            analysis == null -> null
                            else -> buildString {
                                val parts = mutableListOf<String>()
                                if (analysis.consensus.isNotEmpty()) parts += "${analysis.consensus.size} agreed"
                                if (analysis.contradictions.isNotEmpty()) parts += "${analysis.contradictions.size} disagreed"
                                if (parts.isEmpty() && analysis.hasUsableDetail) parts += "done"
                                append(parts.joinToString(" · "))
                            }.takeIf { it.isNotBlank() }
                        },
                        reducedMotion = reducedMotion,
                    )

                    FusionProcessStep(
                        label = when (mergeState) {
                            FusionStepState.Active -> "Merging into one answer…"
                            FusionStepState.Failed -> "Answer"
                            else -> "Answer"
                        },
                        state = mergeState,
                        meta = when {
                            panelDidNotRun -> "no deliberation"
                            mergeState == FusionStepState.Active -> null
                            mergeState == FusionStepState.Failed -> "no answer"
                            mergeState == FusionStepState.Done && (answerStarted || !processLive) -> "ready"
                            else -> null
                        },
                        reducedMotion = reducedMotion,
                    )

                    if (panelDidNotRun && !processLive) {
                        Text(
                            "Your panel was not used — the model replied without multi-model deliberation. Try again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }

                    // Optional depth only when settled and we actually have structured detail.
                    if (!processLive && analysis != null && analysis.hasUsableDetail) {
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
    reducedMotion: Boolean,
) {
    val dimmed = state == FusionStepState.Pending
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FusionStepMark(state = state, reducedMotion = reducedMotion)
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
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta != null) {
            Spacer(Modifier.width(Spacing.s))
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMono),
                color = if (state == FusionStepState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Active busy mark. [LoadingIndicator] keeps spinning even when animator duration scale is 0,
 * so reduced-motion falls back to a still secondary ring.
 */
@Composable
private fun FusionBusyIndicator(reducedMotion: Boolean, size: Dp) {
    if (reducedMotion) {
        Box(
            Modifier
                .size(size)
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape),
        )
    } else {
        LoadingIndicator(
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun FusionStepMark(state: FusionStepState, reducedMotion: Boolean) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        when (state) {
            FusionStepState.Active -> FusionBusyIndicator(reducedMotion = reducedMotion, size = 16.dp)
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

private fun fusionModelFailed(model: String, failed: List<String>): Boolean {
    if (failed.isEmpty()) return false
    val id = model.lowercase()
    val short = shortModel(model).lowercase()
    return failed.any { f ->
        val fl = f.lowercase()
        fl == id || fl.endsWith(id) || id.endsWith(fl) ||
            fl == short || fl.endsWith(short) || short.endsWith(fl)
    }
}

private fun fusionModelResponded(model: String, responses: List<FusionResponse>): Boolean {
    if (responses.isEmpty()) return false
    val id = model.lowercase()
    val short = shortModel(model).lowercase()
    return responses.any { r ->
        val m = r.model.lowercase()
        m == id || m.endsWith(id) || id.endsWith(m) ||
            shortModel(r.model).lowercase() == short
    }
}

/**
 * Multi-line tool-call style rows — one spinner (or check/fail) per panel model.
 * While the panel is out every row is active; after resolve, marks follow real outcomes when known.
 */
@Composable
private fun FusionModelRows(
    models: List<String>,
    analysis: FusionAnalysis?,
    panelActive: Boolean,
    panelFailed: Boolean,
    reducedMotion: Boolean,
) {
    Column(
        Modifier.padding(start = Spacing.base),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        models.forEach { model ->
            val state = when {
                panelActive || analysis == null -> FusionStepState.Active
                panelFailed -> FusionStepState.Failed
                !analysis.toolResultFound -> FusionStepState.Done
                fusionModelFailed(model, analysis.failedModels) -> FusionStepState.Failed
                fusionModelResponded(model, analysis.responses) -> FusionStepState.Done
                analysis.responses.isNotEmpty() -> FusionStepState.Failed // others returned; this one silent
                analysis.hasUsableDetail -> FusionStepState.Done
                else -> FusionStepState.Done
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FusionStepMark(state = state, reducedMotion = reducedMotion)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    shortModel(model),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (state) {
                        FusionStepState.Failed -> MaterialTheme.colorScheme.error
                        FusionStepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
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
