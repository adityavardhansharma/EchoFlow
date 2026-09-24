@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.echoflow.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoflow.ui.theme.Spacing

/**
 * Memory settings. The Supermemory / EchoBrain switch is the same connected toggle the Models and
 * Dictation pages use; below it, Supermemory is either a guided connect flow or, once connected, a
 * dashboard — account, library, conversation controls, learning and notes — in that order.
 */
@Composable
internal fun MemoryPage(onBack: () -> Unit, onMemories: () -> Unit, vm: MemoryViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf("supermemory") }
    var key by remember { mutableStateOf("") } // Never save API keys in instance state.
    var space by rememberSaveable { mutableStateOf(vm.settings.space) }
    var consent by remember { mutableStateOf(false) }
    var disconnect by remember { mutableStateOf(false) }
    LaunchedEffect(vm.connected) { if (vm.connected) key = "" }
    LaunchedEffect(Unit) { if (vm.connected) vm.refreshBilling() }
    SettingsPageScaffold(
        "Memory",
        if (vm.connected) "Supermemory is connected" else "A little continuity, on your terms",
        onBack,
    ) {
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
                    EchoBrainSection(onUseSupermemory = { tab = "supermemory" })
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
                    } else {
                        MemoryAccountSection(vm)
                        Spacer(Modifier.height(Spacing.m))
                        MemoryLibrarySection(onMemories)
                        Spacer(Modifier.height(Spacing.xl))
                        MemoryConversationSection(vm, onEnableLearning = { consent = true })
                        Spacer(Modifier.height(Spacing.xl))
                        MemoryNotesSection()
                        Spacer(Modifier.height(Spacing.xl))
                        OutlinedButton(
                            onClick = { disconnect = true },
                            enabled = !vm.busy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Default.LinkOff, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.s))
                            Text("Disconnect Supermemory")
                        }
                    }
                }
            }
        }
    }
    if (consent) {
        LearningConsentDialog(
            busy = vm.busy,
            onConfirm = { vm.setLearning(true); consent = false },
            onDismiss = { consent = false },
        )
    }
    if (disconnect) {
        AlertDialog(
            onDismissRequest = { disconnect = false },
            icon = { Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Disconnect Supermemory?") },
            text = {
                Text("Stops future memory requests and removes the key from this device. Everything already in your Supermemory account stays there.")
            },
            confirmButton = {
                Button(
                    onClick = { vm.disconnect(); disconnect = false },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { disconnect = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Consent for learning, laid out as what is sent and what never is — scannable in two glances
 * instead of one dense paragraph. The wording carries every commitment the old copy made.
 */
@Composable
private fun LearningConsentDialog(busy: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            MemoryMark(
                Icons.Default.Psychology, MaterialShapes.Cookie9Sided,
                container = MaterialTheme.colorScheme.primaryContainer,
                onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 56.dp,
            )
        },
        title = { Text("Let Supermemory learn?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ConsentLine(Icons.Default.CheckCircle, "Your new messages are sent to your Supermemory account. Clear personal facts may be saved right away; the rest is learned in the background.")
                ConsentLine(Icons.Default.CheckCircle, "Relevant memories may be shared with the model answering you.")
                ConsentLine(Icons.Default.Block, "Assistant replies, attachments, reasoning and tool results are never sent. Existing chat history isn't imported.")
                Text(
                    "You can turn this off at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm, enabled = !busy) { Text("Enable learning") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun ConsentLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.m))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Work in flight as a wavy line; errors and confirmations as dismissible tonal banners. */
@Composable
private fun MemoryFeedback(vm: MemoryViewModel) {
    MemoryBusyLine(vm.busy)
    vm.error?.let {
        MemoryBanner(it, MemoryTone.Error, onDismiss = vm::clearFeedback)
        Spacer(Modifier.height(Spacing.m))
    }
    vm.notice?.let {
        MemoryBanner(it, MemoryTone.Success, onDismiss = vm::clearFeedback)
        Spacer(Modifier.height(Spacing.m))
    }
}
