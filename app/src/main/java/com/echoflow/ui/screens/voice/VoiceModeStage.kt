@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.echoflow.data.voice.VoiceAgentActivity
import com.echoflow.ui.chat.VoiceModePhase
import com.echoflow.ui.chat.VoiceModeUiState
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val TWO_PI = (2 * PI).toFloat()

/**
 * The voice-mode screen that replaces the message list while a session is open. Typed and spoken
 * turns are not listed here (they're saved into the chat and shown once the session ends); the
 * stage shows only what is happening now: an orb that breathes with the voices, light rising
 * from the bottom, a status line and live captions.
 *
 * Every colour comes from [MaterialTheme.colorScheme], so the stage follows each palette and
 * light/dark. With reduced motion the orb and the light hold still and only follow the levels.
 */
@Composable
internal fun VoiceModeStage(
    state: VoiceModeUiState,
    activity: VoiceAgentActivity,
    inputLevel: Float,
    outputLevel: Float,
    userCaption: String,
    agentCaption: String,
    topInset: Dp,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val scheme = MaterialTheme.colorScheme

    // One clock for everything that drifts; reduced motion freezes it.
    val clock = rememberInfiniteTransition(label = "voice-clock")
    val drift by clock.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMillis = 7_000, easing = LinearEasing)),
        label = "voice-drift",
    )
    val time = if (reducedMotion) 0f else drift

    val live = state.phase == VoiceModePhase.Live
    val speaking = live && activity == VoiceAgentActivity.Speaking
    val rawLevel = when {
        !live -> 0f
        speaking -> outputLevel
        state.micMuted -> 0f
        else -> inputLevel
    }
    val level by animateFloatAsState(
        targetValue = rawLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "voice-level",
    )
    val glowLevel by animateFloatAsState(
        targetValue = max(inputLevel.takeUnless { state.micMuted } ?: 0f, outputLevel).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "voice-glow",
    )
    val quietOrb = live && state.micMuted && !speaking && activity != VoiceAgentActivity.Thinking

    Box(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .testTag("voice-stage"),
    ) {
        BottomIllumination(
            level = glowLevel,
            time = time,
            dimmed = state.phase == VoiceModePhase.Saving,
            beamColors = listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.primaryContainer),
            modifier = Modifier
                .fillMaxSize()
                .blur(28.dp),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = topInset + Spacing.s, bottom = bottomInset + Spacing.base),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SessionPill(state)
            Spacer(Modifier.weight(1f))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                val orbSize = minOf(maxWidth * 0.72f, 300.dp)
                VoiceOrb(
                    activity = if (live) activity else VoiceAgentActivity.Connecting,
                    level = level,
                    time = time,
                    quiet = quietOrb || state.phase == VoiceModePhase.Saving,
                    modifier = Modifier.size(orbSize),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            StatusLine(state, activity)
            Spacer(Modifier.height(Spacing.base))
            Captions(
                userCaption = if (live) userCaption else "",
                agentCaption = if (live) agentCaption else "",
            )
            Spacer(Modifier.weight(1f))
        }

        AnimatedVisibility(
            visible = state.phase == VoiceModePhase.Saving,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            SavingCard(state.step ?: "Saving your conversation…")
        }
    }
}

/** The model and voice this session runs on, so the user knows what they're talking to. */
@Composable
private fun SessionPill(state: VoiceModeUiState) {
    val label = buildList {
        if (state.llmLabel.isNotBlank()) add(state.llmLabel)
        add(state.voiceName?.takeIf { it.isNotBlank() } ?: "Default voice")
    }.joinToString("  ·  ")
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.base, vertical = Spacing.s),
        )
    }
}

