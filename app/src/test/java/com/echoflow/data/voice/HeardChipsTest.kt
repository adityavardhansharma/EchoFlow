package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class HeardChipsTest {

    @Test
    fun `picks amounts, names and months in the order they are said`() {
        assertEquals(
            listOf("3 days", "Lisbon", "March"),
            HeardChips.extract("Can you help me plan three days in Lisbon in March?"),
        )
    }

    @Test
    fun `a run of capitalised words is one name`() {
        assertEquals(listOf("New York", "Friday"), HeardChips.extract("I'm flying to New York on friday"))
    }

    @Test
    fun `the first word of a sentence is not a name`() {
        assertEquals(emptyList<String>(), HeardChips.extract("Sure. Maybe later. Thanks"))
    }

    @Test
    fun `numbers need a following word and digits work too`() {
        assertEquals(listOf("2 tickets", "5 pm"), HeardChips.extract("book 2 tickets for 5 pm"))
        assertEquals(emptyList<String>(), HeardChips.extract("give me three"))
    }

    @Test
    fun `filler after a number is not an amount`() {
        assertEquals(emptyList<String>(), HeardChips.extract("two of them were fine and the one I liked"))
    }

    @Test
    fun `keeps at most three and never repeats`() {
        val chips = HeardChips.extract("maybe Paris, then Rome, then Paris again, then Berlin and Madrid")
        assertEquals(listOf("Paris", "Rome", "Berlin"), chips)
    }

    @Test
    fun `money and clock times are kept as said`() {
        assertEquals(listOf("$40", "10:30"), HeardChips.extract("under $40 and before 10:30 please"))
    }

    @Test
    fun `nothing heard yet means no chips`() {
        assertEquals(emptyList<String>(), HeardChips.extract(""))
    }
}
