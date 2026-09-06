package com.echoflow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.ChatDrawerContent
import com.echoflow.ui.screens.ChatScreen
import com.echoflow.ui.screens.PageWebSearch
import com.echoflow.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigationHub(chatViewModel: ChatViewModel, settingsViewModel: SettingsViewModel, shareIntake: com.echoflow.ui.ShareIntakeViewModel? = null) {
    var activeTab by remember { mutableStateOf("chat") }
    var settingsStartPage by remember { mutableStateOf<String?>(null) }
    val activeBrowserSession by chatViewModel.activeBrowserSession.collectAsState()
    val browserWorkspaceChatId by chatViewModel.browserWorkspaceChatId.collectAsState()
    val artifactWorkspaceOpen by chatViewModel.artifactWorkspaceOpen.collectAsState()
    val researchWorkspace by chatViewModel.researchWorkspace.collectAsState()
    val artifactsGalleryOpen by chatViewModel.artifactsGalleryOpen.collectAsState()
    val quickOpen by chatViewModel.quickTasks.open.collectAsState()
    val incoming by (shareIntake?.input ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val importing by (shareIntake?.busy ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val importError by (shareIntake?.error ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()
    val projectsHubOpen by chatViewModel.projectsHubOpen.collectAsState()

    Box(Modifier.fillMaxSize()) {
        if (activeTab == "settings") {
            BackHandler { activeTab = "chat" }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClicked = { activeTab = "chat" },
                startPage = settingsStartPage,
                onStartPageConsumed = { settingsStartPage = null },
            )
        } else {
            AdaptiveChatWorkspace(chatViewModel, settingsViewModel, { activeTab = "settings" }) {
                settingsStartPage = PageWebSearch
                activeTab = "settings"
            }
        }
        activeBrowserSession?.takeIf { browserWorkspaceChatId == null }?.let { session ->
            com.echoflow.ui.components.GlobalBrowserPill(
                session,
                { chatViewModel.openBrowserWorkspace(session.chatId) },
                Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
            )
        }
        if (browserWorkspaceChatId != null) {
            BackHandler { chatViewModel.closeBrowserWorkspace() }
            com.echoflow.ui.screens.BrowserWorkspaceScreen(
                chatViewModel = chatViewModel,
                onClose = { chatViewModel.closeBrowserWorkspace() },
            )
        }
        if (researchWorkspace != null) {
            BackHandler { chatViewModel.closeResearchWorkspace() }
            com.echoflow.ui.screens.ResearchWorkspaceScreen(
                chatViewModel = chatViewModel,
                onClose = { chatViewModel.closeResearchWorkspace() },
            )
        }
        if (artifactsGalleryOpen) {
            BackHandler { chatViewModel.closeArtifactsGallery() }
            com.echoflow.ui.screens.ArtifactsGalleryScreen(
                chatViewModel = chatViewModel,
                onClose = { chatViewModel.closeArtifactsGallery() },
            )
        }
        if (projectsHubOpen) {
            // The hub owns its own back stepping: home → list → closed (see ProjectsHubScreen).
            com.echoflow.ui.screens.ProjectsHubScreen(chatViewModel = chatViewModel)
        }
        // The artifact workspace is the top-most overlay: opening a tile from the gallery slides it
        // up *over* the still-mounted gallery (one continuous flow), and closing it reveals the
        // gallery again rather than flashing the chat behind it.
        if (artifactWorkspaceOpen) {
            BackHandler { chatViewModel.closeArtifactWorkspace() }
            com.echoflow.ui.screens.ArtifactWorkspaceScreen(
                chatViewModel = chatViewModel,
                onClose = { chatViewModel.closeArtifactWorkspace() },
            )
        }
        if (activeTab != "settings" && (quickOpen || incoming != null)) {
            BackHandler { if (incoming == null || !chatViewModel.quickTasks.busy.value) { if (incoming != null) shareIntake?.consumed(discard = true); chatViewModel.quickTasks.close() } }
            com.echoflow.ui.screens.QuickToolsScreen(chatViewModel, settingsViewModel, incoming,
                onConsumed = { shareIntake?.consumed(it) }, onClose = { chatViewModel.quickTasks.close() },
                onManageModels = { chatViewModel.quickTasks.close(); activeTab = "settings" })
        }
        if (importing) AlertDialog(onDismissRequest = {}, title = { Text("Reading shared content") },
            text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text("Copying files and extracting document text on this device…") } }, confirmButton = {})
        importError?.let { message -> AlertDialog(onDismissRequest = { shareIntake?.error?.value = null }, title = { Text("Could not import share") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { shareIntake?.error?.value = null }) { Text("OK") } }) }

    }
}

@Composable
fun AdaptiveChatWorkspace(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    onOpenWebSearchSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val threads by chatViewModel.filteredThreads.collectAsState()
    val query by chatViewModel.drawerSearchQuery.collectAsState()
    val selectedId by chatViewModel.currentChatThreadId.collectAsState()
    val mode by chatViewModel.appMode.collectAsState()
    val renderingChatIds by chatViewModel.renderingChatIds.collectAsState()
    val otherModeMatches by chatViewModel.otherModeMatchCount.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxHeight().width(320.dp)) {
                    ChatDrawerContent(
                        mode = mode,
                        allThreads = threads,
                        currentThreadId = selectedId,
                        renderingChatIds = renderingChatIds,
                        otherModeMatchCount = otherModeMatches,
                        onThreadSelected = chatViewModel::selectThread,
                        onNewChatClicked = chatViewModel::startNewChat,
                        onDeleteThread = chatViewModel::deleteThread,
                        onRenameThread = chatViewModel::renameThread,
                        onPinThread = chatViewModel::pinThread,
                        onUnpinThread = chatViewModel::unpinThread,
                        onSettingsClicked = onSettingsClicked,
                        onProjectsClicked = chatViewModel::openProjectsHub,
                        onArtifactsClicked = chatViewModel::openArtifactsGallery,
                        onCompareClicked = { chatViewModel.quickTasks.show() },
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f)) {
                    ChatScreen(chatViewModel, settingsViewModel, {}, onSettingsClicked, onOpenWebSearchSettings)
                }
            }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    modifier = Modifier.widthIn(max = 340.dp),
                ) {
                    ChatDrawerContent(
                        mode = mode,
                        allThreads = threads,
                        currentThreadId = selectedId,
                        renderingChatIds = renderingChatIds,
                        otherModeMatchCount = otherModeMatches,
                        onThreadSelected = chatViewModel::selectThread,
                        onNewChatClicked = chatViewModel::startNewChat,
                        onDeleteThread = chatViewModel::deleteThread,
                        onRenameThread = chatViewModel::renameThread,
                        onPinThread = chatViewModel::pinThread,
                        onUnpinThread = chatViewModel::unpinThread,
                        onSettingsClicked = onSettingsClicked,
                        onProjectsClicked = chatViewModel::openProjectsHub,
                        onArtifactsClicked = chatViewModel::openArtifactsGallery,
                        onCompareClicked = { chatViewModel.quickTasks.show() },
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
            }) {
                ChatScreen(chatViewModel, settingsViewModel,
                    { scope.launch { drawerState.open() } }, onSettingsClicked, onOpenWebSearchSettings)
            }
        }
    }
}
