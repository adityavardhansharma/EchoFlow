@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import com.echoflow.data.AppMode
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.screens.imagine.ImagineSurface

/**
 * The app's main surface, and the seam between its two modes.
 *
 * Deliberately thin. It owns only what both modes share — the scaffold, the floating top bar
 * and the error banner — and hands the body to whichever surface is active. The top bar sits
 * *outside* the crossfade on purpose: chrome that persists across a mode change should
 * transform in place, never fade out and back in, or switching reads as navigating somewhere
 * else rather than refocusing where you are.
 */
@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onMenuClicked: () -> Unit,
    onSettingsClicked: () -> Unit,
    onOpenWebSearchSettings: () -> Unit = {},
    onOpenSchedule: (scheduleId: String) -> Unit = {},
) {
    val mode by chatViewModel.appMode.collectAsState()
    val currentThreadId by chatViewModel.currentChatThreadId.collectAsState()
    val threads by chatViewModel.allThreads.collectAsState()
    val scheduleThread = threads.firstOrNull { it.id == currentThreadId }?.takeIf { it.scheduleId != null }
    val renderingModes by chatViewModel.renderingModes.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()
    val temporaryChat by chatViewModel.isTemporaryChat.collectAsState()
    // A voice call takes the whole screen: the app's bar steps aside so nothing competes with it.
    val voiceModeState by chatViewModel.voiceModeState.collectAsState()
    val voiceCallOpen = voiceModeState.active

    // Top inset so content scrolls behind the floating bar without hiding its first item.
    val topBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp
    // The schedule pill under the bar pushes the first message down so it never hides its text.
    val messagesTopInset = if (scheduleThread != null) topBarInset + StatusPillHeight + Spacing.s else topBarInset

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Everything floats; each surface fills behind it. Insets are handled per element.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    // Fade-through with a slight lean in the direction of the switch: enough to
                    // say which way you moved, far too little to read as a page transition.
                    val forward = targetState == AppMode.Imagine
                    val shift = { full: Int -> if (forward) full / 24 else -full / 24 }
                    (slideInHorizontally(spatial, shift) + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -shift(it) } + fadeOut(effects))
                },
                label = "appMode",
            ) { current ->
                when (current) {
                    AppMode.Chat -> ChatSurface(
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        onSettingsClicked = onSettingsClicked,
                        onOpenWebSearchSettings = onOpenWebSearchSettings,
                        topBarInset = topBarInset,
                        messagesTopInset = messagesTopInset,
                    )
                    AppMode.Imagine -> ImagineSurface(
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        onSettingsClicked = onSettingsClicked,
                        topBarInset = topBarInset,
                    )
                }
            }

            AnimatedVisibility(
                visible = !voiceCallOpen,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ChatTopBar(
                    mode = mode,
                    onSelectMode = chatViewModel::switchMode,
                    renderingModes = renderingModes,
                    onMenu = onMenuClicked,
                    onNewChat = { chatViewModel.startNewChat() },
                    onNewTemporaryChat = { chatViewModel.startTemporaryChat() },
                    temporaryChat = temporaryChat && mode == AppMode.Chat,
                )
            }

            // A chat a schedule produced says so under the title bar, one tap from its schedule.
            AnimatedVisibility(
                visible = scheduleThread != null && errorMessage == null && !voiceCallOpen,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topBarInset),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                scheduleThread?.scheduleId?.let { id -> FromSchedulePill { onOpenSchedule(id) } }
            }


            // Errors belong to the app, not to a surface: a failure raised in one mode should
            // still be readable if the user has already switched away from it.
            AnimatedVisibility(
                visible = errorMessage != null,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = topBarInset),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                errorMessage?.let { ErrorBanner(it) { chatViewModel.clearError() } }
            }
        }
    }
}

// Icon row (20dp text line) plus 6dp vertical padding on each side.
private val StatusPillHeight = 32.dp

@Composable
private fun FromSchedulePill(onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.echoflow.ui.screens.schedules.ScheduleMark(size = 16.dp, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            androidx.compose.material3.Text("From a schedule · Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}
