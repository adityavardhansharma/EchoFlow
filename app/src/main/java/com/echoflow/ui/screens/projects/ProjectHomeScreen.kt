@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package com.echoflow.ui.screens.projects

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.components.projectShape
import com.echoflow.ui.theme.RoundedPolygonShape
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
    val accent = projectAccent(colorIndex)

    if (showRename && project != null) {
        ProjectNameDialog(
            title = "Rename project",
            initial = project.name,
            confirmLabel = "Save",
            onDismiss = { showRename = false },
            onConfirm = { showRename = false; chatViewModel.renameProject(projectId, it) },
        )
    }
    if (showDelete && project != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            icon = { Icon(Icons.Default.DeleteOutline, null) },
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

    Column(Modifier.fillMaxSize()) {
        ProjectHeader(
            onBack = onBack,
            trailing = {
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menuOpen = false; showRename = true })
                        DropdownMenuItem(text = { Text("Colour & shape") }, leadingIcon = { Icon(Icons.Default.Palette, null) }, onClick = { menuOpen = false; showColor = true })
                        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menuOpen = false; showDelete = true })
                    }
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectMedallion(colorIndex, size = 54.dp)
                Spacer(Modifier.width(Spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(
                        project?.name ?: "Project",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        projectMetaLine(chats.size, documents.size, project?.instructions.orEmpty()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.base, Spacing.l, Spacing.base, Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // The primary action — start a chat that already carries this project's context.
            item {
                NewChatHero(
                    colorIndex = colorIndex,
                    accentContainer = accent.container,
                    accentOnContainer = accent.onContainer,
                    onClick = { chatViewModel.startNewChatInProject(projectId) },
                )
            }

            // Context setup: a labelled, connected slab so the two live as a considered pair, not
            // two squares glued side by side. Label + slab share one item so they stay tight.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        "Project context",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        SetupRow(
                            index = 0, count = 2,
                            icon = Icons.Default.Description,
                            title = "Instructions",
                            status = project?.instructions?.trim().orEmpty().ifBlank { "Set a standing brief for every chat" },
                            filled = !project?.instructions?.trim().isNullOrBlank(),
                            onClick = onOpenInstructions,
                        )
                        SetupRow(
                            index = 1, count = 2,
                            icon = Icons.Default.FolderOpen,
                            title = "Files",
                            status = when (documents.size) {
                                0 -> "Attach reference the model can draw on"
                                1 -> "1 file attached"
                                else -> "${documents.size} files attached"
                            },
                            filled = documents.isNotEmpty(),
                            onClick = onOpenFiles,
                        )
                    }
                }
            }

            item {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                )
            }

            if (chats.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "No conversations yet. Start one above and it will live in this project, carrying its instructions and files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.base),
                        )
                    }
                }
            } else {
                items(chats, key = { it.id }) { thread ->
                    ProjectChatRow(thread, colorIndex) { chatViewModel.openChatFromProject(thread.id) }
                }
            }
        }
    }
}

/**
 * The hero call-to-action: a full-bleed accent card with the project's own shape bled in as a faint
 * watermark, its shaped mark, and an arrow that reads as "go".
 */
@Composable
private fun NewChatHero(
    colorIndex: Int,
    accentContainer: Color,
    accentOnContainer: Color,
    onClick: () -> Unit,
) {
    val shape = remember(colorIndex) { RoundedPolygonShape(projectShape(colorIndex)) }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = accentContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            // The identity bleeding into its own action — a big, faint project-shape watermark,
            // clipped by the card so it only hints at the edge.
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 40.dp)
                    .size(150.dp)
                    .clip(shape)
                    .background(accentOnContainer.copy(alpha = 0.09f)),
            )
            Row(
                Modifier.padding(Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.base),
            ) {
                Box(
                    Modifier.size(48.dp).clip(shape).background(accentOnContainer.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Add, null, Modifier.size(24.dp), tint = accentOnContainer) }
                Column(Modifier.weight(1f)) {
                    Text(
                        "New chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentOnContainer,
                    )
                    Text(
                        "Start with this project's context",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accentOnContainer.copy(alpha = 0.78f),
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = accentOnContainer)
            }
        }
    }
}

/** One row in the context slab. Corners are shaped by [groupedItemShape] so the pair reads as one. */
@Composable
private fun SetupRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    status: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (filled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon, null, Modifier.size(20.dp),
                    tint = if (filled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (filled) {
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(Icons.Default.Check, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectChatRow(thread: com.echoflow.data.ChatThread, colorIndex: Int, onClick: () -> Unit) {
    val accent = projectAccent(colorIndex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(accent.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp), tint = accent.onContainer)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    thread.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    DateUtils.getRelativeTimeSpanString(thread.updatedAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
