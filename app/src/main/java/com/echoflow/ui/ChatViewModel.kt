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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

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
    private val artifactVersionDao: ArtifactVersionDao,
    private val generatedImageDao: GeneratedImageDao,
    private val generatedVideoDao: GeneratedVideoDao,
    private val localInferenceGate: LocalInferenceGate
) : AndroidViewModel(application) {

    // Artifacts: model-built, rendered content (web page / document / report) with version history.
    private val artifactManager = ArtifactManager(artifactDao, artifactVersionDao)

    // Image generation: decoded PNGs on disk, version chain in Room (see GeneratedImageStore).
    private val generatedImageStore = GeneratedImageStore(application, generatedImageDao)

    private val openRouterImageEngine by lazy { OpenRouterImageGenerationEngine(openRouterService, generatedImageStore) }

    // Video generation: MP4s on disk, async job rows in Room (see GeneratedVideoStore). The
    // rows are what make a run outlive the process, so the store is created eagerly.
    private val generatedVideoStore = GeneratedVideoStore(application, generatedVideoDao)
    private val videoEngine by lazy {
        OpenRouterVideoGenerationEngine(
            service = OpenRouterVideoService(),
            directory = OpenRouterVideoModelDirectory(),
            store = generatedVideoStore,
        )
    }

    private val openRouterService = OpenRouterService(application)
    private val customProviderService = CustomProviderService(application)
    private val customProviderFlowRouter = CustomProviderFlowRouter(customProviderService)
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

    // ── App mode ─────────────────────────────────────────────────────────────────────────

    /** Chat or Imagine. Lateral state, never a navigation destination — back never moves it. */
    val appMode: StateFlow<AppMode> = settingsRepository.appMode

    /**
     * Switches surface, parking the current conversation so this mode returns to it later and
     * restoring whatever the target mode had open. Switching is free: renders and streams keep
     * running in the mode you left, and [renderingModes] is what says so.
     */
    fun switchMode(mode: AppMode) {
        if (mode == appMode.value) return
        if (mode == AppMode.Chat) clearImagineArmedModes()
        parkCurrentPosition()
        settingsRepository.saveAppMode(mode)
        _drawerSearchQuery.value = ""
        restorePosition(mode)
    }

    /**
     * Image/video gen is armed by Imagine for a single send. If that sticky [ChatMode]
     * survives a surface switch, every later Chat message generates media instead of
     * answering — which is exactly what happens when a long video render tempts the user
     * into Chat while the last Imagine mode is still live.
     */
    private fun clearImagineArmedModes() {
        if (_chatMode.value is ChatMode.ImageGen || _chatMode.value is ChatMode.VideoGen) {
            setMode(ChatMode.Normal)
        }
    }

    /**
     * Reopens [mode]'s remembered position — the only path that does so.
     *
     * Restoring hits the database, so it can still be suspended when the user switches again
     * or taps a conversation, and an unguarded coroutine then lands its stale answer on top of
     * wherever they actually are: the wrong thread on screen, and the next message filed into
     * it. Three guards, because they cover different races — the job is tracked so a second
     * restore cancels the first; the mode is rechecked in case the switch was reversed; and
     * the [NavigationGuard] catches navigation *within* the mode being restored, which the
     * mode check alone cannot see and which is much the likeliest version.
     *
     * Startup used to do this same lookup with none of that, on the reasoning that nothing can
     * have happened yet. Startup is exactly when it can: the database is cold, the lookup is at
     * its slowest, and the drawer is one tap away. There is only one restore path now so that
     * a future caller cannot reintroduce the gap by forgetting a guard.
     */
    private fun restorePosition(mode: AppMode) {
        modeRestoreJob?.cancel()
        // A mode switch is already a conversation transition, even if its suspended lookup is
        // later superseded. Clear conversation-scoped composer state synchronously so a stale
        // restore cannot leave the previous thread's edit attachment armed for the next send.
        clearConversationComposerState()
        modeRestoreJob = viewModelScope.launch {
            val token = navigation.begin()
            val restored = restoredThreadId(mode)
            // Anything that moved the user in the meantime — another switch, tapping a
            // conversation, a notification, a send that created a thread — outranks a lookup
            // that started before it.
            if (appMode.value == mode && navigation.stillCurrent(token)) openThread(restored)
        }
    }

    /**
     * Every change of which conversation is open, in one place.
     *
     * Routed through [NavigationGuard] so a suspended restore can tell whether the user has
     * navigated since it began. Restoring reads the database, and a coroutine landing its
     * stale answer afterwards would show the wrong thread and file the next message into it.
     */
    private fun openThread(chatId: String?) {
        navigation.navigated()
        _currentChatThreadId.value = chatId
        // Version pick is session-only; reopening always lands on the latest answer.
        _replyVersionPick.value = emptyMap()
        clearConversationComposerState()
    }

    private fun clearConversationComposerState() {
        _editingUserMessageId.value = null
        clearPendingAttachment()
    }

    // ── Prompt edit + reply versions ──────────────────────────────────────────────

    /** User message currently loaded into the composer for edit; null when not editing. */
    private val _editingUserMessageId = MutableStateFlow<String?>(null)
    val editingUserMessageId: StateFlow<String?> = _editingUserMessageId.asStateFlow()

    /**
     * Which archived answer is shown for each assistant message id. Missing keys mean
     * "show latest" so close/reopen always prefers the newest reply.
     */
    private val _replyVersionPick = MutableStateFlow<Map<String, Int>>(emptyMap())
    val replyVersionPick: StateFlow<Map<String, Int>> = _replyVersionPick.asStateFlow()

    /**
     * Start editing [messageId] if it is the last user prompt. Returns the text to put in
     * the composer, or null when edit is not allowed.
     */
    fun beginEditUserMessage(messageId: String): String? {
        val lastUser = currentMessages.value.lastOrNull { it.role == "user" } ?: return null
        if (lastUser.id != messageId) return null
        _editingUserMessageId.value = messageId
        val uri = lastUser.localAttachmentUri?.let(Uri::parse)
        if (uri != null) {
            setPendingAttachment(uri, lastUser.localAttachmentMimeType, lastUser.localAttachmentName)
        } else {
            clearPendingAttachment()
        }
        return lastUser.content
    }

    fun cancelEditMessage() {
        clearConversationComposerState()
    }

    fun selectReplyVersion(messageId: String, index: Int) {
        _replyVersionPick.value = _replyVersionPick.value + (messageId to index.coerceAtLeast(0))
    }

    fun replyVersionIndex(messageId: String, total: Int): Int {
        val latest = (total - 1).coerceAtLeast(0)
        return _replyVersionPick.value[messageId]?.coerceIn(0, latest) ?: latest
    }

    /** Records where this mode is being left — including "on a blank composer". */
    private fun parkCurrentPosition() {
        val current = _currentChatThreadId.value
        settingsRepository.saveLastPosition(
            appMode.value,
            if (current == null) ModePosition.Blank else ModePosition.Thread(current),
        )
    }

    /**
     * The thread to reopen for [mode], or null for a blank composer. A remembered thread that
     * has since been deleted, or that somehow belongs to the other mode, also lands on blank
     * rather than on someone else's conversation.
     */
    private suspend fun restoredThreadId(mode: AppMode): String? =
        when (val position = settingsRepository.getLastPositionDirect(mode)) {
            is ModePosition.Thread ->
                chatRepository.thread(position.chatId)?.takeIf { it.mode == mode }?.id
            ModePosition.Blank, ModePosition.Unset -> null
        }

    /** This mode's conversations. The two histories never mix. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allThreads: StateFlow<List<ChatThread>> = appMode
        .flatMapLatest { chatRepository.threadsForMode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Modes with a render in flight, so the switch can show which one is still working. A
     * clip takes minutes and is usually started in one mode and waited for in another;
     * without this the split would simply hide the activity.
     */
    /**
     * Conversations with a clip in flight. Reads from the database rather than a job registry,
     * which is the only source covering live turns, resumed jobs and process death alike.
     */
    val renderingChatIds: StateFlow<Set<String>> = generatedVideoDao.observeRenderingChatIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val renderingModes: StateFlow<Set<AppMode>> = renderingChatIds
        .flatMapLatest { chatIds ->
            if (chatIds.isEmpty()) flowOf(emptySet()) else chatRepository.allThreads().map { threads ->
                threads.filter { it.id in chatIds }.map(ChatThread::mode).toSet()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Drawer search: matches conversation titles and full message text, scoped to the mode.
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

    /**
     * How many conversations the *other* mode would have matched. A filtered list that hides
     * a result the user knows exists is the one way this design can feel like data loss, so
     * the drawer says where it went instead of staying silent.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val otherModeMatchCount: StateFlow<Int> = combine(appMode, _drawerSearchQuery) { mode, query ->
        mode to query.trim()
    }.flatMapLatest { (mode, query) ->
        if (query.isBlank()) flowOf(0)
        else chatRepository.searchMatchCount(if (mode == AppMode.Chat) AppMode.Imagine else AppMode.Chat, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _currentChatThreadId = MutableStateFlow<String?>(null)
    val currentChatThreadId: StateFlow<String?> = _currentChatThreadId.asStateFlow()

    /** Held by [openThread] and by any restore that must not undo a later navigation. */
    private val navigation = NavigationGuard()

    /** The in-flight mode restore, so a second switch cancels the first rather than racing it. */
    private var modeRestoreJob: Job? = null

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

    // Image generation mode: sticky, so follow-up turns keep editing the chat's latest image.
    val imageGenActive: StateFlow<Boolean> = modeIs<ChatMode.ImageGen>()

    // Video generation mode: sticky like image mode, so a run of clips doesn't need the menu
    // reopened between each one.
    val videoGenActive: StateFlow<Boolean> = modeIs<ChatMode.VideoGen>()

    /**
     * The live row behind one video card. Cards resolve their state from here rather than
     * from the persisted segment, so a clip that was still rendering when its message was
     * written (or that finished while the app was dead) shows the truth on the next open.
     */
    fun observeVideo(videoId: String): Flow<GeneratedVideo?> = generatedVideoDao.observeById(videoId)

    /**
     * Renders that outlive their turn — resumed on launch, cancelled when their chat is
     * deleted. None of this is chat streaming, so none of it lives here.
     */
    private val videoRecovery by lazy {
        VideoJobRecovery(
            context = application,
            store = generatedVideoStore,
            engine = videoEngine,
            settings = settingsRepository,
            chatDao = chatDao,
            messageDao = messageDao,
            scope = viewModelScope,
        )
    }

    init {
        viewModelScope.launch { videoRecovery.resumeInterrupted() }
        // Reopen exactly where the user left off — including on a blank composer. Through the
        // same guarded path as a mode switch, because a cold database makes this the slowest
        // this lookup ever is, and the drawer is reachable throughout.
        restorePosition(appMode.value)
    }

    /**
     * Opens a conversation from outside the UI — a notification tap — switching to whichever
     * mode owns it first. Without the switch the thread would be selected while its own
     * history was filtered out of view, which looks exactly like the app losing it.
     */
    fun openThreadFromNotification(chatId: String) {
        viewModelScope.launch {
            val thread = chatRepository.thread(chatId) ?: return@launch
            if (thread.mode != appMode.value) {
                if (thread.mode == AppMode.Chat) clearImagineArmedModes()
                parkCurrentPosition()
                settingsRepository.saveAppMode(thread.mode)
                _drawerSearchQuery.value = ""
            }
            selectThread(thread.id)
        }
    }

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

    /**
     * The Deep Research report currently open fullscreen, or null.
     *
     * Snapshotted rather than observed: a finished run never changes again, and the segment
     * already carries the report text, so the workspace works even if the `research_runs` row is
     * missing — the row only enriches it with the step timeline and full source records.
     */
    private val _researchWorkspace = MutableStateFlow<ResearchWorkspaceState?>(null)
    val researchWorkspace: StateFlow<ResearchWorkspaceState?> = _researchWorkspace.asStateFlow()

    /**
     * Only the newest open request may publish. Two cards tapped in quick succession each suspend
     * on the DAO, and without this the first query to return last would win and the workspace
     * would show the wrong report.
     */
    private var researchWorkspaceJob: Job? = null

    fun openResearchWorkspace(research: ResearchRef) {
        researchWorkspaceJob?.cancel()
        researchWorkspaceJob = viewModelScope.launch {
            val run = researchRunDao.getById(research.runId)
            ensureActive()
            _researchWorkspace.value = ResearchWorkspaceState(
                research = research,
                steps = ResearchJson.timelineFromJson(run?.stepsJson),
                sources = ResearchJson.sourcesFromJson(run?.sourcesJson),
            )
        }
    }

    fun closeResearchWorkspace() {
        researchWorkspaceJob?.cancel()
        _researchWorkspace.value = null
    }

    /** The run behind a persisted research segment, for the card's step list and favicon stack. */
    fun observeResearchRun(runId: String): Flow<ResearchRun?> = researchRunDao.observeById(runId)

    /**
     * Re-run a research request that failed, reusing the original engine and limits. A fresh row
     * is inserted rather than the old one revived, so the failed attempt stays in the transcript
     * instead of silently rewriting itself.
     */
    fun retryResearch(research: ResearchRef) {
        viewModelScope.launch {
            clearError()
            val previous = researchRunDao.getById(research.runId) ?: run {
                _errorMessage.value = "That research run is no longer available to retry."
                return@launch
            }
            val now = System.currentTimeMillis()
            val runId = UUID.randomUUID().toString()
            // The idle check and the insert share a transaction: two taps could otherwise both
            // pass a pre-check and start two foreground services for one conversation.
            val started = researchRunDao.insertIfChatIdle(
                previous.copy(
                    id = runId,
                    status = ResearchRun.STATUS_QUEUED,
                    uiVersion = ResearchRun.UI_VERSION_CURRENT,
                    phase = null,
                    progressDone = 0,
                    progressTotal = 0,
                    planJson = null,
                    stepsJson = null,
                    sourcesJson = null,
                    report = null,
                    error = null,
                    costInfo = null,
                    providerJobId = null,
                    assistantMessageId = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (!started) {
                _errorMessage.value = "A run is already in progress in this chat."
                return@launch
            }
            DeepResearchForegroundService.start(getApplication(), runId)
        }
    }

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
                    openThread(chatId)
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
    fun toggleImageGen() = toggleMode(ChatMode.ImageGen)
    fun toggleVideoGen() = toggleMode(ChatMode.VideoGen)

    // ── Imagine ──────────────────────────────────────────────────────────────────────────

    /**
     * Sends an Imagine turn. The media switch *is* the capability, so the surface sets the
     * generation mode from what the user is making rather than asking them to arm it from a
     * menu first — which is the entire reason image and video left Chat's "+" menu.
     *
     * The mode is one-shot: [sendMessage] consumes it before launching so a later Chat send
     * cannot inherit ImageGen/VideoGen after the user switches surfaces mid-render.
     */
    fun sendImagineMessage(prompt: String, media: ImagineMedia) {
        setMode(if (media == ImagineMedia.Video) ChatMode.VideoGen else ChatMode.ImageGen)
        sendMessage(prompt)
    }

    /**
     * Carries a finished result forward as the reference for the next Imagine turn.
     *
     * This used to open a Chat conversation with the media attached, on the theory that people
     * would want to ask questions about what they had made. They do not — a picture you wrote
     * the prompt for holds no mysteries. What they want is *another go*: the same subject in a
     * different style, or that frame as the seed of a clip. Keeping the media inside Imagine
     * turns the dead end into the loop the mode exists for.
     */
    fun useMediaAsReference(filePath: String, label: String? = null) {
        val file = File(filePath)
        if (!file.exists()) {
            _errorMessage.value = "That file is no longer available."
            return
        }
        // Both consumers take an image and only an image: image generation sends an image
        // part, and video sends a first frame as an `image_url` data URL. Refusing here rather
        // than trusting call sites, because the failure is silent — the composer would show a
        // reference chip for a file the request drops or rejects.
        if (!IMAGE_REFERENCE_EXTENSIONS.contains(file.extension.lowercase())) {
            _errorMessage.value = "Only an image can be used as a reference."
            return
        }
        setPendingAttachment(Uri.fromFile(file), "image/png", overrideName = label)
    }

    /**
     * The most recent generated image in the open conversation, if there is one.
     *
     * Exposed so switching Image → Video can offer it as a first frame. Read from the message
     * timeline rather than the image store because the timeline is what the user can see: an
     * image belonging to this conversation but scrolled off a deleted turn is not what anyone
     * means by "my last image".
     */
    val lastGeneratedImagePath: StateFlow<String?> = currentMessages
        .map { messages ->
            messages.asReversed().asSequence()
                .flatMap { ToolEventJson.segmentsFromJson(it.segmentsJson).asReversed().asSequence() }
                .firstOrNull { it.type == "image" }
                ?.image?.filePath
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun toggleDeepResearch() = toggleMode(ChatMode.DeepResearch)
    fun toggleWebSearchChip() = toggleMode(ChatMode.WebSearch)
    fun toggleDataAgent() = toggleMode(ChatMode.DataAgent)
    fun toggleEchoAdviser() = toggleMode(ChatMode.EchoAdviser)
    fun toggleEchoFusion() = toggleMode(ChatMode.EchoFusion)
    fun toggleEchoAgent() = toggleMode(ChatMode.EchoAgent)
    fun toggleBrowserFlow() = toggleMode(ChatMode.BrowserFlow)

    // ── Browser Flow actions (delegate to the manager; the card/workspace observe its rows) ──

    fun openBrowserWorkspace(chatId: String) {
        openThread(chatId)
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
        openThread(chatId)
        // Parked so this mode reopens here after a round trip through the other one — and a
        // null is parked too, because "I just started something new" is a position worth
        // returning to, not an absence of one.
        settingsRepository.saveLastPosition(
            appMode.value,
            if (chatId == null) ModePosition.Blank else ModePosition.Thread(chatId),
        )
        clearPendingAttachment()
        clearError()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setPendingAttachment(uri: Uri, fallbackMimeType: String? = null, overrideName: String? = null) {
        viewModelScope.launch {
            _pendingAttachmentUri.value = uri
            val resolver = getApplication<Application>().contentResolver
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val mimeType = resolver.getType(uri) ?: fallbackMimeType ?: "image/jpeg"
            _pendingAttachmentMimeType.value = mimeType

            // Get display name. An override wins outright: a file the app generated has a UUID
            // for a name, and showing that tells the user nothing about why it is attached.
            var displayName = if (mimeType.equals("application/pdf", ignoreCase = true)) "Attached PDF" else "Attached Image"
            runCatching { resolver.query(uri, null, null, null, null) }.getOrNull()?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            _pendingAttachmentName.value = overrideName ?: displayName
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

    /**
     * Re-encodes an attached image as a data URL for the video API's `frame_images`. Capped
     * because the whole payload travels in one JSON submit — an oversized frame would fail
     * the request rather than degrade, so an image over the cap is simply not sent and the
     * turn falls back to text-to-video.
     */
    private suspend fun attachmentAsDataUrl(uriString: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = getApplication<Application>().contentResolver
            val uri = Uri.parse(uriString)
            val mimeType = resolver.getType(uri)?.takeIf { it.startsWith("image/", true) } ?: "image/png"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.isEmpty() || bytes.size > MAX_FRAME_IMAGE_BYTES) return@runCatching null
            "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
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
            // Stop anything still writing for this chat and WAIT for it. A video render keeps
            // running for minutes and would otherwise carry on past the cascade — writing rows
            // against a chat that no longer exists, and landing an MP4 that nothing points at.
            cancelWorkForChat(thread.id)
            // Media files live outside Room; remove them while their rows (and paths) still exist.
            generatedImageStore.deleteFilesForChat(thread.id)
            generatedVideoStore.deleteFilesForChat(thread.id)
            chatDao.deleteThread(thread)
            if (_currentChatThreadId.value == thread.id) {
                selectThread(allThreads.value.firstOrNull { it.id != thread.id }?.id)
            }
        }
    }

    /**
     * Cancels the chat's in-flight stream and any video jobs resumed outside it, then joins
     * them so the caller can safely delete rows and files afterwards. Cancellation alone is
     * not enough: a coroutine is only stopped once it has actually unwound.
     */
    private suspend fun cancelWorkForChat(chatId: String) {
        streamJobs.remove(chatId)?.cancelAndJoin()
        videoRecovery.cancelForChat(chatId)
    }

    fun renameThread(thread: ChatThread, newTitle: String) {
        viewModelScope.launch {
            val cleanTitle = newTitle.trim()
            if (cleanTitle.isNotEmpty()) {
                chatDao.setTitleAndUpdatedAt(thread.id, cleanTitle, System.currentTimeMillis())
            }
        }
    }

    fun pinThread(thread: ChatThread) {
        viewModelScope.launch {
            chatDao.setPinnedAt(thread.id, System.currentTimeMillis())
        }
    }

    fun unpinThread(thread: ChatThread) {
        viewModelScope.launch {
            chatDao.setPinnedAt(thread.id, null)
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

    private fun reduceSegments(segments: MutableList<StreamSegment>, chunk: StreamChunk): String? =
        ChatSegmentReducer.reduce(segments, chunk)

    /**
     * Routes a local (on-device) generation flow through the shared inference gate so a
     * local LLM reply and a local image generation can never run at the same time. The
     * gate is released on completion, failure and cancellation alike.
     */
    private fun Flow<StreamChunk>.withLocalInferenceGate(label: String): Flow<StreamChunk> {
        val upstream = this
        return flow {
            localInferenceGate.withExclusive(label) {
                upstream.collect { emit(it) }
            }
        }
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
        val editingUserId = _editingUserMessageId.value
        val attachmentUri = _pendingAttachmentUri.value?.toString()
        val attachmentMime = _pendingAttachmentMimeType.value
        val attachmentName = _pendingAttachmentName.value

        if (prompt.isEmpty() && attachmentUri == null) return

        // Prompt edit always uses the normal chat stream path (archive old answer, stream new).
        // Special modes would fork into a different pipeline and lose the version chain.
        if (editingUserId == null) {
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
        }

        _currentChatThreadId.value?.let { chatId ->
            if (streamJobs[chatId]?.isActive == true) return
        }

        // Capture and consume image/video gen before the coroutine starts. These modes are
        // armed per send by [sendImagineMessage]; leaving them sticky makes Chat generate
        // media for every later question after the user switches away mid-render.
        // Edit-turn never generates media — it re-asks as a normal chat reply.
        val imageGenMode = editingUserId == null && _chatMode.value is ChatMode.ImageGen
        val videoGenMode = editingUserId == null && _chatMode.value is ChatMode.VideoGen
        if (imageGenMode || videoGenMode) clearImagineArmedModes()

        viewModelScope.launch {
            clearError()

            if (editingUserId != null) {
                validateEditTurn(editingUserId, prompt, hasAttachment = attachmentUri != null)?.let { reason ->
                    _errorMessage.value = reason
                    return@launch
                }
            }

            clearPendingAttachment()

            val apiKey = settingsRepository.getApiKeyDirect()
            val selectedModel = settingsRepository.getSelectedModelDirect()
            val customProviderConfig = settingsRepository.getCustomProviderConfigDirect()
            val customProvider = when {
                selectedModel.startsWith(CustomProviderConfig.PREFIX_OPENAI) -> "openai"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_CLAUDE) -> "claude"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_GEMINI) -> "gemini"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_CEREBRAS) -> "cerebras"
                selectedModel.startsWith(CustomProviderConfig.PREFIX_XAI) -> "xai"
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
                "xai" -> selectedModel.removePrefix(CustomProviderConfig.PREFIX_XAI)
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
            // on-device engine even if a local model is the global selection. Image generation
            // likewise always runs its own OpenRouter image model, whatever chat model is picked.
            // Video is the same story, and cloud-only besides — there is no on-device route.
            val isLocal = selectedModel.startsWith("local/") &&
                _chatMode.value !is ChatMode.EchoFusion && !imageGenMode && !videoGenMode

            if (imageGenMode) {
                if (attachmentMime.equals("application/pdf", ignoreCase = true)) {
                    _errorMessage.value = "Image generation works with image attachments only, not PDFs."
                    return@launch
                }
                if (apiKey.isBlank()) {
                    _errorMessage.value = "Image generation uses OpenRouter. Add your API key in Settings → Cloud models."
                    return@launch
                }
            }

            if (videoGenMode) {
                if (prompt.isEmpty()) {
                    _errorMessage.value = "Describe the video you want before sending."
                    return@launch
                }
                if (apiKey.isBlank()) {
                    _errorMessage.value = "Video generation uses OpenRouter. Add your API key in Settings → Cloud models."
                    return@launch
                }
                if (attachmentMime.equals("application/pdf", ignoreCase = true)) {
                    _errorMessage.value = "Video generation works with image attachments only, not PDFs."
                    return@launch
                }
            }

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
                "xai" -> CustomProviderCapabilities.xAiSupportsImages(requestModel)
                "ollama" -> customProviderConfig.ollamaImagesEnabled
                "openai-compatible" -> customProviderConfig.openAiCompatibleImagesEnabled
                else -> false
            }
            val customPdfAllowed = when (customProvider) {
                "openai", "claude", "gemini" -> true
                "cerebras" -> CustomProviderCapabilities.cerebrasSupportsPdfs(requestModel)
                "xai" -> CustomProviderCapabilities.xAiSupportsPdfs(requestModel)
                "ollama" -> customProviderConfig.ollamaPdfsEnabled
                "openai-compatible" -> customProviderConfig.openAiCompatiblePdfsEnabled
                else -> false
            }
            val pendingIsPdf = attachmentMime.equals("application/pdf", ignoreCase = true)
            if (customProviderActive && !imageGenMode && !videoGenMode && attachmentUri != null &&pendingIsPdf && !customPdfAllowed) {
                _errorMessage.value = "PDF is off for this custom endpoint. Turn it on in Settings → Echo Labs → Custom API Endpoint."
                return@launch
            }
            if (customProviderActive && !imageGenMode && !videoGenMode && attachmentUri != null &&!pendingIsPdf && !customImageAllowed) {
                _errorMessage.value = if (customProvider == "xai") {
                    "$requestModel does not support image attachments. Choose an xAI vision model such as grok-4.5."
                } else {
                    "Images are off for this custom endpoint. Turn them on in Settings → Echo Labs → Custom API Endpoint."
                }
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
                    else -> true // OpenAI / Claude / Gemini / Cerebras / xAI
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
                chatId = chatRepository.createThread(mode = appMode.value).id
                openThread(chatId)
                // The blank thread is real now, so this mode returns here rather than to
                // whatever came before it.
                settingsRepository.saveLastPosition(appMode.value, ModePosition.Thread(chatId))
            }

            if (streamJobs[chatId]?.isActive == true) return@launch
            coroutineContext[Job]?.let { streamJobs[chatId] = it }

            // Carried into the new assistant row so prior answers survive regeneration.
            var archivedReplyVersionsJson: String? = null
            // If an edited generation fails, restore this exact row instead of persisting the
            // failed attempt as another browsable answer version.
            var replacedAssistant: ChatMessage? = null

            if (editingUserId == null) {
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
                    val defaultTitle = when (appMode.value) {
                        AppMode.Chat -> "New Conversation"
                        AppMode.Imagine -> "New Creation"
                    }
                    val titleSource = prompt.takeIf { it.isNotBlank() }
                        ?: attachmentName?.takeIf { it.isNotBlank() }
                        ?: ""
                    if (isLocal || customProviderActive) {
                        val fallbackTitle = resolveFallbackThreadTitle(
                            prompt = prompt,
                            attachmentName = attachmentName,
                            defaultTitle = defaultTitle,
                        )
                        chatRepository.renameThread(chatId, fallbackTitle)
                    } else {
                        launch {
                            val generatedTitle = openRouterService.generateTitle(
                                apiKey,
                                selectedModel,
                                titleSource,
                            )
                            chatRepository.renameThread(chatId!!, generatedTitle)
                        }
                    }
                }
            } else {
                // Show thinking immediately while we drop the old answer and start the stream.
                setStreamState(
                    chatId,
                    ActiveStreamState(
                        segments = emptyList(),
                        statusNote = null,
                        progressLoading = true,
                        isLocal = isLocal,
                    ),
                )
                val editResult = withContext(NonCancellable) {
                    commitEditTurn(
                        userMessageId = editingUserId,
                        newContent = prompt,
                        attachmentUri = attachmentUri,
                        attachmentMime = attachmentMime,
                        attachmentName = attachmentName,
                    )
                }
                if (editResult == null) {
                    setStreamState(chatId, null)
                    _errorMessage.value = "Could not edit message."
                    return@launch
                }
                archivedReplyVersionsJson = editResult.archivedVersionsJson
                replacedAssistant = editResult.replacedAssistant
                chatRepository.touchThread(chatId)
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

            // Image mode: resolve the version being edited (if any) and this generation's dot
            // animation before building the stream. Ripple and rain alternate at random so the
            // wait itself has variety across generations.
            val imageGenModelId = if (imageGenMode) settingsRepository.getImageGenModelDirect() else ""
            val imagePrev = if (imageGenMode) generatedImageStore.latestForChat(chatId) else null
            // The base64 re-encode is cloud-only: OpenRouter gets true conversational editing.
            val imageEditUrl = if (imageGenMode) imagePrev?.let { generatedImageStore.asDataUrl(it) } else null
            val imagePattern = if (imageGenMode) listOf("ripple", "rain").random() else ""

            // Video mode: framing comes from Settings; length never does. An image attachment
            // becomes the clip's first frame where the model supports that.
            val videoModelId = if (videoGenMode) settingsRepository.getVideoGenModelDirect() else ""
            val videoAspectRatio = settingsRepository.getVideoAspectRatioDirect()
            val videoStartImage = if (videoGenMode && attachmentUri != null && !pendingIsPdf) {
                attachmentAsDataUrl(attachmentUri)
            } else null
            val videoPattern = if (videoGenMode) listOf("ripple", "rain").random() else ""

            val baseResponseFlow: Flow<StreamChunk> = when {
                videoGenMode ->
                    videoEngine.generate(
                        VideoGenerationRequest(
                            chatId = chatId,
                            prompt = prompt,
                            modelId = videoModelId,
                            apiKey = apiKey,
                            aspectRatio = videoAspectRatio,
                            resolution = settingsRepository.getVideoResolutionDirect(),
                            generateAudio = settingsRepository.getVideoAudioEnabledDirect(),
                            startImageDataUrl = videoStartImage,
                        )
                    ).map { event ->
                        when (event) {
                            is VideoGenerationEvent.Queued ->
                                StreamChunk.VideoGenStarted(event.video.id, videoPattern, videoAspectRatio)
                            is VideoGenerationEvent.Progress ->
                                StreamChunk.VideoGenProgress(event.video.id, event.video.status, event.video.error)
                            is VideoGenerationEvent.VideoFile ->
                                StreamChunk.VideoGenerated(event.video.id, event.video.filePath.orEmpty())
                        }
                    }
                imageGenMode ->
                    flow {
                        emit(StreamChunk.ImageGenStarted(imagePattern, imagePrev != null, imagePrev?.filePath))
                        val request = ImageGenerationRequest(
                            chatId = chatId,
                            prompt = prompt,
                            modelId = imageGenModelId,
                            previousImage = imagePrev,
                            apiKey = apiKey,
                            history = fullHistory,
                            systemPrompt = SystemPrompts.buildImageGen(
                                editing = imageEditUrl != null,
                                aspectRatio = settingsRepository.getImageAspectRatioDirect(),
                            ),
                            editImageDataUrl = imageEditUrl,
                            params = inferenceParams,
                        )
                        val relay: suspend (ImageGenerationEvent) -> Unit = { event ->
                            when (event) {
                                is ImageGenerationEvent.Text -> emit(StreamChunk.Content(event.delta))
                                is ImageGenerationEvent.ImageFile -> emit(
                                    StreamChunk.ImageGenerated(
                                        dataUrl = "",
                                        filePath = event.image.filePath,
                                        imageId = event.image.id,
                                    )
                                )
                            }
                        }
                        openRouterImageEngine.generate(request).collect(relay)
                    }
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
                    ).withLocalInferenceGate("a chat reply")
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
                        .withLocalInferenceGate("a chat reply")
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
                    ).withLocalInferenceGate("a chat reply")
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
                // A clip renders for minutes, almost always with the app in the background —
                // the ping is what tells the user it landed.
                videoGenMode -> "Video"
                else -> null
            }
            val keepAliveText = when {
                videoGenMode -> "Rendering a video…"
                imageGenMode -> "Creating an image…"
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
                    val chunk = when {
                        rawChunk is StreamChunk.ArtifactCompleted && rawChunk.content.isNotBlank() -> {
                            val ref = artifactManager.saveVersion(
                                chatId = chatId,
                                title = rawChunk.title,
                                type = rawChunk.artifactType,
                                content = rawChunk.content,
                                sourcePrompt = prompt,
                            )
                            rawChunk.copy(artifactId = ref.artifactId, artifactType = ref.type, version = ref.version)
                        }
                        // Persist a generated image to disk/Room before reducing — the timeline
                        // only ever carries file paths, never multi-MB base64 payloads.
                        rawChunk is StreamChunk.ImageGenerated && rawChunk.filePath == null -> {
                            val saved = generatedImageStore.save(
                                chatId = chatId,
                                prompt = prompt,
                                dataUrl = rawChunk.dataUrl,
                                parentId = imagePrev?.id,
                            )
                            StreamChunk.ImageGenerated(dataUrl = "", filePath = saved.filePath, imageId = saved.id)
                        }
                        else -> rawChunk
                    }
                    val note = reduceSegments(segments, chunk)
                    if (note != null) statusNote = note
                    emitStreamUiState()
                }
                pendingStreamUiState?.let { emitStreamUiState(force = true) }
                // The image usually arrives as the final chunk, so persisting immediately would
                // replace the streaming bubble (and its stretch-and-reveal choreography) after a
                // few frames. Hold the live bubble long enough for the ~2s handoff to finish;
                // the persisted block then lands in the exact same footprint, so the swap is
                // invisible. Stop/cancel skips this — those paths persist immediately.
                if (imageGenMode && segments.any { it is StreamSegment.Image && !it.generating }) {
                    delay(2600)
                }
                // Video shares that choreography — the clip is the last chunk too.
                if (videoGenMode && segments.any { it is StreamSegment.Video && !it.generating }) {
                    delay(2600)
                }
                if (
                    editingUserId != null &&
                    AssistantMessagePersistence.draft(
                        segments = segments,
                        interrupted = null,
                        stopped = false,
                    ) == null
                ) {
                    throw IllegalStateException("The model returned no response. Try again.")
                }
                persistAssistantMessage(
                    chatId, segments, interrupted = null,
                    replyVersionsJson = archivedReplyVersionsJson,
                )
                if (editingUserId != null) _editingUserMessageId.value = null
                if (videoGenMode) {
                    // The video flow also completes normally when its row was deleted
                    // mid-render, so announce a clip only once one actually exists.
                    segments.filterIsInstance<StreamSegment.Video>()
                        .lastOrNull { it.filePath != null }
                        ?.let { segment ->
                            // "The flow completed" is not "there is a video": a run whose chat
                            // was deleted mid-render also completes normally, with no file.
                            val clip = runCatching { generatedVideoStore.byId(segment.videoId) }.getOrNull()
                            if (clip?.isPlayable == true) {
                                ReplyNotifications.notifyReplyReady(
                                    getApplication(), chatId,
                                    title = "Your video is ready",
                                    text = "Tap to watch it in EchoFlow.",
                                )
                            }
                        }
                } else if (echoLabel != null) {
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
                    persistAssistantMessage(
                        chatId, segments, interrupted = null, stopped = true,
                        replyVersionsJson = archivedReplyVersionsJson,
                    )
                }
                if (editingUserId != null) _editingUserMessageId.value = null
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                _errorMessage.value = e.message ?: "An unexpected error occurred during chat."
                if (editingUserId != null) {
                    // A transport/provider failure is not an answer version. Put the replaced
                    // answer back exactly as it was and keep edit mode ready for a retry.
                    withContext(NonCancellable) {
                        val assistantToRestore = replacedAssistant
                        if (assistantToRestore != null) {
                            chatRepository.insertMessage(assistantToRestore)
                        }
                    }
                    _editingUserMessageId.value = editingUserId
                } else {
                    persistAssistantMessage(
                        chatId,
                        segments,
                        interrupted = e.message,
                    )
                }
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
    ): Flow<StreamChunk> = customProviderFlowRouter.streamWithTools(provider, config, model, history, systemPrompt, params, search)

    private fun customProviderFlow(
        provider: String?,
        config: CustomProviderConfig,
        model: String,
        history: List<ChatMessage>,
        systemPrompt: String,
        params: InferenceParams,
    ): Flow<StreamChunk> = customProviderFlowRouter.stream(provider, config, model, history, systemPrompt, params)

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
                openThread(chatId)
                val fallbackTitle = fallbackThreadTitle(topic)
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
            // Stay in Deep Research mode so the model pill keeps the research engine list until
            // the user toggles the capability off. Exiting to Normal on send made the composer
            // jump back to chat models mid-run, which reads as the wrong selector.
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
                openThread(chatId)
            } else {
                openThread(chatId)
            }
            // Title a fresh thread from the instruction (manager writes the message rows).
            chatDao.getThreadById(chatId)?.let { thread ->
                if (thread.title == "New Conversation") {
                    val title = fallbackThreadTitle(prompt)
                    chatDao.setTitle(thread.id, title.ifBlank { "Browser session" })
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
                openThread(chatId)
                val fallbackTitle = fallbackThreadTitle(topic)
                chatDao.setTitle(chatId, fallbackTitle)
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
            chatDao.touchUpdatedAt(chatId, now)

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
            // Stay in Data Agent mode for the same reason as Deep Research: the engine pill must
            // keep its agent list until the user turns the capability off.
        }
    }

    /** Cancel the in-flight research for the open conversation (keeps any partial result). */
    fun cancelResearch() {
        currentResearchRun.value?.let { DeepResearchForegroundService.cancel(getApplication(), it.id) }
    }

    private suspend fun validateEditTurn(
        userMessageId: String,
        newContent: String,
        hasAttachment: Boolean,
    ): String? {
        val chatId = _currentChatThreadId.value ?: return "No conversation open."
        val messages = chatRepository.history(chatId)
        val lastUser = messages.lastOrNull { it.role == "user" } ?: return "No message to edit."
        if (lastUser.id != userMessageId) return "Only your most recent message can be edited."
        if (newContent.isBlank() && !hasAttachment) return "Edited message cannot be empty."
        // Keep edit on the normal answer path so version history stays coherent.
        val mode = _chatMode.value
        if (mode !is ChatMode.Normal && mode !is ChatMode.WebSearch) {
            return "Turn off the active mode before editing a message."
        }
        return null
    }

    private data class EditTurnResult(
        val archivedVersionsJson: String?,
        val replacedAssistant: ChatMessage?,
    )

    /**
     * Updates the last user prompt, archives the previous assistant answer, and removes that
     * answer from the transcript so streaming can replace it. Returns null on validation failure.
     * [EditTurnResult.archivedVersionsJson] may be null when there was no assistant yet.
     */
    private suspend fun commitEditTurn(
        userMessageId: String,
        newContent: String,
        attachmentUri: String?,
        attachmentMime: String?,
        attachmentName: String?,
    ): EditTurnResult? {
        val chatId = _currentChatThreadId.value ?: return null
        val messages = chatRepository.history(chatId)
        val lastUser = messages.lastOrNull { it.role == "user" } ?: return null
        if (lastUser.id != userMessageId) return null

        val userIndex = messages.indexOfFirst { it.id == userMessageId }
        val assistant = messages.getOrNull(userIndex + 1)?.takeIf { it.role == "assistant" }

        var archivedJson = assistant?.replyVersionsJson
        if (assistant != null) {
            archivedJson = ReplyVersions.archiveCurrent(assistant)
            _replyVersionPick.value = _replyVersionPick.value - assistant.id
        }

        chatRepository.replaceUserTurn(
            updatedUser = lastUser.withEditedPrompt(
                content = newContent,
                attachmentUri = attachmentUri,
                attachmentMimeType = attachmentMime,
                attachmentName = attachmentName,
            ),
            oldAssistantId = assistant?.id,
        )
        return EditTurnResult(
            archivedVersionsJson = archivedJson,
            replacedAssistant = assistant,
        )
    }

    private suspend fun persistAssistantMessage(
        chatId: String,
        segments: List<StreamSegment>,
        interrupted: String?,
        stopped: Boolean = false,
        replyVersionsJson: String? = null,
    ) {
        val draft = AssistantMessagePersistence.draft(segments, interrupted, stopped) ?: return
        messageDao.insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = draft.content,
                createdAt = System.currentTimeMillis(),
                reasoning = draft.reasoning,
                toolEventsJson = ToolEventJson.toolEventsToJson(draft.toolEvents),
                citationsJson = ToolEventJson.citationsToJson(draft.citations),
                segmentsJson = ToolEventJson.segmentsToJson(draft.segments),
                replyVersionsJson = replyVersionsJson,
            )
        )
        chatDao.touchUpdatedAt(chatId, System.currentTimeMillis())
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
        return ChatResponsePolicy.extractSearchQuery(text)
    }

    override fun onCleared() {
        localLlmService.releaseAll()
        super.onCleared()
    }

    companion object {
        /** What may be handed forward as a reference or a first frame. */
        private val IMAGE_REFERENCE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        private val CLIENT_SEARCH_PROVIDERS = setOf("exa", "parallel", "firecrawl")
        private const val MAX_LOCAL_SEARCH_ROUNDS = 3
        private const val STREAM_UI_EMIT_MS = 33L

        /** ~6 MB of base64: comfortably a phone photo, well under OpenRouter's body limit. */
        private const val MAX_FRAME_IMAGE_BYTES = 4 * 1024 * 1024

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
            artifactVersionDao: ArtifactVersionDao,
            generatedImageDao: GeneratedImageDao,
            generatedVideoDao: GeneratedVideoDao,
            localInferenceGate: LocalInferenceGate
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(
                    application, chatDao, messageDao, settingsRepository, localModelDao,
                    researchRunDao, deepResearchModelDao, advisorProfileDao, fusionPanelDao,
                    agentProfileDao, browserSessionDao, browserStepDao, artifactDao, artifactVersionDao,
                    generatedImageDao, generatedVideoDao,
                    localInferenceGate
                ) as T
            }
        }
    }
}
