package com.echoflow.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicePlanToolTest {

    private val lisbon = mapOf(
        "title" to "Lisbon · 3 days · March",
        "items" to listOf(
            "Day 1: Alfama & Baixa, the old town",
            "Day 2: Sintra palaces, by train",
            "Day 3: Belém, sunset at a miradouro",
        ),
    )

    @Test
    fun `items split into a label and its text`() {
        val plan = VoicePlanTool.parse(lisbon, previous = null)!!
        assertEquals("Lisbon · 3 days · March", plan.title)
        assertEquals(VoicePlanRow("Day 1", "Alfama & Baixa, the old town"), plan.rows[0])
        assertEquals(3, plan.rows.size)
        assertTrue(plan.rows.none { it.edited })
    }

    @Test
    fun `an update of the same card marks only the lines that changed`() {
        val first = VoicePlanTool.parse(lisbon, previous = null)
        val changed = lisbon + ("items" to listOf(
            "Day 1: Alfama & Baixa, the old town",
            "Day 2: Belém and the river, long lunch",
            "Day 3: LX Factory, sunset at a miradouro",
        ))
        val plan = VoicePlanTool.parse(changed, previous = first)!!
        assertFalse(plan.rows[0].edited)
        assertTrue(plan.rows[1].edited)
        assertTrue(plan.rows[2].edited)
    }

    @Test
    fun `a different card starts clean`() {
        val first = VoicePlanTool.parse(lisbon, previous = null)
        val other = VoicePlanTool.parse(mapOf("title" to "Pasta", "items" to listOf("Step 1: Boil water")), first)!!
        assertFalse(other.rows.single().edited)
    }

    @Test
    fun `lines without a label keep their whole text`() {
        val plan = VoicePlanTool.parse(mapOf("title" to "Options", "items" to listOf("Take the train, it's quicker")), null)!!
        assertEquals(VoicePlanRow("", "Take the train, it's quicker"), plan.rows.single())
    }

    @Test
    fun `a list sent as one string, or as objects, still reads`() {
        val text = VoicePlanTool.parse(mapOf("title" to "T", "items" to "- One: a\n- Two: b"), null)!!
        assertEquals(listOf("One", "Two"), text.rows.map { it.label })
        val objects = VoicePlanTool.parse(mapOf("title" to "T", "items" to listOf(mapOf("label" to "A", "text" to "x"))), null)!!
        assertEquals(VoicePlanRow("A", "x"), objects.rows.single())
    }

    @Test
    fun `an empty call shows nothing and long lists are capped`() {
        assertNull(VoicePlanTool.parse(mapOf("items" to emptyList<String>()), null))
        val many = VoicePlanTool.parse(mapOf("title" to "T", "items" to (1..20).map { "Item $it" }), null)!!
        assertEquals(VoicePlanTool.MAX_ROWS, many.rows.size)
    }

    @Test
    fun `the tool definition names the tool and requires both fields`() {
        val definition = VoicePlanTool.definition()
        assertEquals("client", definition["type"])
        assertEquals(VoicePlanTool.NAME, definition["name"])
        assertEquals(listOf("title", "items"), (definition["parameters"] as Map<*, *>)["required"])
    }
}
