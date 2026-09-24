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
import com.echoflow.ui.screens.chat.ChatScreen
import com.echoflow.ui.screens.settings.PageWebSearch
import com.echoflow.ui.screens.settings.SettingsScreen
import com.echoflow.data.ScheduleManager
import com.echoflow.data.ScheduleTask
import com.echoflow.data.ScheduleText
import com.echoflow.ui.screens.schedules.ScheduleRoute
import kotlinx.coroutines.launch

@Composable
fun MainNavigationHub(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    pendingScheduleId: String? = null,
    pendingScheduleChatId: String? = null,
    onPendingScheduleConsumed: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf("chat") }
    var scheduleRoute by remember { mutableStateOf<ScheduleRoute?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheduleManager = remember(context) { ScheduleManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    // A finished-run notification opens that schedule's conversation, over whatever is showing.
    LaunchedEffect(pendingScheduleId) {
        val id = pendingScheduleId ?: return@LaunchedEffect
        val task = scheduleManager.taskNow(id)
        if (task != null) {
            activeTab = "chat"
            scheduleRoute = ScheduleRoute.Conversation(task.id, task.threadId)
        } else if (pendingScheduleChatId != null) {
            activeTab = "chat"
            scheduleRoute = null
            chatViewModel.openThreadFromNotification(pendingScheduleChatId)
        }
        onPendingScheduleConsumed()
    }
    // Schedule conversations open in their own screen; everything else is an ordinary chat.
    // A chat written by a v28 run carries the schedule's mark but is not its conversation.
    val openThread: (String) -> Unit = { id ->
        scope.launch {
            val thread = chatViewModel.filteredThreads.value.firstOrNull { it.id == id }
            val scheduleId = thread?.scheduleId
            val task = scheduleId?.let { scheduleManager.taskNow(it) }
            if (scheduleId != null && (task == null || task.threadId == id)) {
                scheduleRoute = ScheduleRoute.Conversation(scheduleId, id)
            } else chatViewModel.selectThread(id)
        }
    }
    val tasks by scheduleManager.tasks.collectAsState(initial = emptyList())
    val schedulesHint = tasks.filter { it.status == ScheduleTask.ACTIVE }.mapNotNull { it.nextRunAt }.minOrNull()
        ?.let { ScheduleText.relative(it) }
    var settingsStartPage by remember { mutableStateOf<String?>(null) }
    val activeBrowserSession by chatViewModel.activeBrowserSession.collectAsState()
    val browserWorkspaceChatId by chatViewModel.browserWorkspaceChatId.collectAsState()
    val artifactWorkspaceOpen by chatViewModel.artifactWorkspaceOpen.collectAsState()
    val researchWorkspace by chatViewModel.researchWorkspace.collectAsState()
    val artifactsGalleryOpen by chatViewModel.artifactsGalleryOpen.collectAsState()
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
            AdaptiveChatWorkspace(chatViewModel, settingsViewModel, { activeTab = "settings" },
                onSchedulesClicked = { scheduleRoute = ScheduleRoute.Home },
                onThreadSelected = openThread,
                schedulesHint = schedulesHint,
                onOpenSchedule = { id ->
                    scope.launch {
                        scheduleManager.taskNow(id)?.let { scheduleRoute = ScheduleRoute.Conversation(it.id, it.threadId) }
                    }
                }) {
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
            com.echoflow.ui.screens.projects.ProjectsHubScreen(chatViewModel = chatViewModel)
        }
        scheduleRoute?.let { route ->
            com.echoflow.ui.screens.schedules.SchedulesScreen(
                settingsViewModel = settingsViewModel,
                route = route,
                onRouteChange = { scheduleRoute = it },
                onManageModels = {
                    scheduleRoute = null
                    activeTab = "settings"
                },
            )
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
    }
}

@Composable
fun AdaptiveChatWorkspace(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    onSchedulesClicked: () -> Unit = {},
    onThreadSelected: (String) -> Unit = chatViewModel::selectThread,
    schedulesHint: String? = null,
    onOpenSchedule: (String) -> Unit = {},
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
                        onThreadSelected = onThreadSelected,
                        onNewChatClicked = chatViewModel::startNewChat,
                        onDeleteThread = chatViewModel::deleteThread,
                        onRenameThread = chatViewModel::renameThread,
                        onPinThread = chatViewModel::pinThread,
                        onUnpinThread = chatViewModel::unpinThread,
                        onSettingsClicked = onSettingsClicked,
                        onProjectsClicked = chatViewModel::openProjectsHub,
                        onArtifactsClicked = chatViewModel::openArtifactsGallery,
                        onSchedulesClicked = onSchedulesClicked,
                        schedulesHint = schedulesHint,
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f)) {
                    ChatScreen(chatViewModel, settingsViewModel, {}, onSettingsClicked, onOpenWebSearchSettings, onOpenSchedule)
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
                        onThreadSelected = onThreadSelected,
                        onNewChatClicked = chatViewModel::startNewChat,
                        onDeleteThread = chatViewModel::deleteThread,
                        onRenameThread = chatViewModel::renameThread,
                        onPinThread = chatViewModel::pinThread,
                        onUnpinThread = chatViewModel::unpinThread,
                        onSettingsClicked = onSettingsClicked,
                        onProjectsClicked = chatViewModel::openProjectsHub,
                        onArtifactsClicked = chatViewModel::openArtifactsGallery,
                        onSchedulesClicked = onSchedulesClicked,
                        schedulesHint = schedulesHint,
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
            }) {
                ChatScreen(chatViewModel, settingsViewModel,
                    { scope.launch { drawerState.open() } }, onSettingsClicked, onOpenWebSearchSettings, onOpenSchedule)
            }
        }
    }
}
