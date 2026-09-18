@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.echoflow.ui.theme.Spacing
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Psychology
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun MemoryPage(onBack: () -> Unit, onMemories: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf("supermemory") }
    var key by remember { mutableStateOf("") } // Never save API keys in instance state.
    var space by rememberSaveable { mutableStateOf(vm.settings.space) }
    var consent by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    LaunchedEffect(vm.connected) { if (vm.connected) key = "" }
    LaunchedEffect(Unit) { if (vm.connected) vm.refreshBilling() }
    val context = LocalContext.current
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
            MemoryBlock("EchoBrain", "Coming soon") {
                Text("A future memory system built into EchoFlow. Nothing to connect yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
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
                Spacer(Modifier.height(16.dp))
                MemoryBlock("In your conversations", "You decide what crosses the boundary") {
                    MemorySwitch("Use memory", "Let supported models recall relevant details and save durable personal facts you clearly state.", vm.recall, !vm.busy, vm::updateRecall)
                    HorizontalDivider()
                    MemorySwitch("Learn from conversations", "Send new chat text to Supermemory to learn lasting preferences, facts and projects.", vm.learn, !vm.busy) { if (it) consent = true else vm.setLearning(false) }
                    if (vm.learn) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text("Learning status", style = MaterialTheme.typography.labelLarge)
                                Text(vm.learningStatus.summary, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = vm::refreshLearningStatus, enabled = !vm.busy) {
                                Icon(Icons.Default.Refresh, "Refresh learning status")
                            }
                        }
                        FilledTonalButton(
                            onClick = vm::learnNow,
                            enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Learn queued chats now") }
                        Text(
                            "Processes eligible completed chats now instead of waiting for Supermemory's normal batch threshold. Unchanged chats are not uploaded twice.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                    MemorySwitch("Include on-device chats", "Allows eligible local chat text to leave your device for Supermemory.", vm.local, !vm.busy, vm::updateLocal)
                }
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onMemories, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("My Memories →") }
                Text("Review what's known, add a fact, or forget something.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                Text("Memory is available in standard chats with tool-capable models. Local models and specialised modes may not support automatic recall. Never store passwords or API keys.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.learningNote.isNotBlank()) {
                    Text(vm.learningNote, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = vm::retryLearning, enabled = vm.learn && !vm.busy) { Text("Retry learning") }
                }
                Text("Deleting a chat only removes its local copy. Manage already-uploaded source conversations in Supermemory. Chats used with local models while cloud memory is off are excluded from learning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xl))
                MemoryAccountSection(vm)
                TextButton(onClick = { disconnect = true }, enabled = !vm.busy) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
            MemoryFeedback(vm)
        }
            }
        }
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Let Supermemory learn?") },
        text = { Text("New user messages are sent to your Supermemory account. High-confidence personal facts may be saved immediately; other learning is processed in the background. Assistant replies, attachments, reasoning and tool results are excluded. Relevant memories may be shared with the model answering you. Existing chat history isn't imported. You can turn this off at any time.") },
        confirmButton = { TextButton(onClick = { vm.setLearning(true); consent = false }) { Text("Enable learning") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("Not now") } })
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Supermemory?") },
        text = { Text("Stops future memory requests and removes the key from this device. Existing data stays in your Supermemory account.") },
        confirmButton = { TextButton(onClick = { vm.disconnect(); disconnect = false }) { Text("Disconnect") } }, dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } })
}


@Composable private fun MemoryFeedback(vm: MemoryViewModel) {
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
    vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) }
}
@Composable private fun MemoryBlock(title: String?, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}
@Composable private fun MemorySwitch(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange, enabled = enabled)
    }
}
