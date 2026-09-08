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
import com.echoflow.data.DeepResearchForegroundService
import com.echoflow.data.ReplyNotifications
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

        val appGraph = EchoFlowAppGraph(application)

        // Resume any Deep Research run that was interrupted by an app kill — but only spin
        // up the service when there is actually something to resume (no idle notification).
        lifecycleScope.launch {
            if (appGraph.database.researchRunDao().getInterrupted().isNotEmpty()) {
                DeepResearchForegroundService.resume(applicationContext)
            }
        }

        setContent {
            // 2. Fetch ViewModels using our custom factory providers
            val settingsVm: SettingsViewModel = viewModel(
                factory = appGraph.settingsViewModelFactory
            )

            val chatVm: ChatViewModel = viewModel(
                factory = appGraph.chatViewModelFactory
            )

            // Notification tap → jump to that conversation, switching modes if it lives in the
            // other one (a finished video is routinely waited for from the opposite surface).
            val pendingChat by openChatRequest.collectAsState()
            LaunchedEffect(pendingChat) {
                pendingChat?.let { chatId ->
                    chatVm.openThreadFromNotification(chatId)
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
