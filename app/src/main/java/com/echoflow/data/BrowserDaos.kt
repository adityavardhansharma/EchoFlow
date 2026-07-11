package com.echoflow.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserSessionDao {
    /** The live (non-terminal) session for one chat, if any — drives the in-chat card. */
    @Query(
        "SELECT * FROM browser_sessions WHERE chatId = :chatId " +
            "AND status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeActiveForChat(chatId: String): Flow<BrowserSession?>

    /** The single app-wide live session (start-a-session lock + global pill). */
    @Query(
        "SELECT * FROM browser_sessions WHERE status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    fun observeAnyActive(): Flow<BrowserSession?>

    @Query("SELECT * FROM browser_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BrowserSession?

    @Query(
        "SELECT * FROM browser_sessions WHERE chatId = :chatId " +
            "AND status NOT IN ('completed','failed','stopped','expired') " +
            "ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun getActiveForChat(chatId: String): BrowserSession?

    @Query("SELECT * FROM browser_sessions WHERE status NOT IN ('completed','failed','stopped','expired')")
    suspend fun getAllActive(): List<BrowserSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: BrowserSession)

    @Query("DELETE FROM browser_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface BrowserStepDao {
    @Query("SELECT * FROM browser_steps WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeForSession(sessionId: String): Flow<List<BrowserStep>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: BrowserStep)
}

