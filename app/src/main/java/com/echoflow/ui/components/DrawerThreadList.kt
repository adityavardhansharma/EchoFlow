@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.echoflow.data.ChatThread
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import java.util.Calendar
import java.util.Locale

private const val PINNED_SECTION = "Pinned"
private val RowShape = RoundedCornerShape(14.dp)
private val RowHeight = 48.dp

/**
 * One mode's conversations, grouped by when they were last touched.
 *
 * The list is a dense, quiet run of single-line titles — no metadata, no chrome per row; the
 * grouping headers already say "when" so the rows don't have to. Selection is deliberately calm:
 * the active row lifts on a neutral pill (elevation, not paint), its title firms up half a weight,
 * and a small brand dot sits at the trailing edge. Nothing is inserted before the text, so the
 * column of titles holds one straight rail whether a row is selected or not. Soft top and bottom
 * fades let the list dissolve into the drawer instead of hard-cutting at the edges.
 */
@Composable
fun DrawerThreadList(
    threads: List<ChatThread>,
    currentThreadId: String?,
    renderingChatIds: Set<String>,
    onThreadSelected: (ChatThread) -> Unit,
    onPin: (ChatThread) -> Unit,
    onUnpin: (ChatThread) -> Unit,
    onRename: (ChatThread) -> Unit,
    onDelete: (ChatThread) -> Unit,
    grouped: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val sections = remember(threads, grouped) {
        val pinned = threads.filter { it.isPinned }
        val unpinned = threads.filter { !it.isPinned }
        val unpinnedSections = if (grouped) ThreadRecency.group(unpinned) else listOf(null to unpinned)
        if (pinned.isEmpty()) unpinnedSections
        else listOf(PINNED_SECTION to pinned) + unpinnedSections
    }
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Box(modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Spacing.xs),
        ) {
            sections.forEach { (label, sectionThreads) ->
                if (label != null) {
                    item(key = "section-$label") {
                        DrawerSectionHeader(label, pinned = label == PINNED_SECTION)
                    }
                }
                itemsIndexed(sectionThreads, key = { _, t -> t.id }) { _, thread ->
                    ThreadRow(
                        thread = thread,
                        isRowSelected = thread.id == currentThreadId,
                        rendering = thread.id in renderingChatIds,
                        reducedMotion = reducedMotion,
                        onClick = { onThreadSelected(thread) },
                        onPin = { onPin(thread) },
                        onUnpin = { onUnpin(thread) },
                        onRename = { onRename(thread) },
                        onDelete = { onDelete(thread) },
                    )
                }
            }
        }
        // Fade the run of titles into the drawer surface at both ends rather than clipping hard.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(Spacing.base)
                .background(Brush.verticalGradient(listOf(surface, Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(Spacing.l)
                .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
        )
    }
}