@Composable
private fun StatusLine(state: VoiceModeUiState, activity: VoiceAgentActivity) {
    val idle = state.idleSecondsLeft
    val text = when {
        state.phase == VoiceModePhase.Saving -> ""
        state.phase == VoiceModePhase.Starting -> state.step ?: "Connecting…"
        idle != null -> "Still there? Ending in ${idle}s"
        activity == VoiceAgentActivity.Connecting -> "Connecting…"
        activity == VoiceAgentActivity.Thinking -> "Thinking…"
        activity == VoiceAgentActivity.Speaking -> "Speaking"
        !state.micAvailable -> "Type to talk"
        state.micMuted -> "Mic off · type to talk"
        else -> "Listening"
    }
    val color = if (idle != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    AnimatedContent(
        targetState = text,
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        label = "voice-status",
    ) { line ->
        Text(
            line,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * What the user is saying (while ElevenLabs is still hearing it) and what the agent is saying.
 * Only the tail is kept so a long reply never pushes the orb around.
 */
@Composable
private fun Captions(userCaption: String, agentCaption: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl)
            .heightIn(min = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        if (userCaption.isNotBlank()) {
            Text(
                tail(userCaption, 120),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (agentCaption.isNotBlank()) {
            Text(
                tail(agentCaption, 220),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun tail(text: String, max: Int): String {
    val clean = text.trim()
    if (clean.length <= max) return clean
    val cut = clean.substring(clean.length - max)
    val space = cut.indexOf(' ')
    return "…" + if (space in 0..20) cut.substring(space + 1) else cut
}

@Composable
private fun SavingCard(step: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator(Modifier.size(56.dp))
            Spacer(Modifier.height(Spacing.m))
            Text(step, style = MaterialTheme.typography.titleMedium)
            Text(
                "Your conversation will appear in the chat",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The orb: three soft blobs layered over a halo. Each blob's edge is a sum of slow sine lobes,
 * so it never repeats a shape for long; the voice level swells the orb and roughens the edge.
 * Thinking turns the blobs slowly; a muted mic washes the colour out.
 */
@Composable
internal fun VoiceOrb(
    activity: VoiceAgentActivity,
    level: Float,
    time: Float,
    quiet: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val wash by animateFloatAsState(if (quiet) 0.6f else 0f, tween(400), label = "orb-wash")
    fun tone(color: Color) = lerp(color, scheme.outline, wash)
    val core = tone(scheme.primary)
    val coreLight = tone(scheme.primaryContainer)
    val second = tone(scheme.tertiary)
    val third = tone(scheme.secondary)
    val shine = scheme.onPrimary
    val halo = tone(scheme.primary)

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension / 2f * 0.62f
        val breath = when (activity) {
            VoiceAgentActivity.Connecting -> 0.03f * sin(time * 2f)
            VoiceAgentActivity.Thinking -> 0.04f * sin(time * 3f)
            else -> 0f
        }
        val swell = 1f + level * 0.22f + breath
        val radius = base * swell
        val wobble = when (activity) {
            VoiceAgentActivity.Connecting -> 0.025f
            VoiceAgentActivity.Thinking -> 0.06f
            VoiceAgentActivity.Listening -> 0.035f + level * 0.10f
            VoiceAgentActivity.Speaking -> 0.04f + level * 0.14f
        }
        val spin = if (activity == VoiceAgentActivity.Thinking) time * 57.3f else time * 9f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(halo.copy(alpha = 0.30f + level * 0.25f), halo.copy(alpha = 0f)),
                center = center,
                radius = radius * 1.75f,
            ),
            radius = radius * 1.75f,
            center = center,
        )
        rotate(spin, center) {
            drawBlob(center, radius * 1.06f, wobble * 1.2f, time, seed = 2.1f, lobes = 3, brush = Brush.radialGradient(
                listOf(second.copy(alpha = 0.75f), second.copy(alpha = 0.25f)),
                center = center + Offset(radius * 0.35f, radius * 0.3f),
                radius = radius * 1.3f,
            ))
        }
        rotate(-spin * 0.7f, center) {
            drawBlob(center, radius * 1.0f, wobble, time * 1.2f, seed = 4.4f, lobes = 4, brush = Brush.radialGradient(
                listOf(third.copy(alpha = 0.7f), third.copy(alpha = 0.2f)),
                center = center + Offset(-radius * 0.35f, radius * 0.2f),
                radius = radius * 1.25f,
            ))
        }
        drawBlob(center, radius * 0.9f, wobble * 0.8f, time * 0.9f, seed = 0.7f, lobes = 5, brush = Brush.radialGradient(
            listOf(coreLight, core),
            center = center + Offset(-radius * 0.25f, -radius * 0.3f),
            radius = radius * 1.1f,
        ))
        // A soft highlight high on the orb gives it some volume.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(shine.copy(alpha = 0.28f), shine.copy(alpha = 0f)),
                center = center + Offset(-radius * 0.28f, -radius * 0.34f),
                radius = radius * 0.5f,
            ),
            radius = radius * 0.5f,
            center = center + Offset(-radius * 0.28f, -radius * 0.34f),
        )
    }
}

private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    wobble: Float,
    time: Float,
    seed: Float,
    lobes: Int,
    brush: Brush,
) {
    drawPath(blobPath(center, radius, wobble, time, seed, lobes), brush)
}

/** A closed blob around [center]: a circle whose radius is nudged by three sine lobes. */
internal fun blobPath(center: Offset, radius: Float, wobble: Float, time: Float, seed: Float, lobes: Int): Path {
    val path = Path()
    val steps = 120
    for (i in 0..steps) {
        val a = i.toFloat() / steps * TWO_PI
        val edge = 0.55f * sin(lobes * a + time + seed) +
            0.30f * sin((lobes + 2) * a - time * 1.3f + seed * 2f) +
            0.15f * sin((lobes + 5) * a + time * 0.7f - seed)
        val r = radius * (1f + wobble * edge)
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Light rising from the bottom of the screen: beams of different lengths that reach at most the
 * middle, tallest in the centre, each drifting on its own beat and stretching with the voice.
 */
@Composable
private fun BottomIllumination(
    level: Float,
    time: Float,
    dimmed: Boolean,
    beamColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val strength by animateFloatAsState(if (dimmed) 0.35f else 1f, tween(400), label = "beam-strength")
    Canvas(modifier) {
        val count = BEAM_LENGTHS.size
        val slot = size.width / count
        val beamWidth = slot * 0.74f
        val mid = (count - 1) / 2f
        for (i in 0 until count) {
            val centreBias = 1f - abs(i - mid) / mid
            val beat = 0.5f + 0.5f * sin(time * (1f + i * 0.17f) + i * 1.9f)
            val reach = (BEAM_LENGTHS[i] * (0.55f + 0.45f * centreBias) +
                level * (0.18f + 0.14f * centreBias) +
                beat * 0.05f).coerceIn(0.06f, 0.5f)
            val height = size.height * reach * strength.coerceAtLeast(0.6f)
            val color = beamColors[i % beamColors.size]
            val top = size.height - height
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0f), color.copy(alpha = (0.22f + 0.30f * level) * strength)),
                    startY = top,
                    endY = size.height,
                ),
                topLeft = Offset(i * slot + (slot - beamWidth) / 2f, top),
                size = Size(beamWidth, height),
                cornerRadius = CornerRadius(beamWidth / 2f),
            )
        }
        val glow = beamColors.first()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, glow.copy(alpha = (0.20f + 0.22f * level) * strength)),
                startY = size.height * 0.62f,
                endY = size.height,
            ),
        )
    }
}

