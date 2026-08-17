@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.echoflow.data.AppMode
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel

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
) {
    val mode by chatViewModel.appMode.collectAsState()
    val renderingModes by chatViewModel.renderingModes.collectAsState()
    val errorMessage by chatViewModel.errorMessage.collectAsState()

    // Top inset so content scrolls behind the floating bar without hiding its first item.
    val topBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp

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
                        topBarInset = topBarInset,
                    )
                    AppMode.Imagine -> ImagineSurface(
                        chatViewModel = chatViewModel,
                        settingsViewModel = settingsViewModel,
                        onSettingsClicked = onSettingsClicked,
                        topBarInset = topBarInset,
                    )
                }
            }

            ChatTopBar(
                modifier = Modifier.align(Alignment.TopCenter),
                mode = mode,
                onSelectMode = chatViewModel::switchMode,
                renderingModes = renderingModes,
                onMenu = onMenuClicked,
                onNewChat = { chatViewModel.startNewChat() },
            )

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
