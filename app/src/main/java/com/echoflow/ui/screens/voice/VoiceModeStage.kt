@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens.voice

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoflow.data.voice.HeardChips
import com.echoflow.data.voice.VoiceAgentActivity
import com.echoflow.data.voice.VoicePlan
import com.echoflow.ui.chat.VoiceModePhase
import com.echoflow.ui.chat.VoiceModeUiState
import com.echoflow.ui.chat.VoiceRecap
import com.echoflow.ui.theme.JetBrainsMono
import com.echoflow.ui.theme.Spacing
import com.echoflow.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sin

/** What the call screen can ask for. Grouped so the screen's signature stays readable. */
internal data class VoiceStageActions(
    val onToggleMic: () -> Unit = {},
    val onToggleSpeaker: () -> Unit = {},
    /** Returns true when the message was sent; the draft is kept otherwise. */
    val onSend: (String) -> Boolean = { false },
    val onTyping: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onCutIn: () -> Unit = {},
    val onOpenChat: () -> Unit = {},
    val onKeepTalking: () -> Unit = {},
)

/**
 * Voice mode, "Horizon": the whole screen while a call is open.
 *
 * One line runs across the middle of the screen and is the call's pulse. It bends down with the
 * user's voice and up with the agent's, carries a travelling bead while the agent thinks, and
 * flashes outward the moment a turn lands. The agent's words sit above the line and the user's
 * below, so who said what is a matter of place, not labels.
 *
 * Around it:
 * - Key details the user says (amounts, dates, names) rise onto the line as chips while they talk.
 * - When the agent shows a card (a plan, steps, options) it builds at the top as the agent
 *   speaks, and lines the user changes are marked.
 * - While the agent talks, a tap anywhere cuts it off.
 * - Held to the ear, the screen goes dark like a phone call.
 * - When the call ends, a recap shows what it produced, with the chat or another call one tap away.
 *
 * Every colour comes from [MaterialTheme.colorScheme], so it follows each palette, light and dark.
 * With reduced motion the line holds still and only follows the voices.
 */
@Composable
internal fun VoiceModeStage(
    state: VoiceModeUiState,
    activity: VoiceAgentActivity,
    inputLevel: Float,
    outputLevel: Float,
    userCaption: String,
    agentCaption: String,
    lastUserTurn: String,
    userTurns: Int,
    plan: VoicePlan?,
    actions: VoiceStageActions,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(scheme.surface)
            .testTag("voice-stage"),
    ) {
        val recap = state.recap
        AnimatedContent(
            targetState = recap != null && (state.phase == VoiceModePhase.Saving || state.phase == VoiceModePhase.Recap),
            transitionSpec = { fadeIn(tween(320, delayMillis = 120)) togetherWith fadeOut(tween(220)) },
            label = "voice-screen",
        ) { showRecap ->
            if (showRecap && recap != null) {
                RecapScreen(state, recap, actions)
            } else {
                CallScreen(
                    state = state,
                    activity = activity,
                    inputLevel = inputLevel,
                    outputLevel = outputLevel,
                    userCaption = userCaption,
                    agentCaption = agentCaption,
                    lastUserTurn = lastUserTurn,
                    userTurns = userTurns,
                    plan = plan,
                    actions = actions,
                )
            }
        }
    }
}

/** Which way the line leans: listening (down, primary), thinking, speaking (up, tertiary) or resting. */
internal enum class LineMode { Listen, Think, Speak, Rest }

internal fun lineModeFor(state: VoiceModeUiState, activity: VoiceAgentActivity): LineMode = when {
    state.phase == VoiceModePhase.Saving || state.phase == VoiceModePhase.Recap -> LineMode.Rest
    // Connecting still listens: the mic is already recording and what's said is sent on connect.
    state.phase == VoiceModePhase.Starting -> if (state.micMuted) LineMode.Rest else LineMode.Listen
    activity == VoiceAgentActivity.Speaking -> LineMode.Speak
    activity == VoiceAgentActivity.Thinking -> LineMode.Think
    state.micMuted -> LineMode.Rest
    else -> LineMode.Listen
}

