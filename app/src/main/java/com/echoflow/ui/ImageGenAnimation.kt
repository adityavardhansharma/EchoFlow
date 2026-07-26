package com.echoflow.ui

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure math behind the image-generation placeholder: a dense dot field that rests near
 * silence while a single narrow wave band (ripple) or sparse falling streaks (rain) travel
 * through it. Everything an intensity function returns is 0..1 and drives all visual
 * channels of a dot (scale, alpha, shape) from one number, so nothing can desync.
 */
internal object ImageDotField {
    /** Grid side. 21×21 keeps dots small enough to read as texture, cheap enough to draw. */
    const val GRID = 21

    /** One full wave pass; the tail of the cycle is a deliberate rest beat. */
    const val CYCLE_MS = 3000L

    /** How long the crest actually travels within a cycle. */
    const val TRAVEL_MS = 2100L

    fun gauss(d: Float, sigma: Float): Float = exp(-(d * d) / (2f * sigma * sigma))

    /** Ease-out: the wavefront launches with energy and lands softly. */
    fun travelEase(phase: Float): Float = 1f - (1f - phase).pow(2.2f)

    val maxDistance: Float = run {
        val c = (GRID - 1) / 2f
        sqrt(2f) * c
    }

    fun distanceFromCenter(col: Int, row: Int): Float {
        val c = (GRID - 1) / 2f
        val dx = col - c
        val dy = row - c
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Ripple: a thin ring radiating from the center with a fainter trailing echo, then rest.
     * [timeMs] is any monotonic clock; [dist] is the dot's distance from center in cells.
     */
    fun rippleIntensity(dist: Float, timeMs: Long): Float {
        val phase = (timeMs % CYCLE_MS).toFloat() / TRAVEL_MS
        if (phase > 1f) return 0f
        val pos = travelEase(phase) * (maxDistance + 3f) - 1.5f
        val crest = gauss(dist - pos, 1.05f)
        val echo = 0.3f * gauss(dist - pos + 2.4f, 1.3f)
        return (crest + echo).coerceAtMost(1f)
    }
}

/** One falling streak: a bright head with a decaying tail, in its own column at its own speed. */
internal data class RainDrop(
    val col: Int,
    var y: Float, // head position in rows (fractional)
    val speed: Float, // rows per second
    val tail: Float, // tail length in rows
    val bright: Float, // head intensity 0.7..1.0
)

/**
 * The rain pattern is deliberately not row-synchronized: drops spawn probabilistically in
 * random columns with random speeds and tail lengths, so fast streaks overtake slow ones and
 * the field never repeats. Capped sparse — the gaps are what make it read as rain.
 */
internal class RainField(private val random: Random = Random.Default) {
    val drops = mutableListOf<RainDrop>()
    private var lastSpawnMs = 0L

    fun step(timeMs: Long, dtMs: Long) {
        if (timeMs - lastSpawnMs > MIN_SPAWN_GAP_MS && drops.size < MAX_DROPS && random.nextFloat() < SPAWN_CHANCE) {
            lastSpawnMs = timeMs
            drops.add(
                RainDrop(
                    col = random.nextInt(ImageDotField.GRID),
                    y = -1f - random.nextFloat() * 4f,
                    speed = MIN_SPEED + random.nextFloat() * (MAX_SPEED - MIN_SPEED),
                    tail = MIN_TAIL + random.nextFloat() * (MAX_TAIL - MIN_TAIL),
                    bright = 0.7f + random.nextFloat() * 0.3f,
                )
            )
        }
        val dt = dtMs / 1000f
        val iterator = drops.iterator()
        while (iterator.hasNext()) {
            val drop = iterator.next()
            drop.y += drop.speed * dt
            if (drop.y - drop.tail > ImageDotField.GRID + 1) iterator.remove()
        }
    }

    fun intensityAt(col: Int, row: Int): Float {
        var total = 0f
        for (drop in drops) {
            if (drop.col != col) continue
            val dy = drop.y - row
            if (dy < -0.4f || dy > drop.tail) continue
            val v = if (dy < 0f) ImageDotField.gauss(dy, 0.25f) else (1f - dy / drop.tail).pow(1.6f)
            total += v * drop.bright
        }
        return total.coerceAtMost(1f)
    }