/** Resting length of each beam, as a fraction of the screen; varied so the row never looks even. */
private val BEAM_LENGTHS = floatArrayOf(0.16f, 0.27f, 0.21f, 0.36f, 0.30f, 0.42f, 0.33f, 0.38f, 0.24f, 0.31f, 0.19f)

/**
 * The composer while voice mode is open: mic on/off, a field to type into the conversation,
 * then Send (with text) or speaker on/off, and End. Typing tells the agent to hold off talking.
 */
@Composable
internal fun VoiceModeComposer(
    state: VoiceModeUiState,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSend: (String) -> Unit,
    onTyping: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val live = state.phase == VoiceModePhase.Live
    val canSend = live && text.isNotBlank()
    val send = {
        if (canSend) {
            onSend(text)
            text = ""
        }
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = Spacing.base, vertical = Spacing.s)
            .testTag("voice-composer"),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.s, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            FilledTonalIconToggleButton(
                checked = !state.micMuted,
                onCheckedChange = { onToggleMic() },
                enabled = state.micAvailable && state.phase != VoiceModePhase.Saving,
            ) {
                Icon(
                    if (state.micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (state.micMuted) "Turn mic on" else "Turn mic off",
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.s),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        if (live) "Type to talk…" else "Connecting…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        if (it.isNotEmpty()) onTyping()
                    },
                    enabled = state.phase != VoiceModePhase.Saving,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(min = 48.dp)
                        .testTag("voice-text"),
                )
            }
            AnimatedContent(
                targetState = text.isNotBlank(),
                transitionSpec = {
                    (fadeIn(tween(140)) + scaleIn(initialScale = 0.8f)) togetherWith
                        (fadeOut(tween(100)) + scaleOut(targetScale = 0.8f))
                },
                label = "voice-send-slot",
            ) { hasText ->
                if (hasText) {
                    FilledIconButton(onClick = send, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                } else {
                    FilledTonalIconToggleButton(
                        checked = !state.speakerMuted,
                        onCheckedChange = { onToggleSpeaker() },
                        enabled = state.phase != VoiceModePhase.Saving,
                    ) {
                        Icon(
                            if (state.speakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (state.speakerMuted) "Turn sound on" else "Turn sound off",
                        )
                    }
                }
            }
            FilledIconButton(
                onClick = onEnd,
                enabled = state.phase != VoiceModePhase.Saving,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "End voice mode")
            }
        }
    }
}
