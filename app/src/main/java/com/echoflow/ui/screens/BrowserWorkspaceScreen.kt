package com.echoflow.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
 * live Firecrawl browser stream ([BrowserSession.interactiveLiveViewUrl]). Browser-dominant: the
 * WebView fills the screen, a command composer sits at the bottom, and a pull-up drawer shows the
 * activity timeline. The live page is itself the progress indicator while the agent works.
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar ────────────────────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = Spacing.s, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close workspace") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            BrowserResolver.domainOf(s.resolvedUrl).ifBlank { "Browser Flow" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Temporary · nothing saved · ~${BrowserSession.CREDITS_PER_MINUTE} cr/min",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BrowserStatusPill(s)
                    Spacer(Modifier.width(Spacing.xs))
                    IconButton(onClick = { chatViewModel.browserStop(s.id) }) {
                        Icon(Icons.Default.Stop, "Stop session", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Live browser ───────────────────────────────────────────────────────────
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (liveUrl != null) {
                    LiveBrowserWebView(url = liveUrl, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(Spacing.l),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (busy) "Opening the browser…" else "Live view isn't available.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Transient phase overlay while the agent is driving.
                AnimatedVisibility(visible = busy, modifier = Modifier.align(Alignment.TopCenter)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        modifier = Modifier.padding(top = Spacing.s),
                    ) {
                        Row(
                            Modifier.padding(horizontal = Spacing.base, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            LinearProgressIndicator(Modifier.width(40.dp))
                            Text(
                                s.phase ?: "Working…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                        }
                    }
                }

                // External-browser escape hatch (WebView fallback).
                if (liveUrl != null) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s).clickable { openExternal() },
                    ) {
                        Icon(
                            Icons.Default.OpenInNew, "Open in external browser",
                            Modifier.padding(Spacing.s).size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── Pause action bar (needs-you states) ──────────────────────────────────────
            BrowserPauseBar(
                session = s,
                onPick = { chatViewModel.browserResolveCandidate(s.id, it) },
                onConfirmDomain = { chatViewModel.browserConfirmDomain(s.id) },
                onConfirmSend = { chatViewModel.browserConfirmSend(s.id) },
                onCancel = { chatViewModel.browserCancelPending(s.id) },
                onOpenExternal = { openExternal() },
            )

            // ── Activity drawer (pull-up timeline) ───────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { drawerOpen = !drawerOpen }.padding(horizontal = Spacing.base, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Activity", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text("${steps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (drawerOpen) Icons.Default.ExpandMore else Icons.Default.ExpandLess, null)
                    }
                    AnimatedVisibility(visible = drawerOpen) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(horizontal = Spacing.base)) {
                            items(steps.reversed(), key = { it.id }) { step -> TimelineRow(step) }
                        }
                    }
                }
            }

            // ── Command composer ─────────────────────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                        .padding(Spacing.s),
                ) {
                    if (busy) {
                        Text(
                            "Agent is driving — please wait…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.s, bottom = 4.dp),
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = command,
                                onValueChange = { command = it },
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
                                modifier = Modifier.weight(1f),
                            )
                            val canSend = command.trim().isNotEmpty() && !busy
                            IconButton(
                                onClick = {
                                    val t = command.trim()
                                    command = ""
                                    chatViewModel.sendMessage(t)
                                },
                                enabled = canSend,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    "Send command",
                                    tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
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

@Composable
private fun TimelineRow(step: BrowserStep) {
    val color = when (step.role) {
        "user" -> MaterialTheme.colorScheme.primary
        "agent" -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(color, CircleShape))
        Text(step.text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
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
