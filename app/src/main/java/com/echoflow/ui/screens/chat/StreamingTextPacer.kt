package com.echoflow.ui.screens.chat

import kotlin.math.exp

/** Frame-clock driven presentation buffer. All positions are UTF-16 offsets into the target. */
internal class StreamingTextPacer {
    private var position = 0.0
    private var speed = BASE_SPEED
    private var lastFrame: Long? = null
    private var bufferedSeconds = 0.0
    private var started = false

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
            val desiredSpeed = BASE_SPEED + remaining / CATCH_UP_SECONDS
            speed += (desiredSpeed - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
            position = (position + speed * dt).coerceAtMost(text.length.toDouble())
        } else {
            // Do not accumulate reveal credit during a network pause.
            speed += (BASE_SPEED - speed) * (1.0 - exp(-dt / SPEED_RESPONSE_SECONDS))
        }
        var end = position.toInt()
        // A frame must never expose half of a supplementary Unicode character.
        if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
            end--
        }
        return end
    }

    fun resume() {
        lastFrame = null
    }

    private companion object {
        const val BASE_SPEED = 55.0
        const val START_BUFFER_SECONDS = 0.08
        const val CATCH_UP_SECONDS = 0.65
        const val SPEED_RESPONSE_SECONDS = 0.18
    }
}
