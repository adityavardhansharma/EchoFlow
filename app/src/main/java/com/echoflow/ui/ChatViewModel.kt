package com.echoflow.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.echoflow.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /** Echo Adviser consultation: the question asked and the advice (null while pending). */
    data class Advisor(
        val advisorName: String,
        val advisorModel: String,
        val prompt: String,
        val advice: String?,
        val active: Boolean
    ) : StreamSegment()

    /** Echo Fusion deliberation: the panel roster and the judge's analysis (null while pending). */
    data class Fusion(
        val panelName: String,
        val models: List<String>,
        val analysis: FusionAnalysis?,
        val active: Boolean
    ) : StreamSegment()

    /** Transient "Echo Agents are deploying…" banner shown while the non-streaming request runs. */
    object AgentRun : StreamSegment()

    /** Echo Agent delegation: a task handed to the worker and its outcome (null while running). */
    data class Subagent(
        val taskName: String,
        val taskDescription: String,
        val workerModel: String,
        val outcome: String?,
        val error: String?,
        val active: Boolean
    ) : StreamSegment()

    /**
     * Artifact card: shown while the model builds an artifact ([building] = true, with a live
     * [charCount]) and afterwards as a finished card carrying the persisted [artifactId]/[version].
     */
    data class Artifact(
        val artifactId: String?,
        val title: String,
        val artifactType: String,
        val version: Int,
        val charCount: Int,
        val building: Boolean,
        val truncated: Boolean,
    ) : StreamSegment()
}

private data class ActiveStreamState(
    val segments: List<StreamSegment> = emptyList(),
    val statusNote: String? = null,
    val progressLoading: Boolean = false,
    val isLocal: Boolean = false
)

data class BrowserStartConflict(
    val activeSession: BrowserSession,
    val targetChatId: String?,
    val pendingPrompt: String,
)