/** Quiet uppercase group header — chrome, not content. "Pinned" gets a small accent bloom. */
@Composable
private fun DrawerSectionHeader(label: String, pinned: Boolean) {
    Row(
        Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(RoundedPolygonShape(MaterialShapes.Sunny))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(Spacing.s))
        }
        Text(
            label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThread,
    isRowSelected: Boolean,
    rendering: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Selection is elevation, not paint: the active row lifts on a neutral container pill and its
    // title firms to Medium. Colour stays out of the row except one small trailing brand dot —
    // enough to say "you are here" without shouting over the run of titles. Nothing is ever
    // inserted before the text, so every title hangs off the same left rail. Under reduced motion
    // the colours snap.
    val container by animateColorAsState(
        targetValue = if (isRowSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "row-container",
    )
    val dot by animateFloatAsState(
        targetValue = if (isRowSelected) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "row-dot",
    )
    // Press gives: the whole row dips and springs back under the finger — unless motion is reduced.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "row-press",
    )

    val description = buildString {
        append(thread.title)
        if (thread.isPinned) append(", pinned")
        if (rendering) append(", video rendering")
    }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(RowShape)
                .background(container)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                )
                .heightIn(min = RowHeight)
                .padding(horizontal = Spacing.m)
                .semantics {
                    selected = isRowSelected
                    contentDescription = description
                    customActions = buildList {
                        add(
                            CustomAccessibilityAction(if (thread.isPinned) "Unpin" else "Pin") {
                                if (thread.isPinned) onUnpin() else onPin(); true
                            },
                        )
                        add(CustomAccessibilityAction("Rename") { onRename(); true })
                        add(CustomAccessibilityAction("Delete") { onDelete(); true })
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                thread.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRowSelected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (thread.isPinned) {
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (rendering) {
                Spacer(Modifier.width(Spacing.s))
                RenderingPulse(
                    color = MaterialTheme.colorScheme.primary,
                    reducedMotion = reducedMotion,
                )
            }
            if (dot > 0f) {
                Spacer(Modifier.width(Spacing.s))
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot }
                        .clip(RoundedPolygonShape(MaterialShapes.Sunny))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        if (menuOpen) {
            ThreadActionMenu(
                title = thread.title,
                isPinned = thread.isPinned,
                reducedMotion = reducedMotion,
                onDismiss = { menuOpen = false },
                onPin = onPin,
                onUnpin = onUnpin,
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}

/**
 * The long-press menu — a custom, precisely-tuned dropdown (not the stock one).
 *
 * It unfolds from the corner nearest the row's trailing edge, overshooting a touch on the way in
 * and collapsing faster on the way out — the way a real object would. Items stagger in so the menu
 * assembles rather than snaps, and the whole thing lives on until its exit animation finishes.
 */
@Composable
private fun ThreadActionMenu(
    title: String,
    isPinned: Boolean,
    reducedMotion: Boolean,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // Driven separately from menuOpen so the exit animation can play before we unmount: enter once
    // on first composition, and only tear down after the collapse has fully settled.
    val visible = remember { MutableTransitionState(false) }
    // Rename/Delete each raise a dialog, so the chosen action is deferred until the popup has
    // fully collapsed and unmounted — otherwise a focusable dialog would land on top of a still
    // focusable, still-animating popup and the two would fight for input.
    val pendingAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) { visible.targetState = true }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) {
            val action = pendingAction.value
            onDismiss()
            action?.invoke()
        }
    }
    val flippedUp = remember { mutableStateOf(false) }
    val positionProvider = remember { ThreadMenuPositionProvider(gapPx = 8, onFlip = { flippedUp.value = it }) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { visible.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        val transition = updateTransition(visible, label = "menu")
        // A fade is retained under reduced motion; the grow/overshoot is not.
        val scaleRaw by transition.animateFloat(
            transitionSpec = {
                if (false isTransitioningTo true) {
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
                } else {
                    tween(120, easing = FastOutLinearInEasing)
                }
            },
            label = "menu-scale",
        ) { if (it) 1f else 0.8f }
        val scale = if (reducedMotion) 1f else scaleRaw
        val alpha by transition.animateFloat(
            transitionSpec = {
                if (false isTransitioningTo true) tween(130) else tween(100, easing = FastOutLinearInEasing)
            },
            label = "menu-alpha",
        ) { if (it) 1f else 0f }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .width(216.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(1f, if (flippedUp.value) 1f else 0f)
                },
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                // Start collapsing the popup. Pin/Unpin change data with no follow-up surface, so
                // they fire right away; Rename and Delete each raise a dialog, so those are deferred
                // until the popup has fully unmounted (see [pendingAction]) to avoid two focusable
                // surfaces overlapping.
                fun act(deferred: Boolean, action: () -> Unit) {
                    if (deferred) pendingAction.value = action else action()
                    visible.targetState = false
                }
                MenuItem(
                    transition = transition,
                    index = 0,
                    reducedMotion = reducedMotion,
                    icon = if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                    label = if (isPinned) "Unpin" else "Pin",
                    chipColor = MaterialTheme.colorScheme.secondaryContainer,
                    chipContent = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { act(deferred = false) { if (isPinned) onUnpin() else onPin() } },
                )
                MenuItem(
                    transition = transition,
                    index = 1,
                    reducedMotion = reducedMotion,
                    icon = Icons.Filled.Edit,
                    label = "Rename",
                    chipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    chipContent = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { act(deferred = true) { onRename() } },
                )
                MenuItem(
                    transition = transition,
                    index = 2,
                    reducedMotion = reducedMotion,
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete",
                    chipColor = MaterialTheme.colorScheme.errorContainer,
                    chipContent = MaterialTheme.colorScheme.onErrorContainer,
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { act(deferred = true) { onDelete() } },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    transition: androidx.compose.animation.core.Transition<Boolean>,
    index: Int,
    reducedMotion: Boolean,
    icon: ImageVector,
    label: String,
    chipColor: Color,
    chipContent: Color,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Each item slides+fades in on its own beat so the menu assembles top-to-bottom. Under reduced
    // motion the per-item delay and the horizontal travel are dropped — only a plain fade remains.
    val stagger = if (reducedMotion) 0 else 30 + index * 38
    val itemAlpha by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(220, delayMillis = stagger, easing = LinearOutSlowInEasing)
            else tween(90, easing = FastOutLinearInEasing)
        },
        label = "item-alpha",
    ) { if (it) 1f else 0f }
    val itemShiftRaw by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(260, delayMillis = stagger, easing = EaseOutBack)
            else tween(90)
        },
        label = "item-shift",
    ) { if (it) 0f else -8f }
    val itemShift = if (reducedMotion) 0f else itemShiftRaw

    val itemInteraction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = itemAlpha; translationX = itemShift }
            .clip(RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = itemInteraction,
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 8.dp, bottomStart = 8.dp, bottomEnd = 12.dp))
                .background(chipColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, Modifier.size(17.dp), tint = chipContent)
        }
        Spacer(Modifier.width(13.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = labelColor)
    }
}

