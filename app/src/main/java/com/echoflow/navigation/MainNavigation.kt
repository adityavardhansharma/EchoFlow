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
import com.echoflow.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigationHub(chatViewModel: ChatViewModel, settingsViewModel: SettingsViewModel) {
    var activeTab by remember { mutableStateOf("chat") }
    val activeBrowserSession by chatViewModel.activeBrowserSession.collectAsState()
    val browserWorkspaceChatId by chatViewModel.browserWorkspaceChatId.collectAsState()
    val artifactWorkspaceOpen by chatViewModel.artifactWorkspaceOpen.collectAsState()

    Box(Modifier.fillMaxSize()) {
        if (activeTab == "settings") {
            BackHandler { activeTab = "chat" }
            SettingsScreen(viewModel = settingsViewModel, onBackClicked = { activeTab = "chat" })
        } else {
            AdaptiveChatWorkspace(chatViewModel, settingsViewModel) { activeTab = "settings" }
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
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f)) {
                    ChatScreen(chatViewModel, settingsViewModel, {}, onSettingsClicked)
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
                        onCloseDrawer = { scope.launch { drawerState.close() } },
                        searchQuery = query,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery,
                    )
                }
            }) {
                ChatScreen(chatViewModel, settingsViewModel,
                    { scope.launch { drawerState.open() } }, onSettingsClicked)
            }
        }
    }
}
