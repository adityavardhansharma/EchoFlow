@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.echoflow.data.InferenceLimits
import com.echoflow.data.InferenceParams
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Collapsible "generation parameters" card used on both the Cloud and Local model pages.
 * One global set of sampler knobs per side; values persist as the user releases each slider.
 * Out-of-range values are clamped per model at generation time, so the sliders here are the
 * user's *preferences*, not necessarily what a given small model will run with.
 */
@Composable
internal fun InferenceParamsCard(
    local: Boolean,
    params: InferenceParams,
    onChange: (InferenceParams) -> Unit,
    onReset: () -> Unit,
) {
    var open by rememberSaveable(local) { mutableStateOf(false) }
    // Live draft so dragging is smooth; persisted only when a slider is released.
    var draft by remember(params) { mutableStateOf(params) }

    val defaults = if (local) InferenceLimits.LOCAL_DEFAULTS else InferenceLimits.CLOUD_DEFAULTS
    val isDefault = params == defaults
    val topKMax = if (local) InferenceLimits.LOCAL_TOP_K_MAX else InferenceLimits.CLOUD_TOP_K_MAX
    val maxTokCeil = if (local) InferenceLimits.LOCAL_MAX_TOKENS_CEIL else InferenceLimits.CLOUD_MAX_TOKENS_CEIL
    val tokenSteps = (maxTokCeil / InferenceLimits.MAX_TOKENS_STEP) - 1

    val chevron by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "paramsChevron",
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Surface(onClick = { open = !open }, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedPolygonShape(MaterialShapes.Cookie4Sided))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Spacer(Modifier.width(Spacing.base))
                    Column(Modifier.weight(1f)) {
                        Text("Generation parameters", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (isDefault) "Shipped defaults" else "Customized",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        Icons.Default.ExpandMore, if (open) "Collapse" else "Expand",
                        Modifier.rotate(chevron), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(visible = open, enter = sectionEnter(), exit = sectionExit()) {
                Column(Modifier.padding(start = Spacing.base, end = Spacing.base, bottom = Spacing.base)) {
                    ParamSliderRow(
                        label = "Temperature",
                        value = draft.temperature,
                        valueRange = InferenceLimits.TEMP_MIN..InferenceLimits.TEMP_MAX,
                        steps = 39,
                        display = { "%.2f".format(it) },
                        editPrefill = { "%.2f".format(it) },
                        editHint = "0.00 – 2.00",
                        editDecimals = true,
                        onValueChange = { draft = draft.copy(temperature = (it * 100).roundToInt() / 100f) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Top P",
                        value = draft.topP,
                        valueRange = InferenceLimits.TOP_P_MIN..InferenceLimits.TOP_P_MAX,
                        steps = 19,
                        display = { "%.2f".format(it) },
                        editPrefill = { "%.2f".format(it) },
                        editHint = "0.00 – 1.00",
                        editDecimals = true,
                        onValueChange = { draft = draft.copy(topP = (it * 100).roundToInt() / 100f) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Top K",
                        value = draft.topK.toFloat(),
                        valueRange = 0f..topKMax.toFloat(),
                        steps = 0,
                        display = { if (it < 1f) "Off" else it.roundToInt().toString() },
                        editPrefill = { it.roundToInt().toString() },
                        editHint = "0 (off) – $topKMax",
                        editDecimals = false,
                        onValueChange = { draft = draft.copy(topK = it.roundToInt()) },
                        onCommit = { onChange(draft) },
                    )
                    ParamSliderRow(
                        label = "Max tokens",
                        value = draft.maxTokens.coerceIn(0, maxTokCeil).toFloat(),
                        valueRange = 0f..maxTokCeil.toFloat(),
                        steps = tokenSteps,
                        display = {
                            val v = it.roundToInt()
                            if (v <= 0) (if (local) "Model default" else "Unlimited") else v.toString()
                        },
                        editPrefill = { it.roundToInt().toString() },
                        editHint = "0 (${if (local) "model default" else "unlimited"}) – $maxTokCeil",
                        editDecimals = false,
                        onValueChange = { draft = draft.copy(maxTokens = it.roundToInt()) },
                        onCommit = { onChange(draft) },
                    )
                    Text(
                        if (local)
                            "Applied to every on-device model. If a value exceeds what the running model supports, it falls back to the default for that model."
                        else
                            "Applied to every OpenRouter model. Top K and Max tokens are only sent when set above their off position.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Spacer(Modifier.height(Spacing.s))
                    TextButton(
                        onClick = onReset,
                        enabled = !isDefault,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Reset to defaults")
                    }
                }
            }
        }
    }
}

/**
 * One labeled slider row with a value chip that's both a readout and a tap target: tapping
 * it opens a small dialog for typing an exact number. Either way the value is snapped to the
 * slider's step grid and clamped to [valueRange] before [onCommit] persists it.
 */
@Composable
internal fun ParamSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    editPrefill: (Float) -> String,
    editHint: String,
    editDecimals: Boolean,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Surface(
                onClick = { editing = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    display(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = 4.dp),
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onCommit,
            valueRange = valueRange,
            steps = steps,
        )
    }

    if (editing) {
        ParamValueDialog(
            label = label,
            initial = editPrefill(value),
            hint = "Allowed: $editHint",
            decimals = editDecimals,
            onDismiss = { editing = false },
            onConfirm = { typed ->
                editing = false
                typed.trim().toFloatOrNull()?.let { raw ->
                    val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
                    onValueChange(snapToStep(clamped, valueRange, steps))
                    onCommit()
                }
            },
        )
    }
}

/** Snaps a value to the slider's discrete grid (a no-op for continuous sliders). */
internal fun snapToStep(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return value
    val stepSize = (range.endInclusive - range.start) / (steps + 1)
    if (stepSize <= 0f) return value
    return range.start + ((value - range.start) / stepSize).roundToInt() * stepSize
}

/** Numeric entry dialog for a single parameter; accepts integers or decimals per [decimals]. */
@Composable
internal fun ParamValueDialog(
    label: String,
    initial: String,
    hint: String,
    decimals: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
