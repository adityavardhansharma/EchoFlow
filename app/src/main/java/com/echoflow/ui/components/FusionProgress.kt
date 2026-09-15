@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.data.FusionAnalysis
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing

// ── Echo Fusion ─────────────────────────────────────────────────────────────────────────

/** Honest process phases for a fusion turn. No fake per-model clocks mid-flight. */
internal enum class FusionStepState { Pending, Active, Done, Failed }

internal fun fusionRevealEnter(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) fadeIn(tween(0)) else expandVertically(tween(300)) + fadeIn(tween(200))

internal fun fusionRevealExit(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) fadeOut(tween(0)) else shrinkVertically(tween(240)) + fadeOut(tween(120))

@Composable
internal fun FusionProcessStep(
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
internal fun FusionBusyIndicator(reducedMotion: Boolean, size: Dp) {
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

/**
 * Multi-line tool-call style rows — one spinner (or check/fail) per panel model.
 * While the panel is out every row is active; after resolve, marks follow real outcomes when known.
 */
@Composable
internal fun FusionModelRows(
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
