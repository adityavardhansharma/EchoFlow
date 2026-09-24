@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.GroupedItemGap
import com.echoflow.ui.components.groupedItemShape
import com.echoflow.ui.theme.Spacing

/** Where a Supermemory API key is created. */
internal const val SUPERMEMORY_CONSOLE_URL = "https://console.supermemory.ai"

/**
 * The not-yet-connected state: a hero that says what memory is for, three short steps to get
 * there, then the form itself. The order is deliberate — you learn why before you're asked for a key.
 */
@Composable
internal fun MemoryConnectionForm(
    key: String,
    onKeyChange: (String) -> Unit,
    space: String,
    onSpaceChange: (String) -> Unit,
    busy: Boolean,
    onConnect: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var keyVisible by remember { mutableStateOf(false) }
    val canConnect = !busy && key.isNotBlank() && space.isNotBlank()

    ConnectHero()
    Spacer(Modifier.height(Spacing.xl))

    MemorySectionHeader("Get connected", supporting = "Your account, your key — about a minute")
    Column(verticalArrangement = Arrangement.spacedBy(GroupedItemGap)) {
        ConnectStep(
            number = 1, count = 3,
            title = "Create an API key",
            detail = "In the Supermemory console, under API keys.",
            action = {
                TextButton(onClick = { uriHandler.openUri(SUPERMEMORY_CONSOLE_URL) }) {
                    Text("Open")
                    Spacer(Modifier.width(Spacing.xs))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                }
            },
        )
        ConnectStep(number = 2, count = 3, title = "Paste it below", detail = "The key is kept on this device.")
        ConnectStep(number = 3, count = 3, title = "Choose what it learns", detail = "Learning stays off until you turn it on.")
    }
    Spacer(Modifier.height(Spacing.xl))

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = space,
                onValueChange = onSpaceChange,
                label = { Text("Memory space") },
                leadingIcon = { Icon(Icons.Outlined.Workspaces, null) },
                supportingText = { Text("Use the same space on every device to share one memory.") },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                singleLine = true,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onConnect,
                enabled = canConnect,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (busy) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Spacing.s))
                    Text("Connecting…", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Connect", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.m))
    Row(Modifier.padding(horizontal = Spacing.s), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Default.Lock, null,
            Modifier.padding(top = 2.dp).size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.s))
        Text(
            "Connecting sends one profile request to Supermemory to check the key. Nothing from your chats is sent until you allow it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The pitch: what memory does for you, in the brand's primary container with a shaped mark. */
@Composable
private fun ConnectHero() {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.primaryContainer,
        contentColor = cs.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            MemoryMark(
                Icons.Default.Psychology, MaterialShapes.Cookie9Sided,
                container = cs.primary, onContainer = cs.onPrimary, size = 64.dp,
            )
            Text(
                "SUPERMEMORY",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.l),
            )
            Text(
                "Give EchoFlow a memory",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                "Bring the details that matter — your preferences, people and projects — into conversations, without loading your whole history into every chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

/** A numbered step in the grouped "get connected" list, with an optional trailing action. */
@Composable
private fun ConnectStep(
    number: Int,
    count: Int,
    title: String,
    detail: String,
    action: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = groupedItemShape(number - 1, count),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 68.dp).padding(start = Spacing.base, end = Spacing.s, top = Spacing.m, bottom = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(Spacing.base))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            if (action != null) action()
        }
    }
}
