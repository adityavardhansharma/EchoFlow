@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.echoflow.data.AudioWavRecorder
import com.echoflow.data.SpeechToTextTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the composer's voice input is doing right now. */
enum class VoicePhase { Idle, Recording, Transcribing }

/**
 * Drives composer dictation: record → stop → transcribe, as one small state machine the composer
 * observes. Owns the recorder and the OpenRouter transcriber; the composer only ever taps the mic
 * and receives the finished text.
 */
class VoiceInputController(
    private val scope: CoroutineScope,
    private val recorder: AudioWavRecorder,
    private val transcriber: SpeechToTextTranscriber,
) {
    var phase by mutableStateOf(VoicePhase.Idle)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val amplitude: StateFlow<Float> = recorder.amplitude
    private var job: Job? = null

    fun startRecording() {
        error = null
        phase = if (recorder.start()) VoicePhase.Recording else {
            error = "Microphone is unavailable."
            VoicePhase.Idle
        }
    }

    /** Stop capturing and transcribe; [onText] receives the transcript on success. */
    fun stopAndTranscribe(apiKey: String, modelId: String, onText: (String) -> Unit) {
        if (phase != VoicePhase.Recording) return
        val wav = recorder.stop()
        if (wav == null) {
            phase = VoicePhase.Idle
            error = "Didn't catch that — try again."
            return
        }
        phase = VoicePhase.Transcribing
        job = scope.launch {
            transcriber.transcribe(apiKey, modelId, wav)
                .onSuccess { onText(it) }
                .onFailure { error = it.message ?: "Couldn't transcribe that." }
            phase = VoicePhase.Idle
        }
    }

    /** Abort whatever is happening: drop the recording, or cancel an in-flight transcription. */
    fun cancel() {
        when (phase) {
            VoicePhase.Recording -> recorder.cancel()
            VoicePhase.Transcribing -> job?.cancel()
            VoicePhase.Idle -> {}
        }
        job = null
        phase = VoicePhase.Idle
    }

    fun clearError() { error = null }
}

@Composable
fun rememberVoiceInputController(): VoiceInputController {
    val scope = rememberCoroutineScope()
    return remember { VoiceInputController(scope, AudioWavRecorder(), SpeechToTextTranscriber()) }
}

/**
 * The mic that lives on the model row (never in the oval's corners). One fixed home: tap to start
 * dictating, tap again to stop. It pulses and turns into a Stop while recording, and is inert
 * while a transcription is in flight.
 */
@Composable
fun ModelRowMic(phase: VoicePhase, onClick: () -> Unit) {
    val recording = phase == VoicePhase.Recording
    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "mic-scale",
    )
    Surface(
        onClick = onClick,
        enabled = phase != VoicePhase.Transcribing,
        shape = CircleShape,
        color = if (recording) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.scale(if (recording) pulseScale else 1f),
    ) {
        Icon(
            if (recording) Icons.Default.Stop else Icons.Default.Mic,
            if (recording) "Stop dictation" else "Dictate",
            Modifier.padding(9.dp).size(20.dp),
            tint = if (recording) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * A live voice waveform that takes over the composer's text area while recording. Keeps a rolling
 * history of amplitudes so it scrolls right-to-left; when [active] is false (transcription in
 * flight) it simply stops advancing — the bars freeze rather than lying about being live.
 */
@Composable
fun VoiceWaveform(amplitude: Float, active: Boolean, modifier: Modifier = Modifier) {
    val bars = remember { mutableStateListOf<Float>().apply { repeat(BAR_COUNT) { add(0.04f) } } }
    LaunchedEffect(active) {
        while (active) {
            bars.removeAt(0)
            bars.add(amplitude.coerceIn(0.04f, 1f))
            delay(55)
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val n = bars.size
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val cy = size.height / 2f
        bars.forEachIndexed { i, a ->
            val h = (size.height * 0.85f) * a
            val x = i * (barW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, cy - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}

private const val BAR_COUNT = 42
