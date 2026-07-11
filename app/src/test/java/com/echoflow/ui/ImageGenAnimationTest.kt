package com.echoflow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ImageGenAnimationTest {
    @Test fun `ripple has a crest during travel and full silence in the rest beat`() {
        // Mid-travel the crest sits somewhere in the field at near-full intensity.
        val midTravel = ImageDotField.TRAVEL_MS / 2
        val peak = (0..40).maxOf { step ->
            ImageDotField.rippleIntensity(ImageDotField.maxDistance * step / 40f, midTravel)
        }
        assertTrue("crest should light up, was $peak", peak > 0.8f)
        // Between TRAVEL_MS and CYCLE_MS every dot rests at zero — the deliberate breath.
        val resting = ImageDotField.rippleIntensity(3f, ImageDotField.TRAVEL_MS + 400)
        assertEquals(0f, resting, 1e-6f)
    }

    @Test fun `ripple crest travels outward over time`() {
        fun crestPosition(t: Long): Float = (0..200)
            .map { it * ImageDotField.maxDistance / 200f }
            .maxBy { ImageDotField.rippleIntensity(it, t) }
        assertTrue(crestPosition(1500) > crestPosition(400))
    }

    @Test fun `rain drops fall independently and stay column-bound`() {
        val field = RainField(Random(7))
        var time = 0L
        repeat(200) { time += 16; field.step(time, 16) }
        assertTrue("drops should have spawned", field.drops.isNotEmpty())
        assertTrue(field.drops.size <= RainField.MAX_DROPS)
        assertTrue(field.drops.all { it.col in 0 until ImageDotField.GRID })
        // Not row-synchronized: heads sit at different fractional rows.
        if (field.drops.size > 2) {
            assertTrue(field.drops.map { it.y }.distinct().size > 1)
        }
        // A dot in a drop's column near its head is lit; other columns stay dark.
        val drop = field.drops.first { it.y in 1f..(ImageDotField.GRID - 1f) }
        val row = drop.y.toInt()
        assertTrue(field.intensityAt(drop.col, row) > 0.2f)
        val emptyCol = (0 until ImageDotField.GRID).first { col -> field.drops.none { it.col == col } }
        assertEquals(0f, field.intensityAt(emptyCol, row), 1e-6f)
    }

    @Test fun `phrase deck deals all 100 without repeats then reshuffles`() {
        assertEquals(100, ImageGenPhrases.ALL.size)
        assertEquals(100, ImageGenPhrases.ALL.distinct().size)
        val deck = PhraseDeck(random = Random(3))
        val firstPass = List(100) { deck.next() }
        assertEquals(100, firstPass.distinct().size)
        // The deck keeps dealing after exhaustion.
        assertTrue(deck.next() in ImageGenPhrases.ALL)
    }
}
