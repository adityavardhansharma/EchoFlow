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
    onSaveKey: (String) -> Unit,
    onRestoreKey: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showManual by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    FormCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.logo_openrouter), null, Modifier.size(width = 28.dp, height = 20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("OpenRouter", style = MaterialTheme.typography.titleMedium)
                if (connection.connected) {
                    SavedKeyBadge(if (connection.signedIn) "Connected through sign-in" else "Connected with API key")
                } else {
                    Text("One account. Your choice of models.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(Spacing.base))
        Text(
            when {
                connection.signedIn -> "Usage is billed to your OpenRouter account. Your connection is stored securely on this device."
                connection.connected -> "Your saved API key is ready to use. You can keep using it or connect through OpenRouter sign-in."
                else -> "Sign in to use your OpenRouter models and credits. Usage is billed to your OpenRouter account."
            },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connection.connected && connection.keyEnding.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.s))
            Text("Key ending in •••• ${connection.keyEnding}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.l))
        Button(
            onClick = { if (connection.connected) confirmation = "switch" else onSignIn() },
            enabled = !auth.busy,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = Spacing.base, vertical = Spacing.m),
        ) {
            if (auth.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(painterResource(R.drawable.logo_openrouter), null, Modifier.size(width = 25.dp, height = 18.dp))
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
                modifier = Modifier.padding(top = Spacing.m), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel sign-in") }
        } else {
            TextButton(onClick = { showManual = true }, enabled = !auth.busy,
                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (connection.connected) "Use a different API key" else "Or add API key manually")
            }
        }
        if (auth.message != null) {
            Text(auth.message, modifier = Modifier.padding(top = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall,
                color = if (auth.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (connection.connected) {
            HorizontalDivider(Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
            if (connection.hasSavedManualKey) {
                Text("Your previous manual key is saved on this device.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirmation = "restore" }, enabled = !auth.busy) { Text("Use saved manual key") }
            }
            TextButton(onClick = { confirmation = "disconnect" }, enabled = !auth.busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Disconnect")
            }
        }
    }
    if (showManual) {
        OpenRouterManualKeyDialog(
            replacing = connection.connected,
            onDismiss = { showManual = false },
            onSave = { showManual = false; onSaveKey(it) },
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
private fun OpenRouterManualKeyDialog(replacing: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    // Do not save credentials in Compose saved-instance state or prefill an existing secret.
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val trimmed = key.trim()
    val valid = trimmed.startsWith("sk-or-") && trimmed.length > 16 && trimmed.none { it.isWhitespace() }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, null) },
        title = { Text(if (replacing) "Replace API key" else "Add API key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(if (replacing) "Saving replaces the active connection and any saved manual key on this device."
                    else "Paste a key from openrouter.ai/keys. It will be stored securely on this device.")
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("OpenRouter API key") },
                    placeholder = { Text("sk-or-v1-…") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = trimmed.isNotEmpty() && !valid,
                    supportingText = { if (trimmed.isNotEmpty() && !valid) Text("Enter a complete OpenRouter API key.") },
                    trailingIcon = { IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (visible) "Hide API key" else "Show API key")
                    } },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(trimmed) }, enabled = valid) { Text("Save key") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
