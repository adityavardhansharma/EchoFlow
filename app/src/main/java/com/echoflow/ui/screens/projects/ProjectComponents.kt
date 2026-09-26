@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.components.projectShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion

// ── Page scaffold ──────────────────────────────────────────────────────────────────────

/**
 * The one frame every Projects page hangs off: the same collapsing [LargeFlexibleTopAppBar], tonal
 * back button and optional FAB as Settings and Schedules, so moving between them feels like one app.
 * [titleLeading] sets a mark (a project's medallion) beside the title. The body receives the
 * scaffold padding and is expected to be a single scrolling container.
 */
@Composable
internal fun ProjectPageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    titleLeading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    if (titleLeading == null) {
                        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            titleLeading()
                            Spacer(Modifier.width(Spacing.m))
                            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                },
                subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** The page's primary action, the same extended FAB Schedules uses, collapsing once you scroll. */
@Composable
internal fun ProjectFab(label: String, icon: ImageVector, expanded: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        text = { Text(label) },
        icon = { Icon(icon, null) },
        onClick = onClick,
        expanded = expanded,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/** A section heading inside a Projects page, styled like the section labels on Schedules. */
@Composable
internal fun ProjectSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.s),
    )
}

/** One line of supporting text under a page's bar, saying what the page is for. */
@Composable
internal fun ProjectPageNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
    )
}

// ── Empty states ─────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProjectsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    PlainEmptyState(
        glyph = Icons.Default.CreateNewFolder,
        title = "No projects yet",
        body = "Group related chats, give them shared instructions, and attach files the model can draw on.",
        actionLabel = "New project",
        onAction = onCreate,
        modifier = modifier,
    )
}

@Composable
internal fun FilesEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    PlainEmptyState(
        glyph = Icons.Default.Description,
        title = "No files yet",
        body = "Add PDFs, Word and Excel files, slides or notes. EchoFlow reads them on your device and every chat in this project can use them.",
        actionLabel = "Add files",
        onAction = onAdd,
        modifier = modifier,
    )
}

/**
 * An empty page: a still icon in a tonal badge, a headline, one line of explanation and a button,
 * laid out the same way as the first-run state on Schedules.
 */
@Composable
private fun PlainEmptyState(
    glyph: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        Surface(shape = RoundedCornerShape(24.dp), color = cs.secondaryContainer) {
            Icon(glyph, null, Modifier.padding(Spacing.l).size(32.dp), tint = cs.onSecondaryContainer)
        }
        Spacer(Modifier.height(Spacing.l))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.m),
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(Spacing.s))
            Text(actionLabel)
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────────────────

/**
 * Create / rename. The project's own medallion leads the dialog so naming a project feels like
 * naming *that* thing, and the field takes focus with the keyboard's Done key wired to confirm.
 */
@Composable
internal fun ProjectNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    colorIndex: Int = 0,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val confirm = { onConfirm(name.trim().ifBlank { "Untitled project" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ProjectMedallion(colorIndex, size = 40.dp) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Project name") },
                placeholder = { Text("e.g. Thesis research") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { Button(onClick = confirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The identity picker: a large live preview of the chosen mark over a row of swatches. Each swatch
 * is its own shape; the selected one grows and gains a ring, so the choice is felt, not just ticked.
 */
@Composable
internal fun ProjectColorDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var choice by remember { mutableStateOf(selected) }
    val reducedMotion = rememberReducedMotion()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour & shape") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ProjectMedallion(choice, size = 64.dp)
                Text(
                    "Every project wears one mark — in the list, its home and the drawer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.base, bottom = Spacing.l),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    (0 until PROJECT_ACCENT_COUNT).forEach { index ->
                        val accent = projectAccent(index)
                        val shape = remember(index) { RoundedPolygonShape(projectShape(index)) }
                        val isSelected = index == choice
                        val swatch by animateDpAsState(
                            targetValue = if (isSelected) 48.dp else 40.dp,
                            animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.55f),
                            label = "swatch",
                        )
                        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                            Box(
                                Modifier
                                    .size(swatch)
                                    .clip(shape)
                                    .background(accent.container)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, accent.onContainer, shape)
                                        } else Modifier
                                    )
                                    .clickable { choice = index },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, "Selected", Modifier.size(20.dp), tint = accent.onContainer)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onPick(choice) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
