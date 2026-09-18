package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.echoflow.ui.theme.Spacing

@Composable
internal fun MemoryConnectionForm(
    key: String,
    onKeyChange: (String) -> Unit,
    space: String,
    onSpaceChange: (String) -> Unit,
    busy: Boolean,
    onConnect: () -> Unit,
) {
    var keyVisible by remember { mutableStateOf(false) }
    PageSection("Connect Supermemory", "Your account. Your API key.")
    FormCard {
        Text(
            "Bring useful details into your conversations without loading your entire memory into every chat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            label = { Text("Supermemory API key") },
            leadingIcon = { Icon(Icons.Default.Key, null) },
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (keyVisible) "Hide API key" else "Show API key",
                    )
                }
            },
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            singleLine = true,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.m))
        OutlinedTextField(
            value = space,
            onValueChange = onSpaceChange,
            label = { Text("Memory space") },
            supportingText = { Text("Use the same space to share memory across your devices.") },
            singleLine = true,
            enabled = !busy,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.l))
        Button(
            onClick = onConnect,
            enabled = !busy && key.isNotBlank() && space.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Connecting…" else "Connect") }
    }
    Spacer(Modifier.height(Spacing.m))
    Text(
        "Connecting sends a profile request to Supermemory. Learning stays off until you enable it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}
