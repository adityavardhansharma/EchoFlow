package com.echoflow.ui.screens.chat

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGreetingsTest {
    @Test fun `every hour maps to the right time of day`() {
        val expected = (0..4).map { GreetingTime.LateNight } +
            (5..7).map { GreetingTime.EarlyMorning } +
            (8..11).map { GreetingTime.Morning } +
            (12..16).map { GreetingTime.Afternoon } +
            (17..20).map { GreetingTime.Evening } +
            (21..23).map { GreetingTime.Night }
        assertEquals(expected, (0..23).map(GreetingTime::of))
    }

    @Test fun `each time of day has twenty distinct greetings`() {
        GreetingTime.entries.forEach { time ->
            val pool = ChatGreetings.forTime(time)
            assertEquals("$time size", 20, pool.size)
            assertEquals("$time duplicates", 20, pool.map { it.headline }.toSet().size)
        }
    }

    @Test fun `greetings follow the house style`() {
        GreetingTime.entries.flatMap(ChatGreetings::forTime).forEach { g ->
            g.accent?.let { assertTrue("accent '$it' not in '${g.headline}'", it in g.headline) }
            assertTrue("no exclamations: ${g.headline}", '!' !in g.headline && '!' !in g.subline)
            assertTrue("headline too long: ${g.headline}", g.headline.length <= 24)
            assertTrue("subline too long: ${g.subline}", g.subline.length <= 36)
        }
    }

    @Test fun `never repeats the previous greeting`() {
        val random = Random(7)
        var previous: Greeting? = null
        repeat(200) {
            val next = ChatGreetings.pick(hour = 23, previous = previous, random = random)
            assertNotEquals(previous, next)
            previous = next
        }
    }
}
