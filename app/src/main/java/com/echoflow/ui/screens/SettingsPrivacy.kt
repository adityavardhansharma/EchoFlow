@file:OptIn(ExperimentalMaterial3Api::class)

package com.echoflow.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.echoflow.ui.RestoreUiState
import com.echoflow.ui.SettingsViewModel
import com.echoflow.ui.theme.Spacing

/**
 * Privacy page (under Echo Labs): explains, in plain language, that EchoFlow keeps everything on
 * the phone and what each provider sees — and hosts the opt-in encrypted, uninstall-surviving
 * backup. The backup is locked with a write-once passkey; see [SettingsViewModel] / BackupManager.
 */
@Composable
internal fun PrivacyPage(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val backupEnabled by viewModel.backupEnabled.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()

    // The encrypted backup relies on modern scoped storage; require Android 13+ so behaviour is
    // consistent. On older phones the controls stay visible but explain why they're unavailable.
    val backupSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var showSetPasskey by remember { mutableStateOf(false) }
    var showTurnOffConfirm by remember { mutableStateOf(false) }
    var showUnsupported by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val pickBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestoreUri = uri }

    // A successful restore means the on-disk data changed under our cached state — restart so
    // every ViewModel and StateFlow re-reads it cleanly.
    LaunchedEffect(restoreState) {
        if (restoreState is RestoreUiState.Done) restartApp(context)
    }

    SettingsPageScaffold(title = "Privacy", subtitle = "Your data, and where it goes", onBack = onBack) {
        FormCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Spacing.s))
                Text("On your phone, and yours", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                "EchoFlow has no servers of its own. Your conversations live in a database on this " +
                    "device, and your API keys are stored encrypted with the phone's hardware-backed " +
                    "keystore (AES-256). There is no EchoFlow account and nothing is uploaded to us — " +
                    "there is no \"us\" to upload it to.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.xl))
        PageSection("When you use a cloud model", "Your request goes straight from your phone to the provider you set up")
        InfoCard(
            "To write a reply, the app sends your conversation directly to whichever provider you " +
                "configured — never through an EchoFlow server. What that provider keeps is set by " +
                "their policy, not ours:\n\n" +
                "• OpenRouter routes your request to the underlying model. It supports zero-data-" +
                "retention (ZDR) routing, but not every model or provider offers it — you control " +
                "this in your OpenRouter account.\n" +
                "• Direct provider keys (OpenAI, Anthropic, Google, Cerebras, xAI, Ollama, or any " +
                "OpenAI-compatible endpoint) send your data straight to that provider under their " +
                "terms. Some don't retain or train on API data; others do — check the one you use."
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Web search", "Only when a search provider is turned on")
        InfoCard(
            "With Exa, Parallel, or Firecrawl enabled, your search query — and the page text they " +
                "fetch for you — passes through that service to bring results back. With search off, " +
                "nothing is sent to them."
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Local models", "Fully offline")
        InfoCard(
            "On-device models run entirely on your phone. Your messages never leave the device and " +
                "work with no connection at all."
        )

        Spacer(Modifier.height(Spacing.xl))
        PageSection("Keep my data if I uninstall", "Off by default")
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(Spacing.base), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Encrypted backup on this phone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Survives uninstall · unlocked only by your passkey",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Switch(
                    checked = backupEnabled,
                    onCheckedChange = { on ->
                        when {
                            !backupSupported -> showUnsupported = true
                            on && viewModel.hasBackupPasskey() -> viewModel.reEnableBackup()
                            on -> showSetPasskey = true
                            else -> showTurnOffConfirm = true
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(Spacing.m))
        InfoCard(
            "When on, EchoFlow writes an encrypted copy of your chats and keys to " +
                "Downloads/EchoFlow on this phone, locked with a passkey only you know. Because it " +
                "lives outside the app, it survives uninstalling EchoFlow: reinstall, tap Recover " +
                "old data, and enter your passkey to bring everything back. It never goes to any " +
                "server. Your passkey is set once and can't be changed — and if you forget it, the " +
                "backup can't be opened, not even by us."
        )

        if (!backupSupported) {
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Requires Android 13 or newer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }

        Spacer(Modifier.height(Spacing.l))
        OutlinedButton(
            onClick = {
                if (!backupSupported) {
                    showUnsupported = true
                } else {
                    viewModel.clearRestoreState()
                    pickBackup.launch(arrayOf("*/*"))
                }
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.s))
            Text("Recover old data")
        }
        Text(
            "Pick your echoflow-backup.efbak file, then enter your passkey. If there's no backup, " +
                "just carry on — EchoFlow starts fresh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.s, start = Spacing.xs, end = Spacing.xs),
        )
    }

    if (showSetPasskey) {
        SetPasskeyDialog(
            onDismiss = { showSetPasskey = false },
            onConfirm = { passkey ->
                viewModel.enableBackupWithPasskey(passkey)
                showSetPasskey = false
            },
        )
    }

    if (showUnsupported) {
        AlertDialog(
            onDismissRequest = { showUnsupported = false },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Needs Android 13 or newer") },
            text = {
                Text(
                    "The encrypted, uninstall-surviving backup requires Android 13 or newer, so it's " +
                        "not available on this phone. Everything else on this page still applies — your " +
                        "data stays on the device either way.",
                )
            },
            confirmButton = { TextButton(onClick = { showUnsupported = false }) { Text("OK") } },
        )
    }

    if (showTurnOffConfirm) {
        AlertDialog(
            onDismissRequest = { showTurnOffConfirm = false },
            icon = { Icon(Icons.Default.CloudOff, null) },
            title = { Text("Turn off and delete the backup?") },
            text = {
                Text(
                    "The encrypted backup file on this phone will be deleted. Your chats and keys " +
                        "stay in the app as normal. Your passkey is remembered, so turning this back " +
                        "on later uses the same one.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.disableBackup(); showTurnOffConfirm = false }) {
                    Text("Turn off")
                }
            },
            dismissButton = { TextButton(onClick = { showTurnOffConfirm = false }) { Text("Cancel") } },
        )
    }

    pendingRestoreUri?.let { uri ->
        RestorePasskeyDialog(
            restoreState = restoreState,
            onDismiss = {
                pendingRestoreUri = null
                viewModel.clearRestoreState()
            },
            onConfirm = { passkey -> viewModel.restoreFrom(uri, passkey) },
        )
    }
}

/** Plain informational slab used for the explainer sections. */
@Composable
private fun InfoCard(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.base),
        )
    }
}

