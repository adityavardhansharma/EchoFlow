package com.echoflow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Settings
import com.echoflow.data.AppDatabase
import com.echoflow.data.DeepResearchForegroundService
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.ReplyNotifications
import com.echoflow.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.ChatDrawerContent
import com.echoflow.ui.screens.ChatScreen
import com.echoflow.ui.screens.SettingsScreen
import com.echoflow.ui.theme.EchoFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    // A chat id to open, set when launched/resumed from a reply-ready notification tap.
    private val openChatRequest = MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(ReplyNotifications.EXTRA_OPEN_CHAT)?.let { openChatRequest.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openChatRequest.value = intent?.getStringExtra(ReplyNotifications.EXTRA_OPEN_CHAT)

        // Android 13+ needs runtime POST_NOTIFICATIONS for the Deep Research / Data Agent
        // foreground-service progress notification to appear in the status bar.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 1. Initializing Local Repositories and SQLite Database
        val database = AppDatabase.getDatabase(application)
        val settingsRepo = SettingsRepository(applicationContext)
        val modelDownloadManager = ModelDownloadManager(applicationContext, database.localModelDao())

        // Resume any Deep Research run that was interrupted by an app kill — but only spin
        // up the service when there is actually something to resume (no idle notification).
        lifecycleScope.launch {
            if (database.researchRunDao().getInterrupted().isNotEmpty()) {
                DeepResearchForegroundService.resume(applicationContext)
            }
        }

        setContent {
            // 2. Fetch ViewModels using our custom factory providers
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    settingsRepo,
                    database.customModelDao(),
                    database.localModelDao(),
                    modelDownloadManager,
                    database.deepResearchModelDao(),
                    database.advisorProfileDao(),
                    database.fusionPanelDao(),
                    database.agentProfileDao()
                )
            )

            val chatVm: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(
                    application,
                    database.chatDao(),
                    database.messageDao(),
                    settingsRepo,
                    database.localModelDao(),
                    database.researchRunDao(),
                    database.deepResearchModelDao(),
                    database.advisorProfileDao(),
                    database.fusionPanelDao(),
                    database.agentProfileDao(),
                    database.browserSessionDao(),
                    database.browserStepDao(),
                    database.artifactDao(),
                    database.artifactVersionDao()
                )
            )

            // Notification tap → jump to that conversation once the ViewModel is ready.
            val pendingChat by openChatRequest.collectAsState()
            LaunchedEffect(pendingChat) {
                pendingChat?.let { chatId ->
                    chatVm.selectThread(chatId)
                    openChatRequest.value = null
                }
            }

            // 3. Reacting to global themes selections
            val userThemeColor by settingsVm.themeColor.collectAsState()
            val userDarkModeId by settingsVm.darkMode.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val themeActiveDark = when (userDarkModeId) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
            }

            // Status/navigation bar icons must contrast with the page, which follows the in-app
            // theme — not the system uiMode that enableEdgeToEdge() keys off by default. Without
            // this, a "Light" theme under a dark OS leaves white icons invisible on white.
            val view = LocalView.current
            LaunchedEffect(themeActiveDark) {
                WindowCompat.getInsetsController(this@MainActivity.window, view).apply {
                    isAppearanceLightStatusBars = !themeActiveDark
                    isAppearanceLightNavigationBars = !themeActiveDark
                }
            }

            EchoFlowTheme(
                darkTheme = themeActiveDark,
                themeName = userThemeColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigationHub(chatVm, settingsVm)
                }
            }
        }
    }
}