    companion object {
        const val MIN_SPAWN_GAP_MS = 80L
        const val SPAWN_CHANCE = 0.6f
        const val MAX_DROPS = 18
        const val MIN_SPEED = 9f
        const val MAX_SPEED = 20f
        const val MIN_TAIL = 4f
        const val MAX_TAIL = 8f
    }
}

/**
 * Deals status phrases from a shuffled deck — no repeats until the whole pool is exhausted,
 * unlike naive random picks which repeat embarrassingly fast within one generation.
 */
internal class PhraseDeck(
    private val phrases: List<String> = ImageGenPhrases.ALL,
    private val random: Random = Random.Default,
) {
    private var order: MutableList<Int> = mutableListOf()

    fun next(): String {
        if (order.isEmpty()) {
            order = phrases.indices.shuffled(random).toMutableList()
        }
        return phrases[order.removeAt(0)]
    }
}

/**
 * The pool shown under the placeholder while a video renders. Kept separate from the image
 * pool because a clip takes minutes rather than seconds — the same phrase would come round
 * again long before the wait ends, and the language is film-set rather than paint-and-canvas.
 */
internal object VideoGenPhrases {
    val ALL: List<String> = listOf(
        "Setting up the shot…", "Blocking the scene…", "Rolling camera…", "Finding the light…",
        "Storyboarding the beats…", "Rigging the dolly…", "Choosing the lens…", "Marking the tape…",
        "Rehearsing the move…", "Pulling focus…", "Framing the opening…", "Timing the pan…",
        "Building the world…", "Casting the shadows…", "Painting the sky…", "Placing the horizon…",
        "Cueing the motion…", "Easing the camera in…", "Holding the beat…", "Watching the light change…",
        "Layering the background…", "Animating the foreground…", "Settling the physics…", "Tuning the motion blur…",
        "Smoothing the frames…", "Checking continuity…", "Matching the colours…", "Grading the highlights…",
        "Deepening the shadows…", "Balancing the exposure…", "Filling the frame…", "Clearing the frame…",
        "Sweetening the ambience…", "Placing the sound…", "Syncing the audio…", "Letting the scene breathe…",
        "Following the action…", "Tracking the subject…", "Widening the shot…", "Pushing in slowly…",
        "Craning upward…", "Tilting to the sky…", "Finding the reflection…", "Catching the movement…",
        "Rendering the details…", "Filling in the middle frames…", "Weathering the surfaces…", "Adding the drift…",
        "Nudging the timing…", "Trimming the tail…", "Holding the last frame…", "Reviewing the take…",
        "Going again from the top…", "Getting one more take…", "Choosing the best take…", "Cutting it together…",
        "Smoothing the transition…", "Locking the edit…", "Rendering the final pass…", "Almost in the can…",
    )
}

/** The 100-phrase pool shown under the placeholder while an image generates. */
internal object ImageGenPhrases {
    val ALL: List<String> = listOf(
        "Dreaming it up…", "Mixing the colors…", "Sketching the outline…", "Warming up the canvas…",
        "Chasing the light…", "Sharpening the details…", "Finding the right shade…", "Letting it take shape…",
        "Painting outside the lines…", "Softening the edges…", "Balancing the light…", "Adding a little magic…",
        "Setting the scene…", "Catching the mood…", "Layering the tones…", "Shaping the shadows…",
        "Polishing the highlights…", "Giving it depth…", "Breathing life into it…", "Arranging the composition…",
        "Tuning the palette…", "Filling in the sky…", "Drawing the first stroke…", "Studying the brief…",
        "Getting the angles right…", "Perfecting the texture…", "Blending the horizon…", "Placing the focal point…",
        "Choosing the brushes…", "Stretching the canvas…", "Waking the muse…", "Following the vision…",
        "Tracing the silhouette…", "Working on the glow…", "Measuring twice…", "Painting the quiet parts…",
        "Building the atmosphere…", "Coloring between ideas…", "Finding the harmony…", "Refining the reflections…",
        "Dusting off the easel…", "Inventing the weather…", "Growing the background…", "Carving out the light…",
        "Listening to the prompt…", "Adding the finishing dust…", "Weaving the details…", "Composing the frame…",
        "Testing a bolder hue…", "Steadying the hand…", "Deepening the contrast…", "Smoothing the gradients…",
        "Curating the chaos…", "Planting the shadows…", "Raising the highlights…", "Sculpting the forms…",
        "Choosing the hour of day…", "Setting the perspective…", "Warming the tones…", "Cooling the corners…",
        "Focusing the lens…", "Feathering the clouds…", "Grounding the scene…", "Lighting the subject…",
        "Framing the moment…", "Checking the proportions…", "Rehearsing the reveal…", "Stirring the pigments…",
        "Drafting the geometry…", "Aligning the stars…", "Filling the silence…", "Threading the light…",
        "Casting the reflections…", "Building it pixel by pixel…", "Second-guessing the sky…", "Committing to the palette…",
        "Detailing the foreground…", "Quieting the background…", "Sharpening the focus…", "Softening the distance…",
        "Anchoring the horizon…", "Shading the undersides…", "Glazing the surface…", "Balancing the whites…",
        "Hiding a small surprise…", "Tidying the edges…", "Naming the colors…", "Respecting the shadows…",
        "Trusting the process…", "Slowing down for quality…", "Taking one more look…", "Adjusting the exposure…",
        "Enriching the midtones…", "Setting the mood lighting…", "Finessing the last strokes…", "Standing back to check…",
        "Signing the corner…", "Letting the paint settle…", "Framing it just right…", "Almost there…",
    )
}
