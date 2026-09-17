package com.echoflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.memory.MemorySync
import com.echoflow.data.memory.MemoryLearning
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemorySyncDaoTest {
    @Test fun `manual learning flush bypasses normal batch threshold`() {
        assertFalse(MemoryLearning.shouldFlush(2, "free", aged = false, forced = false))
        assertTrue(MemoryLearning.shouldFlush(2, "free", aged = false, forced = true))
        assertFalse(MemoryLearning.shouldFlush(0, "free", aged = true, forced = true))
    }

    @Test fun `late upload acknowledgement cannot mark a newer revision as sent or recreate a deleted chat`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.memorySyncDao()
            dao.put(MemorySync("chat", 1, "new"))
            dao.accepted("chat", 1, "old", "doc")
            assertEquals("", dao.entries(1).single().sentRevision)
            dao.accepted("chat", 1, "new", "doc")
            assertEquals("processing", dao.entries(1).single().status)
            dao.ready("chat", 1)
            assertEquals("ready", dao.entries(1).single().status)
            assertTrue(dao.entries(2).isEmpty())
            dao.remove("chat")
            dao.accepted("chat", 1, "new", "doc")
            assertTrue(dao.entries(1).isEmpty())
        } finally { db.close() }
    }
}
