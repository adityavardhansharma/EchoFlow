package com.echoflow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoflow.data.BrowserResolver
import com.echoflow.data.BrowserSession
import com.echoflow.data.BrowserStep
import com.echoflow.ui.theme.Spacing

/** Short, legible status label for the pill (collapses the 9 internal statuses). */
fun browserStatusLabel(session: BrowserSession): String = when (session.status) {
    BrowserSession.STATUS_RESOLVING -> "Finding site"
    BrowserSession.STATUS_STARTING -> "Opening"
    BrowserSession.STATUS_RUNNING -> "Running"
    BrowserSession.STATUS_AWAITING_USER -> if (session.pendingKind == BrowserSession.PENDING_DRAFT_CONFIRM) "Confirm" else "Needs you"
    BrowserSession.STATUS_AWAITING_INSTRUCTION -> "Live"
    BrowserSession.STATUS_COMPLETED -> "Done"
    BrowserSession.STATUS_STOPPED -> "Stopped"
    BrowserSession.STATUS_EXPIRED -> "Expired"
    BrowserSession.STATUS_FAILED -> "Error"
    else -> session.status
}

@Composable
private fun browserStatusColor(session: BrowserSession): Color = when (session.status) {
    BrowserSession.STATUS_AWAITING_USER -> MaterialTheme.colorScheme.tertiary
    BrowserSession.STATUS_FAILED -> MaterialTheme.colorScheme.error
    BrowserSession.STATUS_STOPPED, BrowserSession.STATUS_EXPIRED -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}

private fun browserBusy(session: BrowserSession): Boolean =
    session.status == BrowserSession.STATUS_RESOLVING ||
        session.status == BrowserSession.STATUS_STARTING ||
        session.status == BrowserSession.STATUS_RUNNING

@Composable
fun BrowserStatusPill(session: BrowserSession, modifier: Modifier = Modifier) {
    val color = browserStatusColor(session)
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PulsingDot(color = color, animate = browserBusy(session) || session.status == BrowserSession.STATUS_AWAITING_INSTRUCTION)
            Text(
                browserStatusLabel(session),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PulsingDot(color: Color, animate: Boolean, size: androidx.compose.ui.unit.Dp = 8.dp) {
    val alpha = if (animate) {
        val t = rememberInfiniteTransition(label = "dot")
        val a by t.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        )
        a
    } else 1f
    Box(Modifier.size(size).background(color.copy(alpha = alpha), CircleShape))
}

/**
 * The floating "remote browser active" pill shown app-wide while a session is live and the
 * workspace is closed. Privacy-first: the user is never unaware a remote browser is running.
 */
@Composable
fun GlobalBrowserPill(
    session: BrowserSession,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 8.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            PulsingDot(color = MaterialTheme.colorScheme.error, animate = true)
            Column {
                Text(
                    "Remote browser active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                val domain = BrowserResolver.domainOf(session.resolvedUrl)
                Text(
                    if (domain.isNotBlank()) "$domain · ${browserStatusLabel(session)}" else browserStatusLabel(session),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                )
            }
            Spacer(Modifier.width(Spacing.xs))
            Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

/**
 * The persistent in-chat Browser Flow card: status, domain, the current pause's controls
 * (disambiguation / confirm / handoff / draft) and the Open/Finish/Stop actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrowserSessionCard(
    session: BrowserSession,
    steps: List<BrowserStep>,
    onOpen: () -> Unit,
    onFinish: () -> Unit,
    onStop: () -> Unit,
    onPickCandidate: (String) -> Unit,
    onConfirmDomain: () -> Unit,
    onConfirmSend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Language, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(Spacing.s))
                Column(Modifier.weight(1f)) {
                    Text("Browser Flow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    val domain = BrowserResolver.domainOf(session.resolvedUrl)
                    Text(
                        domain.ifBlank { session.phase ?: "Starting…" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BrowserStatusPill(session)
            }

            if (browserBusy(session)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                session.phase?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            // Pause controls
            when {
                session.status == BrowserSession.STATUS_AWAITING_USER &&
                    session.pendingKind == BrowserSession.PENDING_DISAMBIGUATION -> {
                    Text("Which site should I open?", style = MaterialTheme.typography.bodyMedium)
                    if (session.candidates.isEmpty()) {
                        Text(
                            "Type the website URL or exact name in the message box below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            session.candidates.forEach { c ->
                                AssistChip(
                                    onClick = { onPickCandidate(c.url) },
                                    label = { Text(c.domain, maxLines = 1) },
                                    leadingIcon = { Icon(Icons.Default.Language, null, Modifier.size(16.dp)) },
                                )
                            }
                        }
                        Text(
                            "Not listed? Type the URL in the message box (Other).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                session.status == BrowserSession.STATUS_AWAITING_USER &&
                    session.pendingKind == BrowserSession.PENDING_CONFIRM_DOMAIN -> {
                    Text(
                        "${BrowserResolver.domainOf(session.resolvedUrl)} looks sensitive (banking/payments/account). Open it?",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onConfirmDomain) { Text("Open it") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }

                session.status == BrowserSession.STATUS_AWAITING_USER &&
                    session.pendingKind == BrowserSession.PENDING_HANDOFF -> {
                    Text(session.lastOutput ?: "This step needs you.", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onOpen) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open live browser")
                        }
                        OutlinedButton(onClick = onCancel) { Text("Skip") }
                    }
                    Text(
                        "When you're done, type \"continue\" to resume.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                session.status == BrowserSession.STATUS_AWAITING_USER &&
                    session.pendingKind == BrowserSession.PENDING_DRAFT_CONFIRM -> {
                    Text("Review before sending:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            session.pendingDraft.orEmpty(),
                            Modifier.padding(Spacing.s),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onConfirmSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Send")
                        }
                        OutlinedButton(onClick = onCancel) { Text("Don't send") }
                    }
                }

                session.status == BrowserSession.STATUS_AWAITING_INSTRUCTION -> {
                    session.lastOutput?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Privacy/cost footer
            if (session.hasLiveBrowser && !session.isTerminal) {
                Text(
                    "Temporary · nothing saved · ~${BrowserSession.CREDITS_PER_MINUTE} cr/min while open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Actions
            if (!session.isTerminal) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    if (session.hasLiveBrowser) {
                        Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open browser")
                        }
                    }
                    OutlinedButton(
                        onClick = onFinish,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Finish")
                    }
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Stop, "Stop", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