@Composable
fun MainNavigationHub(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel
) {
    var activeTab by remember { mutableStateOf("chat") }

    val activeBrowserSession by chatViewModel.activeBrowserSession.collectAsState()
    val browserWorkspaceChatId by chatViewModel.browserWorkspaceChatId.collectAsState()
    val artifactWorkspaceOpen by chatViewModel.artifactWorkspaceOpen.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (activeTab == "settings") {
            // Intercept the system back gesture/button so it returns to chat
            // instead of falling through to the Activity and exiting the app.
            BackHandler { activeTab = "chat" }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClicked = { activeTab = "chat" }
            )
        } else {
            AdaptiveChatWorkspace(
                chatViewModel = chatViewModel,
                settingsViewModel = settingsViewModel,
                onSettingsClicked = { activeTab = "settings" }
            )
        }

        // Global "remote browser active" pill — visible across tabs while a session is live and
        // the workspace is closed. Tapping it returns to the owning chat's workspace.
        val pillSession = activeBrowserSession
        if (pillSession != null && browserWorkspaceChatId == null) {
            com.echoflow.ui.components.GlobalBrowserPill(
                session = pillSession,
                onClick = { chatViewModel.openBrowserWorkspace(pillSession.chatId) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp),
            )
        }

        // Fullscreen Browser Workspace overlay (the native mini-browser).
        if (browserWorkspaceChatId != null) {
            BackHandler { chatViewModel.closeBrowserWorkspace() }
            com.echoflow.ui.screens.BrowserWorkspaceScreen(
                chatViewModel = chatViewModel,
                onClose = { chatViewModel.closeBrowserWorkspace() },
            )
        }

        // Fullscreen Artifact Workspace overlay (preview / code / version switcher). The smooth
        // slide-up/fade on open is animated inside the screen itself (see ArtifactWorkspaceScreen)
        // so its presence is never gated behind a transition — keeping "Open" reliable.
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
    onSettingsClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allConversations by chatViewModel.filteredThreads.collectAsState()
    val drawerSearchQuery by chatViewModel.drawerSearchQuery.collectAsState()
    val selectedThreadId by chatViewModel.currentChatThreadId.collectAsState()

    // Inspect screen boundaries for Tablet orientation check
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useSplitLayout = maxWidth >= 600.dp

        if (useSplitLayout) {
            // Dual-Pane Slate: Side-by-Side persistent layout for Expanded screens
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                ) {
                    ChatDrawerContent(
                        allThreads = allConversations,
                        currentThreadId = selectedThreadId,
                        onThreadSelected = { id -> chatViewModel.selectThread(id) },
                        onNewChatClicked = { chatViewModel.startNewChat() },
                        onDeleteThread = { t -> chatViewModel.deleteThread(t) },
                        onRenameThread = { t, name -> chatViewModel.renameThread(t, name) },
                        onSettingsClicked = onSettingsClicked,
                        searchQuery = drawerSearchQuery,
                        onSearchQueryChange = chatViewModel::setDrawerSearchQuery
                    )
                }

                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(modifier = Modifier.weight(1f)) {
                    ChatScreen(
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        onMenuClicked = { /* Drawer is persistent, no trigger required */ },
                        onSettingsClicked = onSettingsClicked
                    )
                }
            }
        } else {
            // Mobile Sliding Layer: Modal sliding drawers for compact display
            val mobileDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            ModalNavigationDrawer(
                drawerState = mobileDrawerState,
                // Gestures on: swipe from the edge to open, swipe the sheet to close.
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        drawerShape = androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        modifier = Modifier.widthIn(max = 340.dp)
                    ) {
                        ChatDrawerContent(
                            allThreads = allConversations,
                            currentThreadId = selectedThreadId,
                            onThreadSelected = { id -> chatViewModel.selectThread(id) },
                            onNewChatClicked = { chatViewModel.startNewChat() },
                            onDeleteThread = { t -> chatViewModel.deleteThread(t) },
                            onRenameThread = { t, name -> chatViewModel.renameThread(t, name) },
                            onSettingsClicked = onSettingsClicked,
                            onCloseDrawer = { scope.launch { mobileDrawerState.close() } },
                            searchQuery = drawerSearchQuery,
                            onSearchQueryChange = chatViewModel::setDrawerSearchQuery
                        )
                    }
                }
            ) {
                ChatScreen(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    onMenuClicked = { scope.launch { mobileDrawerState.open() } },
                    onSettingsClicked = onSettingsClicked
                )
            }
        }
    }
}
