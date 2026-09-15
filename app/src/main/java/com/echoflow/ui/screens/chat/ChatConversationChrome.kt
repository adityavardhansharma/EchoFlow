
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.ModeSwitch
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress

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
                ShapedIconButton(
                    onClick = onMenu, enabled = true, size = 44.dp,
                    restShape = MaterialShapes.Cookie4Sided, pressedShape = MaterialShapes.Cookie7Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                ) { Icon(Icons.Default.Menu, "Open conversations", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
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
            ShapedIconButton(
                onClick = onNewChat, enabled = true, size = 44.dp,
                restShape = MaterialShapes.Cookie7Sided, pressedShape = MaterialShapes.Sunny,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                pulseOnClick = true,
            ) { Icon(Icons.Default.Create, newLabel, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
    )
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
internal fun EmptyState(onSuggestion: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Haloed, morphing hero — a strong colored focal point.
        Box(contentAlignment = Alignment.Center) {
            val morph = rememberMorph(BrandShapes.heroStart, BrandShapes.heroEnd)
            val progress by rememberMorphProgress(3400)
            Box(
                Modifier.size(150.dp).clip(MorphPolygonShape(morph, progress)).background(MaterialTheme.colorScheme.primaryContainer),
            )
            BrandMark(size = 84.dp, animated = true, iconScale = 0.42f)
        }
        Spacer(Modifier.height(Spacing.xl))
        Text(
            "How can I help\nyou today?",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))

        data class Sug(val icon: ImageVector, val label: String, val prompt: String, val container: Color, val onContainer: Color)
        val cs = MaterialTheme.colorScheme
        val suggestions = listOf(
            Sug(Icons.Default.Lightbulb, "Explain", "Explain quantum computing in simple terms", cs.primaryContainer, cs.onPrimaryContainer),
            Sug(Icons.Default.Edit, "Write", "Write an email asking for a deadline extension", cs.secondaryContainer, cs.onSecondaryContainer),
            Sug(Icons.Default.Map, "Plan", "Plan a 3-day itinerary for Tokyo", cs.tertiaryContainer, cs.onTertiaryContainer),
            Sug(Icons.Default.Code, "Code", "Write a Python script to rename files in a folder", cs.surfaceContainerHigh, cs.onSurface),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            suggestions.forEach { s ->
                AssistPill(s.icon, s.label, s.container, s.onContainer) { onSuggestion(s.prompt) }
            }
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
