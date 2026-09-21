package com.echoflow.ui.screens.chat

import java.text.BreakIterator
import java.util.Locale
import kotlin.math.exp

/** Frame-clock driven presentation buffer. All positions are UTF-16 offsets into the target. */
internal class StreamingTextPacer {
    private var position = 0.0
    private var speed = BASE_SPEED
    private var lastFrame: Long? = null
    private var bufferedSeconds = 0.0
    private var started = false
    private val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT)
    private var boundaryText = ""
    private var lastEmitted = 0

    fun advance(text: String, frameNanos: Long): Int {
        val previous = lastFrame
        lastFrame = frameNanos
        // Never turn a missed frame or a resumed activity into a large text dump.
        val dt = if (previous == null) 0.0 else
            ((frameNanos - previous) / 1_000_000_000.0).coerceIn(0.0, 0.05)
        if (position > text.length) {
            position = 0.0
            speed = BASE_SPEED
            bufferedSeconds = 0.0
            started = false
            lastEmitted = 0
        }
        if (position < text.length) {
            if (!started) {
                bufferedSeconds += dt
                if (bufferedSeconds < START_BUFFER_SECONDS) return 0
                started = true
            }
            val remaining = text.length - position
            // A small backlog absorbs network jitter. Larger bursts accelerate gradually;
            // retaining fractional progress gives the same cadence at 60, 90 and 120 Hz.
            val desiredSpeed = (BASE_SPEED + remaining / CATCH_UP_SECONDS).coerceAtMost(MAX_SPEED)
            speed += (desiredSpeed - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
            position = (position + speed * dt).coerceAtMost(text.length.toDouble())
        } else {
            // Do not accumulate reveal credit during a network pause.
            speed += (BASE_SPEED - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
        }
        val candidate = position.toInt()
        if (candidate <= 0 || candidate >= text.length) return emit(candidate)
        if (boundaryText != text) {
            boundaryText = text
            graphemes.setText(text)
        }
        val boundary = if (graphemes.isBoundary(candidate)) candidate else graphemes.preceding(candidate).coerceAtLeast(0)
        return emit(boundary)
    }

    /** A newly arrived combining mark may move a grapheme boundary, but visible text never retracts. */
    private fun emit(candidate: Int): Int = candidate.coerceAtLeast(lastEmitted).also { lastEmitted = it }

    fun resume() {
        lastFrame = null
    }

    private companion object {
        const val BASE_SPEED = 55.0
        const val MAX_SPEED = 260.0
        const val START_BUFFER_SECONDS = 0.08
        const val CATCH_UP_SECONDS = 0.65
        const val SPEED_RESPONSE_SECONDS = 0.18
    }
}
