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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

/**
 * The Projects hub — a fullscreen surface opened from the drawer. It hosts two levels: the list of
 * projects, and one project's home (its chats, instructions and documents). Which is shown is driven
 * by [ChatViewModel.openProjectId], so back steps home → list → closed. This is Option B ("a
 * launchpad, not a container"): a project home surfaces its chats, and points to instructions and
 * documents as focused sub-screens rather than cramming everything onto one page.
 *
 * Every level shares [ProjectPageScaffold] — a collapsing medium flexible app bar, a tonal back
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
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

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
                ExtendedFloatingActionButton(
                    text = { Text("New project") },
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = { showCreate = true },
                    expanded = fabExpanded,
                    shape = RoundedCornerShape(20.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
        // A connected list, not a board: the name is what you scan for, so each project is one
        // quiet row with its mark, its name and what's inside — the same rhythm as Settings.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 112.dp),
        ) {
            itemsIndexed(projects, key = { _, it -> it.id }) { index, project ->
                ProjectRow(
                    project = project,
                    chatCountFlow = { chatViewModel.projectChatCountFlow(project.id) },
                    docCountFlow = { chatViewModel.projectDocumentCountFlow(project.id) },
                    shape = groupedItemShape(index, projects.size),
                    onClick = { chatViewModel.openProjectHome(project.id) },
                    modifier = Modifier.padding(bottom = GroupedItemGap).animateItem(),
                )
            }
        }
    }
}

/**
 * One project in the list: its medallion leads, the name carries the row, and a single quiet line
 * says what's inside. A set brief shows as a small sparkle beside the counts rather than a badge.
 */
@Composable
private fun ProjectRow(
    project: Project,
    chatCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    docCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatCount by remember(project.id) { chatCountFlow() }.collectAsState(0)
    val docCount by remember(project.id) { docCountFlow() }.collectAsState(0)

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectMedallion(project.colorIndex, size = 36.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (project.instructions.isNotBlank()) {
                        Icon(
                            Icons.Default.AutoAwesome, "Instructions set",
                            Modifier.padding(end = Spacing.xs).size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        projectRowMeta(chatCount, docCount, project.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Counts when the project has substance, otherwise when it was last touched. */
internal fun projectRowMeta(chatCount: Int, docCount: Int, updatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val parts = buildList {
        if (chatCount > 0) add(if (chatCount == 1) "1 chat" else "$chatCount chats")
        if (docCount > 0) add(if (docCount == 1) "1 file" else "$docCount files")
    }
    return parts.joinToString(" · ").ifEmpty { "Edited ${relativeTime(updatedAt, now)}" }
}

/** "just now" for the first minute rather than DateUtils' literal "0 minutes ago". */
internal fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String =
    if (now - time in 0 until DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
