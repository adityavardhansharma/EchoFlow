package com.echoflow.ui.screens.schedules

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private val ItemHeight = 44.dp
private const val VISIBLE = 5

/**
 * A crown wheel: a scroll picker drawn as a turning cylinder. Values tilt away in 3D as they leave
 * the sapphire window in the middle, the knurled grooves on both edges roll with your thumb and
 * bunch up toward the top and bottom like a real crown seen side-on, and every detent is a clock
 * tick. [cyclic] wheels (hours, minutes) turn forever instead of hitting an end stop.
 */
@Composable
fun CrownWheel(
    values: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    cyclic: Boolean = false,
    width: Dp = 76.dp,
) {
    val size = values.size
    val laps = if (cyclic) 400 else 1
    val count = size * laps
    val base = if (cyclic) size * (laps / 2) else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = base + selected.coerceIn(0, size - 1))
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }
    val current by rememberUpdatedState(selected)
    val callback by rememberUpdatedState(onSelected)

    LaunchedEffect(state, size) {
        snapshotFlow { centerIndex(state, itemPx).let { Math.floorMod(it, size) } }
            .distinctUntilChanged()
            .collect { value ->
                if (state.isScrollInProgress) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (value != current) callback(value)
            }
    }
    // Follow outside changes (the dial, a model edit) by turning the short way to the new value.
    LaunchedEffect(selected) {
        val at = centerIndex(state, itemPx)
        if (!state.isScrollInProgress && Math.floorMod(at, size) != selected) {
            var delta = selected - Math.floorMod(at, size)
            if (cyclic && abs(delta) > size / 2) delta -= Integer.signum(delta) * size
            state.animateScrollToItem((at + delta).coerceIn(0, count - 1))
        }
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .width(width)
            .height(ItemHeight * VISIBLE)
            .semantics {
                contentDescription = "$label, ${values[selected.coerceIn(0, size - 1)]}"
                customActions = listOf(
                    CustomAccessibilityAction("Increase") { callback(if (cyclic) (selected + 1) % size else (selected + 1).coerceAtMost(size - 1)); true },
                    CustomAccessibilityAction("Decrease") { callback(if (cyclic) Math.floorMod(selected - 1, size) else (selected - 1).coerceAtLeast(0)); true },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.fillMaxWidth().height(ItemHeight),
            shape = RoundedCornerShape(14.dp),
            color = colors.secondaryContainer,
        ) {}
        Grooves(state, itemPx, Modifier.align(Alignment.CenterStart))
        Grooves(state, itemPx, Modifier.align(Alignment.CenterEnd))
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = ItemHeight * (VISIBLE / 2)),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        ) {
            items(count) { index ->
                val value = values[index % size]
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ItemHeight)
                        .graphicsLayer {
                            val d = index - centerFloat(state, itemPx)
                            val tilt = (d * 24f).coerceIn(-80f, 80f)
                            rotationX = -tilt
                            cameraDistance = 10f * density
                            val fade = (1f - abs(d) * 0.3f).coerceIn(0.12f, 1f)
                            alpha = fade
                            scaleX = 1f - (abs(d) * 0.05f).coerceAtMost(0.2f)
                            scaleY = scaleX
                        }
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    val isCenter = Math.floorMod(index, size) == selected
                    Text(
                        value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCenter) colors.onSecondaryContainer else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Knurling on a wheel edge: ridges evenly spaced around the cylinder, projected onto the screen. */
@Composable
private fun Grooves(state: LazyListState, itemPx: Float, modifier: Modifier) {
    val ridge = MaterialTheme.colorScheme.outlineVariant
    val light = MaterialTheme.colorScheme.surfaceBright
    Canvas(modifier.width(8.dp).fillMaxHeight().padding(vertical = 6.dp)) {
        val radius = size.height / 2f
        val step = (PI / 22).toFloat()
        val travel = (state.firstVisibleItemIndex * itemPx + state.firstVisibleItemScrollOffset) / radius
        val phase = travel - floor(travel / step) * step
        var theta = -PI.toFloat() / 2f + phase
        while (theta < PI / 2) {
            val y = radius + radius * sin(theta)
            val depth = cos(theta)
            drawLine(ridge.copy(alpha = 0.25f + 0.75f * depth), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.6f * depth + 0.4f)
            drawLine(light.copy(alpha = 0.6f * depth), Offset(0f, y + 1.6f), Offset(size.width, y + 1.6f), strokeWidth = 0.8f)
            theta += step
        }
    }
}

private fun centerFloat(state: LazyListState, itemPx: Float): Float =
    state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx

private fun centerIndex(state: LazyListState, itemPx: Float): Int = kotlin.math.round(centerFloat(state, itemPx)).toInt()
