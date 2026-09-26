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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.delay

internal val LocalProjectsNow = staticCompositionLocalOf { System.currentTimeMillis() }

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
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            val current = System.currentTimeMillis()
            value = current
            delay(DateUtils.MINUTE_IN_MILLIS - current % DateUtils.MINUTE_IN_MILLIS)
        }
    }

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

    CompositionLocalProvider(LocalProjectsNow provides now) {
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
            0 -> "Chats, instructions and files in one place"
            1 -> "1 project"
            else -> "${projects.size} projects"
        },
        onBack = { chatViewModel.closeProjectsHub() },
        floatingActionButton = {
            if (projects.isNotEmpty()) {
                ProjectFab("New project", Icons.Default.Add, fabExpanded) { showCreate = true }
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
        // A connected list: the name is what you scan for, so each project is one row with its
        // mark, its name and what's inside, in the same rhythm as the Schedules list.
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

/** One project in the list: its medallion leads, the name carries the row, one line says what's inside. */
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
    val now = LocalProjectsNow.current

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectMedallion(project.colorIndex, size = 40.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    projectRowMeta(chatCount, docCount, project.updatedAt, now),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.padding(start = Spacing.s),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
