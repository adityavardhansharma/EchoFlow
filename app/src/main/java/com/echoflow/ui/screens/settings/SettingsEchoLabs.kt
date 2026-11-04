@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoflow.ui.chat.AskEchoFlowSelection
import com.echoflow.ui.lock.LocalAppLock
import com.echoflow.ui.lock.LockAfter
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

// ── Echo Adviser ──────────────────────────────────────────────────────────────────────

/** Master on/off card shown at the top of each Echo Labs feature page. */
@Composable
internal fun LabMasterToggle(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/** Small info/empty slab used on the Echo Adviser/Fusion pages. */
@Composable
internal fun EchoNoticeCard(icon: ImageVector, text: String, error: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(28.dp), tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.s))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Name-entry dialog used to finish creating an advisor profile or a fusion panel. */
@Composable
internal fun EchoNamePromptDialog(
    title: String,
    supporting: String,
    defaultName: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(defaultName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AutoAwesome, null) },
        title = { Text(title) },
        text = {
            Column {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.trim().isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Beta: adds "Ask EchoFlow" to other apps' text-selection menu, next to Copy and Select all. */
@Composable
internal fun AskEchoFlowRow(index: Int, count: Int) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AskEchoFlowSelection.isEnabled(context)) }
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ask EchoFlow", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Select text in any app and tap Ask EchoFlow to open it in a new chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.s))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    AskEchoFlowSelection.setEnabled(context, it)
                    enabled = it
                },
            )
        }
    }
}

/**
 * Beta: locks EchoFlow behind the phone's own fingerprint, face or screen lock (see AppLock).
 * Turning it on asks for that unlock first, so it can't be switched on in a state it can't open.
 */
@Composable
internal fun AppLockRow(index: Int, count: Int) {
    val appLock = LocalAppLock.current ?: return
    val enabled by appLock.enabled.collectAsState()
    val lockAfter by appLock.lockAfter.collectAsState()
    var needsScreenLock by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(
        shape = groupedItemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("App lock", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Open EchoFlow with your fingerprint, face or screen lock. Chats are hidden in Recents while it's on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        if (!on) appLock.disable()
                        else if (!appLock.deviceSecure()) needsScreenLock = true
                        else appLock.enable {}
                    },
                )
            }
            if (enabled) {
                Spacer(Modifier.height(Spacing.m))
                Text("Lock after", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.s))
                ConnectedToggleRow(
                    options = LockAfter.entries.map { it.storageKey to it.label },
                    selected = lockAfter.storageKey,
                    onSelect = { appLock.setLockAfter(LockAfter.fromStorage(it)) },
                )
            }
        }
    }
    if (needsScreenLock) {
        AlertDialog(
            onDismissRequest = { needsScreenLock = false },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Set a screen lock first") },
            text = { Text("App lock uses your phone's own PIN, pattern or password, with fingerprint or face on top. Add one in your phone's security settings, then turn this on.") },
            confirmButton = {
                TextButton(onClick = {
                    needsScreenLock = false
                    runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { needsScreenLock = false }) { Text("Not now") } },
        )
    }
}
