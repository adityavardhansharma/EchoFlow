@file:OptIn(ExperimentalLayoutApi::class)

package com.echoflow.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.echoflow.R
import com.echoflow.data.OpenRouterConnection
import com.echoflow.ui.OpenRouterAuthState
import com.echoflow.ui.theme.Spacing

@Composable
internal fun OpenRouterConnectionCard(
    connection: OpenRouterConnection,
    auth: OpenRouterAuthState,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onSaveKey: (String, () -> Unit) -> Unit,
    onRestoreKey: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    val compactTextPadding = PaddingValues(horizontal = Spacing.s, vertical = Spacing.xs)
    FormCard(contentPadding = Spacing.m) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.logo_openrouter), null, Modifier.size(width = 24.dp, height = 16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("OpenRouter", style = MaterialTheme.typography.titleSmall)
                if (connection.connected) {
                    SavedKeyBadge(if (connection.signedIn) "Connected through sign-in" else "Connected with API key")
                    if (connection.keyEnding.isNotEmpty()) {
                        Text("Key ending in •••• ${connection.keyEnding}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text("One account. Your choice of models.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!connection.connected) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Sign in to use your OpenRouter models and credits. Usage is billed to your OpenRouter account.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.s))
        Button(
            onClick = { if (connection.connected) confirmation = "switch" else onSignIn() },
            enabled = !auth.busy,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.s),
        ) {
            if (auth.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.logo_openrouter), null, Modifier.size(width = 22.dp, height = 16.dp))
            Spacer(Modifier.width(Spacing.s))
            Text(when {
                auth.waitingForBrowser -> "Waiting for OpenRouter…"
                auth.busy -> "Saving connection…"
                connection.signedIn -> "Switch OpenRouter account"
                else -> "Sign in with OpenRouter"
            })
        }
        if (auth.waitingForBrowser) {
            Text("Finish signing in in your browser, then return here. If the app restarts, start sign-in again.",
                modifier = Modifier.padding(top = Spacing.s), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (auth.message != null) {
            Text(auth.message, modifier = Modifier.padding(top = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (auth.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.Center,
            ) {
                if (auth.waitingForBrowser) {
                    TextButton(onClick = onCancel, contentPadding = compactTextPadding) { Text("Cancel sign-in") }
                } else {
                    TextButton(onClick = { showManual = true }, enabled = !auth.busy, contentPadding = compactTextPadding) {
                        Text(if (connection.connected) "Use a different API key" else "Or add API key manually")
                    }
                }
                if (connection.connected) {
                    TextButton(
                        onClick = { confirmation = "disconnect" },
                        enabled = !auth.busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = compactTextPadding,
                    ) { Text("Disconnect") }
                }
            }
            if (connection.connected && connection.hasSavedManualKey) {
                TextButton(
                    onClick = { confirmation = "restore" },
                    enabled = !auth.busy,
                    contentPadding = compactTextPadding,
                ) { Text("Use saved manual key") }
            }
        }
    }
    if (showManual) {
        OpenRouterManualKeyDialog(
            replacing = connection.connected,
            saving = auth.busy,
            error = auth.message.takeIf { auth.error },
            onDismiss = { showManual = false },
            onSave = { key -> onSaveKey(key) { showManual = false } },
        )
    }
    if (confirmation != null) {
        val action = confirmation
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(when (action) {
                "disconnect" -> "Disconnect OpenRouter?"
                "restore" -> "Use your saved manual key?"
                else -> "Switch OpenRouter connection?"
            }) },
            text = { Text(when (action) {
                "disconnect" -> "This removes your OpenRouter connection and any saved manual key from this device. Your models and chats stay here. To revoke a key on OpenRouter, visit openrouter.ai/keys."
                "restore" -> "Your saved manual key will become the active connection. The sign-in key will be removed from this device."
                else -> if (connection.signedIn) "Your current connection stays active until the new sign-in succeeds."
                    else "Your current API key stays active until sign-in succeeds. It will remain saved so you can switch back later."
            }) },
            confirmButton = { TextButton(onClick = {
                confirmation = null
                when (action) { "disconnect" -> onDisconnect(); "restore" -> onRestoreKey(); else -> onSignIn() }
            }) { Text(if (action == "disconnect") "Disconnect" else "Continue") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun OpenRouterManualKeyDialog(
    replacing: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    // Do not save credentials in Compose saved-instance state or prefill an existing secret.
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val trimmed = key.trim()
    val valid = trimmed.startsWith("sk-or-") && trimmed.length > 16 && trimmed.none { it.isWhitespace() }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Default.Key, null) },
        title = { Text(if (replacing) "Replace API key" else "Add API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(if (replacing) "Saving replaces the active connection and any saved manual key on this device."
                    else "Paste a key from openrouter.ai/keys. It will be stored securely on this device.")
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("OpenRouter API key") },
                    enabled = !saving,
                    placeholder = { Text("sk-or-v1-…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = trimmed.isNotEmpty() && !valid,
                    supportingText = { if (trimmed.isNotEmpty() && !valid) Text("Enter a complete OpenRouter API key.") },
                    trailingIcon = { IconButton(onClick = { visible = !visible }, enabled = !saving) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (visible) "Hide API key" else "Show API key")
                    } },
                )
                if (attempted && error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
        },
        confirmButton = { TextButton(onClick = { attempted = true; onSave(trimmed) }, enabled = valid && !saving) {
            Text(if (saving) "Saving…" else "Save key")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