/** First-time enable: choose the write-once passkey (entered twice), with a clear warning. */
@Composable
private fun SetPasskeyDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var passkey by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val longEnough = passkey.length >= 6
    val matches = passkey.isNotBlank() && passkey == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text("Set your backup passkey") },
        text = {
            Column {
                Text(
                    "This locks your backup. It's set once and can't be changed later. If you " +
                        "forget it, the backup can't be recovered — write it down somewhere safe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = passkey,
                    onValueChange = { passkey = it },
                    singleLine = true,
                    label = { Text("Passkey") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    singleLine = true,
                    label = { Text("Confirm passkey") },
                    isError = confirm.isNotEmpty() && confirm != passkey,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (passkey.isNotEmpty() && !longEnough) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "Use at least 6 characters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passkey) }, enabled = longEnough && matches) {
                Text("Turn on")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Recovery: enter the passkey for the picked backup file; shows progress and errors inline. */
@Composable
private fun RestorePasskeyDialog(
    restoreState: RestoreUiState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passkey by remember { mutableStateOf("") }
    val working = restoreState is RestoreUiState.Working || restoreState is RestoreUiState.Done
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(Icons.Default.Key, null) },
        title = { Text("Enter your passkey") },
        text = {
            Column {
                Text(
                    "Unlock and restore the backup you picked. This adds its chats and keys to this " +
                        "install, then restarts EchoFlow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = passkey,
                    onValueChange = { passkey = it },
                    singleLine = true,
                    enabled = !working,
                    label = { Text("Passkey") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                (restoreState as? RestoreUiState.Failed)?.let {
                    Spacer(Modifier.height(Spacing.s))
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (working) {
                    Spacer(Modifier.height(Spacing.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.s))
                        Text("Restoring…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(passkey) }, enabled = passkey.isNotBlank() && !working) {
                Text("Recover")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

/** Fully relaunch the app so all singletons and cached flows re-read restored data. */
private fun restartApp(context: Context) {
    val ctx = context.applicationContext
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}
