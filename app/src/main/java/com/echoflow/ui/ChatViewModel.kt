package com.echoflow.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private val openRouterService = OpenRouterService(application)

    val allThreads: StateFlow<List<ChatThread>> = chatDao.getAllThreads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _currentChatThreadId = MutableStateFlow<String?>(null)
    val currentChatThreadId: StateFlow<String?> = _currentChatThreadId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<ChatMessage>> = _currentChatThreadId
        .flatMapLatest { chatId ->
            if (chatId == null) {
                flowOf(emptyList())
            } else {
                messageDao.getMessagesForChat(chatId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Streaming & Loading states
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _activeStreamingBuffer = MutableStateFlow("")
    val activeStreamingBuffer: StateFlow<String> = _activeStreamingBuffer.asStateFlow()

    private val _activeReasoningBuffer = MutableStateFlow("")
    val activeReasoningBuffer: StateFlow<String> = _activeReasoningBuffer.asStateFlow()

    private val _apiProgressLoading = MutableStateFlow(false)
    val apiProgressLoading: StateFlow<Boolean> = _apiProgressLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Pending attachment references
    private val _pendingAttachmentUri = MutableStateFlow<Uri?>(null)
    val pendingAttachmentUri: StateFlow<Uri?> = _pendingAttachmentUri.asStateFlow()

    private val _pendingAttachmentMimeType = MutableStateFlow<String?>(null)
    val pendingAttachmentMimeType: StateFlow<String?> = _pendingAttachmentMimeType.asStateFlow()

    private val _pendingAttachmentName = MutableStateFlow<String?>(null)
    val pendingAttachmentName: StateFlow<String?> = _pendingAttachmentName.asStateFlow()

    fun selectThread(chatId: String?) {
        _currentChatThreadId.value = chatId
        clearPendingAttachment()
        clearError()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setPendingAttachment(uri: Uri) {
        viewModelScope.launch {
            _pendingAttachmentUri.value = uri
            val resolver = getApplication<Application>().contentResolver
            _pendingAttachmentMimeType.value = resolver.getType(uri) ?: "image/jpeg"

            // Get display name
            var displayName = "Attached Image"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            _pendingAttachmentName.value = displayName
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachmentUri.value = null
        _pendingAttachmentMimeType.value = null
        _pendingAttachmentName.value = null
    }

    fun startNewChat() {
        selectThread(null)
    }

    fun deleteThread(thread: ChatThread) {
        viewModelScope.launch {
            chatDao.deleteThread(thread)
            if (_currentChatThreadId.value == thread.id) {
                selectThread(allThreads.value.firstOrNull { it.id != thread.id }?.id)
            }
        }
    }

    fun renameThread(thread: ChatThread, newTitle: String) {
        viewModelScope.launch {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isNotEmpty()) {
                chatDao.updateThread(thread.copy(title = cleanTitle, updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun sendMessage(content: String) {
        val prompt = content.trim()
        val attachmentUri = _pendingAttachmentUri.value?.toString()
        val attachmentMime = _pendingAttachmentMimeType.value
        val attachmentName = _pendingAttachmentName.value

        if (prompt.isEmpty() && attachmentUri == null) return

        viewModelScope.launch {
            clearPendingAttachment()
            clearError()

            val apiKey = settingsRepository.getApiKeyDirect()
            val selectedModel = settingsRepository.getSelectedModelDirect()
            val webSearchEnabled = settingsRepository.getWebSearchEnabledDirect()

            if (apiKey.isBlank()) {
                _errorMessage.value = "OpenRouter API Key is missing! Go to Settings to configure it."
                return@launch
            }

            var isFirstMsgInChat = false
            var chatId = _currentChatThreadId.value

            if (chatId == null) {
                isFirstMsgInChat = true
                chatId = UUID.randomUUID().toString()
                val newThread = ChatThread(
                    id = chatId,
                    title = "New Conversation",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                chatDao.insertThread(newThread)
                _currentChatThreadId.value = chatId
            }

            // Insert User Message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "user",
                content = prompt,
                createdAt = System.currentTimeMillis(),
                localAttachmentUri = attachmentUri,
                localAttachmentMimeType = attachmentMime,
                localAttachmentName = attachmentName
            )
            messageDao.insertMessage(userMsg)

            // Update Thread Timestamp
            val tempThread = chatDao.getThreadById(chatId)
            if (tempThread != null) {
                chatDao.updateThread(tempThread.copy(updatedAt = System.currentTimeMillis()))
            }

            // Trigger background Title generation
            if (isFirstMsgInChat) {
                launch {
                    val generatedTitle = openRouterService.generateTitle(apiKey, selectedModel, prompt)
                    val activeThread = chatDao.getThreadById(chatId!!)
                    if (activeThread != null) {
                        chatDao.updateThread(activeThread.copy(title = generatedTitle))
                    }
                }
            }

            // Load updated dialog history
            val fullHistory = messageDao.getMessagesForChatSync(chatId)

            // Begin Streaming Assistant response
            _isStreaming.value = true
            _activeStreamingBuffer.value = ""
            _activeReasoningBuffer.value = ""
            _apiProgressLoading.value = true // Show leading load before tokens stream

            var accumulatedResponse = ""
            var accumulatedReasoning = ""
            try {
                openRouterService.sendChatMessageStream(apiKey, selectedModel, fullHistory, webSearchEnabled)
                    .collect { chunk ->
                        _apiProgressLoading.value = false // Dismiss initial load as stream flows
                        when (chunk) {
                            is StreamChunk.Reasoning -> {
                                accumulatedReasoning += chunk.text
                                _activeReasoningBuffer.value = accumulatedReasoning
                            }
                            is StreamChunk.Content -> {
                                accumulatedResponse += chunk.text
                                _activeStreamingBuffer.value = accumulatedResponse
                            }
                        }
                    }

                // If content streamed, write to local db
                if (accumulatedResponse.isNotEmpty()) {
                    val assistantMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = "assistant",
                        content = accumulatedResponse,
                        createdAt = System.currentTimeMillis(),
                        reasoning = accumulatedReasoning.ifBlank { null }
                    )
                    messageDao.insertMessage(assistantMsg)

                    // Touch updatedAt
                    chatDao.getThreadById(chatId)?.let { currentT ->
                        chatDao.updateThread(currentT.copy(updatedAt = System.currentTimeMillis()))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message ?: "An unexpected error occurred during chat."

                // If some partial tokens accumulated, save them as conversational response to avoid loss
                if (accumulatedResponse.trim().isNotEmpty()) {
                    val assistantMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        chatId = chatId,
                        role = "assistant",
                        content = "$accumulatedResponse\n\n*[Connection lost: ${e.message}]*",
                        createdAt = System.currentTimeMillis(),
                        reasoning = accumulatedReasoning.ifBlank { null }
                    )
                    messageDao.insertMessage(assistantMsg)
                }
            } finally {
                _isStreaming.value = false
                _activeStreamingBuffer.value = ""
                _activeReasoningBuffer.value = ""
                _apiProgressLoading.value = false
            }
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            chatDao: ChatDao,
            messageDao: MessageDao,
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(application, chatDao, messageDao, settingsRepository) as T
            }
        }
    }
}
