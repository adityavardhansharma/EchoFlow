@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.ui.theme.Spacing
import java.text.NumberFormat
import java.util.Locale

/**
 * The connected account at a glance, leading the page: who you're connected to, the plan, the
 * memory space, and usage as a wavy meter when Supermemory reports a limit.
 */
@Composable
internal fun MemoryAccountSection(vm: MemoryViewModel) {
    val uriHandler = LocalUriHandler.current
    val billing = vm.billing
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val cs = MaterialTheme.colorScheme
    val plan = billing?.plan?.takeUnless { it == "unknown" }?.replaceFirstChar { it.uppercase() }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemoryMark(
                    Icons.Default.Psychology, MaterialShapes.Cookie9Sided,
                    container = cs.primary, onContainer = cs.onPrimary, size = 56.dp,
                )
                Spacer(Modifier.width(Spacing.base))
                Column(Modifier.weight(1f)) {
                    Text("Supermemory", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        StatusPill("Connected", cs.tertiaryContainer, cs.onTertiaryContainer, dot = cs.tertiary)
                        if (plan != null) StatusPill("$plan plan", cs.secondaryContainer, cs.onSecondaryContainer)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
            ) {
                Row(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Workspaces, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.m))
                    Text("Memory space", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(
                        vm.settings.space,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(Modifier.padding(top = Spacing.l)) {
                if (billing?.used != null && billing.limit != null && billing.limit > 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Usage", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${currency.format(billing.used)} of ${currency.format(billing.limit)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    LinearWavyProgressIndicator(
                        progress = { (billing.used / billing.limit).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
                    )
                    Text(
                        buildString {
                            append("${currency.format((billing.limit - billing.used).coerceAtLeast(0.0))} left")
                            billing.reset?.let { append(" · resets $it") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                } else {
                    Text("Usage", style = MaterialTheme.typography.titleSmall)
                    Text(
                        billing?.used?.let { "${currency.format(it)} used · no fixed limit reported" }
                            ?: vm.billingNote ?: "Your API key doesn't expose a usage balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    billing?.reset?.let {
                        Text("Resets $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.l),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                FilledTonalButton(
                    onClick = vm::refreshBilling,
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.s))
                    Text("Refresh")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri(SUPERMEMORY_CONSOLE_URL) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Dashboard")
                    Spacer(Modifier.width(Spacing.s))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, container: Color, content: Color, dot: Color? = null) {
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = Spacing.s, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
