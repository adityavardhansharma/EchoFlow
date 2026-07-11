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

    /** Chat ids whose message text matches the drawer search query. */
    @Query("SELECT DISTINCT chatId FROM chat_messages WHERE content LIKE '%' || :query || '%'")
    fun searchChatIdsByContent(query: String): Flow<List<String>>
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
interface DeepResearchModelDao {
    @Query("SELECT * FROM deep_research_models ORDER BY addedAt ASC")
    fun getAll(): Flow<List<DeepResearchModel>>

    @Query("SELECT * FROM deep_research_models ORDER BY addedAt ASC")
    suspend fun getAllSync(): List<DeepResearchModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: DeepResearchModel)

    @Query("DELETE FROM deep_research_models WHERE id = :modelId")
    suspend fun delete(modelId: String)
}

@Dao
interface AdvisorProfileDao {
    @Query("SELECT * FROM advisor_profiles ORDER BY createdAt ASC")
    fun getAll(): Flow<List<AdvisorProfile>>

    @Query("SELECT * FROM advisor_profiles ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<AdvisorProfile>

    @Query("SELECT * FROM advisor_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AdvisorProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AdvisorProfile)

    @Query("DELETE FROM advisor_profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FusionPanelDao {
    @Query("SELECT * FROM fusion_panels ORDER BY createdAt ASC")
    fun getAll(): Flow<List<FusionPanel>>

    @Query("SELECT * FROM fusion_panels ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<FusionPanel>

    @Query("SELECT * FROM fusion_panels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FusionPanel?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(panel: FusionPanel)

    @Query("DELETE FROM fusion_panels WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AgentProfileDao {
    @Query("SELECT * FROM agent_profiles ORDER BY createdAt ASC")
    fun getAll(): Flow<List<AgentProfile>>

    @Query("SELECT * FROM agent_profiles ORDER BY createdAt ASC")
    suspend fun getAllSync(): List<AgentProfile>

    @Query("SELECT * FROM agent_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AgentProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AgentProfile)

    @Query("DELETE FROM agent_profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ImageModelDao {
    @Query("SELECT * FROM image_models ORDER BY addedAt ASC")
    fun getAll(): Flow<List<ImageModel>>

    @Query("SELECT * FROM image_models ORDER BY addedAt ASC")
    suspend fun getAllSync(): List<ImageModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: ImageModel)

    @Query("DELETE FROM image_models WHERE id = :modelId")
    suspend fun delete(modelId: String)
}

@Dao
interface GeneratedImageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: GeneratedImage)

    @Query("SELECT * FROM generated_images WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeneratedImage?

    @Query("SELECT * FROM generated_images WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): GeneratedImage?

    @Query("SELECT * FROM generated_images WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getForChat(chatId: String): List<GeneratedImage>
}

@Dao
interface ResearchRunDao {
    @Query("SELECT * FROM research_runs WHERE chatId = :chatId AND status NOT IN ('completed','failed','cancelled') ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveForChat(chatId: String): Flow<ResearchRun?>

    @Query("SELECT * FROM research_runs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ResearchRun?

    @Query("SELECT * FROM research_runs WHERE status NOT IN ('completed','failed','cancelled')")
    suspend fun getInterrupted(): List<ResearchRun>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: ResearchRun)

    @Query("DELETE FROM research_runs WHERE id = :id")
    suspend fun delete(id: String)
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
