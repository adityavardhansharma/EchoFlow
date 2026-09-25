
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.ui.components.ModeSwitch
import com.echoflow.ui.theme.Spacing

/**
 * The floating top bar: place on the left, **identity** in the middle, action on the right.
 *
 * The centre slot belongs to the most permanent thing on screen, which is which surface you
 * are on — so the mode switch lives here and the model selector moved down to the composer,
 * where it sits with the other controls you change mid-thought.
 */
@Composable
internal fun ChatTopBar(
    mode: AppMode,
    onSelectMode: (AppMode) -> Unit,
    renderingModes: Set<AppMode>,
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val newLabel = if (mode == AppMode.Imagine) "New creation" else "New conversation"
    CenterAlignedTopAppBar(
        modifier = modifier,
        navigationIcon = {
            // Nudge inward from the screen edge — flush against the bezel reads cramped.
            Box(Modifier.padding(start = Spacing.s)) {
                RoundTopBarButton(onClick = onMenu, container = MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Menu, "Open conversations", Modifier.size(22.dp)) }
            }
        },
        title = {
            ModeSwitch(
                selected = mode,
                onSelect = onSelectMode,
                renderingModes = renderingModes,
                modifier = Modifier.widthIn(max = 220.dp),
            )
        },
        actions = {
            Box(Modifier.padding(end = Spacing.s)) {
                RoundTopBarButton(onClick = onNewChat, container = MaterialTheme.colorScheme.tertiaryContainer) { Icon(Icons.Default.Create, newLabel, Modifier.size(22.dp)) }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
}

/**
 * A round top-bar button: a solid colour disc the same height as the [ModeSwitch] tray beside
 * it, given a little body without gloss: a soft cast shadow (which carries it in light themes)
 * plus a faint top-to-bottom shade (which carries it in dark ones, where shadows vanish).
 * Pressing sinks it — it shrinks slightly and the shadow settles in.
 */
@Composable
private fun RoundTopBarButton(
    onClick: () -> Unit,
    container: Color,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "topbar-button-press",
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = contentColorFor(container),
        shadowElevation = if (pressed) 1.dp else 4.dp,
        interactionSource = interaction,
        modifier = Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        val shade = Brush.verticalGradient(listOf(lerp(container, Color.White, 0.08f), lerp(container, Color.Black, 0.08f)))
        Box(Modifier.background(shade), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
internal fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base, vertical = Spacing.s),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(Spacing.m))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
internal fun AssistPill(icon: ImageVector, label: String, container: Color, onContainer: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = container) {
        Row(
            Modifier.padding(start = 14.dp, end = 18.dp, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = onContainer)
            Spacer(Modifier.width(Spacing.s))
            Text(label, style = MaterialTheme.typography.labelLarge, color = onContainer)
        }
    }
}
