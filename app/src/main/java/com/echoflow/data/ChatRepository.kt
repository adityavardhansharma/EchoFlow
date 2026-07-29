package com.echoflow.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(
    val chatDao: ChatDao,
    val messageDao: MessageDao,
    val localModelDao: LocalModelDao,
    val researchRunDao: ResearchRunDao,
    val deepResearchModelDao: DeepResearchModelDao,
    val advisorProfileDao: AdvisorProfileDao,
    val fusionPanelDao: FusionPanelDao,
    val agentProfileDao: AgentProfileDao,
    val browserSessionDao: BrowserSessionDao,
    val browserStepDao: BrowserStepDao,
    val artifactDao: ArtifactDao,
    val artifactVersionDao: ArtifactVersionDao,
) {
    fun allThreads(): Flow<List<ChatThread>> = chatDao.getAllThreads()

    /** One mode's conversations. The drawer never mixes the two. */
    fun threadsForMode(mode: AppMode): Flow<List<ChatThread>> = chatDao.getThreadsByKind(mode.storageKey)

    /** How many of the *other* mode's conversations a search would have matched. */
    fun searchMatchCount(mode: AppMode, query: String): Flow<Int> =
        chatDao.countMatchingInKind(mode.storageKey, query)

    fun messagesForChat(chatId: String): Flow<List<ChatMessage>> = messageDao.getMessagesForChat(chatId)
    fun searchChatIdsByContent(query: String): Flow<List<String>> = messageDao.searchChatIdsByContent(query)

    /**
     * Creates a conversation owned by [mode]. This is the single place a thread's kind is
     * ever decided; it is immutable afterwards, so there is no drift to keep in sync.
     */
    suspend fun createThread(
        mode: AppMode = AppMode.Chat,
        title: String = defaultTitleFor(mode),
        now: Long = System.currentTimeMillis(),
    ): ChatThread {
        val thread = ChatThread(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
            kind = mode.storageKey,
        )
        chatDao.insertThread(thread)
        return thread
    }

    private fun defaultTitleFor(mode: AppMode) = when (mode) {
        AppMode.Chat -> "New Conversation"
        AppMode.Imagine -> "New Creation"
    }

    suspend fun touchThread(chatId: String, now: Long = System.currentTimeMillis()) {
        chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(updatedAt = now)) }
    }

    suspend fun renameThread(chatId: String, title: String) {
        chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(title = title)) }
    }

    suspend fun insertMessage(message: ChatMessage) = messageDao.insertMessage(message)
    suspend fun deleteMessage(messageId: String) = messageDao.deleteMessageById(messageId)
    suspend fun applyEditedUserTurn(updatedUser: ChatMessage, assistantMessageId: String?) =
        messageDao.applyEditedUserTurn(updatedUser, assistantMessageId)
    suspend fun history(chatId: String): List<ChatMessage> = messageDao.getMessagesForChatSync(chatId)
    suspend fun thread(chatId: String): ChatThread? = chatDao.getThreadById(chatId)
}