class ChatViewModel(
    application: Application,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val settingsRepository: SettingsRepository,
    private val localModelDao: LocalModelDao,
    private val researchRunDao: ResearchRunDao,
    private val deepResearchModelDao: DeepResearchModelDao,
    private val advisorProfileDao: AdvisorProfileDao,
    private val fusionPanelDao: FusionPanelDao,
    private val agentProfileDao: AgentProfileDao,
    private val browserSessionDao: BrowserSessionDao,
    private val browserStepDao: BrowserStepDao,
    private val artifactDao: ArtifactDao,
    private val artifactVersionDao: ArtifactVersionDao
) : AndroidViewModel(application) {

    // Artifacts: model-built, rendered content (web page / document / report) with version history.
    private val artifactManager = ArtifactManager(artifactDao, artifactVersionDao)

    private val openRouterService = OpenRouterService(application)
    private val customProviderService = CustomProviderService(application)
    private val webSearchService = WebSearchService()
    private val localLlmService = LocalLlmService(application)
    private val chatRepository = ChatRepository(
        chatDao = chatDao,
        messageDao = messageDao,
        localModelDao = localModelDao,
        researchRunDao = researchRunDao,
        deepResearchModelDao = deepResearchModelDao,
        advisorProfileDao = advisorProfileDao,
        fusionPanelDao = fusionPanelDao,
        agentProfileDao = agentProfileDao,
        browserSessionDao = browserSessionDao,
        browserStepDao = browserStepDao,
        artifactDao = artifactDao,
        artifactVersionDao = artifactVersionDao,
    )
    private val openRouterGateway = OpenRouterGateway(openRouterService)
    private val localGateway = LocalLlmGateway(localLlmService)

    // Browser Flow: stateful Firecrawl browser controlled through chat. The manager owns all
    // orchestration and writes session/step rows; the UI observes them.
    private val browserAgent = BrowserAgentManager(
        chatDao, messageDao, browserSessionDao, browserStepDao, settingsRepository, webSearchService, viewModelScope
    )

    val allThreads: StateFlow<List<ChatThread>> = chatRepository.allThreads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Drawer search: matches conversation titles and full message text.
    private val _drawerSearchQuery = MutableStateFlow("")
    val drawerSearchQuery: StateFlow<String> = _drawerSearchQuery.asStateFlow()

    fun setDrawerSearchQuery(query: String) {
        _drawerSearchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredThreads: StateFlow<List<ChatThread>> = combine(
        allThreads,
        _drawerSearchQuery,
        _drawerSearchQuery.flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else chatRepository.searchChatIdsByContent(query.trim())
        },
    ) { threads, query, contentMatchIds ->
        val q = query.trim()
        if (q.isEmpty()) threads
        else threads.filter { it.title.contains(q, ignoreCase = true) || it.id in contentMatchIds }
    }.stateIn(
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
                chatRepository.messagesForChat(chatId)
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

    // Per-message capability selected by the "+" menu. Exposed as legacy boolean flows so
    // existing UI components can stay simple while illegal combinations remain unrepresentable.
    private val _chatMode = MutableStateFlow<ChatMode>(ChatMode.Normal)
    val chatMode: StateFlow<ChatMode> = _chatMode.asStateFlow()

    val deepResearchActive: StateFlow<Boolean> = modeIs<ChatMode.DeepResearch>()
    val webSearchChipOn: StateFlow<Boolean> = modeIs<ChatMode.WebSearch>()
    val dataAgentActive: StateFlow<Boolean> = modeIs<ChatMode.DataAgent>()

    // Echo Adviser / Echo Fusion: OpenRouter-only modes chosen from the "+" menu. Persist
    // across messages (a deliberate, cost-heavy choice) until the user turns them off.
    val echoAdviserActive: StateFlow<Boolean> = modeIs<ChatMode.EchoAdviser>()
    val echoFusionActive: StateFlow<Boolean> = modeIs<ChatMode.EchoFusion>()
    val echoAgentActive: StateFlow<Boolean> = modeIs<ChatMode.EchoAgent>()

    // Artifact mode: sticky like the Echo modes. While on, every turn builds/updates an artifact;
    // follow-ups iterate the chat's current artifact (full version history).
    val artifactActive: StateFlow<Boolean> = modeIs<ChatMode.Artifact>()

    /** The chat's current artifact (latest lineage), observed by the in-chat card and workspace. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentArtifact: StateFlow<Artifact?> = _currentChatThreadId
        .flatMapLatest { id -> if (id == null) flowOf(null) else artifactManager.observeForChat(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** All versions of the current artifact (drives the workspace version switcher). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentArtifactVersions: StateFlow<List<ArtifactVersion>> = currentArtifact
        .flatMapLatest { a -> if (a == null) flowOf(emptyList()) else artifactManager.observeVersions(a.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** True while the fullscreen Artifact Workspace overlay is open. */
    private val _artifactWorkspaceOpen = MutableStateFlow(false)
    val artifactWorkspaceOpen: StateFlow<Boolean> = _artifactWorkspaceOpen.asStateFlow()

    fun openArtifactWorkspace() {
        // currentArtifact is a WhileSubscribed flow whose only collector is the workspace screen
        // itself, so outside it the cached .value is a stale null. Resolve existence straight from
        // the store instead, otherwise the guard is always false and "Open" silently does nothing.
        viewModelScope.launch {
            val chatId = _currentChatThreadId.value ?: return@launch
            if (artifactManager.getLatestForChat(chatId) != null) {
                _artifactWorkspaceOpen.value = true
            }
        }
    }
    fun closeArtifactWorkspace() { _artifactWorkspaceOpen.value = false }

    // Browser Flow igniter chip. Unlike Echo modes it is NOT sticky: it only *starts* a session;
    // once a session exists, the session row (not this flag) captures the owning chat's routing.
    val browserFlowActive: StateFlow<Boolean> = modeIs<ChatMode.BrowserFlow>()

    /** The single app-wide live browser session (start-lock + global pill). */
    val activeBrowserSession: StateFlow<BrowserSession?> = browserAgent.activeSession

    /** The live browser session owning the open chat, if any (drives the in-chat card/capture). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBrowserSession: StateFlow<BrowserSession?> = _currentChatThreadId
        .flatMapLatest { chatId -> if (chatId == null) flowOf(null) else browserAgent.observeForChat(chatId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Timeline steps for the open chat's session. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentBrowserSteps: StateFlow<List<BrowserStep>> = currentBrowserSession
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else browserAgent.observeSteps(s.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Browser Flow is offered when enabled in Settings and a Firecrawl key exists. */
    val browserFlowAvailable: StateFlow<Boolean> = combine(
        settingsRepository.browserFlowEnabled, settingsRepository.firecrawlApiKey
    ) { enabled, key -> enabled && key.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Which chat's Browser Workspace should be open fullscreen (null = closed). */
    private val _browserWorkspaceChatId = MutableStateFlow<String?>(null)
    val browserWorkspaceChatId: StateFlow<String?> = _browserWorkspaceChatId.asStateFlow()

    /** Set when the user tries to start a 2nd session while one is already live elsewhere. */
    private val _browserStartConflict = MutableStateFlow<BrowserStartConflict?>(null)
    val browserStartConflict: StateFlow<BrowserStartConflict?> = _browserStartConflict.asStateFlow()

    init {
        // Auto-open the workspace when a session goes live (the manager requests it).
        viewModelScope.launch {
            browserAgent.openWorkspaceFor.collect { chatId ->
                if (chatId != null) {
                    _currentChatThreadId.value = chatId
                    _browserWorkspaceChatId.value = chatId
                    browserAgent.clearWorkspaceRequest()
                }
            }
        }
    }

    /** The Deep Research engine/model the user has added (built-in provider engines + agentic). */
    val deepResearchModels: StateFlow<List<DeepResearchModel>> = deepResearchModelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The live (non-terminal) research run for the open conversation, if any. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentResearchRun: StateFlow<ResearchRun?> = _currentChatThreadId
        .flatMapLatest { chatId ->
            if (chatId == null) flowOf(null) else researchRunDao.observeActiveForChat(chatId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private inline fun <reified T : ChatMode> modeIs(): StateFlow<Boolean> =
        _chatMode
            .map { it is T }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _chatMode.value is T)

    private fun setMode(mode: ChatMode) {
        _chatMode.value = mode
    }

    private fun toggleMode(mode: ChatMode) {
        _chatMode.value = if (_chatMode.value == mode) ChatMode.Normal else mode
    }

    fun toggleArtifact() = toggleMode(ChatMode.Artifact)
    fun toggleDeepResearch() = toggleMode(ChatMode.DeepResearch)
    fun toggleWebSearchChip() = toggleMode(ChatMode.WebSearch)
    fun toggleDataAgent() = toggleMode(ChatMode.DataAgent)
    fun toggleEchoAdviser() = toggleMode(ChatMode.EchoAdviser)
    fun toggleEchoFusion() = toggleMode(ChatMode.EchoFusion)
    fun toggleEchoAgent() = toggleMode(ChatMode.EchoAgent)
    fun toggleBrowserFlow() = toggleMode(ChatMode.BrowserFlow)

    // ── Browser Flow actions (delegate to the manager; the card/workspace observe its rows) ──

    fun openBrowserWorkspace(chatId: String) {
        _currentChatThreadId.value = chatId
        clearError()
        _browserWorkspaceChatId.value = chatId
    }

    fun closeBrowserWorkspace() {
        _browserWorkspaceChatId.value = null
        browserAgent.clearWorkspaceRequest()
    }

    fun browserResolveCandidate(sessionId: String, url: String) = browserAgent.resolveCandidate(sessionId, url)
    fun browserConfirmDomain(sessionId: String) = browserAgent.confirmDomain(sessionId)
    fun browserConfirmSend(sessionId: String) = browserAgent.confirmSend(sessionId)
    fun browserCancelPending(sessionId: String) = browserAgent.cancelPending(sessionId)
    fun browserStop(sessionId: String) {
        browserAgent.stop(sessionId)
        if (_browserWorkspaceChatId.value != null) closeBrowserWorkspace()
    }
    fun browserFinish(sessionId: String) = browserAgent.finish(sessionId)

    fun dismissBrowserConflict() { _browserStartConflict.value = null }

    fun browserReturnToActive() {
        _browserStartConflict.value?.let { openBrowserWorkspace(it.activeSession.chatId) }
        _browserStartConflict.value = null
    }

    /** Finish the currently-active session, then start a new one in the chat that requested it. */
    fun browserFinishActiveThenStart() {
        val conflict = _browserStartConflict.value ?: return
        _browserStartConflict.value = null
        viewModelScope.launch {
            browserAgent.finishNow(conflict.activeSession.id)
            startBrowserSession(conflict.pendingPrompt, force = true, targetChatId = conflict.targetChatId)
        }
    }

    fun selectThread(chatId: String?) {
        _currentChatThreadId.value = chatId
        clearPendingAttachment()
        clearError()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setPendingAttachment(uri: Uri, fallbackMimeType: String? = null) {
        viewModelScope.launch {
            _pendingAttachmentUri.value = uri
            val resolver = getApplication<Application>().contentResolver
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mimeType = resolver.getType(uri) ?: fallbackMimeType ?: "image/jpeg"
            _pendingAttachmentMimeType.value = mimeType

            // Get display name
            var displayName = if (mimeType.equals("application/pdf", ignoreCase = true)) "Attached PDF" else "Attached Image"
            runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            _pendingAttachmentName.value = displayName
        }
    }

    fun setPendingPastedImage(uri: Uri, fallbackMimeType: String? = null) {
        viewModelScope.launch {
            val cached = copyPastedImageToCache(uri, fallbackMimeType)
            if (cached == null) {
                _errorMessage.value = "Could not paste image."
            } else {
                setPendingAttachment(cached.first, cached.second)
            }
        }
    }

    private suspend fun copyPastedImageToCache(uri: Uri, fallbackMimeType: String?): Pair<Uri, String>? =
        withContext(Dispatchers.IO) {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val mimeType = resolver.getType(uri) ?: fallbackMimeType ?: "image/png"
            if (!mimeType.startsWith("image/", ignoreCase = true)) return@withContext null

            runCatching {
                val dir = File(app.cacheDir, "pasted_images").apply { mkdirs() }
                val file = File.createTempFile("pasted_image_", ".${imageExtensionFor(mimeType)}", dir)
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                Uri.fromFile(file) to mimeType
            }.getOrNull()
        }

    private fun imageExtensionFor(mimeType: String): String =
        when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "img"
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
        // The "deploying agents" banner is only a waiting indicator: clear it the moment any real
        // reply content (a delegation, reasoning, the answer, …) starts to arrive.
        if (chunk !is StreamChunk.AgentRunStarted && chunk !is StreamChunk.StatusNote) {
            segments.removeAll { it is StreamSegment.AgentRun }
        }
        when (chunk) {
            is StreamChunk.AgentRunStarted -> {
                if (segments.none { it is StreamSegment.AgentRun }) segments.add(StreamSegment.AgentRun)
            }
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
            is StreamChunk.AdvisorStarted -> {
                segments.add(
                    StreamSegment.Advisor(
                        advisorName = chunk.advisorName,
                        advisorModel = chunk.advisorModel,
                        prompt = chunk.prompt,
                        advice = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.AdvisorResolved -> {
                val idx = segments.indexOfLast { it is StreamSegment.Advisor && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Advisor
                    segments[idx] = seg.copy(
                        advisorModel = chunk.advice.advisorModel.ifBlank { seg.advisorModel },
                        prompt = seg.prompt.ifBlank { chunk.advice.prompt },
                        advice = chunk.advice.advice,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Advisor(
                            advisorName = chunk.advice.advisorName,
                            advisorModel = chunk.advice.advisorModel,
                            prompt = chunk.advice.prompt,
                            advice = chunk.advice.advice,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.FusionStarted -> {
                segments.add(
                    StreamSegment.Fusion(
                        panelName = chunk.panelName,
                        models = chunk.models,
                        analysis = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.FusionResolved -> {
                val idx = segments.indexOfLast { it is StreamSegment.Fusion && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Fusion
                    segments[idx] = seg.copy(
                        models = seg.models.ifEmpty { chunk.analysis.models },
                        analysis = chunk.analysis,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Fusion(
                            panelName = chunk.analysis.panelName,
                            models = chunk.analysis.models,
                            analysis = chunk.analysis,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.SubagentStarted -> {
                segments.add(
                    StreamSegment.Subagent(
                        taskName = chunk.taskName,
                        taskDescription = chunk.taskDescription,
                        workerModel = chunk.workerModel,
                        outcome = null,
                        error = null,
                        active = true,
                    )
                )
            }
            is StreamChunk.SubagentResolved -> {
                val r = chunk.result
                // Match the running delegation by task name; fall back to the last active one.
                val idx = segments.indexOfLast {
                    it is StreamSegment.Subagent && it.active && (it.taskName == r.taskName || r.taskName.isBlank())
                }.takeIf { it >= 0 } ?: segments.indexOfLast { it is StreamSegment.Subagent && it.active }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Subagent
                    segments[idx] = seg.copy(
                        workerModel = r.workerModel.ifBlank { seg.workerModel },
                        outcome = r.outcome,
                        error = r.error,
                        active = false,
                    )
                } else {
                    segments.add(
                        StreamSegment.Subagent(
                            taskName = r.taskName,
                            taskDescription = r.taskDescription,
                            workerModel = r.workerModel,
                            outcome = r.outcome,
                            error = r.error,
                            active = false,
                        )
                    )
                }
            }
            is StreamChunk.ArtifactStarted -> {
                segments.add(
                    StreamSegment.Artifact(
                        artifactId = null,
                        title = chunk.title,
                        artifactType = chunk.artifactType,
                        version = 0,
                        charCount = 0,
                        building = true,
                        truncated = false,
                    )
                )
            }
            is StreamChunk.ArtifactProgress -> {
                val idx = segments.indexOfLast { it is StreamSegment.Artifact && it.building }
                if (idx >= 0) {
                    val seg = segments[idx] as StreamSegment.Artifact
                    segments[idx] = seg.copy(charCount = chunk.charCount)
                }
            }
            is StreamChunk.ArtifactCompleted -> {
                val idx = segments.indexOfLast { it is StreamSegment.Artifact && it.building }
                val finished = StreamSegment.Artifact(
                    artifactId = chunk.artifactId,
                    title = chunk.title,
                    artifactType = chunk.artifactType,
                    version = chunk.version,
                    charCount = chunk.content.length,
                    building = false,
                    truncated = chunk.truncated,
                )
                if (idx >= 0) segments[idx] = finished else segments.add(finished)
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

        // Deep Research and Data Agent are different pipelines (foreground service +
        // provider/agent orchestration), so they short-circuit the normal streaming send.
        if (_chatMode.value is ChatMode.DeepResearch && prompt.isNotEmpty()) {
            startDeepResearch(prompt, attachmentUri, attachmentMime, attachmentName)
            return
        }
        if (_chatMode.value is ChatMode.DataAgent && prompt.isNotEmpty()) {
            startDataAgent(prompt)
            return
        }
        // Browser Flow: the owning chat is captured — every message drives the same live browser.
        val activeBrowser = currentBrowserSession.value
        if (activeBrowser != null && prompt.isNotEmpty()) {
            clearPendingAttachment()
            browserAgent.sendCommand(activeBrowser.chatId, prompt)
            return
        }
        // Igniter: the "+ → Browser Flow" chip starts a new session, then turns itself off.
        if (_chatMode.value is ChatMode.BrowserFlow && prompt.isNotEmpty()) {
            startBrowserSession(prompt)
            return
        }

        _currentChatThreadId.value?.let { chatId ->
            if (streamJobs[chatId]?.isActive == true) return
        }

        viewModelScope.launch {
            clearPendingAttachment()
            clearError()

            val apiKey = settingsRepository.getApiKeyDirect()
            val selectedModel = settingsRepository.getSelectedModelDirect()
            val customProviderConfig = settingsRepository.getCustomProviderConfigDirect()
            val customProvider = when {
                selectedModel.startsWith(CustomProviderConfig.PREFIX_OPENAI) -> "openai"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_CLAUDE) -> "claude"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_GEMINI) -> "gemini"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_CEREBRAS) -> "cerebras"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_OLLAMA) -> "ollama"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE) -> "openai-compatible"
                else -> null
            }
            val customProviderActive = customProvider != null
            val requestModel = when (customProvider) {
                "openai" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_OPENAI)
                "claude" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_CLAUDE)
                "gemini" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_GEMINI)
                "cerebras" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_CEREBRAS)
                "ollama" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_OLLAMA)
                "openai-compatible" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_OPENAI_COMPATIBLE)
                else -> selectedModel
            }
            // The "+ → Web Search" chip force-enables search using the last-used provider,
            // so the user never has to open Settings just to search one message.
            val chipProvider = if (_chatMode.value is ChatMode.WebSearch) settingsRepository.resolveChipSearchProvider() else null
            val provider = chipProvider ?: settingsRepository.getWebSearchProviderDirect()
            val searchScope = if (chipProvider != null) "both" else settingsRepository.getWebSearchScopeDirect()
            // Echo Fusion always runs cloud models (the panel + judge), so it never uses the
            // on-device engine even if a local model is the global selection.
            val isLocal = selectedModel.startsWith("local/") && _chatMode.value !is ChatMode.EchoFusion

            if (isLocal && _activeStreams.value.values.any { it.isLocal }) {
                _errorMessage.value = "The on-device model is still responding. Wait for it to finish before starting another local reply."
                return@launch
            }

            if (isLocal && attachmentMime.equals("application/pdf", ignoreCase = true)) {
                _errorMessage.value = "PDF files work with OpenRouter models only. Pick a cloud model to attach this PDF."
                return@launch
            }

            if (!isLocal && !customProviderActive && apiKey.isBlank()) {
                _errorMessage.value = "OpenRouter API Key is missing! Go to Settings to configure it."
                return@launch
            }

            val customImageAllowed = when (customProvider) {
                "openai", "claude", "gemini" -> true
                "cerebras" -> CustomProviderCapabilities.cerebrasSupportsImages(requestModel)
                "ollama" -> customProviderConfig.ollamaImagesEnabled
                "openai-compatible" -> customProviderConfig.openAiCompatibleImagesEnabled
                else -> false
            }
            val customPdfAllowed = when (customProvider) {
                "openai", "claude", "gemini" -> true
                "cerebras" -> CustomProviderCapabilities.cerebrasSupportsPdfs(requestModel)
                "ollama" -> customProviderConfig.ollamaPdfsEnabled
                "openai-compatible" -> customProviderConfig.openAiCompatiblePdfsEnabled
                else -> false
            }
            val pendingIsPdf = attachmentMime.equals("application/pdf", ignoreCase = true)
            if (customProviderActive && attachmentUri != null && pendingIsPdf && !customPdfAllowed) {
                _errorMessage.value = "PDF is off for this custom endpoint. Turn it on in Settings → Echo Labs → Custom API Endpoint."
                return@launch
            }
            if (customProviderActive && attachmentUri != null && !pendingIsPdf && !customImageAllowed) {
                _errorMessage.value = "Images are off for this custom endpoint. Turn them on in Settings → Echo Labs → Custom API Endpoint."
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
                customProviderActive && provider == "openrouter" -> "off"
                provider == "openrouter" -> "openrouter"
                clientSearchReady -> provider
                else -> "off"
            }

            // Native tool calling for custom providers: the model itself calls web_search in a loop,
            // instead of the app pre-injecting one search. Needs a client search backend (so an
            // effective client provider). Cloud brands are always on; Ollama / OpenAI-compatible are
            // gated by a per-provider toggle since their tool support depends on the chosen model.
            val customToolCallingActive = customProviderActive &&
                effectiveProvider in CLIENT_SEARCH_PROVIDERS &&
                when (customProvider) {
                    "ollama" -> customProviderConfig.ollamaToolCallingEnabled
                    "openai-compatible" -> customProviderConfig.openAiCompatibleToolCallingEnabled
                    else -> true // OpenAI / Claude / Gemini / Cerebras
                }

            // Echo Adviser / Echo Fusion: OpenRouter-only modes. Resolve the active profile/panel
            // here; both override the model, system prompt and response flow. Cloud models only.
            var advisorReq: OpenRouterService.AdvisorRequest? = null
            var fusionReq: OpenRouterService.FusionRequest? = null
            var agentReq: OpenRouterService.AgentRequest? = null
            var echoModel = selectedModel
            var echoSystemPrompt: String? = null
            if (_chatMode.value is ChatMode.EchoAgent) {
                if (isLocal) {
                    _errorMessage.value = "Echo Agents needs a cloud main model. Pick one from the model selector."
                    return@launch
                }
                if (customProviderActive) {
                    _errorMessage.value = "Echo Agents uses OpenRouter server tools. Pick an OpenRouter Cloud model first."
                    return@launch
                }
                val profile = agentProfileDao.getById(settingsRepository.getEchoAgentProfileIdDirect())
                if (profile == null) {
                    _errorMessage.value = "Set up an Echo Agent first in Settings → Echo Agents, then pick it."
                    return@launch
                }
                agentReq = OpenRouterService.AgentRequest(profile.workerModelId, profile.workerModelName, profile.maxToolCalls)
                echoSystemPrompt = SystemPrompts.buildEchoAgent(profile.name)
            } else if (_chatMode.value is ChatMode.EchoAdviser) {
                if (isLocal) {
                    _errorMessage.value = "Echo Adviser needs a cloud model. Pick one from the model selector."
                    return@launch
                }
                if (customProviderActive) {
                    _errorMessage.value = "Echo Adviser uses OpenRouter server tools. Pick an OpenRouter Cloud model first."
                    return@launch
                }
                val profile = advisorProfileDao.getById(settingsRepository.getEchoAdviserProfileIdDirect())
                if (profile == null) {
                    _errorMessage.value = "Pick an advisor first — set one up in Settings → Echo Adviser."
                    return@launch
                }
                advisorReq = OpenRouterService.AdvisorRequest(profile.name, profile.modelId)
                echoSystemPrompt = SystemPrompts.buildEchoAdviser(profile.name)
            } else if (_chatMode.value is ChatMode.EchoFusion) {
                if (customProviderActive) {
                    _errorMessage.value = "Echo Fusion uses OpenRouter server tools. Pick an OpenRouter Cloud model first."
                    return@launch
                }
                val panel = fusionPanelDao.getById(settingsRepository.getEchoFusionPanelIdDirect())
                if (panel == null || panel.models.isEmpty()) {
                    _errorMessage.value = "Set up a Fusion panel first in Settings → Echo Fusion, then pick it."
                    return@launch
                }
                val judge = panel.judgeModelId?.takeIf { it.isNotBlank() } ?: panel.models.first()
                echoModel = judge
                fusionReq = OpenRouterService.FusionRequest(panel.name, panel.models, panel.judgeModelId)
                echoSystemPrompt = SystemPrompts.buildEchoFusion(panel.name)
            }

            // Artifact mode: override the system prompt with the artifact builder. Feed the chat's
            // current artifact back so a follow-up revises it. On-device implies offline (no CDN).
            val artifactMode = _chatMode.value is ChatMode.Artifact
            val artifactSystemPrompt = if (artifactMode) {
                val prior = _currentChatThreadId.value?.let { artifactManager.getLatestVersionContent(it) }
                val offline = settingsRepository.getArtifactsOfflineDirect() || isLocal
                SystemPrompts.buildArtifact(isLocal, offline, prior)
            } else null

            val systemPrompt = echoSystemPrompt ?: artifactSystemPrompt ?: when {
                // Tool-calling custom providers behave like cloud models: they call web_search
                // themselves, so they get the standard "you have a web_search tool" prompt.
                customToolCallingActive -> SystemPrompts.build(false, effectiveProvider)
                // Other custom providers have no native tool calling; their search results are
                // pre-injected, so they need the "results are provided" prompt instead.
                customProviderActive -> SystemPrompts.buildCustomProvider(effectiveProvider)
                else -> SystemPrompts.build(isLocal, effectiveProvider)
            }

            var isFirstMsgInChat = false
            var chatId = _currentChatThreadId.value

            if (chatId == null) {
                isFirstMsgInChat = true
                chatId = chatRepository.createThread().id
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
            chatRepository.insertMessage(userMsg)

            // Update Thread Timestamp
            chatRepository.touchThread(chatId)

            // Trigger background Title generation. Local chats use the word fallback:
            // no API key may exist, and the on-device engine is single-flight.
            if (isFirstMsgInChat) {
                if (isLocal || customProviderActive) {
                    val words = prompt.split("\\s+".toRegex())
                    val fallbackTitle = words.take(4).joinToString(" ") + if (words.size > 4) "..." else ""
                    chatRepository.renameThread(chatId, fallbackTitle)
                } else {
                    launch {
                        val generatedTitle = openRouterService.generateTitle(apiKey, selectedModel, prompt)
                        chatRepository.renameThread(chatId!!, generatedTitle)
                    }
                }
            }

            // Load updated dialog history
            val fullHistory = chatRepository.history(chatId)

            // Resolve the user's global sampler settings for whichever model is about to run,
            // clamped to that model's limits (so a budget set for a big model can't break a
            // smaller one — it falls back to the shipped default instead).
            val inferenceParams = if (isLocal) {
                val lm = localModel!!
                InferenceLimits.coerce(
                    settingsRepository.getInferenceParamsDirect(local = true),
                    ModelCapabilities(
                        maxContextTokens = (lm.maxTokens ?: LocalModelCatalog.maxTokensFor(lm.id, lm.fileName))
                            .coerceAtMost(InferenceLimits.LOCAL_MAX_TOKENS_CEIL),
                        maxTopK = InferenceLimits.LOCAL_TOP_K_MAX,
                    ),
                    InferenceLimits.LOCAL_DEFAULTS,
                )
            } else {
                InferenceLimits.coerce(
                    settingsRepository.getInferenceParamsDirect(local = false),
                    // Cloud context length isn't known here; OpenRouter clamps server-side.
                    ModelCapabilities(maxContextTokens = 0, maxTopK = InferenceLimits.CLOUD_TOP_K_MAX),
                    InferenceLimits.CLOUD_DEFAULTS,
                )
            }

            val baseResponseFlow: Flow<StreamChunk> = when {
                agentReq != null ->
                    openRouterService.sendWithAgentTools(
                        apiKey = apiKey,
                        model = echoModel,
                        history = fullHistory,
                        systemPrompt = systemPrompt,
                        agent = agentReq,
                        params = inferenceParams,
                    )
                // Artifact mode runs the plain streaming path (no search); the parser extracts the
                // <echo:artifact> block from the content stream.
                artifactMode && isLocal ->
                    localGateway.stream(
                        LlmStreamRequest(
                            model = selectedModel,
                            chatId = chatId,
                            history = fullHistory,
                            systemPrompt = systemPrompt,
                            params = inferenceParams,
                            localModel = localModel,
                        )
                    )
                artifactMode && customProviderActive ->
                    customProviderFlow(customProvider, customProviderConfig, requestModel, fullHistory, systemPrompt, inferenceParams)
                artifactMode ->
                    openRouterGateway.stream(
                        LlmStreamRequest(
                            apiKey = apiKey,
                            model = selectedModel,
                            chatId = chatId,
                            history = fullHistory,
                            systemPrompt = systemPrompt,
                            params = inferenceParams,
                            serverWebSearch = false,
                        )
                    )
                advisorReq != null || fusionReq != null ->
                    openRouterService.sendWithEchoTools(
                        apiKey = apiKey,
                        model = echoModel,
                        history = fullHistory,
                        systemPrompt = systemPrompt,
                        advisor = advisorReq,
                        fusion = fusionReq,
                        params = inferenceParams,
                    )
                isLocal && clientSearchReady ->
                    localPromptProtocolFlow(localModel!!, chatId, fullHistory, systemPrompt, provider, searchKey, inferenceParams)
                isLocal ->
                    localGateway.stream(
                        LlmStreamRequest(
                            model = selectedModel,
                            chatId = chatId,
                            history = fullHistory,
                            systemPrompt = systemPrompt,
                            params = inferenceParams,
                            localModel = localModel,
                        )
                    )
                customToolCallingActive ->
                    customProviderToolFlow(customProvider, customProviderConfig, requestModel, fullHistory, systemPrompt, inferenceParams) { query ->
                        webSearchService.search(provider, searchKey, query)
                    }
                customProviderActive && clientSearchReady ->
                    flow {
                        val query = prompt
                        emit(StreamChunk.SearchStarted(query))
                        val sources = webSearchService.search(provider, searchKey, query)
                        emit(StreamChunk.SearchSources(query, sources))
                        val searchContext = sources.joinToString("\n\n") { source ->
                            "[${source.title}](${source.url})\n${source.snippet.orEmpty()}"
                        }
                        val withSearch = systemPrompt + "\n\nUse these web search results when relevant:\n$searchContext"
                        emitAll(customProviderFlow(customProvider, customProviderConfig, requestModel, fullHistory, withSearch, inferenceParams))
                    }
                customProviderActive ->
                    customProviderFlow(customProvider, customProviderConfig, requestModel, fullHistory, systemPrompt, inferenceParams)
                provider == "openrouter" ->
                    openRouterGateway.stream(
                        LlmStreamRequest(
                            apiKey = apiKey,
                            model = selectedModel,
                            chatId = chatId,
                            history = fullHistory,
                            systemPrompt = systemPrompt,
                            params = inferenceParams,
                            serverWebSearch = true,
                        )
                    )
                clientSearchReady ->
                    openRouterService.sendWithClientSearch(apiKey, selectedModel, fullHistory, systemPrompt, inferenceParams) { query ->
                        webSearchService.search(provider, searchKey, query)
                    }
                else ->
                    openRouterGateway.stream(
                        LlmStreamRequest(
                            apiKey = apiKey,
                            model = selectedModel,
                            chatId = chatId,
                            history = fullHistory,
                            systemPrompt = systemPrompt,
                            params = inferenceParams,
                            serverWebSearch = false,
                        )
                    )
            }

            // In artifact mode, route the <echo:artifact> block out of the chat bubble into
            // artifact events; otherwise pass the stream through untouched.
            val responseFlow: Flow<StreamChunk> =
                if (artifactMode) baseResponseFlow.extractArtifacts() else baseResponseFlow

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
            // Echo Adviser/Fusion are cost-heavy and can take a while; label the keep-alive
            // notification and ping the user when they finish in the background (like research).
            val echoLabel = when {
                agentReq != null -> "Echo Agents"
                advisorReq != null -> "Echo Adviser"
                fusionReq != null -> "Echo Fusion"
                else -> null
            }
            val keepAliveText = when {
                agentReq != null -> "Echo Agents — handing tasks to your Echo Agent…"
                fusionReq != null -> "Echo Fusion — the panel is deliberating…"
                advisorReq != null -> "Echo Adviser — consulting your advisor…"
                else -> "Generating a reply…"
            }
            // Keep the process unfrozen so the reply keeps streaming while minimized.
            KeepAliveService.acquire(getApplication(), keepAliveText)
            try {
                var lastStreamUiEmit = 0L
                var pendingStreamUiState: ActiveStreamState? = null
                fun emitStreamUiState(force: Boolean = false) {
                    val state = ActiveStreamState(
                        segments = segments.toList(),
                        statusNote = statusNote,
                        progressLoading = false,
                        isLocal = isLocal
                    )
                    pendingStreamUiState = state
                    val now = System.currentTimeMillis()
                    if (force || now - lastStreamUiEmit >= STREAM_UI_EMIT_MS) {
                        setStreamState(chatId, state)
                        pendingStreamUiState = null
                        lastStreamUiEmit = now
                    }
                }
                responseFlow.collect { rawChunk ->
                    // Persist a completed artifact version before reducing, so the card/segment
                    // carry a real artifactId/version to deep-link the workspace.
                    val chunk = if (rawChunk is StreamChunk.ArtifactCompleted && rawChunk.content.isNotBlank()) {
                        val ref = artifactManager.saveVersion(
                            chatId = chatId,
                            title = rawChunk.title,
                            type = rawChunk.artifactType,
                            content = rawChunk.content,
                            sourcePrompt = prompt,
                        )
                        rawChunk.copy(artifactId = ref.artifactId, artifactType = ref.type, version = ref.version)
                    } else rawChunk
                    val note = reduceSegments(segments, chunk)
                    if (note != null) statusNote = note
                    emitStreamUiState()
                }
                pendingStreamUiState?.let { emitStreamUiState(force = true) }
                persistAssistantMessage(chatId, segments, interrupted = null)
                if (echoLabel != null) {
                    ReplyNotifications.notifyReplyReady(
                        getApplication(), chatId,
                        title = "$echoLabel reply is ready",
                        text = "Tap to read the answer in EchoFlow.",
                    )
                }
            } catch (e: CancellationException) {
                // User tapped Stop — keep whatever streamed so far and surface no error banner.
                // The persist runs under NonCancellable so it isn't skipped by the cancellation.
                withContext(NonCancellable) {
                    persistAssistantMessage(chatId, segments, interrupted = null, stopped = true)
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message ?: "An unexpected error occurred during chat."
                persistAssistantMessage(chatId, segments, interrupted = e.message)
                if (echoLabel != null) {
                    ReplyNotifications.notifyReplyReady(
                        getApplication(), chatId,
                        title = "$echoLabel couldn't finish",
                        text = e.message ?: "The reply was interrupted. Tap to reopen.",
                    )
                }
            } finally {
                KeepAliveService.release(getApplication())
                streamJobs.remove(chatId)
                setStreamState(chatId, null)
            }
        }
    }

    /**
     * Custom provider with native tool calling: routes to the per-format tool loop in
     * [CustomProviderService], passing a [search] executor backed by the active client search
     * provider. Falls back to the plain (no-tool) flow for any unknown provider.
     */
    private fun customProviderToolFlow(
        provider: String?,
        config: CustomProviderConfig,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
        search: suspend (String) -> List<SearchSource>,
    ): Flow<StreamChunk> = when (provider) {
        "openai" -> customProviderService.streamOpenAiTools("https://api.openai.com/v1", config.openAiApiKey, model, history, systemPrompt, params, search)
        "cerebras" -> customProviderService.streamOpenAiTools("https://api.cerebras.ai/v1", config.cerebrasApiKey, model, history, systemPrompt, params, search)
        "openai-compatible" -> customProviderService.streamOpenAiTools(config.openAiBaseUrl, config.openAiCompatibleApiKey, model, history, systemPrompt, params, search)
        "ollama" -> customProviderService.streamOllamaTools(config.ollamaBaseUrl, model, history, systemPrompt, params, search)
        "claude" -> customProviderService.streamClaudeTools(config.claudeApiKey, model, history, systemPrompt, params, search)
        "gemini" -> customProviderService.streamGeminiTools(config.geminiApiKey, model, history, systemPrompt, params, search)
        else -> customProviderFlow(provider, config, model, history, systemPrompt, params)
    }

    private fun customProviderFlow(
        provider: String?,
        config: CustomProviderConfig,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = when (provider) {
        "openai" -> customProviderService.streamOpenAi(config.openAiApiKey, model, history, systemPrompt, params)
        "claude" -> customProviderService.streamClaude(config.claudeApiKey, model, history, systemPrompt, params)
        "gemini" -> customProviderService.streamGemini(config.geminiApiKey, model, history, systemPrompt, params)
        "cerebras" -> customProviderService.streamCerebras(config.cerebrasApiKey, model, history, systemPrompt, params)
        "ollama" -> customProviderService.streamOllama(config.ollamaBaseUrl, model, history, systemPrompt, params)
        "openai-compatible" -> customProviderService.streamOpenAiCompatible(
            baseUrl = config.openAiBaseUrl,
            apiKey = config.openAiCompatibleApiKey,
            model = model,
            history = history,
            systemPrompt = systemPrompt,
            params = params,
        )
        else -> flow { throw Exception("Unknown custom endpoint provider.") }
    }

    // -------------------------------------------------------------------------------
    // Deep Research
    // -------------------------------------------------------------------------------

    /**
     * Validates the configured Deep Research engine, records the user's question, creates a
     * durable [ResearchRun], and hands off to the foreground service which owns execution.
     */
    private fun startDeepResearch(
        topic: String,
        attachmentUri: String?,
        attachmentMime: String?,
        attachmentName: String?,
    ) {
        viewModelScope.launch {
            clearError()
            if (currentResearchRun.value != null) {
                _errorMessage.value = "A research run is already in progress in this chat."
                return@launch
            }

            val engineId = settingsRepository.getDeepResearchModelDirect()
            if (engineId.isBlank()) {
                _errorMessage.value = "Pick a Deep Research engine from the model selector first."
                return@launch
            }
            val isProvider = DeepResearchCatalog.isProviderEngine(engineId)
            val maxSearches = settingsRepository.getDeepResearchMaxSearchesDirect()
            val maxSources = settingsRepository.getDeepResearchMaxSourcesDirect()
            val hasPdfAttachment = attachmentUri != null && attachmentMime.equals("application/pdf", ignoreCase = true)
            if (attachmentUri != null && !hasPdfAttachment) {
                _errorMessage.value = "Deep Research can attach PDFs only."
                return@launch
            }
            if (hasPdfAttachment && isProvider) {
                _errorMessage.value = "PDF files are available for OpenRouter Deep Research models only. Pick one of your research models instead of a provider-native engine."
                return@launch
            }

            var searchProvider: String? = null
            val engineLabel: String

            if (isProvider) {
                val engine = DeepResearchCatalog.providerEngineById(engineId)!!
                engineLabel = engine.name
                if (settingsRepository.getSearchApiKeyDirect(engine.provider).isBlank()) {
                    _errorMessage.value = "Add your ${engine.provider.replaceFirstChar { it.uppercase() }} API key in Settings → Web search."
                    return@launch
                }
            } else {
                if (settingsRepository.getApiKeyDirect().isBlank()) {
                    _errorMessage.value = "OpenRouter API key is missing. Add it in Settings → Cloud models."
                    return@launch
                }
                val chosen = settingsRepository.getDeepResearchSearchProviderDirect()
                searchProvider = if (chosen == "auto") {
                    listOf("exa", "parallel", "firecrawl").firstOrNull { settingsRepository.getSearchApiKeyDirect(it).isNotBlank() }
                } else {
                    chosen.takeIf { settingsRepository.getSearchApiKeyDirect(it).isNotBlank() }
                }
                if (searchProvider == null) {
                    _errorMessage.value = "Deep Research needs a search provider. Add an Exa, Parallel or Firecrawl key in Settings → Web search."
                    return@launch
                }
                engineLabel = deepResearchModelDao.getAllSync().firstOrNull { it.id == engineId }?.name ?: engineId
            }

            val now = System.currentTimeMillis()
            var chatId = _currentChatThreadId.value
            if (chatId == null) {
                chatId = chatRepository.createThread(now = now).id
                _currentChatThreadId.value = chatId
                val words = topic.split("\\s+".toRegex())
                val fallbackTitle = words.take(5).joinToString(" ") + if (words.size > 5) "…" else ""
                chatRepository.renameThread(chatId, fallbackTitle)
            }

            chatRepository.insertMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = "user",
                    content = topic,
                    createdAt = now,
                    localAttachmentUri = attachmentUri,
                    localAttachmentMimeType = attachmentMime,
                    localAttachmentName = attachmentName,
                )
            )
            chatRepository.touchThread(chatId, now)

            val runId = UUID.randomUUID().toString()
            researchRunDao.upsert(
                ResearchRun(
                    id = runId,
                    chatId = chatId,
                    topic = topic,
                    engineId = engineId,
                    engineKind = if (isProvider) "provider" else "agent",
                    engineLabel = engineLabel,
                    searchProvider = searchProvider,
                    level = if (engineId == "exa-agent") settingsRepository.getDeepResearchExaEffortDirect() else null,
                    maxSearches = maxSearches,
                    maxSources = maxSources,
                    status = ResearchRun.STATUS_QUEUED,
                    localAttachmentUri = attachmentUri,
                    localAttachmentMimeType = attachmentMime,
                    localAttachmentName = attachmentName,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            DeepResearchForegroundService.start(getApplication(), runId)
            clearPendingAttachment()
            setMode(ChatMode.Normal) // deliberate, per-question opt-in
        }
    }

    /** Start a Firecrawl Data Agent run (structured extraction). */
    private fun startBrowserSession(prompt: String, force: Boolean = false, targetChatId: String? = null) {
        viewModelScope.launch {
            clearError()
            if (!settingsRepository.getBrowserFlowEnabledDirect()) {
                _errorMessage.value = "Turn on Browser Flow in Settings first."
                return@launch
            }
            if (settingsRepository.getSearchApiKeyDirect("firecrawl").isBlank()) {
                _errorMessage.value = "Add your Firecrawl API key in Settings → Web search."
                return@launch
            }
            // One live browser session app-wide — block starting a second.
            if (!force) {
                browserAgent.activeSession.value?.let { existing ->
                    _browserStartConflict.value = BrowserStartConflict(
                        activeSession = existing,
                        targetChatId = _currentChatThreadId.value,
                        pendingPrompt = prompt,
                    )
                    return@launch
                }
            }

            val now = System.currentTimeMillis()
            var chatId = targetChatId ?: _currentChatThreadId.value
            if (chatId == null) {
                chatId = UUID.randomUUID().toString()
                chatDao.insertThread(ChatThread(id = chatId, title = "New Conversation", createdAt = now, updatedAt = now))
                _currentChatThreadId.value = chatId
            } else {
                _currentChatThreadId.value = chatId
            }
            // Title a fresh thread from the instruction (manager writes the message rows).
            chatDao.getThreadById(chatId)?.let { thread ->
                if (thread.title == "New Conversation") {
                    val words = prompt.split("\\s+".toRegex())
                    val title = words.take(5).joinToString(" ") + if (words.size > 5) "…" else ""
                    chatDao.updateThread(thread.copy(title = title.ifBlank { "Browser session" }))
                }
            }
            setMode(ChatMode.Normal) // igniter consumed
            browserAgent.startSession(chatId, prompt)
        }
    }

    private fun startDataAgent(topic: String) {
        viewModelScope.launch {
            clearError()
            if (currentResearchRun.value != null) {
                _errorMessage.value = "A run is already in progress in this chat."
                return@launch
            }
            if (!settingsRepository.getDataAgentEnabledDirect()) {
                _errorMessage.value = "Turn on Data Agent in Settings → Data Agent first."
                return@launch
            }
            if (settingsRepository.getSearchApiKeyDirect("firecrawl").isBlank()) {
                _errorMessage.value = "Add your Firecrawl API key in Settings → Web search."
                return@launch
            }
            val engine = DataAgentCatalog.byId(settingsRepository.getDataAgentEngineDirect())
                ?: DataAgentCatalog.engines.first()

            val now = System.currentTimeMillis()
            var chatId = _currentChatThreadId.value
            if (chatId == null) {
                chatId = UUID.randomUUID().toString()
                chatDao.insertThread(ChatThread(id = chatId, title = "New Conversation", createdAt = now, updatedAt = now))
                _currentChatThreadId.value = chatId
                val words = topic.split("\\s+".toRegex())
                val fallbackTitle = words.take(5).joinToString(" ") + if (words.size > 5) "…" else ""
                chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(title = fallbackTitle)) }
            }
            messageDao.insertMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    chatId = chatId,
                    role = "user",
                    content = topic,
                    createdAt = now,
                )
            )
            chatDao.getThreadById(chatId)?.let { chatDao.updateThread(it.copy(updatedAt = now)) }

            val runId = UUID.randomUUID().toString()
            researchRunDao.upsert(
                ResearchRun(
                    id = runId,
                    chatId = chatId,
                    topic = topic,
                    engineId = engine.id,
                    engineKind = "data-agent",
                    engineLabel = engine.name,
                    maxCredits = settingsRepository.getDataAgentMaxCreditsDirect(),
                    status = ResearchRun.STATUS_QUEUED,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            DeepResearchForegroundService.start(getApplication(), runId)
            setMode(ChatMode.Normal)
        }
    }

    /** Cancel the in-flight research for the open conversation (keeps any partial result). */
    fun cancelResearch() {
        currentResearchRun.value?.let { DeepResearchForegroundService.cancel(getApplication(), it.id) }
    }

    private suspend fun persistAssistantMessage(
        chatId: String,
        segments: List<StreamSegment>,
        interrupted: String?,
        stopped: Boolean = false
    ) {
        val normalizedSegments = normalizeAssistantSegmentsForPersistence(segments)
        val contentText = normalizedSegments.filterIsInstance<StreamSegment.Text>()
            .joinToString("\n\n") { it.text }.trim()
        // Keep a turn that produced only an Echo Adviser/Fusion artifact (no prose answer) so
        // the consultation/deliberation card still persists and re-renders.
        val hasEchoArtifact = normalizedSegments.any {
            it is StreamSegment.Advisor || it is StreamSegment.Fusion || it is StreamSegment.Subagent ||
                (it is StreamSegment.Artifact && it.artifactId != null)
        }
        // A user-stopped turn is always kept (even with no prose yet) so the "Message stopped"
        // notice is shown; otherwise an empty, content-less turn is dropped.
        if (contentText.isEmpty() && !hasEchoArtifact && !stopped) return

        val reasoningText = normalizedSegments.filterIsInstance<StreamSegment.Reasoning>()
            .joinToString("\n\n") { it.text }.trim()

        val toolEvents = normalizedSegments.mapIndexedNotNull { index, seg ->
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

        // The ordered timeline (reason → search → reason → text) is persisted as-is so a
        // finished reply renders exactly like it streamed, never merging reasoning blocks.
        val persistedSegments = normalizedSegments.mapNotNull { seg ->
            when (seg) {
                is StreamSegment.Reasoning -> seg.text.trim().takeIf { it.isNotEmpty() }
                    ?.let { PersistedSegment(type = "reasoning", text = it) }
                is StreamSegment.Search ->
                    PersistedSegment(type = "search", query = seg.query, sources = seg.sources)
                is StreamSegment.Advisor ->
                    PersistedSegment(
                        type = "advisor",
                        advisor = AdvisorAdvice(
                            advisorName = seg.advisorName,
                            advisorModel = seg.advisorModel,
                            prompt = seg.prompt,
                            advice = seg.advice.orEmpty(),
                        ),
                    )
                is StreamSegment.Fusion ->
                    PersistedSegment(
                        type = "fusion",
                        fusion = (seg.analysis ?: FusionAnalysis(panelName = seg.panelName, judgeModel = null, models = seg.models))
                            .copy(panelName = seg.panelName, models = seg.models.ifEmpty { seg.analysis?.models ?: emptyList() }),
                    )
                is StreamSegment.Subagent ->
                    PersistedSegment(
                        type = "subagent",
                        subagent = SubagentResult(
                            taskName = seg.taskName,
                            taskDescription = seg.taskDescription,
                            workerModel = seg.workerModel,
                            outcome = seg.outcome.orEmpty(),
                            error = seg.error,
                        ),
                    )
                is StreamSegment.Artifact -> seg.artifactId?.let { id ->
                    PersistedSegment(
                        type = "artifact",
                        artifact = ArtifactRef(
                            artifactId = id,
                            title = seg.title,
                            type = seg.artifactType,
                            version = seg.version,
                        ),
                    )
                }
                // Transient waiting indicator — never persisted.
                is StreamSegment.AgentRun -> null
                is StreamSegment.Text -> seg.text.trim().takeIf { it.isNotEmpty() }
                    ?.let { PersistedSegment(type = "text", text = it) }
            }
        }.let { list ->
            var out = list
            if (interrupted != null) out = out + PersistedSegment(type = "text", text = "*[Connection lost: $interrupted]*")
            // A trailing "stopped" segment renders the red "Message stopped" notice under the reply.
            if (stopped) out = out + PersistedSegment(type = "stopped")
            out
        }

        messageDao.insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = finalContent,
                createdAt = System.currentTimeMillis(),
                reasoning = reasoningText.ifBlank { null },
                toolEventsJson = ToolEventJson.toolEventsToJson(toolEvents),
                citationsJson = ToolEventJson.citationsToJson(citations),
                segmentsJson = ToolEventJson.segmentsToJson(persistedSegments)
            )
        )

        chatDao.getThreadById(chatId)?.let { currentT ->
            chatDao.updateThread(currentT.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Some OpenRouter/provider combinations (notably a few DeepSeek routes) can stream the
     * visible answer through `reasoning` / `reasoning_content` and never send `content`.
     * Without this guard the live answer disappears when streaming ends because there is no
     * assistant content to persist. Preserve the timeline, but promote the final reasoning tail
     * into normal text when it is the only answer-like material we received.
     */
    private fun normalizeAssistantSegmentsForPersistence(segments: List<StreamSegment>): List<StreamSegment> {
        if (segments.any { it is StreamSegment.Text && it.text.isNotBlank() }) return segments

        val lastReasoningIndex = segments.indexOfLast {
            it is StreamSegment.Reasoning && it.text.isNotBlank()
        }
        if (lastReasoningIndex < 0) return segments

        val normalized = segments.toMutableList()
        val reasoning = normalized[lastReasoningIndex] as StreamSegment.Reasoning
        val (remainingReasoning, answerText) = splitReasoningOnlyAnswer(reasoning.text)
        val replacement = mutableListOf<StreamSegment>()
        if (remainingReasoning.isNotBlank()) replacement.add(StreamSegment.Reasoning(remainingReasoning))
        replacement.add(StreamSegment.Text(answerText))

        normalized.removeAt(lastReasoningIndex)
        normalized.addAll(lastReasoningIndex, replacement)
        return normalized
    }

    private fun splitReasoningOnlyAnswer(text: String): Pair<String, String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "" to ""

        val thinkEnd = trimmed.lastIndexOf("</think>", ignoreCase = true)
        if (thinkEnd >= 0) {
            val answer = trimmed.substring(thinkEnd + "</think>".length).trim()
            if (answer.isNotEmpty()) return trimmed.substring(0, thinkEnd + "</think>".length).trim() to answer
        }

        val marker = Regex(
            pattern = "(?im)^\\s*(final\\s+answer|answer|response)\\s*:\\s*"
        ).findAll(trimmed).lastOrNull()
        if (marker != null) {
            val answerStart = marker.range.last + 1
            val answer = trimmed.substring(answerStart).trim()
            if (answer.isNotEmpty()) return trimmed.substring(0, marker.range.first).trim() to answer
        }

        return "" to trimmed
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
        searchKey: String,
        params: InferenceParams
    ): Flow<StreamChunk> = flow {
        var round = 0
        var continuation = false

        while (true) {
            val allowTag = round < MAX_LOCAL_SEARCH_ROUNDS
            val upstream = if (continuation) {
                localLlmService.continueGeneration()
            } else {
                localLlmService.generate(model, chatId, history, systemPrompt, params)
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
        private const val STREAM_UI_EMIT_MS = 33L

        fun provideFactory(
            application: Application,
            chatDao: ChatDao,
            messageDao: MessageDao,
            settingsRepository: SettingsRepository,
            localModelDao: LocalModelDao,
            researchRunDao: ResearchRunDao,
            deepResearchModelDao: DeepResearchModelDao,
            advisorProfileDao: AdvisorProfileDao,
            fusionPanelDao: FusionPanelDao,
            agentProfileDao: AgentProfileDao,
            browserSessionDao: BrowserSessionDao,
            browserStepDao: BrowserStepDao,
            artifactDao: ArtifactDao,
            artifactVersionDao: ArtifactVersionDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    application, chatDao, messageDao, settingsRepository, localModelDao,
                    researchRunDao, deepResearchModelDao, advisorProfileDao, fusionPanelDao,
                    agentProfileDao, browserSessionDao, browserStepDao, artifactDao, artifactVersionDao
                ) as T
            }
        }
    }
}
