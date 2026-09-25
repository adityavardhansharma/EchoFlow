@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

// ── Project home ───────────────────────────────────────────────────────────────────────

private enum class ProjectDetail { None, Instructions, Files }

private fun restoreProjectDetail(saved: String): ProjectDetail =
    ProjectDetail.entries.firstOrNull { it.name == saved } ?: ProjectDetail.None

@Composable
internal fun ProjectHomeScreen(chatViewModel: ChatViewModel, projectId: String, onBack: () -> Unit) {
    val project by remember(projectId) { chatViewModel.projectFlow(projectId) }.collectAsState(null)
    val documents by remember(projectId) { chatViewModel.projectDocumentsFlow(projectId) }.collectAsState(emptyList())
    val chats by remember(projectId) { chatViewModel.projectChatsFlow(projectId) }.collectAsState(emptyList())
    // Saveable so rotation does not reset to home and drop the editor without calling leave/save.
    var detail by rememberSaveable(
        projectId,
        saver = Saver(
            save = { it.value.name },
            restore = { saved -> mutableStateOf(restoreProjectDetail(saved)) },
        ),
    ) { mutableStateOf(ProjectDetail.None) }

    val p = project
    when {
        p != null && detail == ProjectDetail.Instructions -> ProjectInstructionsScreen(
            project = p,
            onSave = { chatViewModel.setProjectInstructions(projectId, it) },
            onBack = { detail = ProjectDetail.None },
        )
        p != null && detail == ProjectDetail.Files -> {
            val modelReadsFiles by chatViewModel.modelReadsFiles.collectAsState()
            val projectFileError by chatViewModel.projectFileError.collectAsState()
            val importProgress by chatViewModel.projectImportProgress.collectAsState()
            val fileError = projectFileError?.takeIf { it.projectId == projectId }?.message
            val queued = importProgress[projectId]?.queued ?: 0
            ProjectFilesScreen(
                project = p,
                documents = documents,
                queued = queued,
                modelReadsFiles = modelReadsFiles,
                fileError = fileError,
                onClearFileError = { chatViewModel.clearProjectFileError(projectId) },
                onAdd = { uris -> chatViewModel.addProjectDocuments(projectId, uris) },
                onRemove = { chatViewModel.removeProjectDocument(it) },
                onBack = { chatViewModel.clearProjectFileError(projectId); detail = ProjectDetail.None },
            )
        }
        else -> {
            BackHandler { onBack() }
            ProjectHomeContent(
                chatViewModel = chatViewModel,
                project = p,
                projectId = projectId,
                documents = documents,
                chats = chats,
                onBack = onBack,
                onOpenInstructions = { detail = ProjectDetail.Instructions },
                onOpenFiles = { detail = ProjectDetail.Files },
            )
        }
    }
}


@Composable
private fun ProjectHomeContent(
    chatViewModel: ChatViewModel,
    project: Project?,
    projectId: String,
    documents: List<ProjectDocument>,
    chats: List<com.echoflow.data.ChatThread>,
    onBack: () -> Unit,
    onOpenInstructions: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    val colorIndex = project?.colorIndex ?: 0
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    if (showRename && project != null) {
        ProjectNameDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Save",
            colorIndex = colorIndex,
            onDismiss = { showRename = false },
            onConfirm = { showRename = false; chatViewModel.renameProject(projectId, it) },
        )
    }
    if (showDelete && project != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete project?") },
            text = { Text("“${project.name}” will be deleted. Its conversations are kept and return to your chat list.") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    chatViewModel.deleteProject(projectId)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
    if (showColor && project != null) {
        ProjectColorDialog(
            selected = project.colorIndex,
            onDismiss = { showColor = false },
            onPick = { showColor = false; chatViewModel.setProjectColor(projectId, it) },
        )
    }

    ProjectPageScaffold(
        title = project?.name ?: "Project",
        subtitle = project?.let { "Updated ${relativeTime(it.updatedAt, LocalProjectsNow.current)}" },
        titleLeading = { ProjectMedallion(colorIndex, size = 28.dp) },
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project options") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; showRename = true })
                    DropdownMenuItem(text = { Text("Colour & shape") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menuOpen = false; showColor = true })
                    HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                    DropdownMenuItem(
                        text = { Text("Delete project", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; showDelete = true },
                    )
                }
            }
        },
        // The primary action: a new chat that already carries this project's instructions and files.
        floatingActionButton = {
            ProjectFab("New chat", Icons.Default.Create, fabExpanded) {
                chatViewModel.startNewChatInProject(projectId)
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.base, end = Spacing.base, top = Spacing.s, bottom = 112.dp),
        ) {
            item(key = "context") {
                val brief = project?.instructions?.trim().orEmpty()
                ProjectContext(
                    brief = brief,
                    fileCount = documents.size,
                    readingCount = documents.count { it.status.isBusy },
                    onOpenInstructions = onOpenInstructions,
                    onOpenFiles = onOpenFiles,
                    modifier = Modifier.padding(bottom = Spacing.l),
                )
            }

            item(key = "chats-label") { ProjectSectionHeader("Chats") }
            if (chats.isEmpty()) {
                item(key = "chats-empty") { NoChatsNote() }
            } else {
                itemsIndexed(chats, key = { _, it -> it.id }) { index, thread ->
                    ProjectChatRow(
                        thread = thread,
                        shape = groupedItemShape(index, chats.size),
                        onClick = { chatViewModel.openChatFromProject(thread.id) },
                        modifier = Modifier.padding(bottom = GroupedItemGap).animateItem(),
                    )
                }
            }
        }
    }
}

// ── Project context ─────────────────────────────────────────────────────────────────────

/**
 * Instructions and Files as two connected rows, the same shape as a Settings row: an icon badge,
 * a title, one line saying what's there, and a chevron into the page.
 */
@Composable
private fun ProjectContext(
    brief: String,
    fileCount: Int,
    readingCount: Int,
    onOpenInstructions: () -> Unit,
    onOpenFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        ContextRow(
            icon = Icons.AutoMirrored.Outlined.Notes,
            title = "Instructions",
            detail = brief.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: "Tell the model how to work in this project",
            shape = groupedItemShape(0, 2),
            onClick = onOpenInstructions,
        )
        ContextRow(
            icon = Icons.Outlined.FolderOpen,
            title = "Files",
            detail = when {
                fileCount == 0 -> "Add files every chat here can use"
                readingCount > 0 -> "$fileCount files · reading $readingCount"
                fileCount == 1 -> "1 file"
                else -> "$fileCount files"
            },
            shape = groupedItemShape(1, 2),
            onClick = onOpenFiles,
        )
    }
}

@Composable
private fun ContextRow(
    icon: ImageVector,
    title: String,
    detail: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.padding(start = Spacing.s), tint = cs.onSurfaceVariant,
            )
        }
    }
}

// ── Chats ─────────────────────────────────────────────────────────────────────────────────

/** Nothing here yet, said in one line under the header. */
@Composable
private fun NoChatsNote() {
    Text(
        "No chats yet. New chats here use this project's instructions and files.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
    )
}

/** One chat in the project's connected list: its title, and how long ago it was used. */
@Composable
private fun ProjectChatRow(
    thread: com.echoflow.data.ChatThread,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = LocalProjectsNow.current
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                thread.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                relativeTime(thread.updatedAt, now),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
