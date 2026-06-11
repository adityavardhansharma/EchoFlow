package com.echoflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Settings
import com.echoflow.data.AppDatabase
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.components.ChatDrawerContent
import com.echoflow.ui.screens.ChatScreen
import com.echoflow.ui.screens.SettingsScreen
import com.echoflow.ui.theme.EchoFlowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initializing Local Repositories and SQLite Database
        val database = AppDatabase.getDatabase(application)
        val settingsRepo = SettingsRepository(applicationContext)
        val modelDownloadManager = ModelDownloadManager(applicationContext, database.localModelDao())

        setContent {
            // 2. Fetch ViewModels using our custom factory providers
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.provideFactory(
                    settingsRepo,
                    database.customModelDao(),
                    database.localModelDao(),
                    modelDownloadManager
                )
            )

            val chatVm: ChatViewModel = viewModel(
                factory = ChatViewModel.provideFactory(
                    application,
                    database.chatDao(),
                    database.messageDao(),
                    settingsRepo,
                    database.localModelDao()
                )
            )

            // 3. Reacting to global themes selections
            val userThemeColor by settingsVm.themeColor.collectAsState()
            val userDarkModeId by settingsVm.darkMode.collectAsState()

            val isSystemDark = isSystemInDarkTheme()
            val themeActiveDark = when (userDarkModeId) {
                "light" -> false
                "dark" -> true
                else -> isSystemDark
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
    }
}

@Composable
fun AdaptiveChatWorkspace(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val allConversations by chatViewModel.allThreads.collectAsState()
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
                        onSettingsClicked = onSettingsClicked
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
                            onCloseDrawer = { scope.launch { mobileDrawerState.close() } }
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
