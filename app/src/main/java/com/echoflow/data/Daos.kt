package com.echoflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun getAllThreads(): Flow<List<ChatThread>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread)

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Delete
    suspend fun deleteThread(thread: ChatThread)

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun getThreadById(id: String): ChatThread?
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChat(chatId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessagesForChatSync(chatId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)
}

@Dao
interface CustomModelDao {
    @Query("SELECT * FROM custom_models ORDER BY name ASC")
    fun getAllCustomModels(): Flow<List<CustomModel>>

    @Query("SELECT * FROM custom_models ORDER BY name ASC")
    suspend fun getAllCustomModelsSync(): List<CustomModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomModel(model: CustomModel)

    @Query("DELETE FROM custom_models WHERE id = :modelId")
    suspend fun deleteCustomModel(modelId: String)

    @Query("SELECT * FROM custom_models WHERE id = :modelId LIMIT 1")
    suspend fun getCustomModelById(modelId: String): CustomModel?
}

@Dao
interface LocalModelDao {
    @Query("SELECT * FROM local_models ORDER BY addedAt ASC")
    fun getAllLocalModels(): Flow<List<LocalModel>>

    @Query("SELECT * FROM local_models ORDER BY addedAt ASC")
    suspend fun getAllLocalModelsSync(): List<LocalModel>

    @Query("SELECT * FROM local_models WHERE id = :modelId LIMIT 1")
    suspend fun getLocalModelById(modelId: String): LocalModel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocalModel(model: LocalModel)

    @Query("DELETE FROM local_models WHERE id = :modelId")
    suspend fun deleteLocalModel(modelId: String)
}
