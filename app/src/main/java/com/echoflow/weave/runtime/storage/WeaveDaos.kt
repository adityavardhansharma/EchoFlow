package com.echoflow.weave.runtime.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WeaveBlockDao {
    @Query("SELECT * FROM weave_blocks WHERE messageId = :messageId AND blockKey = :blockKey LIMIT 1")
    suspend fun get(messageId: String, blockKey: Int): WeaveBlockRow?

    @Query("SELECT * FROM weave_blocks WHERE messageId = :messageId ORDER BY blockKey")
    suspend fun forMessage(messageId: String): List<WeaveBlockRow>

    @Query("SELECT * FROM weave_blocks WHERE chatId = :chatId ORDER BY createdAt, blockKey")
    suspend fun forChat(chatId: String): List<WeaveBlockRow>

    @Query("SELECT * FROM weave_blocks WHERE chatId = :chatId ORDER BY createdAt, blockKey")
    fun observeChat(chatId: String): Flow<List<WeaveBlockRow>>

    /** Header ids already used in a chat, so a new block gets the next free `bN` (02 §7). */
    @Query("SELECT blockId FROM weave_blocks WHERE chatId = :chatId")
    suspend fun blockIds(chatId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: WeaveBlockRow): Long

    @Upsert
    suspend fun upsert(row: WeaveBlockRow)

    @Query("UPDATE weave_blocks SET stateJson = :stateJson, historyJson = :historyJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateState(id: String, stateJson: String, historyJson: String, updatedAt: Long)

    @Query("UPDATE weave_blocks SET firedJson = :firedJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFired(id: String, firedJson: String, updatedAt: Long)

    @Query("UPDATE weave_blocks SET uiStateJson = :uiStateJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUiState(id: String, uiStateJson: String, updatedAt: Long)

    @Query("UPDATE weave_blocks SET version = :version, source = :source, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSource(id: String, version: Int, source: String, updatedAt: Long)

    @Query("DELETE FROM weave_blocks WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: String)
}

@Dao
interface WeaveVersionDao {
    @Query("SELECT * FROM weave_versions WHERE blockRowId = :blockRowId ORDER BY version")
    suspend fun forBlock(blockRowId: String): List<WeaveVersionRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: WeaveVersionRow): Long
}

@Dao
interface WeaveLibraryDao {
    @Query("SELECT * FROM weave_library ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<WeaveLibraryRow>>

    @Upsert
    suspend fun upsert(row: WeaveLibraryRow)

    @Query("DELETE FROM weave_library WHERE id = :id")
    suspend fun delete(id: String)
}

/** Writes that must land together. */
@Dao
abstract class WeaveStorageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBlock(row: WeaveBlockRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertVersion(row: WeaveVersionRow): Long

    @Query("UPDATE weave_blocks SET version = :version, source = :source, updatedAt = :updatedAt WHERE id = :id")
    protected abstract suspend fun setSource(id: String, version: Int, source: String, updatedAt: Long)

    /** A new block after its fence closes: the row at version 1 and its `stream` version (05 §9.3). */
    @Transaction
    open suspend fun createBlock(row: WeaveBlockRow): Boolean {
        if (insertBlock(row) == -1L) return false
        insertVersion(WeaveVersionRow(blockRowId = row.id, version = row.version, source = row.source, cause = "stream", createdAt = row.createdAt))
        return true
    }

    /** An accepted change to a block's source: bump the version and keep the old one (05 §9.3). */
    @Transaction
    open suspend fun newVersion(id: String, version: Int, source: String, cause: String, at: Long) {
        setSource(id, version, source, at)
        insertVersion(WeaveVersionRow(blockRowId = id, version = version, source = source, cause = cause, createdAt = at))
    }
}
