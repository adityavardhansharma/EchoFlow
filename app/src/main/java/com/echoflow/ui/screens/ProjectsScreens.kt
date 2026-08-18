@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.Project
import com.echoflow.data.ProjectDocument
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.PROJECT_ACCENT_COUNT
import com.echoflow.ui.components.ProjectMedallion
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.components.projectAccent
import com.echoflow.ui.components.projectShape
import com.echoflow.ui.theme.BrandShapes
import com.echoflow.ui.theme.MorphPolygonShape
import com.echoflow.ui.theme.RoundedPolygonShape
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberMorph
import com.echoflow.ui.theme.rememberMorphProgress
import com.echoflow.ui.theme.rememberReducedMotion

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

    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
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

// ── Hero header ────────────────────────────────────────────────────────────────────────

/**
 * The shared surface header: a rounded-bottom slab that carries the down-chevron, an optional
 * trailing action, and a large title block. The rounding and containment are what turn a flat
 * toolbar into a native header the page hangs off of.
 */
@Composable
private fun ProjectHeader(
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
private fun projectMetaLine(chatCount: Int, docCount: Int, instructions: String): String {
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

// ── Project home ───────────────────────────────────────────────────────────────────────

private enum class ProjectDetail { None, Instructions, Files }

private fun restoreProjectDetail(saved: String): ProjectDetail =
    ProjectDetail.entries.firstOrNull { it.name == saved } ?: ProjectDetail.None

@Composable
private fun ProjectHomeScreen(chatViewModel: ChatViewModel, projectId: String, onBack: () -> Unit) {
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
        p != null && detail == ProjectDetail.Files -> ProjectFilesScreen(
            project = p,
            documents = documents,
            onAdd = { uri -> chatViewModel.addProjectDocument(projectId, uri) },
            onRemove = { chatViewModel.removeProjectDocument(it) },
            onBack = { detail = ProjectDetail.None },
        )
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

// ── Instructions editor ────────────────────────────────────────────────────────────────

@Composable
private fun ProjectInstructionsScreen(project: Project, onSave: (String) -> Unit, onBack: () -> Unit) {
    // rememberSaveable, not remember: autosave only fires on leave, so a config change or process
    // death mid-edit must not discard the in-progress text and snap back to the persisted value.
    var text by rememberSaveable(project.id) { mutableStateOf(project.instructions) }
    val leave = { onSave(text.trim()); onBack() }
    BackHandler { leave() }

    Column(Modifier.fillMaxSize()) {
        ProjectHeader(
            onBack = leave,
            trailing = { TextButton(onClick = leave) { Text("Save") } },
        ) {
            Text("Instructions", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.fillMaxSize().padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            // A quiet one-line hint — guidance, not a heavy coloured slab competing with the editor.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Sent to the model on every message in this project — a standing brief for tone, role and rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            // The writing surface: a generous card with comfortable inner margins so long briefs
            // read like a document, not a cramped form field.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            "e.g. You are helping me write a noir mystery. Keep a wry, hard-boiled tone and remember the characters and timeline in the attached files.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxSize().padding(Spacing.s).imePadding(),
                )
            }
            Text(
                "Changes save when you leave",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
    }
}

// ── Files ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectFilesScreen(
    project: Project,
    documents: List<ProjectDocument>,
    onAdd: (android.net.Uri) -> Unit,
    onRemove: (ProjectDocument) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAdd(uri)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ProjectHeader(onBack = onBack) {
                Text("Files", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when (documents.size) {
                        0 -> "Background knowledge for this project"
                        1 -> "1 file"
                        else -> "${documents.size} files"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (documents.isEmpty()) {
                FilesEmptyState(onAdd = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base, Spacing.l, Spacing.base, 104.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    itemsIndexed(documents, key = { _, it -> it.id }) { index, doc ->
                        DocumentRow(doc, groupedItemShape(index, documents.size)) { onRemove(doc) }
                    }
                }
            }
        }
        if (documents.isNotEmpty()) {
            FloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.base),
            ) { Icon(Icons.Default.Add, "Add file") }
        }
    }
}

@Composable
private fun DocumentRow(document: ProjectDocument, shape: Shape, onRemove: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = Spacing.base, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(document.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(formatBytes(document.sizeBytes))
                        if (!document.hasText) append(" · not read as text")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (document.hasText) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, "Remove file", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Empty states & dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun ProjectsEmptyState(onCreate: () -> Unit, modifier: Modifier = Modifier) {
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
private fun FilesEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    HeroEmptyState(
        glyph = Icons.Default.Description,
        title = "No files yet",
        body = "Add text files — notes, briefs, transcripts — and their contents become background knowledge for this project.",
        actionLabel = "Add file",
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
private fun ProjectNameDialog(
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
private fun ProjectColorDialog(selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
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

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