@Composable
private fun CallScreen(
    state: VoiceModeUiState,
    activity: VoiceAgentActivity,
    inputLevel: Float,
    outputLevel: Float,
    userCaption: String,
    agentCaption: String,
    lastUserTurn: String,
    userTurns: Int,
    plan: VoicePlan?,
    actions: VoiceStageActions,
) {
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val mode = lineModeFor(state, activity)
    val speaking = mode == LineMode.Speak
    var typing by rememberSaveable { mutableStateOf(false) }
    // Kept here, not in the field, so a draft survives hiding and reopening the keyboard row.
    var draft by rememberSaveable { mutableStateOf("") }

    PocketMode(enabled = state.phase == VoiceModePhase.Live || state.phase == VoiceModePhase.Starting)

    // The line's weights blend between moods rather than switching, so changes read as one motion.
    val listenW by animateFloatAsState(if (mode == LineMode.Listen) 1f else 0f, tween(420), label = "w-listen")
    val thinkW by animateFloatAsState(if (mode == LineMode.Think) 1f else 0f, tween(420), label = "w-think")
    val speakW by animateFloatAsState(if (mode == LineMode.Speak) 1f else 0f, tween(420), label = "w-speak")
    val restW by animateFloatAsState(if (mode == LineMode.Rest) 1f else 0f, tween(420), label = "w-rest")
    val inLevel by animateFloatAsState(
        targetValue = if (state.micMuted) 0f else inputLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "in-level",
    )
    val outLevel by animateFloatAsState(
        targetValue = outputLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "out-level",
    )

    // The line draws itself out from the centre the moment the call opens.
    val grow = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }

    // A bright band runs out along the line each time the user's turn lands: "got it".
    val flash = remember { Animatable(0f) }
    LaunchedEffect(userTurns) {
        if (userTurns > 0 && !reducedMotion) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    // A ring where the user tapped to cut in.
    var tapAt by remember { mutableStateOf<Offset?>(null) }
    val tapRing = remember { Animatable(1f) }
    LaunchedEffect(tapAt) {
        if (tapAt != null) {
            tapRing.snapTo(0f)
            tapRing.animateTo(1f, tween(520))
        }
    }

    val time by produceState(0f, reducedMotion) {
        if (reducedMotion) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = (it - start) / 1000f }
    }

    val colors = LineColors(
        listen = scheme.primary,
        speak = scheme.tertiary,
        think = scheme.secondary,
        rest = scheme.outline,
        flash = scheme.onSurface,
    )

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        CallHeader(state, activity, onToggleSpeaker = actions.onToggleSpeaker)

        AnimatedVisibility(
            visible = plan != null,
            enter = fadeIn(tween(260)) + expandVertically(tween(320)),
            exit = fadeOut(tween(160)) + shrinkVertically(tween(220)),
        ) {
            if (plan != null) {
                // Bounded and scrollable, so a long card never squeezes out the line or the controls.
                PlanCard(
                    plan = plan,
                    tag = "Live",
                    scrollable = true,
                    modifier = Modifier
                        .padding(horizontal = Spacing.base, vertical = Spacing.s)
                        .heightIn(max = 220.dp),
                )
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(speaking) {
                    detectTapGestures { at ->
                        if (speaking) {
                            tapAt = at
                            actions.onCutIn()
                        }
                    }
                }
                .semantics { if (speaking) contentDescription = "Tap to interrupt" },
        ) {
            val area = maxHeight
            val lineY by animateDpAsState(area * (if (plan != null) 0.56f else 0.5f), tween(420), label = "line-y")

            Canvas(Modifier.fillMaxSize()) {
                drawHorizon(
                    hy = lineY.toPx(),
                    t = time,
                    weights = LineWeights(listenW, thinkW, speakW, restW),
                    inLevel = inLevel,
                    outLevel = outLevel,
                    grow = grow.value,
                    flash = flash.value,
                    colors = colors,
                )
                tapAt?.let { at ->
                    val p = tapRing.value
                    if (p < 1f) {
                        drawCircle(
                            color = colors.flash.copy(alpha = 0.35f * (1f - p)),
                            radius = (12.dp + 60.dp * p).toPx(),
                            center = at,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            }

            // The agent, above the line.
            Box(
                Modifier
                    .fillMaxWidth()
                    // Clear of the line even at the top of its swing while the agent talks.
                    .height((lineY - 44.dp).coerceAtLeast(0.dp))
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.BottomStart,
            ) {
                AgentZone(state, activity, agentCaption)
            }

            // What was heard, riding the line.
            HeardChipRow(
                text = when {
                    userCaption.isNotBlank() -> userCaption
                    mode == LineMode.Think -> lastUserTurn
                    else -> ""
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = lineY - 15.dp),
            )

            // The user, below it.
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = lineY + 22.dp)
                    .padding(horizontal = Spacing.xl),
            ) {
                UserZone(state, userCaption, lastUserTurn)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(bottom = Spacing.base),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = speaking && userTurns <= 1 && !typing,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(160)),
            ) {
                Text(
                    "TAP ANYWHERE TO CUT IN",
                    style = MonoLabel,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }
            AnimatedVisibility(
                visible = typing,
                enter = fadeIn(tween(180)) + expandVertically(),
                exit = fadeOut(tween(120)) + shrinkVertically(),
            ) {
                TypeRow(state, draft, onDraft = { draft = it }, actions = actions)
            }
            CallControls(
                state = state,
                speaking = speaking,
                inLevel = inLevel,
                typing = typing,
                onToggleTyping = { typing = !typing },
                actions = actions,
            )
        }
    }
}

