
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.echoflow.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.echoflow.data.ArtifactVersion
import com.echoflow.ui.StreamSegment
import com.echoflow.ui.components.AdvisorCard
import com.echoflow.ui.components.AgentDeployingCard
import com.echoflow.ui.components.ArtifactCard
import com.echoflow.ui.components.BrandMark
import com.echoflow.ui.components.FusionCard
import com.echoflow.ui.components.MarkdownText
import com.echoflow.ui.components.SearchActivityCard
import com.echoflow.ui.components.SubagentCard
import com.echoflow.ui.theme.Spacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Shown while an on-device model is loaded into RAM (or a long chat is prefilled) —
 * this can take several seconds to a minute on first use, so it gets a richer
 * animation than the plain "Thinking…" row: breathing brand mark plus an
 * indeterminate expressive wavy progress line.
 */
@Composable
internal fun ModelLoadingRow(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(modifier = Modifier.padding(end = Spacing.m), size = 32.dp, animated = true)
            Column(Modifier.weight(1f)) {
                Text(
                    "Loading model…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Getting it into memory — first use takes the longest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(Spacing.m))
        LinearWavyProgressIndicator(
            modifier = Modifier.fillMaxWidth().padding(end = Spacing.xl),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The live assistant reply, rendered as an ordered timeline: reasoning traces, web search
 * steps and text blocks appear in the order the model produced them — so when the model
 * searches, writes, then searches again, the user sees exactly that.
 */
@Composable
internal fun StreamingAssistantBubble(
    segments: List<StreamSegment>,
    statusNote: String?,
    isStreaming: Boolean,
    onArtifactOpen: (artifactId: String, version: Int) -> Unit = { _, _ -> },
    observeArtifactVersions: (String) -> Flow<List<ArtifactVersion>> = { flowOf(emptyList()) },
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(size = 26.dp, animated = true)
            Spacer(Modifier.width(Spacing.s))
            Text("EchoFlow", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Spacing.s))

        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            key(index) {
                when (segment) {
                    is StreamSegment.Reasoning -> {
                        ReasoningSection(reasoning = segment.text, active = isStreaming && isLast)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Search -> {
                        SearchActivityCard(query = segment.query, sources = segment.sources, active = segment.active)
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Advisor -> {
                        AdvisorCard(
                            advisorName = segment.advisorName,
                            advisorModel = segment.advisorModel,
                            prompt = segment.prompt,
                            advice = segment.advice,
                            active = segment.active,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Fusion -> {
                        // Collapse the process shell once final answer text appears after this
                        // segment — same hierarchy as Reasoning (process yields to the answer).
                        val answerStarted = segments
                            .subList(index + 1, segments.size)
                            .any { it is StreamSegment.Text && it.text.isNotBlank() }
                        FusionCard(
                            panelName = segment.panelName,
                            models = segment.models,
                            analysis = segment.analysis,
                            active = segment.active,
                            isStreaming = isStreaming,
                            answerStarted = answerStarted,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.AgentRun -> {
                        AgentDeployingCard()
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Subagent -> {
                        SubagentCard(
                            taskName = segment.taskName,
                            taskDescription = segment.taskDescription,
                            workerModel = segment.workerModel,
                            outcome = segment.outcome,
                            error = segment.error,
                            active = segment.active,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Artifact -> {
                        ArtifactCard(
                            artifactId = segment.artifactId,
                            title = segment.title,
                            artifactType = segment.artifactType,
                            version = segment.version,
                            building = segment.building,
                            charCount = segment.charCount,
                            truncated = segment.truncated,
                            observeVersions = observeArtifactVersions,
                            onOpen = onArtifactOpen,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Image -> {
                        // One composable through generating → stretch → reveal → settled, so the
                        // dot field animates INTO the image instead of hard-swapping components.
                        com.echoflow.ui.components.GeneratedImageSegment(
                            filePath = segment.filePath,
                            pattern = segment.pattern,
                            previousImagePath = segment.previousImagePath,
                            animate = true,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Video -> {
                        com.echoflow.ui.components.GeneratedVideoSegment(
                            videoId = segment.videoId,
                            filePath = segment.filePath,
                            pattern = segment.pattern,
                            aspectRatio = segment.aspectRatio,
                            status = segment.status,
                            animate = true,
                            errorMessage = segment.error,
                        )
                        Spacer(Modifier.height(Spacing.s))
                    }
                    is StreamSegment.Text -> {
                        SmoothStreamingText(segment.text, Modifier.fillMaxWidth())
                        if (!isLast) Spacer(Modifier.height(Spacing.s))
                    }
                }
            }
        }

        statusNote?.let { note ->
            Spacer(Modifier.height(Spacing.s))
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Smooth "typewriter" reveal, the way production AI apps (T3 Chat, Vercel v0, ChatGPT) do it:
 * the network delivers text in bursts, but we reveal it at a steady, frame-synced cadence so it
 * reads pleasantly instead of flickering in chunks. The pace is a gentle base speed plus a
 * proportional catch-up, so it never lags far behind a fast model yet never feels rushed.
 */
@Composable
internal fun SmoothStreamingText(
    text: String,
    modifier: Modifier = Modifier,
    markdown: Boolean = true,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val target by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            val dt = if (lastFrame == 0L) 0f else (frame - lastFrame) / 1_000_000_000f
            lastFrame = frame
            val t = target
            if (shown > t.length) shown = 0 // a new message started — restart the reveal
            if (shown < t.length) {
                val remaining = t.length - shown
                // Steady, pleasant cadence that scales with backlog so the whole answer finishes a
                // beat after the model (≈2s drain), never instant-dumping even at 1000+ tps.
                val charsPerSec = (remaining / 2f).coerceIn(40f, 900f)
                val add = (charsPerSec * dt).toInt().coerceAtLeast(1)
                shown = (shown + add).coerceAtMost(t.length)
            }
        }
    }
    val revealed = target.take(shown)
    if (markdown) {
        // Live markdown while streaming. Because the text is revealed gradually (not in bursts),
        // markdown spans complete one char at a time, so re-layout stays smooth like ChatGPT/Claude.
        MarkdownText(text = revealed, modifier = modifier, textColor = color, style = style)
    } else {
        SelectionContainer { Text(text = revealed, style = style, color = color, modifier = modifier) }
    }
}

/**
 * Collapsible reasoning ("thinking") trace, styled like T3 Chat / Claude. Subtly tinted so it reads
 * as meta-content. Auto-expands and streams while the model is reasoning, then auto-collapses once
 * the answer begins; the user can expand/collapse at any time (collapsed by default once complete).
 */
@Composable
internal fun ReasoningSection(reasoning: String, active: Boolean) {
    var userToggled by remember { mutableStateOf<Boolean?>(null) }
    val expanded = userToggled ?: active
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "reasoning-chevron")
    val toggleInteraction = remember { MutableInteractionSource() }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = toggleInteraction,
                indication = null,
                onClick = { userToggled = !expanded },
            ),
    ) {
        Column(Modifier.padding(horizontal = Spacing.base, vertical = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(Spacing.s))
                Text(
                    if (active) "Reasoning…" else "Reasoning",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (active) {
                    Spacer(Modifier.width(Spacing.s))
                    LoadingIndicator(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.KeyboardArrowDown, if (expanded) "Collapse" else "Expand",
                    Modifier.size(20.dp).rotate(chevron),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                if (active) {
                    // Bounded, internally auto-scrolling panel so a fast reasoning stream can never
                    // overflow / "break out" of the container — it scrolls within a fixed height.
                    val sc = rememberScrollState()
                    LaunchedEffect(sc.maxValue) { sc.scrollTo(sc.maxValue) }
                    Box(
                        Modifier
                            .padding(top = Spacing.s)
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                            .verticalScroll(sc),
                    ) {
                        SmoothStreamingText(
                            reasoning, Modifier.fillMaxWidth(),
                            markdown = true,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Box(Modifier.padding(top = Spacing.s)) {
                        MarkdownText(
                            reasoning, Modifier.fillMaxWidth(),
                            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ThinkingRow(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(modifier = Modifier.padding(end = Spacing.m), size = 32.dp, animated = true)
        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(Spacing.s))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Red "Message stopped" line shown under a reply the user cancelled with the Stop button. */
@Composable
internal fun StoppedNotice(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Stop,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            "Message stopped",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
