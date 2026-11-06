package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class LocalEngineStateTest {
    @Test fun `a session continues only for the next turn of its own chat`() {
        val bound = SessionBinding("c1", historySize = 3)
        assertTrue(bound.continuesWith("c1", 5))
        assertFalse(bound.continuesWith("c1", 3)) // regenerate: same history
        assertFalse(bound.continuesWith("c1", 7)) // turns were added elsewhere
        assertFalse(bound.continuesWith("c2", 5))
    }

    @Test fun `any change to the engine's fixed settings is a different engine`() {
        val loaded = LoadedEngine("/m.task", 4096, 64)
        assertEquals(LoadedEngine("/m.task", 4096, 64), loaded)
        assertNotEquals(LoadedEngine("/m.task", 8192, 64), loaded)
        assertNotEquals(LoadedEngine("/m.task", 4096, 128), loaded)
        assertNotEquals(LoadedEngine("/other.task", 4096, 64), loaded)
    }
}
