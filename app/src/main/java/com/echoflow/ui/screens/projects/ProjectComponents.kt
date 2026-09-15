@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.components.projectShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import com.echoflow.ui.theme.rememberReducedMotion

// ── Hero header ────────────────────────────────────────────────────────────────────────

/**
 * The shared surface header: a rounded-bottom slab that carries the down-chevron, an optional
 * trailing action, and a large title block. The rounding and containment are what turn a flat
 * toolbar into a native header the page hangs off of.
 */
@Composable
internal fun ProjectHeader(
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    titleContent: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = Spacing.xs, end = Spacing.s, top = Spacing.xs, bottom = Spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Back", Modifier.size(26.dp)) }
                Spacer(Modifier.weight(1f))
                if (trailing != null) trailing()
            }
            Column(
                Modifier.padding(start = Spacing.base, end = Spacing.base, top = Spacing.xs),
                content = titleContent,
            )
        }
    }
}

// ── Empty states & dialogs ───────────────────────────────────────────────────────────────

@Composable
internal fun ProjectsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    HeroEmptyState(
        glyph = Icons.Default.CreateNewFolder,
        title = "Start your first\nproject",
        body = "Group related chats, give them a shared instruction, and attach reference files the model can draw on.",
        actionLabel = "New project",
        onAction = onCreate,
        modifier = modifier,
    )
}

@Composable
internal fun FilesEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    HeroEmptyState(
        glyph = Icons.Default.Description,
        title = "No files yet",
        body = "Add PDFs, Word or Excel files, slides or notes — EchoFlow reads them on your device and makes them background knowledge for this project.",
        actionLabel = "Add files",
        onAction = onAdd,
        modifier = modifier,
    )
}

/**
 * The signature empty state: a haloed, morphing [MaterialShapes] hero — the same focal move the chat
 * home makes — over a display-voice title and a real action. Upper-biased so it never floats in a
 * dead-centred void.
 */
@Composable
private fun HeroEmptyState(
    glyph: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.fillMaxHeight(0.16f))
        Box(contentAlignment = Alignment.Center) {
            val morph = rememberMorph(BrandShapes.heroStart, BrandShapes.heroEnd)
            // Honour the reduced-motion policy: hold the resting frame (never start the infinite
            // transition) when the user has turned system animations off.
            val progress = if (rememberReducedMotion()) 0f else {
                val p by rememberMorphProgress(3400)
                p
            }
            Box(
                Modifier.size(132.dp).clip(MorphPolygonShape(morph, progress))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Icon(glyph, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xl),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = Spacing.xl)) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(actionLabel)
        }
    }
}

@Composable
internal fun ProjectNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Project name") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim().ifBlank { "Untitled project" }) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun ProjectColorDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour & shape") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                (0 until PROJECT_ACCENT_COUNT).forEach { index ->
                    val accent = projectAccent(index)
                    val shape = remember(index) { RoundedPolygonShape(projectShape(index)) }
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(shape)
                            .background(accent.container)
                            .then(
                                if (index == selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape)
                                } else Modifier
                            )
                            .clickable { onPick(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == selected) {
                            Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = accent.onContainer)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
