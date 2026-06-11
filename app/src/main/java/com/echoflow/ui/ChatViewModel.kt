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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** One visual block of the in-progress assistant reply, rendered in arrival order. */
sealed class StreamSegment {
    data class Text(val text: String) : StreamSegment()
    data class Reasoning(val text: String) : StreamSegment()
    data class Search(
        val query: String,
        val sources: List<SearchSource>,
        val active: Boolean
    ) : StreamSegment()
}

private data class ActiveStreamState(
    val segments: List<StreamSegment> = emptyList(),
    val statusNote: String? = null,
    val progressLoading: Boolean = false,
    val isLocal: Boolean = false
)

class ChatViewModel(
    application: Application,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val localModelDao: LocalModelDao
) : AndroidViewModel(application) {

    private val openRouterService = OpenRouterService(application)
    private val webSearchService = WebSearchService()
    private val localLlmService = LocalLlmService(application)

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

    // Streaming & Loading states. Streams are scoped per chat so a live reply in one
    // conversation never appears while viewing another conversation.
    private val _activeStreams = MutableStateFlow<Map<String, ActiveStreamState>>(emptyMap())

    val isStreaming: StateFlow<Boolean> = combine(_currentChatThreadId, _activeStreams) { chatId, streams ->
        chatId != null && streams.containsKey(chatId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /** Ordered timeline of the in-progress reply: text, reasoning and search blocks. */
    val activeSegments: StateFlow<List<StreamSegment>> = combine(_currentChatThreadId, _activeStreams) { chatId, streams ->
        streams[chatId]?.segments ?: emptyList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Transient status line shown under the streaming bubble (e.g. search failures). */
    val statusNote: StateFlow<String?> = combine(_currentChatThreadId, _activeStreams) { chatId, streams ->
        streams[chatId]?.statusNote
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val apiProgressLoading: StateFlow<Boolean> = combine(_currentChatThreadId, _activeStreams) { chatId, streams ->
        streams[chatId]?.progressLoading == true
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /** True while an on-device model is being loaded into RAM / its context prefilled. */
    val localModelLoading: StateFlow<Boolean> = combine(
        _currentChatThreadId,
        _activeStreams,
        localLlmService.modelLoading
    ) { chatId, streams, modelLoading ->
        modelLoading && streams[chatId]?.isLocal == true
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val anyLocalStreamActive: StateFlow<Boolean> = _activeStreams
        .map { streams -> streams.values.any { it.isLocal } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val streamJobs = mutableMapOf<String, Job>()

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

    fun stopStreaming() {
        _currentChatThreadId.value?.let { chatId ->
            streamJobs[chatId]?.cancel()
        }
    }

    // -------------------------------------------------------------------------------
    // Segment reducer
    // -------------------------------------------------------------------------------

    private fun reduceSegments(segments: MutableList<StreamSegment>, chunk: StreamChunk): String? {
        when (chunk) {
            is StreamChunk.Reasoning -> {
                val last = segments.lastOrNull()
                if (last is StreamSegment.Reasoning) {
                    segments[segments.lastIndex] = last.copy(text = last.text + chunk.text)
                } else {
                    segments.add(StreamSegment.Reasoning(chunk.text))
                }
            }
            is StreamChunk.Content -> {
                val last = segments.lastOrNull()
                if (last is StreamSegment.Text) {
                    segments[segments.lastIndex] = last.copy(text = last.text + chunk.text)
                } else {
                    // A Text block after a Search block is what creates the t3-style
                    // "searched → wrote → searched again" interleave.
                    segments.add(StreamSegment.Text(chunk.text))
                }
            }
            is StreamChunk.SearchStarted -> {
                segments.add(StreamSegment.Search(chunk.query, emptyList(), active = true))
            }
            is StreamChunk.SearchSources -> {
                val activeIdx = segments.indexOfLast { it is StreamSegment.Search && it.active }
                if (activeIdx >= 0) {
                    val seg = segments[activeIdx] as StreamSegment.Search
                    segments[activeIdx] = seg.copy(sources = seg.sources + chunk.sources, active = false)
                } else {
                    // Sources without a started search (e.g. server-tool annotations whose
                    // tool-call delta shape we didn't recognize) still get a card.
                    segments.add(StreamSegment.Search(chunk.query, chunk.sources, active = false))
                }
            }
            is StreamChunk.StatusNote -> {
                return chunk.text
            }
        }
        return null
    }

    // -------------------------------------------------------------------------------
    // Send + routing
    // -------------------------------------------------------------------------------

    private fun setStreamState(chatId: String, state: ActiveStreamState?) {
        _activeStreams.value = _activeStreams.value.toMutableMap().apply {
            if (state == null) remove(chatId) else put(chatId, state)
        }
    }

    fun sendMessage(content: String) {
        val prompt = content.trim()
        val attachmentUri = _pendingAttachmentUri.value?.toString()
        val attachmentMime = _pendingAttachmentMimeType.value
        val attachmentName = _pendingAttachmentName.value

        if (prompt.isEmpty() && attachmentUri == null) return
        _currentChatThreadId.value?.let { chatId ->
            if (streamJobs[chatId]?.isActive == true) return
        }

        viewModelScope.launch {
            clearPendingAttachment()
            clearError()

            val apiKey = settingsRepository.getApiKeyDirect()
            val selectedModel = settingsRepository.getSelectedModelDirect()
            val provider = settingsRepository.getWebSearchProviderDirect()
            val searchScope = settingsRepository.getWebSearchScopeDirect()
            val isLocal = selectedModel.startsWith("local/")

            if (isLocal && _activeStreams.value.values.any { it.isLocal }) {
                _errorMessage.value = "The on-device model is still responding. Wait for it to finish before starting another local reply."
                return@launch
            }

            if (!isLocal && apiKey.isBlank()) {
                _errorMessage.value = "OpenRouter API Key is missing! Go to Settings to configure it."
                return@launch
            }

            var localModel: LocalModel? = null
            if (isLocal) {
                localModel = localModelDao.getLocalModelById(selectedModel)
                if (localModel == null || !localLlmService.modelFileExists(localModel)) {
                    _errorMessage.value = "The selected on-device model is missing. Re-download or re-import it in Settings."
                    return@launch
                }
            }

            val searchKey = settingsRepository.getSearchApiKeyDirect(provider)
            val clientSearchReady = provider in CLIENT_SEARCH_PROVIDERS && searchKey.isNotBlank()
            val searchAllowedForModel = when (searchScope) {
                "cloud" -> !isLocal
                "local" -> isLocal
                else -> true
            }

            // OpenRouter's server-side search cannot serve on-device models; a client
            // provider without a key is also unusable. Both degrade to "off" prompts.
            val effectiveProvider = when {
                !searchAllowedForModel -> "off"
                isLocal && provider == "openrouter" -> "off"
                provider == "openrouter" -> "openrouter"
                clientSearchReady -> provider
                else -> "off"
            }
            val systemPrompt = SystemPrompts.build(isLocal, effectiveProvider)

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

            if (streamJobs[chatId]?.isActive == true) return@launch
            coroutineContext[Job]?.let { streamJobs[chatId] = it }

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

            // Trigger background Title generation. Local chats use the word fallback:
            // no API key may exist, and the on-device engine is single-flight.
            if (isFirstMsgInChat) {
                if (isLocal) {
                    val words = prompt.split("\\s+".toRegex())
                    val fallbackTitle = words.take(4).joinToString(" ") + if (words.size > 4) "..." else ""
                    chatDao.getThreadById(chatId)?.let { thread ->
                        chatDao.updateThread(thread.copy(title = fallbackTitle))
                    }
                } else {
                    launch {
                        val generatedTitle = openRouterService.generateTitle(apiKey, selectedModel, prompt)
                        val activeThread = chatDao.getThreadById(chatId!!)
                        if (activeThread != null) {
                            chatDao.updateThread(activeThread.copy(title = generatedTitle))
                        }
                    }
                }
            }

            // Load updated dialog history
            val fullHistory = messageDao.getMessagesForChatSync(chatId)

            val responseFlow: Flow<StreamChunk> = when {
                isLocal && clientSearchReady ->
                    localPromptProtocolFlow(localModel!!, chatId, fullHistory, systemPrompt, provider, searchKey)
                isLocal ->
                    localLlmService.generate(localModel!!, chatId, fullHistory, systemPrompt)
                provider == "openrouter" ->
                    openRouterService.sendChatMessageStream(apiKey, selectedModel, fullHistory, systemPrompt, serverWebSearch = true)
                clientSearchReady ->
                    openRouterService.sendWithClientSearch(apiKey, selectedModel, fullHistory, systemPrompt) { query ->
                        webSearchService.search(provider, searchKey, query)
                    }
                else ->
                    openRouterService.sendChatMessageStream(apiKey, selectedModel, fullHistory, systemPrompt, serverWebSearch = false)
            }

            // Begin Streaming Assistant response
            setStreamState(
                chatId,
                ActiveStreamState(
                    segments = emptyList(),
                    statusNote = null,
                    progressLoading = true,
                    isLocal = isLocal
                )
            )

            val segments = mutableListOf<StreamSegment>()
            var statusNote: String? = null
            try {
                responseFlow.collect { chunk ->
                    val note = reduceSegments(segments, chunk)
                    if (note != null) statusNote = note
                    setStreamState(
                        chatId,
                        ActiveStreamState(
                            segments = segments.toList(),
                            statusNote = statusNote,
                            progressLoading = false,
                            isLocal = isLocal
                        )
                    )
                }
                persistAssistantMessage(chatId, segments, interrupted = null)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message ?: "An unexpected error occurred during chat."
                persistAssistantMessage(chatId, segments, interrupted = e.message)
            } finally {
                streamJobs.remove(chatId)
                setStreamState(chatId, null)
            }
        }
    }

    private suspend fun persistAssistantMessage(
        chatId: String,
        segments: List<StreamSegment>,
        interrupted: String?
    ) {
        val contentText = segments.filterIsInstance<StreamSegment.Text>()
            .joinToString("\n\n") { it.text }.trim()
        if (contentText.isEmpty()) return

        val reasoningText = segments.filterIsInstance<StreamSegment.Reasoning>()
            .joinToString("\n\n") { it.text }.trim()

        val toolEvents = segments.mapIndexedNotNull { index, seg ->
            (seg as? StreamSegment.Search)?.let {
                ToolEvent(query = it.query, sources = it.sources, orderIndex = index)
            }
        }
        val citations = toolEvents.flatMap { it.sources }
            .distinctBy { it.url }
            .map { Citation(title = it.title, url = it.url) }

        val finalContent = if (interrupted != null) {
            "$contentText\n\n*[Connection lost: $interrupted]*"
        } else contentText

        messageDao.insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = finalContent,
                createdAt = System.currentTimeMillis(),
                reasoning = reasoningText.ifBlank { null },
                toolEventsJson = ToolEventJson.toolEventsToJson(toolEvents),
                citationsJson = ToolEventJson.citationsToJson(citations)
            )
        )

        chatDao.getThreadById(chatId)?.let { currentT ->
            chatDao.updateThread(currentT.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // -------------------------------------------------------------------------------
    // Local model + client search: prompt-based tool protocol
    // -------------------------------------------------------------------------------

    /** Thrown to abort collection of a local generation once a complete tag is parsed. */
    private class SearchTagFound : Exception()

    private enum class TagState { HOLDING, TEXT, TAG_XML, TAG_PLAIN }

    /**
     * Agentic search loop for on-device models. The system prompt instructs the model to
     * reply with a single `search: query` line when it needs the web; output is
     * held back until it's clear whether the reply is a tag or normal text, so partial
     * tags never reach the UI. Results are injected into the live session and generation
     * continues, up to [MAX_LOCAL_SEARCH_ROUNDS] rounds.
     */
    private fun localPromptProtocolFlow(
        model: LocalModel,
        chatId: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        provider: String,
        searchKey: String
    ): Flow<StreamChunk> = flow {
        var round = 0
        var continuation = false

        while (true) {
            val allowTag = round < MAX_LOCAL_SEARCH_ROUNDS
            val upstream = if (continuation) {
                localLlmService.continueGeneration()
            } else {
                localLlmService.generate(model, chatId, history, systemPrompt)
            }

            val buf = StringBuilder()
            var state = if (allowTag) TagState.HOLDING else TagState.TEXT
            var emittedLen = 0

            try {
                upstream.collect { chunk ->
                    if (chunk !is StreamChunk.Content) {
                        emit(chunk)
                        return@collect
                    }
                    buf.append(chunk.text)
                    val trimmed = buf.toString().trimStart()

                    if (state == TagState.HOLDING) {
                        state = when {
                            trimmed.startsWith("<search>") -> TagState.TAG_XML
                            trimmed.lowercase().startsWith("search:") -> TagState.TAG_PLAIN
                            trimmed.isNotEmpty() &&
                                !"<search>".startsWith(trimmed.take(8)) &&
                                !"search:".startsWith(trimmed.lowercase().take(7)) -> TagState.TEXT
                            else -> TagState.HOLDING
                        }
                    }

                    when (state) {
                        TagState.TEXT -> {
                            val full = buf.toString()
                            if (emittedLen < full.length) {
                                emit(StreamChunk.Content(full.substring(emittedLen)))
                                emittedLen = full.length
                            }
                        }
                        TagState.TAG_XML -> if (trimmed.contains("</search>")) throw SearchTagFound()
                        TagState.TAG_PLAIN -> if (trimmed.contains("\n")) throw SearchTagFound()
                        TagState.HOLDING -> Unit
                    }
                }
            } catch (e: SearchTagFound) {
                // Expected: upstream cancelled, the tag is complete in buf.
            }

            val query = when (state) {
                TagState.TAG_XML, TagState.HOLDING, TagState.TAG_PLAIN -> extractSearchQuery(buf.toString())
                TagState.TEXT -> null
            }

            if (query == null) {
                // Normal answer (or an unparseable tag): flush anything still held back.
                if (state != TagState.TEXT) {
                    val leftover = buf.toString().trim()
                    if (leftover.isNotEmpty() && extractSearchQuery(leftover) == null) {
                        emit(StreamChunk.Content(leftover))
                    }
                }
                break
            }

            emit(StreamChunk.SearchStarted(query))
            val sources = try {
                webSearchService.search(provider, searchKey, query)
            } catch (e: Exception) {
                emit(StreamChunk.StatusNote("Search failed: ${e.message}"))
                emptyList()
            }
            emit(StreamChunk.SearchSources(query, sources))

            val resultBlock = if (sources.isEmpty()) {
                "The search failed or returned nothing. Answer from your own knowledge and " +
                    "tell the user you could not verify current information."
            } else {
                "Search results for \"$query\":\n" + formatSearchResultsForModel(sources)
            }
            round++
            val instruction = if (round >= MAX_LOCAL_SEARCH_ROUNDS) {
                "\n\nAnswer the user's question now using these results, citing claims as [n](url). " +
                    "Do not search again.\n\nEchoFlow reply:"
            } else {
                "\n\nAnswer the user's question now using these results, citing claims as [n](url). " +
                    "Only reply with another single-line search: query if these results are truly insufficient.\n\nEchoFlow reply:"
            }
            localLlmService.appendContext(resultBlock + instruction)
            continuation = true
        }
    }

    private fun extractSearchQuery(text: String): String? {
        val xml = Regex("<search>(.*?)</search>", RegexOption.DOT_MATCHES_ALL).find(text)
        if (xml != null) {
            return xml.groupValues[1].trim().lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }
        // Unterminated tag at stream end: take what followed the opener.
        val open = text.indexOf("<search>")
        if (open >= 0) {
            return text.substring(open + "<search>".length)
                .substringBefore("</search").trim()
                .lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }
        val plain = Regex("^\\s*search:\\s*(.+)$", RegexOption.IGNORE_CASE)
            .find(text.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
        return plain?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    override fun onCleared() {
        localLlmService.releaseAll()
        super.onCleared()
    }

    companion object {
        private val CLIENT_SEARCH_PROVIDERS = setOf("exa", "parallel", "firecrawl")
        private const val MAX_LOCAL_SEARCH_ROUNDS = 3

        fun provideFactory(
            application: Application,
            chatDao: ChatDao,
            messageDao: MessageDao,
            settingsRepository: SettingsRepository,
            localModelDao: LocalModelDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(application, chatDao, messageDao, settingsRepository, localModelDao) as T
            }
        }
    }
}
