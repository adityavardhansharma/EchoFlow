package com.echoflow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query(
        "SELECT * FROM chat_threads ORDER BY " +
            "CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt DESC, updatedAt DESC"
    )
    fun getAllThreads(): Flow<List<ChatThread>>

    /** The drawer's list: pinned conversations first, then newest activity. */
    @Query(
        "SELECT * FROM chat_threads WHERE kind = :kind ORDER BY " +
            "CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt DESC, updatedAt DESC"
    )
    fun getThreadsByKind(kind: String): Flow<List<ChatThread>>

    /**
     * How many conversations the *other* mode holds that match a search. Drives the drawer's
     * "also N results in Imagine" hint, so a filtered list never reads as a lost one.
     */
    @Query(
        "SELECT COUNT(*) FROM chat_threads WHERE kind = :kind AND " +
            "(title LIKE '%' || :query || '%' OR id IN " +
            "(SELECT DISTINCT chatId FROM chat_messages WHERE content LIKE '%' || :query || '%'))"
    )
    fun countMatchingInKind(kind: String, query: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread)

    @Update
    suspend fun updateThread(thread: ChatThread)

    @Query("UPDATE chat_threads SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchUpdatedAt(id: String, updatedAt: Long)

    @Query("UPDATE chat_threads SET title = :title WHERE id = :id")
    suspend fun setTitle(id: String, title: String)

    @Query("UPDATE chat_threads SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTitleAndUpdatedAt(id: String, title: String, updatedAt: Long)

    @Query("UPDATE chat_threads SET pinnedAt = :pinnedAt WHERE id = :id")
    suspend fun setPinnedAt(id: String, pinnedAt: Long?)

    /** Conversations belonging to one project, newest activity first (pins still float to the top). */
    @Query(
        "SELECT * FROM chat_threads WHERE projectId = :projectId ORDER BY " +
            "CASE WHEN pinnedAt IS NULL THEN 1 ELSE 0 END, pinnedAt DESC, updatedAt DESC"
    )
    fun getThreadsByProject(projectId: String): Flow<List<ChatThread>>

    @Query("SELECT COUNT(*) FROM chat_threads WHERE projectId = :projectId")
    fun countThreadsInProject(projectId: String): Flow<Int>

    @Query("UPDATE chat_threads SET projectId = :projectId WHERE id = :id")
    suspend fun setProjectId(id: String, projectId: String?)

    /** Return a project's chats to the drawer — used when a project is deleted (no FK cascade). */
    @Query("UPDATE chat_threads SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearProjectAssignments(projectId: String)

    @Delete
    suspend fun deleteThread(thread: ChatThread)

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun getThreadById(id: String): ChatThread?

    /** The project a conversation belongs to (or null), observed for the in-chat project pill. */
    @Query("SELECT projectId FROM chat_threads WHERE id = :id LIMIT 1")
    fun observeProjectId(id: String): Flow<String?>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    fun getMessagesForChat(chatId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getMessagesForChatSync(chatId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    /**
     * Prompt edit: write the updated user row and drop the assistant answer it replaces
     * in one transaction so a crash never leaves a half-edited turn.
     */
    @Transaction
    suspend fun replaceUserTurn(updatedUser: ChatMessage, oldAssistantId: String?) {
        insertMessage(updatedUser)
        if (oldAssistantId != null) deleteMessageById(oldAssistantId)
    }

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
interface VideoModelDao {
    @Query("SELECT * FROM video_models ORDER BY addedAt ASC")
    fun getAll(): Flow<List<VideoModel>>

    @Query("SELECT * FROM video_models ORDER BY addedAt ASC")
    suspend fun getAllSync(): List<VideoModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(model: VideoModel)

    @Query("DELETE FROM video_models WHERE id = :modelId")
    suspend fun delete(modelId: String)
}

@Dao
interface GeneratedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(video: GeneratedVideo)

    @Query("SELECT * FROM generated_videos WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GeneratedVideo?

    @Query("SELECT * FROM generated_videos WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<GeneratedVideo?>

    @Query("SELECT * FROM generated_videos WHERE chatId = :chatId ORDER BY createdAt ASC")
    suspend fun getForChat(chatId: String): List<GeneratedVideo>

    @Query("SELECT * FROM generated_videos WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForChat(chatId: String): GeneratedVideo?

    /** Jobs left mid-flight by a kill or crash — polling resumes from these on next launch. */
    @Query("SELECT * FROM generated_videos WHERE status NOT IN ('completed','failed','cancelled','expired')")
    suspend fun getUnfinished(): List<GeneratedVideo>

    /**
     * Conversations with a render in flight. The database is the honest source for this —
     * it covers live turns and resumed jobs alike, and survives process death, which no
     * in-memory job registry does. Drives the "still working" pulse in the mode switch and
     * the drawer.
     */
    @Query(
        "SELECT DISTINCT chatId FROM generated_videos " +
            "WHERE status NOT IN ('completed','failed','cancelled','expired')"
    )
    fun observeRenderingChatIds(): Flow<List<String>>

    @Query("DELETE FROM generated_videos WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ResearchRunDao {
    @Query("SELECT * FROM research_runs WHERE chatId = :chatId AND status NOT IN ('completed','failed','cancelled') ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveForChat(chatId: String): Flow<ResearchRun?>

    @Query("SELECT * FROM research_runs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ResearchRun?

    /** Backs the result card, which reads a finished run's step timeline and full source records. */
    @Query("SELECT * FROM research_runs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ResearchRun?>

    @Query("SELECT * FROM research_runs WHERE status NOT IN ('completed','failed','cancelled')")
    suspend fun getInterrupted(): List<ResearchRun>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: ResearchRun)

    @Query("SELECT COUNT(*) FROM research_runs WHERE chatId = :chatId AND status NOT IN ('completed','failed','cancelled')")
    suspend fun countActiveForChat(chatId: String): Int

    /**
     * Insert [run] only if its chat has nothing in flight, returning whether it was inserted.
     *
     * Checking the observed run flow and then inserting is not enough: two taps can both pass the
     * check before either write becomes visible, which would start two foreground services for
     * one conversation. Doing both inside one transaction is what makes the guard hold.
     */
    @Transaction
    suspend fun insertIfChatIdle(run: ResearchRun): Boolean {
        if (countActiveForChat(run.chatId) > 0) return false
        upsert(run)
        return true
    }

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
