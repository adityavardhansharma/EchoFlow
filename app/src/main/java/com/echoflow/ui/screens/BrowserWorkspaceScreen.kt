@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.echoflow.data.BrowserResolver
import com.echoflow.data.BrowserSession
import com.echoflow.data.BrowserStep
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.components.BrowserStatusPill
import com.echoflow.ui.theme.Spacing

/**
 * The native, fullscreen Browser Workspace — a mini-browser inside EchoFlow wrapped around the
 * live Firecrawl browser stream ([BrowserSession.interactiveLiveViewUrl]). Browser-dominant: a
 * browser-style address bar on top, the live page in a rounded viewport, a pull-up activity
 * drawer, and a chat-style command composer. The live page is itself the progress indicator
 * while the agent works.
 */
@Composable
fun BrowserWorkspaceScreen(
    chatViewModel: ChatViewModel,
    onClose: () -> Unit,
) {
    val session by chatViewModel.currentBrowserSession.collectAsState()
    val steps by chatViewModel.currentBrowserSteps.collectAsState()
    val context = LocalContext.current

    // If the session ends (Finish/Stop/expire), leave the workspace.
    LaunchedEffect(session?.isTerminal, session == null) {
        if (session == null || session?.isTerminal == true) onClose()
    }
    val s = session ?: return

    var command by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }

    val busy = s.status == BrowserSession.STATUS_RUNNING ||
        s.status == BrowserSession.STATUS_STARTING ||
        s.status == BrowserSession.STATUS_RESOLVING
    val liveUrl = s.interactiveLiveViewUrl ?: s.liveViewUrl

    fun openExternal() {
        val url = s.interactiveLiveViewUrl ?: s.liveViewUrl ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxSize()) {
            WorkspaceTopBar(
                session = s,
                onClose = onClose,
                onStop = { chatViewModel.browserStop(s.id) },
            )

            // ── Live browser viewport ────────────────────────────────────────────────────
            // Extracted to its own composable so the enclosing ColumnScope does not leak in: the
            // overlay AnimatedVisibility calls would otherwise resolve to ColumnScope.AnimatedVisibility
            // (whose receiver isn't available here) instead of the plain Box-friendly overload.
            BrowserViewport(
                session = s,
                busy = busy,
                liveUrl = liveUrl,
                onOpenExternal = { openExternal() },
                modifier = Modifier.weight(1f),
            )

            BrowserPauseBar(
                session = s,
                onPick = { chatViewModel.browserResolveCandidate(s.id, it) },
                onConfirmDomain = { chatViewModel.browserConfirmDomain(s.id) },
                onConfirmSend = { chatViewModel.browserConfirmSend(s.id) },
                onCancel = { chatViewModel.browserCancelPending(s.id) },
                onOpenExternal = { openExternal() },
            )

            ActivityDrawer(steps = steps, open = drawerOpen, onToggle = { drawerOpen = !drawerOpen })

            CommandComposer(
                value = command,
                onValue = { command = it },
                busy = busy,
                onSend = {
                    val t = command.trim()
                    if (t.isNotEmpty()) { command = ""; chatViewModel.sendMessage(t) }
                },
            )
        }
    }
}

// ── Live viewport ────────────────────────────────────────────────────────────────────────

/**
 * The live page viewport plus its overlays (load bar, phase chip, take-over button). Kept as a
 * standalone composable (not a ColumnScope extension) so `AnimatedVisibility` inside the [Box]
 * resolves to the plain overload rather than the leaked-in `ColumnScope` one. The caller supplies
 * the `weight(1f)` via [modifier] from the enclosing Column.
 */
