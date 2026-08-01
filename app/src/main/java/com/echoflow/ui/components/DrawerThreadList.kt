@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
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

private const val PINNED_SECTION = "Pinned"
private val RowShape = RoundedCornerShape(16.dp)
private val RowHeight = 50.dp

/**
 * One mode's conversations, grouped by when they were last touched.
 *
 * The list is deliberately airy: rows are transparent and sit straight on the drawer surface, so
 * the eye reads titles first and colour only ever means "this is the conversation you're in". The
 * grouping gives the run of titles somewhere to breathe.
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
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        sections.forEach { (label, sectionThreads) ->
            if (label != null) {
                item(key = "section-$label") {
                    DrawerSectionHeader(label, pinned = label == PINNED_SECTION)
                }
            }
            itemsIndexed(sectionThreads, key = { _, t -> t.id }) { _, thread ->
                ThreadRow(
                    thread = thread,
                    selected = thread.id == currentThreadId,
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
}

/** Quiet, uppercase group header. "Pinned" gets a small accent bloom so the eye finds it first. */
@Composable
private fun DrawerSectionHeader(label: String, pinned: Boolean) {
    Row(
        Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.base, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pinned) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedPolygonShape(MaterialShapes.Sunny))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(Spacing.s))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThread,
    selected: Boolean,
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

    // The active pill fills and fades in with a soft spring; content colour crossfades so a title
    // never "flickers" between roles as the selection lands.
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "row-container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = 240),
        label = "row-content",
    )
    val mutedColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Press gives: the whole row dips and springs back under the finger.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
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
            // The signature: a brand bloom that springs in only on the active row and gently
            // breathes. Reserving no space keeps every other row clean text — the medallion is a
            // reward for being "here", not a stamp on every line.
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn(tween(220)),
                exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActiveBloom(reducedMotion)
                    Spacer(Modifier.width(Spacing.m))
                }
            }
            Text(
                thread.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (thread.isPinned) {
                Spacer(Modifier.width(Spacing.s))
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(15.dp),
                    tint = mutedColor,
                )
            }
            if (rendering) {
                Spacer(Modifier.width(Spacing.s))
                RenderingPulse(
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    reducedMotion = reducedMotion,
                )
            }
        }

        if (menuOpen) {
            ThreadActionMenu(
                title = thread.title,
                isPinned = thread.isPinned,
                onDismiss = { menuOpen = false },
                onPin = onPin,
                onUnpin = onUnpin,
                onRename = onRename,
                onDelete = onDelete,
            )
        }
    }
}

/** The breathing brand bloom on the active row — a filled Sunny polygon in the accent colour. */
@Composable
private fun ActiveBloom(reducedMotion: Boolean) {
    val scale = if (reducedMotion) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "bloom")
        t.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.09f,
            animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
            label = "bloom-scale",
        ).value
    }
    Box(
        Modifier
            .size(20.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedPolygonShape(MaterialShapes.Sunny))
            .background(MaterialTheme.colorScheme.primary),
    )
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
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    // Driven separately from menuOpen so the exit animation can play before we unmount: enter once
    // on first composition, and only tear down after the collapse has fully settled.
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }
    val flippedUp = remember { mutableStateOf(false) }
    val positionProvider = remember { ThreadMenuPositionProvider(gapPx = 8, onFlip = { flippedUp.value = it }) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = { visible.targetState = false },
        properties = PopupProperties(focusable = true),
    ) {
        val transition = updateTransition(visible, label = "menu")
        val scale by transition.animateFloat(
            transitionSpec = {
                if (false isTransitioningTo true) {
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
                } else {
                    tween(120, easing = FastOutLinearInEasing)
                }
            },
            label = "menu-scale",
        ) { if (it) 1f else 0.8f }
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
                fun act(action: () -> Unit) {
                    action()
                    visible.targetState = false
                }
                MenuItem(
                    transition = transition,
                    index = 0,
                    icon = if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                    label = if (isPinned) "Unpin" else "Pin",
                    chipColor = MaterialTheme.colorScheme.secondaryContainer,
                    chipContent = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { act { if (isPinned) onUnpin() else onPin() } },
                )
                MenuItem(
                    transition = transition,
                    index = 1,
                    icon = Icons.Filled.Edit,
                    label = "Rename",
                    chipColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    chipContent = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { act { onRename() } },
                )
                MenuItem(
                    transition = transition,
                    index = 2,
                    icon = Icons.Filled.DeleteOutline,
                    label = "Delete",
                    chipColor = MaterialTheme.colorScheme.errorContainer,
                    chipContent = MaterialTheme.colorScheme.onErrorContainer,
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = { act { onDelete() } },
                )
            }
        }
    }
}

@Composable
private fun MenuItem(
    transition: androidx.compose.animation.core.Transition<Boolean>,
    index: Int,
    icon: ImageVector,
    label: String,
    chipColor: Color,
    chipContent: Color,
    onClick: () -> Unit,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Each item slides+fades in on its own beat so the menu assembles top-to-bottom.
    val itemAlpha by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(220, delayMillis = 30 + index * 38, easing = LinearOutSlowInEasing)
            else tween(90, easing = FastOutLinearInEasing)
        },
        label = "item-alpha",
    ) { if (it) 1f else 0f }
    val itemShift by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) tween(260, delayMillis = 30 + index * 38, easing = EaseOutBack)
            else tween(90)
        },
        label = "item-shift",
    ) { if (it) 0f else -8f }

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
