@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.echoflow.ui.screens.settings

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
    var tab by rememberSaveable { mutableStateOf(0) }
    var key by remember { mutableStateOf("") } // Never save API keys in instance state.
    var space by rememberSaveable { mutableStateOf(vm.settings.space) }
    var consent by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    LaunchedEffect(vm.connected) { if (vm.connected) key = "" }
    LaunchedEffect(Unit) { if (vm.connected) vm.refreshBilling() }
    val context = LocalContext.current
    SettingsPageScaffold("Memory", "A little continuity, on your terms", onBack) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Supermemory", "EchoBrain").forEachIndexed { i, label ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (tab == 1) {
            MemoryBlock("EchoBrain", "Coming soon") {
                Text("A future memory system built into EchoFlow. Nothing to connect yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            MemoryBlock(if (vm.connected) "Connected to Supermemory" else "Bring your own memory", if (vm.connected) vm.settings.space else "Your account. Your API key.") {
                if (!vm.connected) {
                    Text("Recall relevant details when they're useful, without loading your entire memory into every chat.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(key, { key = it }, label = { Text("Supermemory API key") }, singleLine = true, enabled = !vm.busy,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, autoCorrectEnabled = false),
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(space, { space = it.trim() }, label = { Text("Memory space") }, singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text("Use the same space to share memory across your devices.") })
                    Text("Connecting sends a profile request. Learning stays off until you enable it.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.connect(key, space) }, enabled = !vm.busy && key.isNotBlank()) { Text("Connect") }
                } else {
                    val billing = vm.billing
                    Text(billing?.plan?.takeUnless { it == "unknown" }?.replaceFirstChar { it.uppercase() }?.let { "$it account" } ?: "Account connected", style = MaterialTheme.typography.titleMedium)
                    if (billing?.used != null && billing.limit != null && billing.limit > 0) {
                        LinearProgressIndicator(progress = { (billing.used / billing.limit).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        val currency = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US)
                        Text("${currency.format(billing.used)} used · ${currency.format((billing.limit - billing.used).coerceAtLeast(0.0))} left", style = MaterialTheme.typography.bodySmall)
                    } else if (billing?.used != null) Text("${java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(billing.used)} used · no fixed limit reported", style = MaterialTheme.typography.bodySmall)
                    else Text(vm.billingNote ?: "Your API key doesn't expose a usage balance.", style = MaterialTheme.typography.bodySmall)
                    billing?.reset?.let { Text("Resets $it", style = MaterialTheme.typography.bodySmall) }
                    Row {
                        TextButton(onClick = { vm.refreshBilling() }, enabled = !vm.busy) { Text("Refresh") }
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.supermemory.ai"))) }) { Text("Dashboard ↗") }
                    }
                }
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
                TextButton(onClick = { disconnect = true }, enabled = !vm.busy) { Text("Disconnect", color = MaterialTheme.colorScheme.error) }
            }
            MemoryFeedback(vm)
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
