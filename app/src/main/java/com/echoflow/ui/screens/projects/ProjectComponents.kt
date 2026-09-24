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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.components.projectShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import com.echoflow.ui.theme.rememberReducedMotion

// ── Page scaffold ──────────────────────────────────────────────────────────────────────

/**
 * The one frame every Projects page hangs off: a [LargeFlexibleTopAppBar] that collapses into a
 * compact bar as the content scrolls, a tonal back button and an optional FAB — the same chrome
 * Settings and Schedules wear, so the hub reads as part of the app rather than a bolted-on web view.
 * The body receives the scaffold padding and is expected to be a single scrolling container.
 */
@Composable
internal fun ProjectPageScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
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
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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

/**
 * A section heading inside a Projects page: a primary-tinted title with an optional count pill and
 * an optional trailing action, so each group announces itself without a heavy divider.
 */
@Composable
internal fun ProjectSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(Spacing.s))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

// ── Legacy hero header ─────────────────────────────────────────────────────────────────

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

// ── Empty states ─────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProjectsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    HeroEmptyState(
        glyph = Icons.Default.CreateNewFolder,
        eyebrow = "Projects",
        title = "Give your work\na home",
        body = "Group related chats, give them a shared brief, and attach files the model can draw on.",
        actionLabel = "Create a project",
        onAction = onCreate,
        modifier = modifier,
    )
}

@Composable
internal fun FilesEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    HeroEmptyState(
        glyph = Icons.Default.Description,
        eyebrow = "Files",
        title = "Add what the\nmodel should know",
        body = "PDFs, Word and Excel files, slides or notes — EchoFlow reads them on your device and uses them as background knowledge for every chat in this project.",
        actionLabel = "Add files",
        onAction = onAdd,
        modifier = modifier,
    )
}

/**
 * The signature empty state: a slowly morphing [BrandShapes] hero set in a soft halo, a small
 * eyebrow, a display-voice title and a full filled button — the M3 Expressive "hero moment". It is
 * upper-biased so it never floats in a dead-centred void, and it holds still under reduced motion.
 */
@Composable
private fun HeroEmptyState(
    glyph: ImageVector,
    eyebrow: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.fillMaxHeight(0.1f))
        Box(contentAlignment = Alignment.Center) {
            val morph = rememberMorph(BrandShapes.heroStart, BrandShapes.heroEnd)
            val progress = if (rememberReducedMotion()) 0f else {
                val p by rememberMorphProgress(3400)
                p
            }
            // Halo: a larger, quieter echo of the hero so it sits in light rather than on a void.
            Box(
                Modifier.size(176.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            )
            Box(
                Modifier.size(136.dp).clip(MorphPolygonShape(morph, progress))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Icon(glyph, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            eyebrow.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xl),
        )
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.m),
        )
        Button(
            onClick = onAction,
            contentPadding = PaddingValues(horizontal = Spacing.xl),
            modifier = Modifier.padding(top = Spacing.xl).height(56.dp),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(Spacing.s))
            Text(actionLabel, style = MaterialTheme.typography.titleMedium)
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
        icon = { ProjectMedallion(colorIndex, size = 56.dp) },
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
                ProjectMedallion(choice, size = 88.dp)
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
