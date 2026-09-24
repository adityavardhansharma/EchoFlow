@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.theme.Spacing

/**
 * The Projects hub — a fullscreen surface opened from the drawer. It hosts two levels: the list of
 * projects, and one project's home (its chats, instructions and documents). Which is shown is driven
 * by [ChatViewModel.openProjectId], so back steps home → list → closed. This is Option B ("a
 * launchpad, not a container"): a project home surfaces its chats, and points to instructions and
 * documents as focused sub-screens rather than cramming everything onto one page.
 *
 * Every level shares [ProjectPageScaffold] — a collapsing large flexible app bar, a tonal back
 * button and an extended FAB for the page's primary action — the same chrome as Settings and
 * Schedules, with each project's [MaterialShapes] medallion as its face.
 */
@Composable
fun ProjectsHubScreen(chatViewModel: ChatViewModel) {
    val openProjectId by chatViewModel.openProjectId.collectAsState()

    // The hub opens over the chat, whose composer may still hold focus and keep the soft keyboard
    // up. Nothing in the hub wants it, so dismiss it as the surface appears — otherwise it hangs
    // over every screen inside the hub until the user taps elsewhere.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
        appeared = true
    }
    val openProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "projects-open",
    )

    Surface(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - openProgress) * size.height
                alpha = openProgress
            },
        color = MaterialTheme.colorScheme.surface,
    ) {
        val projectId = openProjectId
        if (projectId == null) {
            BackHandler { chatViewModel.closeProjectsHub() }
            ProjectsListContent(chatViewModel)
        } else {
            ProjectHomeScreen(chatViewModel, projectId, onBack = { chatViewModel.closeProjectHome() })
        }
    }
}

// ── Projects list ──────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectsListContent(chatViewModel: ChatViewModel) {
    val projects by chatViewModel.projects.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val fabExpanded by remember { derivedStateOf { gridState.firstVisibleItemIndex == 0 } }

    if (showCreate) {
        // New projects rotate through the identities so a fresh board isn't five identical marks;
        // the dialog previews exactly the mark the project will be created with.
        val nextColor = projects.size % PROJECT_ACCENT_COUNT
        ProjectNameDialog(
            title = "New project",
            initial = "",
            confirmLabel = "Create",
            colorIndex = nextColor,
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                chatViewModel.createProject(name, nextColor) { id -> chatViewModel.openProjectHome(id) }
            },
        )
    }

    ProjectPageScaffold(
        title = "Projects",
        subtitle = when (projects.size) {
            0 -> "Chats, a shared brief and files — together"
            1 -> "1 project"
            else -> "${projects.size} projects"
        },
        onBack = { chatViewModel.closeProjectsHub() },
        floatingActionButton = {
            if (projects.isNotEmpty()) {
                MediumExtendedFloatingActionButton(
                    text = { Text("New project") },
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = { showCreate = true },
                    expanded = fabExpanded,
                )
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            ProjectsEmptyState(
                onCreate = { showCreate = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@ProjectPageScaffold
        }
        // Tiles on phones, more columns as the window widens — a board of projects, not a ledger.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 128.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item(key = "section", span = { GridItemSpan(maxLineSpan) }) {
                ProjectSectionHeader("Your projects")
            }
            items(projects, key = { it.id }) { project ->
                ProjectTile(
                    project = project,
                    chatCountFlow = { chatViewModel.projectChatCountFlow(project.id) },
                    docCountFlow = { chatViewModel.projectDocumentCountFlow(project.id) },
                    onClick = { chatViewModel.openProjectHome(project.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * One project on the board: its medallion leads, a brief-set badge rides the top edge, the name
 * gets real type, and the contents read as small icon counts rather than a sentence of prose.
 */
@Composable
private fun ProjectTile(
    project: Project,
    chatCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    docCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatCount by remember(project.id) { chatCountFlow() }.collectAsState(0)
    val docCount by remember(project.id) { docCountFlow() }.collectAsState(0)
    val hasBrief = project.instructions.isNotBlank()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.heightIn(min = 188.dp).padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.Top) {
                ProjectMedallion(project.colorIndex, size = 56.dp)
                Spacer(Modifier.weight(1f))
                if (hasBrief) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome, "Instructions set",
                            Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f).heightIn(min = Spacing.l))
            Text(
                project.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chatCount == 0 && docCount == 0) {
                    Text(
                        "Edited ${DateUtils.getRelativeTimeSpanString(project.updatedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    TileStat(Icons.Outlined.ChatBubbleOutline, chatCount)
                    TileStat(Icons.Outlined.Description, docCount)
                }
            }
        }
    }
}

@Composable
private fun TileStat(icon: ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Spacing.xs))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A glanceable summary of what's inside a project — counts when it has substance, a nudge when not. */
internal fun projectMetaLine(chatCount: Int, docCount: Int, instructions: String): String {
    val parts = buildList {
        if (chatCount > 0) add(if (chatCount == 1) "1 chat" else "$chatCount chats")
        if (docCount > 0) add(if (docCount == 1) "1 file" else "$docCount files")
    }
    return when {
        parts.isNotEmpty() -> parts.joinToString(" · ")
        instructions.isNotBlank() -> "Instructions set · tap to open"
        else -> "Empty project · tap to set up"
    }
}
