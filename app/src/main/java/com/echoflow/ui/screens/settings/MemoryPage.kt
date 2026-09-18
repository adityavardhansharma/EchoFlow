@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.ui.theme.Spacing

@Composable
internal fun MemoryPage(onBack: () -> Unit, onMemories: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf("supermemory") }
    var key by remember { mutableStateOf("") } // Never save API keys in instance state.
    var space by rememberSaveable { mutableStateOf(vm.settings.space) }
    var consent by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    LaunchedEffect(vm.connected) { if (vm.connected) key = "" }
    LaunchedEffect(Unit) { if (vm.connected) vm.refreshBilling() }
    SettingsPageScaffold("Memory", "A little continuity, on your terms", onBack) {
        ConnectedToggleRow(
            options = listOf("supermemory" to "Supermemory", "echobrain" to "EchoBrain"),
            selected = tab,
            onSelect = { tab = it },
            icons = listOf(Icons.Default.CloudQueue, Icons.Default.Psychology),
        )
        Spacer(Modifier.height(Spacing.xl))
        val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
            label = "memoryProvider",
        ) { current ->
            Column(Modifier.fillMaxWidth()) {
                if (current == "echobrain") {
                    EchoBrainSection()
                } else {
                    MemoryFeedback(vm)
                    if (!vm.connected) {
                        MemoryConnectionForm(
                            key = key,
                            onKeyChange = { key = it },
                            space = space,
                            onSpaceChange = { space = it.trim() },
                            busy = vm.busy,
                            onConnect = { vm.connect(key, space) },
                        )
                    }
                    if (vm.connected) {
                        MemoryConversationSection(vm, onEnableLearning = { consent = true })
                        Spacer(Modifier.height(Spacing.xl))
                        MemoryLibrarySection(onMemories)
                        Spacer(Modifier.height(Spacing.xl))
                        MemoryAccountSection(vm)
                        Spacer(Modifier.height(Spacing.s))
                        TextButton(
                            onClick = { disconnect = true },
                            enabled = !vm.busy,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) { Text("Disconnect Supermemory") }
                    }
                }
            }
        }
    }
    if (consent) {
        AlertDialog(
            onDismissRequest = { consent = false },
            icon = { Icon(Icons.Default.Psychology, null) },
            title = { Text("Let Supermemory learn?") },
            text = {
                Text(
                    "New user messages are sent to your Supermemory account. High-confidence personal facts may be saved immediately; " +
                        "other learning is processed in the background. Assistant replies, attachments, reasoning and tool results are excluded. " +
                        "Relevant memories may be shared with the model answering you. Existing chat history isn't imported. You can turn this off at any time.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.setLearning(true); consent = false },
                    enabled = !vm.busy,
                ) { Text("Enable learning") }
            },
            dismissButton = { TextButton(onClick = { consent = false }) { Text("Not now") } },
        )
    }
    if (disconnect) {
        AlertDialog(
            onDismissRequest = { disconnect = false },
            title = { Text("Disconnect Supermemory?") },
            text = { Text("Stops future memory requests and removes the key from this device. Existing data stays in your Supermemory account.") },
            confirmButton = {
                TextButton(
                    onClick = { vm.disconnect(); disconnect = false },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } },
        )
    }
}


@Composable
private fun MemoryFeedback(vm: MemoryViewModel) {
    if (vm.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.m))
    }
    vm.error?.let { MemoryFeedbackMessage(it, isError = true) }
    vm.notice?.let { MemoryFeedbackMessage(it, isError = false) }
}

@Composable
private fun MemoryFeedbackMessage(message: String, isError: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(message, modifier = Modifier.padding(Spacing.base), style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(Spacing.m))
}
