package com.echoflow.data.memory

import com.echoflow.data.ChatMessage
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MemoryPolicyTest {
    private fun assistant(text: String) = ChatMessage("a", "chat", "assistant", text, 1)

    @Test fun `obvious personal questions recall but self contained questions do not`() {
        assertEquals("user's name or preferred name", MemoryPolicy.recallQuery("what's my name?", null))
        assertNotNull(MemoryPolicy.recallQuery("what do you know about me?", null))
        assertNotNull(MemoryPolicy.recallQuery("what are my preferences?", null))
        assertNotNull(MemoryPolicy.recallQuery("of people I know", null))
        assertNull(MemoryPolicy.recallQuery("any big birthdays today?", null))
        assertNull(MemoryPolicy.recallQuery("Explain Kotlin coroutines", null))
    }

    @Test fun `correction after denial forces recall`() {
        assertNotNull(MemoryPolicy.recallQuery("u do", assistant("I don't know your name yet.")))
        assertNull(MemoryPolicy.recallQuery("u do", assistant("Yes, your name is visible above.")))
    }

    @Test fun `extracts only high confidence durable facts`() {
        val date = LocalDate.of(2026, 9, 17)
        val facts = MemoryPolicy.durableFacts("My name is Aditya. I am 22 and I live in Dublin.", date)
        assertEquals(listOf("identity", "demographic", "location"), facts.map { it.kind })
        assertEquals("The user's name is Aditya.", facts.first().text)
        assertTrue(facts[1].text.contains("as of 2026-09-17"))
        assertTrue(MemoryPolicy.durableFacts("I am tired", date).isEmpty())
        assertTrue(MemoryPolicy.durableFacts("I am 22 minutes away", date).isEmpty())
        assertTrue(MemoryPolicy.durableFacts("I am 30% done", date).isEmpty())
        assertTrue(MemoryPolicy.durableFacts("Call me crazy", date).isEmpty())
    }

    @Test fun `rejects assistant capability statements as memories`() {
        assertTrue(MemoryPolicy.isAssistantMetaMemory("The assistant can retrieve the user's name when prompted."))
        assertTrue(MemoryPolicy.isAssistantMetaMemory("Use the search_memory tool."))
        assertFalse(MemoryPolicy.isAssistantMetaMemory("The user's name is Aditya."))
        assertFalse(MemoryPolicy.isAssistantMetaMemory("The user is building the memory tool."))
    }

    @Test fun `prompt requires recall and supports combined tools`() {
        assertTrue(MemoryTools.PROMPT.contains("MUST call search_memory"))
        assertTrue(MemoryTools.PROMPT.contains("Memory and web search are complementary"))
        assertTrue(MemoryTools.PROMPT.contains("of people I know"))
    }

    @Test fun `cleanup rejects meta memories and keeps newest exact equivalent`() {
        fun memory(id: String, text: String, updated: String) = RemoteMemory(id, text, updated, emptyList(), emptyList())
        val plan = MemoryPolicy.cleanupPlan(listOf(
            memory("old", "My name is Aditya", "2026-01-01"),
            memory("new", "The user's name is Aditya.", "2026-09-17"),
            memory("meta", "The assistant can retrieve the user's name.", "2026-09-17"),
        ))
        assertEquals(1, plan.duplicateGroups)
        assertEquals(1, plan.metaMemories)
        assertEquals(setOf("old", "meta"), plan.discard.map { it.id }.toSet())
    }
}