/**
 * Anchors the menu just below the row, right edges aligned, and flips it above when there isn't
 * room underneath. Reports the flip so the content can pivot its grow-from origin accordingly.
 */
private class ThreadMenuPositionProvider(
    private val gapPx: Int,
    private val onFlip: (Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(gapPx, (windowSize.width - popupContentSize.width - gapPx).coerceAtLeast(gapPx))
        val below = anchorBounds.bottom + gapPx
        val flip = below + popupContentSize.height > windowSize.height - gapPx
        onFlip(flip)
        val y = if (flip) {
            (anchorBounds.top - popupContentSize.height - gapPx)
                .coerceIn(gapPx, (windowSize.height - popupContentSize.height - gapPx).coerceAtLeast(gapPx))
        } else {
            below
        }
        return IntOffset(x, y)
    }
}

/**
 * Buckets conversations by last activity. Pure and calendar-based rather than arithmetic on
 * millisecond spans: "yesterday" means the previous calendar day, not 24-to-48 hours ago, and
 * a conversation touched at 00:30 should not be filed under yesterday at 00:45.
 */
internal object ThreadRecency {
    const val TODAY = "Today"
    const val YESTERDAY = "Yesterday"
    const val THIS_WEEK = "Previous 7 days"
    const val EARLIER = "Older"

    fun group(
        threads: List<ChatThread>,
        now: Long = System.currentTimeMillis(),
    ): List<Pair<String, List<ChatThread>>> {
        val order = listOf(TODAY, YESTERDAY, THIS_WEEK, EARLIER)
        return threads.groupBy { bucketOf(it.updatedAt, now) }
            .toList()
            .sortedBy { (label, _) -> order.indexOf(label) }
    }

    fun bucketOf(timestamp: Long, now: Long): String {
        val startOfToday = startOfDay(now)
        return when {
            timestamp >= startOfToday -> TODAY
            timestamp >= startOfToday - DAY_MS -> YESTERDAY
            timestamp >= startOfToday - 6 * DAY_MS -> THIS_WEEK
            else -> EARLIER
        }
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
