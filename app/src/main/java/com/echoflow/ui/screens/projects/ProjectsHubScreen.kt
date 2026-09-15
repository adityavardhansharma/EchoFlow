@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.theme.Spacing

/**
 * The Projects hub — a fullscreen surface opened from the drawer. It hosts two levels: the list of
 * projects, and one project's home (its chats, instructions and documents). Which is shown is driven
 * by [ChatViewModel.openProjectId], so back steps home → list → closed. This is Option B ("a
 * launchpad, not a container"): a project home surfaces its chats, and points to instructions and
 * documents as focused sub-screens rather than cramming everything onto one page.
 *
 * The surface speaks the app's Expressive language — a rounded hero header, [MaterialShapes]
 * medallions for each project's identity, morphing empty-state heroes and grouped connected
 * containers — rather than the flat "bar over a page of buttons" that made it read like a web view.
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

    if (showCreate) {
        ProjectNameDialog(
            title = "New project",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                chatViewModel.createProject(name) { id -> chatViewModel.openProjectHome(id) }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (projects.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                ProjectHeader(onBack = { chatViewModel.closeProjectsHub() }) {
                    Text("Projects", style = MaterialTheme.typography.displaySmall)
                }
                ProjectsEmptyState(onCreate = { showCreate = true }, modifier = Modifier.fillMaxSize())
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item {
                    ProjectHeader(onBack = { chatViewModel.closeProjectsHub() }) {
                        Text("Projects", style = MaterialTheme.typography.displaySmall)
                        Text(
                            if (projects.size == 1) "1 project" else "${projects.size} projects",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                items(projects, key = { it.id }) { project ->
                    ProjectListCard(
                        project = project,
                        chatCountFlow = { chatViewModel.projectChatCountFlow(project.id) },
                        docCountFlow = { chatViewModel.projectDocumentCountFlow(project.id) },
                        onClick = { chatViewModel.openProjectHome(project.id) },
                        modifier = Modifier.padding(horizontal = Spacing.base),
                    )
                }
            }
            FloatingActionButton(
                onClick = { showCreate = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Add, "New project") }
        }
    }
}

@Composable
private fun ProjectListCard(
    project: Project,
    chatCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    docCountFlow: () -> kotlinx.coroutines.flow.Flow<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chatCount by remember(project.id) { chatCountFlow() }.collectAsState(0)
    val docCount by remember(project.id) { docCountFlow() }.collectAsState(0)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(Spacing.base),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProjectMedallion(project.colorIndex, size = 52.dp)
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    projectMetaLine(chatCount, docCount, project.instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
