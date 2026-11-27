package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.echoflow.ui.components.MarkdownExtensionBlock
import com.echoflow.ui.theme.Spacing
import com.echoflow.weave.compose.LocalWeaveRuntime
import com.echoflow.weave.compose.WeaveBlock
import com.echoflow.weave.compose.WeaveHostLayer
import com.echoflow.weave.compose.tokens.TokenResolver
import com.echoflow.weave.runtime.WeaveRuntime

/** "Made for you" (01 §23.5): kept blocks, each running standalone with its saved state. */
@Composable
internal fun WeaveLibraryPage(onBack: () -> Unit) {
    WeaveHostLayer(onSay = {}) {
        val runtime = LocalWeaveRuntime.current ?: return@WeaveHostLayer
        val rows by remember(runtime) { runtime.observeLibrary() }.collectAsState(initial = emptyList())
        val context = LocalContext.current
        SettingsPageScaffold(title = "Made for you", subtitle = "Blocks you kept from chats", onBack = onBack) {
            if (rows.isEmpty()) {
                Text("Keep a block from its ⋯ menu to find it here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                rows.forEach { row ->
                    var open by remember(row.id) { mutableStateOf(false) }
                    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.fillMaxWidth().padding(start = Spacing.base, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(TokenResolver.icon(row.icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(row.title, Modifier.weight(1f).padding(start = Spacing.s), style = MaterialTheme.typography.titleSmall)
                            IconButton({ com.echoflow.weave.widget.WeaveWidgets.requestPin(context, row.id) }) { Icon(TokenResolver.icon("widgets"), "Pin as widget") }
                            IconButton({ open = !open }) { Icon(TokenResolver.icon(if (open) "expand_less" else "expand_more"), if (open) "Close" else "Open") }
                            IconButton({ runtime.deleteFromLibrary(row.id) }) { Icon(TokenResolver.icon("delete"), "Remove") }
                        }
                        if (open) Column(Modifier.padding(horizontal = Spacing.base).padding(bottom = Spacing.base)) {
                            val key = WeaveRuntime.LIBRARY + row.id
                            WeaveBlock(runtime.controller(key, null, 0), MarkdownExtensionBlock(0, "weave", "weave", row.source, true))
                        }
                    }
                }
            }
        }
    }
}
