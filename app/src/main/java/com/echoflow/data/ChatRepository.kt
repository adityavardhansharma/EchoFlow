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
    fun messagesForChat(chatId: String): Flow<List<ChatMessage>> = messageDao.getMessagesForChat(chatId)
    fun searchChatIdsByContent(query: String): Flow<List<String>> = messageDao.searchChatIdsByContent(query)

    suspend fun createThread(title: String = "New Conversation", now: Long = System.currentTimeMillis()): ChatThread {
        val thread = ChatThread(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.insertThread(thread)
        return thread
    }

    suspend fun touchThread(chatId: String, now: Long = System.currentTimeMillis()) {
        chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(updatedAt = now)) }
    }

    suspend fun renameThread(chatId: String, title: String) {
        chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(title = title)) }
    }

    suspend fun insertMessage(message: ChatMessage) = messageDao.insertMessage(message)
    suspend fun history(chatId: String): List<ChatMessage> = messageDao.getMessagesForChatSync(chatId)
    suspend fun thread(chatId: String): ChatThread? = chatDao.getThreadById(chatId)
}