/** Name, call time and sound; under them a quiet line for anything worth knowing right now. */
@Composable
private fun CallHeader(state: VoiceModeUiState, activity: VoiceAgentActivity, onToggleSpeaker: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = state.voiceName?.takeIf { it.isNotBlank() } ?: "Echo"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.connectedAt) {
        while (state.connectedAt != null) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val elapsed = state.connectedAt?.let { ((now - it).coerceAtLeast(0) / 1_000) }
    val idle = state.idleSecondsLeft
    val note = when {
        idle != null -> "Still there? Ending in ${idle}s"
        state.phase == VoiceModePhase.Starting -> "Connecting"
        !state.micAvailable -> "Typing only"
        state.micMuted && activity != VoiceAgentActivity.Speaking -> "Mic off"
        else -> null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(48.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (elapsed != null) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(formatDuration(elapsed), style = MonoLabel, color = scheme.onSurfaceVariant)
                }
            }
            AnimatedContent(
                targetState = note,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                label = "voice-note",
            ) { line ->
                if (line != null) {
                    Text(
                        line.uppercase(),
                        style = MonoLabel,
                        color = if (idle != null) scheme.error else scheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
        IconButton(onClick = onToggleSpeaker, enabled = state.phase != VoiceModePhase.Saving) {
            Icon(
                if (state.speakerMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (state.speakerMuted) "Turn sound on" else "Turn sound off",
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The agent's words, newest last; older lines fall away above. */
@Composable
private fun AgentZone(state: VoiceModeUiState, activity: VoiceAgentActivity, caption: String) {
    val scheme = MaterialTheme.colorScheme
    val name = (state.voiceName?.takeIf { it.isNotBlank() } ?: "Echo").uppercase()
    val thinking = state.phase == VoiceModePhase.Live && activity == VoiceAgentActivity.Thinking
    Column(Modifier.fillMaxWidth().animateContentSize(tween(220))) {
        if (caption.isNotBlank() || thinking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MonoLabel, color = scheme.tertiary)
                if (thinking) {
                    Spacer(Modifier.width(Spacing.s))
                    Text("THINKING", style = MonoLabel, color = scheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(Spacing.s))
        }
        if (caption.isNotBlank()) {
            Text(
                tail(caption, 240),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 31.sp),
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** What the user is saying now (bright), or what they said last (quiet). */
@Composable
private fun UserZone(state: VoiceModeUiState, live: String, last: String) {
    val scheme = MaterialTheme.colorScheme
    val text = live.ifBlank { last }
    val hint = when {
        text.isNotBlank() -> null
        !state.micAvailable || state.micMuted -> "Type below, or turn the mic on to talk."
        else -> "Go ahead, I'm listening."
    }
    val color by animateColorAsState(
        if (live.isNotBlank()) scheme.onSurface else scheme.onSurfaceVariant,
        tween(300),
        label = "you-color",
    )
    Column(Modifier.fillMaxWidth()) {
        Text("YOU", style = MonoLabel, color = scheme.primary)
        Spacer(Modifier.height(Spacing.s))
        Text(
            if (hint != null) hint else tail(text, 160),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
            color = if (hint != null) scheme.onSurfaceVariant else color,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Up to three details the user just said, as chips on the line. */
@Composable
private fun HeardChipRow(text: String, modifier: Modifier = Modifier) {
    val chips = remember(text) { if (text.isBlank()) emptyList() else HeardChips.extract(text) }
    var shown by remember { mutableStateOf(emptyList<String>()) }
    if (chips.isNotEmpty()) shown = chips
    AnimatedVisibility(
        visible = chips.isNotEmpty(),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(400)),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            shown.forEach { chip -> androidx.compose.runtime.key(chip) { HeardChip(chip) } }
        }
    }
}

@Composable
private fun HeardChip(label: String) {
    val scheme = MaterialTheme.colorScheme
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
    Row(
        Modifier
            .offset(y = (10f * (1f - rise.value)).dp)
            .scale(0.9f + 0.1f * rise.value)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.primary.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(scheme.primary))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1)
    }
}

/** The card the agent builds: a title, then a line per item. Changed lines carry a mark. */
@Composable
internal fun PlanCard(
    plan: VoicePlan,
    tag: String,
    modifier: Modifier = Modifier,
    /** True when the card has a bounded height of its own (the call screen); the recap scrolls as a whole. */
    scrollable: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice-plan"),
    ) {
        Column(
            Modifier
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = Spacing.base, vertical = Spacing.m)
                .animateContentSize(tween(260)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    plan.title.ifBlank { "Plan" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(tag.uppercase(), style = MonoLabel, color = scheme.onSurfaceVariant)
            }
            plan.rows.forEachIndexed { index, row ->
                androidx.compose.runtime.key(index) {
                    Box(
                        Modifier
                            .padding(top = Spacing.s)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                    Row(
                        Modifier.padding(top = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (row.label.isNotBlank()) {
                            Text(
                                row.label.uppercase(),
                                style = MonoLabel,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.width(64.dp),
                            )
                        }
                        val edited by animateFloatAsState(if (row.edited) 1f else 0f, tween(300), label = "edited")
                        if (edited > 0f) {
                            Box(
                                Modifier
                                    .padding(end = Spacing.s)
                                    .width(2.dp * edited)
                                    .height(16.dp)
                                    .clip(CircleShape)
                                    .background(scheme.primary),
                            )
                        }
                        AnimatedContent(
                            targetState = row.text,
                            transitionSpec = {
                                (fadeIn(tween(260, delayMillis = 80)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(140))
                            },
                            label = "plan-row",
                            modifier = Modifier.weight(1f),
                        ) { text ->
                            Text(
                                text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A field to type into the call, opened from the keyboard button. Typing asks the agent to wait. */
@Composable
private fun TypeRow(state: VoiceModeUiState, text: String, onDraft: (String) -> Unit, actions: VoiceStageActions) {
    val scheme = MaterialTheme.colorScheme
    val live = state.phase == VoiceModePhase.Live
    val canSend = live && text.isNotBlank()
    val send = { if (canSend && actions.onSend(text)) onDraft("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.base, vertical = Spacing.s)
            .testTag("voice-composer"),
    ) {
        Row(
            Modifier.padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
                        onDraft(it)
                        if (it.isNotEmpty()) actions.onTyping()
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .testTag("voice-text"),
                )
            }
            FilledIconButton(onClick = send, enabled = canSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Keyboard, the one big button, and End. The big button is the mic while the user has the turn
 * and Stop while the agent talks; a ring around it breathes with the user's voice.
 */
@Composable
private fun CallControls(
    state: VoiceModeUiState,
    speaking: Boolean,
    inLevel: Float,
    typing: Boolean,
    onToggleTyping: () -> Unit,
    actions: VoiceStageActions,
) {
    val scheme = MaterialTheme.colorScheme
    val ended = state.phase == VoiceModePhase.Saving
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconToggleButton(
            checked = typing,
            onCheckedChange = { onToggleTyping() },
            enabled = !ended,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.Keyboard, contentDescription = if (typing) "Hide keyboard" else "Type instead")
        }

        val micOn = !state.micMuted && state.micAvailable
        val ring by animateFloatAsState(if (micOn && !speaking) inLevel else 0f, tween(90), label = "mic-ring")
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
            Box(
                Modifier
                    .size(84.dp)
                    .scale(1f + 0.22f * ring)
                    .border(1.5.dp, scheme.primary.copy(alpha = if (micOn && !speaking) 0.35f + 0.5f * ring else 0f), CircleShape),
            )
            val container by animateColorAsState(
                if (speaking || micOn) scheme.inverseSurface else scheme.surfaceContainerHighest,
                tween(200),
                label = "mic-container",
            )
            val content by animateColorAsState(
                if (speaking || micOn) scheme.inverseOnSurface else scheme.onSurfaceVariant,
                tween(200),
                label = "mic-content",
            )
            FilledIconButton(
                onClick = { if (speaking) actions.onCutIn() else actions.onToggleMic() },
                enabled = !ended && (speaking || state.micAvailable),
                shape = RoundedCornerShape(if (speaking) 28.dp else 40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
                modifier = Modifier.size(80.dp).testTag("voice-main-button"),
            ) {
                AnimatedContent(
                    targetState = when {
                        speaking -> 0
                        micOn -> 1
                        else -> 2
                    },
                    transitionSpec = { (fadeIn(tween(140)) + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 0.7f)) },
                    label = "mic-icon",
                ) { icon ->
                    when (icon) {
                        0 -> Icon(Icons.Default.Stop, contentDescription = "Stop and talk", modifier = Modifier.size(30.dp))
                        1 -> Icon(Icons.Default.Mic, contentDescription = "Turn mic off", modifier = Modifier.size(30.dp))
                        else -> Icon(Icons.Default.MicOff, contentDescription = "Turn mic on", modifier = Modifier.size(30.dp))
                    }
                }
            }
        }

        FilledIconButton(
            onClick = actions.onEnd,
            enabled = !ended,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            ),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = "End call")
        }
    }
}

/**
 * What the call left behind: how long it ran, the card if there was one (or the agent's last
 * words), and the next step one tap away. It shows while the call is still being saved, so
 * ending a call never shows a spinner.
 */
@Composable
private fun RecapScreen(state: VoiceModeUiState, recap: VoiceRecap, actions: VoiceStageActions) {
    val scheme = MaterialTheme.colorScheme
    val saved = state.phase == VoiceModePhase.Recap
    val eyebrow = if (saved) {
        buildList {
            add("Saved to chat")
            if (recap.durationSeconds > 0) add(formatDuration(recap.durationSeconds))
            if (recap.turns > 0) add(if (recap.turns == 1) "1 turn" else "${recap.turns} turns")
        }.joinToString(" · ")
    } else {
        "Saving to chat…"
    }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .padding(horizontal = Spacing.xl)
            .testTag("voice-recap"),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(Spacing.huge))
            AnimatedContent(
                targetState = eyebrow,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "recap-eyebrow",
            ) { line ->
                Text(line.uppercase(), style = MonoLabel, color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                recap.plan?.title?.takeIf { it.isNotBlank() } ?: "That's in your chat",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xl))
            val plan = recap.plan
            if (plan != null) {
                PlanCard(plan = plan.copy(rows = plan.rows.map { it.copy(edited = false) }), tag = if (saved) "Saved" else "Saving")
            } else if (recap.lastAgentTurn.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceContainerHigh,
                    contentColor = scheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Spacing.base)) {
                        Text(
                            "LAST WORD",
                            style = MonoLabel,
                            color = scheme.tertiary,
                        )
                        Spacer(Modifier.height(Spacing.s))
                        Text(
                            recap.lastAgentTurn,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.base))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.base),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            OutlinedButton(
                onClick = actions.onOpenChat,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Text("Open chat")
            }
            Button(
                onClick = actions.onKeepTalking,
                enabled = saved,
                colors = ButtonDefaults.buttonColors(containerColor = scheme.inverseSurface, contentColor = scheme.inverseOnSurface),
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.s))
                Text("Keep talking")
            }
        }
    }
}

/**
 * Pocket mode: while a call is open, holding the phone to the ear turns the screen off, as a
 * phone call does, so a cheek can't press anything. (The audio moves to the earpiece in the
 * session.) Phones without a proximity sensor simply keep the screen on.
 */
@SuppressLint("WakelockTimeout")
@Composable
private fun PocketMode(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lock = if (enabled && power?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            runCatching {
                power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "EchoFlow:voice-pocket").apply {
                    setReferenceCounted(false)
                    acquire(POCKET_LOCK_MAX_MS)
                }
            }.getOrNull()
        } else {
            null
        }
        onDispose {
            runCatching { if (lock?.isHeld == true) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
        }
    }
}

private const val POCKET_LOCK_MAX_MS = 60 * 60 * 1000L

// ── The line ─────────────────────────────────────────────────────────────────────────────

internal data class LineWeights(val listen: Float, val think: Float, val speak: Float, val rest: Float)

internal data class LineColors(val listen: Color, val speak: Color, val think: Color, val rest: Color, val flash: Color)

private data class Strand(val alpha: Float, val width: Dp, val freq: Float, val speed: Float, val phase: Float)

private val STRANDS = listOf(
    Strand(1f, 1.6.dp, 0.052f, 7.5f, 0f),
    Strand(0.55f, 1.1.dp, 0.071f, -5.2f, 1.7f),
    Strand(0.3f, 0.9.dp, 0.038f, 9.1f, 3.1f),
)

/**
 * The Horizon line at height [hy]: three strands that bend down with the user's voice and up with
 * the agent's, a soft bloom on the side that is talking, a bead that runs along it while the agent
 * thinks, and a band of light that runs outward when a turn lands ([flash] 1 → 0).
 */
internal fun DrawScope.drawHorizon(
    hy: Float,
    t: Float,
    weights: LineWeights,
    inLevel: Float,
    outLevel: Float,
    grow: Float,
    flash: Float,
    colors: LineColors,
) {
    val w = size.width
    val cx = w / 2f
    val unit = density
    val color = blend(colors, weights)

    bloom(hy, above = true, color = colors.speak, alpha = weights.speak * (0.05f + 0.32f * outLevel))
    bloom(hy, above = false, color = colors.listen, alpha = weights.listen * (0.05f + 0.32f * inLevel))

    val half = w / 2f * grow
    if (half <= 1f) return
    val x0 = cx - half
    val x1 = cx + half
    val amp = 66f * unit
    val signal = weights.listen * inLevel - weights.speak * outLevel
    val live = weights.listen + weights.speak
    val calm = 1f - minOf(1f, abs(signal) * 3f)

    STRANDS.forEachIndexed { i, strand ->
        val path = Path()
        var px = x0
        var first = true
        val step = 3f * unit
        while (px <= x1 + 0.1f) {
            val xd = px / unit
            val e = exp(-((px - cx) / (w * 0.33f)).let { it * it })
            val wave = 0.5f + 0.5f * sin(xd * strand.freq - t * strand.speed + strand.phase)
            var y = hy + signal * amp * e * (0.45f + 0.55f * wave)
            y += live * (1.2f * unit * sin(xd * 0.045f + t * 1.4f + i * 2f)) * calm
            if (first) path.moveTo(px, y) else path.lineTo(px, y)
            first = false
            px += step
        }
        val brush = fadeEnds(color, strand.alpha, x0, x1)
        if (i == 0) {
            // A wide, faint pass under the main strand stands in for a glow.
            drawPath(path, fadeEnds(color, 0.18f, x0, x1), style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round))
        }
        drawPath(path, brush, style = Stroke(width = strand.width.toPx(), cap = StrokeCap.Round))
    }

    if (weights.think > 0.02f) {
        val bx = cx + w * 0.36f * sin(t * 2.3f)
        val vx = cos(t * 2.3f)
        val tail = (70f * abs(vx) + 12f) * unit
        val from = bx - sign(vx) * tail
        drawLine(
            brush = Brush.linearGradient(
                listOf(colors.think.copy(alpha = 0f), colors.think.copy(alpha = weights.think)),
                start = Offset(from, hy),
                end = Offset(bx, hy),
            ),
            start = Offset(from, hy),
            end = Offset(bx, hy),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(colors.think.copy(alpha = 0.25f * weights.think), radius = 9.dp.toPx(), center = Offset(bx, hy))
        drawCircle(colors.think.copy(alpha = weights.think), radius = 3.2.dp.toPx(), center = Offset(bx, hy))
    }

    if (flash > 0f) {
        val r = minOf(w / 2f, (40f + 300f * (1f - flash)) * unit)
        drawLine(
            brush = Brush.horizontalGradient(
                0f to colors.flash.copy(alpha = 0f),
                0.5f to colors.flash.copy(alpha = 0.9f * flash),
                1f to colors.flash.copy(alpha = 0f),
                startX = cx - r,
                endX = cx + r,
            ),
            start = Offset(cx - r, hy),
            end = Offset(cx + r, hy),
            strokeWidth = 2.2.dp.toPx(),
        )
        drawLine(
            brush = Brush.horizontalGradient(
                0f to colors.listen.copy(alpha = 0f),
                0.5f to colors.listen.copy(alpha = 0.25f * flash),
                1f to colors.listen.copy(alpha = 0f),
                startX = cx - r,
                endX = cx + r,
            ),
            start = Offset(cx - r, hy),
            end = Offset(cx + r, hy),
            strokeWidth = 10.dp.toPx(),
        )
    }
}

/** A soft wash of [color] on one side of the line, strongest at the centre. */
private fun DrawScope.bloom(hy: Float, above: Boolean, color: Color, alpha: Float) {
    if (alpha <= 0.005f) return
    val w = size.width
    val r = w * 0.78f
    val top = if (above) 0f else hy
    val bottom = if (above) hy else size.height
    clipRect(top = top, bottom = bottom) {
        scale(scaleX = 1f, scaleY = 0.62f, pivot = Offset(w / 2f, hy)) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = alpha),
                    0.55f to color.copy(alpha = alpha * 0.35f),
                    1f to color.copy(alpha = 0f),
                    center = Offset(w / 2f, hy),
                    radius = r,
                ),
                radius = r,
                center = Offset(w / 2f, hy),
            )
        }
    }
}

/** A horizontal brush that is [color] at [alpha] across the middle and fades out at both ends. */
private fun fadeEnds(color: Color, alpha: Float, x0: Float, x1: Float): Brush = Brush.horizontalGradient(
    0f to color.copy(alpha = 0f),
    0.16f to color.copy(alpha = alpha),
    0.84f to color.copy(alpha = alpha),
    1f to color.copy(alpha = 0f),
    startX = x0,
    endX = x1,
)

/** The line's colour: each mood's colour, weighted by how much of that mood is showing. */
private fun blend(colors: LineColors, weights: LineWeights): Color {
    val parts = listOf(
        colors.listen to weights.listen,
        colors.speak to weights.speak,
        colors.think to weights.think,
        colors.rest to weights.rest,
    )
    val total = parts.sumOf { it.second.toDouble() }.toFloat().takeIf { it > 0.001f } ?: return colors.rest
    var r = 0f
    var g = 0f
    var b = 0f
    parts.forEach { (c, wt) ->
        r += c.red * wt
        g += c.green * wt
        b += c.blue * wt
    }
    return Color(r / total, g / total, b / total)
}

// ── Small helpers ────────────────────────────────────────────────────────────────────────

private val MonoLabel
    @Composable get() = MaterialTheme.typography.labelSmall.copy(
        fontFamily = JetBrainsMono,
        letterSpacing = 1.2.sp,
        fontWeight = FontWeight.Medium,
    )

internal fun formatDuration(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private fun tail(text: String, max: Int): String {
    val clean = text.trim()
    if (clean.length <= max) return clean
    val cut = clean.substring(clean.length - max)
    val space = cut.indexOf(' ')
    return "…" + if (space in 0..20) cut.substring(space + 1) else cut
}
