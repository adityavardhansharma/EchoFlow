package com.echoflow.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun MemoryAccountSection(vm: MemoryViewModel) {
    val uriHandler = LocalUriHandler.current
    val billing = vm.billing
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    PageSection("Account", "Supermemory connection and usage")
    FormCard {
        SavedKeyBadge("Connected to Supermemory")
        Spacer(Modifier.height(Spacing.m))
        Text(
            billing?.plan?.takeUnless { it == "unknown" }?.replaceFirstChar { it.uppercase() }
                ?.let { "$it account" } ?: "Account connected",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Memory space: ${vm.settings.space}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.l))
        if (billing?.used != null && billing.limit != null && billing.limit > 0) {
            LinearProgressIndicator(
                progress = { (billing.used / billing.limit).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                "${currency.format(billing.used)} used · ${currency.format((billing.limit - billing.used).coerceAtLeast(0.0))} left",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                billing?.used?.let { "${currency.format(it)} used · no fixed limit reported" }
                    ?: vm.billingNote ?: "Your API key doesn't expose a usage balance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        billing?.reset?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text("Resets $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            FilledTonalButton(onClick = vm::refreshBilling, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Refresh")
            }
            OutlinedButton(
                onClick = { uriHandler.openUri("https://console.supermemory.ai") },
                modifier = Modifier.weight(1f),
            ) {
                Text("Dashboard")
                Spacer(Modifier.width(Spacing.xs))
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
            }
        }
    }
}