@Composable
private fun BrowserViewport(
    session: BrowserSession,
    busy: Boolean,
    liveUrl: String?,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.s)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (liveUrl != null) {
            LiveBrowserWebView(url = liveUrl, modifier = Modifier.fillMaxSize())
        } else {
            OpeningState(domain = BrowserResolver.domainOf(session.resolvedUrl), busy = busy, phase = session.phase)
        }

        // Thin browser-style load bar pinned to the top edge of the page.
        AnimatedVisibility(busy, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        }

        // Floating phase chip — the agent is driving.
        AnimatedVisibility(
            visible = busy && liveUrl != null,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.base),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 6.dp) {
                Row(
                    Modifier.padding(start = Spacing.s, end = Spacing.base, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                    Text(
                        session.phase ?: "Working…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }

        // Take-over-in-Chrome escape hatch.
        if (liveUrl != null) {
            FilledTonalIconButton(
                onClick = onOpenExternal,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.base),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Icon(Icons.Default.OpenInNew, "Open in external browser", Modifier.size(20.dp))
            }
        }
    }
}

// ── Top bar (browser chrome) ─────────────────────────────────────────────────────────────

@Composable
private fun WorkspaceTopBar(
    session: BrowserSession,
    onClose: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Minimize to chat", Modifier.size(26.dp))
                }
                // The address bar — favicon stand-in (lock = secure/temporary) + domain + status.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        Modifier.padding(start = Spacing.base, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Icon(Icons.Default.Lock, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            BrowserResolver.domainOf(session.resolvedUrl).ifBlank { "Browser Flow" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        BrowserStatusPill(session)
                    }
                }
                FilledTonalIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Icon(Icons.Default.PowerSettingsNew, "Stop session", Modifier.size(20.dp)) }
            }
            // Privacy / cost strip.
            Row(
                Modifier.padding(start = Spacing.base),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text(
                    "Temporary · nothing saved · ~${BrowserSession.CREDITS_PER_MINUTE} cr/min while open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Opening / no-live-view state ──────────────────────────────────────────────────────────

@Composable
private fun OpeningState(domain: String, busy: Boolean, phase: String?) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.base, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            LoadingIndicator(modifier = Modifier.size(56.dp), color = MaterialTheme.colorScheme.primary)
            Text(
                if (domain.isNotBlank()) "Opening $domain…" else (phase ?: "Opening the browser…"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Starting a temporary, private browser session",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Icon(Icons.Default.OpenInNew, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Live view isn't available", style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── Command composer (mirrors the chat message bar) ──────────────────────────────────────

@Composable
private fun CommandComposer(
    value: String,
    onValue: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = Spacing.s, vertical = Spacing.s),
        ) {
            AnimatedVisibility(busy, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.padding(start = Spacing.base, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    LoadingIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Agent is driving — please wait…",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Row(Modifier.padding(start = Spacing.base, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = value,
                        onValueChange = onValue,
                        enabled = !busy,
                        placeholder = { Text("Command the browser…") },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    val canSend = value.trim().isNotEmpty() && !busy
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(44.dp).clickable(enabled = canSend, onClick = onSend),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send command",
                                Modifier.size(20.dp),
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Activity drawer (pull-up timeline) ────────────────────────────────────────────────────

@Composable
private fun ActivityDrawer(steps: List<BrowserStep>, open: Boolean, onToggle: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Grabber handle.
            Box(
                Modifier
                    .padding(top = Spacing.s)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Spacing.base, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Activity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "${steps.size}",
                        Modifier.padding(horizontal = Spacing.s, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Icon(if (open) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null)
            }
            AnimatedVisibility(visible = open) {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).padding(horizontal = Spacing.base, vertical = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    items(steps.reversed(), key = { it.id }) { step -> TimelineRow(step) }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(step: BrowserStep) {
    val (label, color) = when (step.role) {
        "user" -> "You" to MaterialTheme.colorScheme.primary
        "agent" -> "Agent" to MaterialTheme.colorScheme.tertiary
        else -> "System" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = Modifier.width(54.dp)) {
            Text(
                label,
                Modifier.padding(vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            step.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Pause action bar (needs-you states) ───────────────────────────────────────────────────

@Composable
private fun BrowserPauseBar(
    session: BrowserSession,
    onPick: (String) -> Unit,
    onConfirmDomain: () -> Unit,
    onConfirmSend: () -> Unit,
    onCancel: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    if (session.status != BrowserSession.STATUS_AWAITING_USER) return
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.xs),
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.base), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            when (session.pendingKind) {
                BrowserSession.PENDING_DISAMBIGUATION -> {
                    Text("Which site? Tap one, or type a URL below.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    session.candidates.forEach { c ->
                        OutlinedButton(onClick = { onPick(c.url) }, modifier = Modifier.fillMaxWidth()) {
                            Text(c.domain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                BrowserSession.PENDING_CONFIRM_DOMAIN -> {
                    Text("${BrowserResolver.domainOf(session.resolvedUrl)} looks sensitive. Open it?", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onConfirmDomain) { Text("Open it") }
                        OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    }
                }
                BrowserSession.PENDING_HANDOFF -> {
                    Text(session.lastOutput ?: "This step needs you.", color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onOpenExternal) {
                            Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open live browser")
                        }
                        OutlinedButton(onClick = onCancel) { Text("Skip") }
                    }
                    Text("Then type \"continue\" below.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                BrowserSession.PENDING_DRAFT_CONFIRM -> {
                    Text("Review before sending:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                        Text(session.pendingDraft.orEmpty(), Modifier.padding(Spacing.s), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onConfirmSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Send")
                        }
                        OutlinedButton(onClick = onCancel) { Text("Don't send") }
                    }
                }
            }
        }
    }
}

/** WebView showing the live remote browser. Created on enter, destroyed on leave (lean). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveBrowserWebView(url: String, modifier: Modifier = Modifier) {
    var lastUrl by remember { mutableStateOf<String?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                loadUrl(url)
                lastUrl = url
            }
        },
        update = { web ->
            if (lastUrl != url) {
                web.loadUrl(url)
                lastUrl = url
            }
        },
        onRelease = { web ->
            web.stopLoading()
            web.loadUrl("about:blank")
            web.destroy()
        },
    )
}
