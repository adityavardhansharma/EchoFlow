@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.FusionAnalysis
import com.echoflow.data.FusionResponse
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

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
    // Hard fail: tool ran and reported model failures.
    // Skip: fusion was never invoked. Unparsed: invoked but structured body not decoded.
    val panelFailed = analysis?.isHardFailure == true
    val panelDidNotRun = analysis?.panelDidNotRun == true
    val detailUnparsed = analysis?.detailUnparsed == true
    val processLive = active || (analysis != null && isStreaming && !answerStarted)

    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    // Stay open when we must disclose skip / unparsed so it is not a quiet "success".
    val expanded = userToggled
        ?: (processLive || ((panelDidNotRun || detailUnparsed) && !answerStarted))
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
        detailUnparsed -> FusionStepState.Done // ran; detail missing — not a hard fail
        else -> FusionStepState.Done
    }
    val compareState = when {
        analysis == null -> FusionStepState.Pending
        panelFailed || panelDidNotRun -> FusionStepState.Failed
        detailUnparsed -> FusionStepState.Done
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
        detailUnparsed -> {
            if (panelName.isNotBlank()) "Fusion · $panelName · detail unavailable" else "Fusion · detail unavailable"
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
                            detailUnparsed -> "ran · detail missing"
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
                            // Only mark every model failed on hard fail or true skip — not unparsed.
                            panelFailed = panelFailed || panelDidNotRun,
                            reducedMotion = reducedMotion,
                        )
                    }

                    FusionProcessStep(
                        label = "Compare",
                        state = compareState,
                        meta = when {
                            panelDidNotRun || panelFailed -> "skipped"
                            detailUnparsed -> "unavailable"
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
                    } else if (detailUnparsed && !processLive) {
                        Text(
                            "The panel ran, but structured comparison detail could not be read. The answer below may still be a fusion synthesis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

internal fun fusionModelFailed(model: String, failed: List<String>): Boolean {
    if (failed.isEmpty()) return false
    val id = model.lowercase()
    val short = shortModel(model).lowercase()
    return failed.any { f ->
        val fl = f.lowercase()
        fl == id || fl.endsWith(id) || id.endsWith(fl) ||
            fl == short || fl.endsWith(short) || short.endsWith(fl)
    }
}

internal fun fusionModelResponded(model: String, responses: List<FusionResponse>): Boolean {
    if (responses.isEmpty()) return false
    val id = model.lowercase()
    val short = shortModel(model).lowercase()
    return responses.any { r ->
        val m = r.model.lowercase()
        m == id || m.endsWith(id) || id.endsWith(m) ||
            shortModel(r.model).lowercase() == short
    }
}
