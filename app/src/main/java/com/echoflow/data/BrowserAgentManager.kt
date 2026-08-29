package com.echoflow.data

import com.echoflow.data.BrowserSession.Companion.PENDING_CONFIRM_DOMAIN
import com.echoflow.data.BrowserSession.Companion.PENDING_DISAMBIGUATION
import com.echoflow.data.BrowserSession.Companion.PENDING_DRAFT_CONFIRM
import com.echoflow.data.BrowserSession.Companion.PENDING_HANDOFF
import com.echoflow.data.BrowserSession.Companion.STATUS_AWAITING_INSTRUCTION
import com.echoflow.data.BrowserSession.Companion.STATUS_AWAITING_USER
import com.echoflow.data.BrowserSession.Companion.STATUS_COMPLETED
import com.echoflow.data.BrowserSession.Companion.STATUS_EXPIRED
import com.echoflow.data.BrowserSession.Companion.STATUS_FAILED
import com.echoflow.data.BrowserSession.Companion.STATUS_RESOLVING
import com.echoflow.data.BrowserSession.Companion.STATUS_RUNNING
import com.echoflow.data.BrowserSession.Companion.STATUS_STARTING
import com.echoflow.data.BrowserSession.Companion.STATUS_STOPPED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Orchestrates Browser Flow: the stateful, multi-turn Firecrawl browser session controlled
 * through chat. It is the only writer to [BrowserSession]/[BrowserStep]; the UI observes those
 * rows. Each turn is one request/response `/interact` call (no streaming) — the live browser
 * WebView is the real-time feedback channel.
 *
 * Lifecycle decisions (see design notes): per-turn work runs in the supplied [scope]
 * (viewModelScope) — no foreground service. Firecrawl's TTL caps billing; on relaunch
 * [sweepOrphans] conservatively expires any session left active by a previous process, and an
 * [idleWatcher] auto-closes idle sessions as a cost guard.
 */
class BrowserAgentManager(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val sessionDao: BrowserSessionDao,
    private val stepDao: BrowserStepDao,
    private val settings: SettingsRepository,
    private val webSearch: WebSearchService,
    private val scope: CoroutineScope,
) {
    private val firecrawl = FirecrawlBrowserService()

    /** The single app-wide live session (the start-a-session lock + the global pill). */
    val activeSession: StateFlow<BrowserSession?> =
        sessionDao.observeAnyActive().stateIn(scope, SharingStarted.Eagerly, null)

    /** Auto-open request: set when a session goes live so the workspace can pop open. */
    private val _openWorkspaceFor = MutableStateFlow<String?>(null)
    val openWorkspaceFor: StateFlow<String?> = _openWorkspaceFor.asStateFlow()

    fun requestOpenWorkspace(chatId: String) { _openWorkspaceFor.value = chatId }
    fun clearWorkspaceRequest() { _openWorkspaceFor.value = null }

    fun observeForChat(chatId: String) = sessionDao.observeActiveForChat(chatId)
    fun observeSteps(sessionId: String) = stepDao.observeForSession(sessionId)

    init {
        scope.launch { sweepOrphans() }
        scope.launch { idleWatcher() }
    }

    // ── Public entry points ────────────────────────────────────────────────────────────

    /** Ignite a new session in [chatId] from the first [instruction]. */
    fun startSession(chatId: String, instruction: String) {
        scope.launch {
            val now = System.currentTimeMillis()
            val sessionId = UUID.randomUUID().toString()
            insertUser(chatId, instruction)
            val session = BrowserSession(
                id = sessionId,
                chatId = chatId,
                goal = instruction,
                status = STATUS_RESOLVING,
                phase = "Finding site…",
                createdAt = now,
                updatedAt = now,
                lastActivityAt = now,
            )
            save(session)
            addStep(sessionId, "system", "Browser Flow started.")
            addStep(sessionId, "user", instruction)
            if (settings.getSearchApiKeyDirect("firecrawl").isBlank()) {
                fail(session, "Add your Firecrawl API key in Settings → Web search.")
                return@launch
            }
            resolveAndProceed(session, instruction)
        }
    }

    /** A message in the owning chat: continue the same session (or answer a pending question). */
    fun sendCommand(chatId: String, text: String) {
        scope.launch {
            val session = sessionDao.getActiveForChat(chatId) ?: return@launch
            if (session.status == STATUS_STARTING || session.status == STATUS_RUNNING) {
                addStep(session.id, "system", "Still working on the previous command.")
                return@launch
            }
            insertUser(chatId, text)
            addStep(session.id, "user", text)
            val touched = session.copy(lastActivityAt = System.currentTimeMillis())
            if (session.status == STATUS_AWAITING_USER &&
                (session.pendingKind == PENDING_DISAMBIGUATION || session.pendingKind == PENDING_CONFIRM_DOMAIN)
            ) {
                // A typed reply during site-resolution is itself a URL/name to resolve.
                save(touched.copy(status = STATUS_RESOLVING, phase = "Finding site…"))
                resolveAndProceed(touched, text)
            } else {
                runTurn(touched, text)
            }
        }
    }

    /** Disambiguation chip tapped. */
    fun resolveCandidate(sessionId: String, url: String) {
        scope.launch {
            val s = sessionDao.getById(sessionId) ?: return@launch
            addStep(sessionId, "user", "Use ${BrowserResolver.domainOf(url)}")
            if (BrowserResolver.isSensitive(url)) askConfirmDomain(s, url)
            else beginScrape(s.copy(resolvedUrl = url), s.goal)
        }
    }

    /** Sensitive-site confirmation accepted. */
    fun confirmDomain(sessionId: String) {
        scope.launch {
            val s = sessionDao.getById(sessionId) ?: return@launch
            if (s.resolvedUrl == null) return@launch
            addStep(sessionId, "user", "Confirmed — open ${BrowserResolver.domainOf(s.resolvedUrl)}")
            beginScrape(s, s.goal)
        }
    }

    /** Draft message approved — actually send it. */
    fun confirmSend(sessionId: String) {
        scope.launch {
            val s = sessionDao.getById(sessionId) ?: return@launch
            val draft = s.pendingDraft ?: return@launch
            val scrapeId = s.scrapeId ?: run { softFail(s, "Lost the browser session."); return@launch }
            addStep(sessionId, "user", "Approved — send it")
            val cleared = s.copy(status = STATUS_RUNNING, phase = "Sending…", pendingKind = null, pendingDraft = null)
            save(cleared)
            try {
                val r = firecrawl.interact(
                    settings.getSearchApiKeyDirect("firecrawl"),
                    scrapeId,
                    SystemPrompts.browserSendConfirmedPrompt(draft),
                )
                val out = r.output.ifBlank { "Sent." }
                save(
                    cleared.copy(
                        status = STATUS_AWAITING_INSTRUCTION,
                        phase = "Waiting for next instruction",
                        lastOutput = out,
                        lastActivityAt = System.currentTimeMillis(),
                        liveViewUrl = r.liveViewUrl ?: s.liveViewUrl,
                        interactiveLiveViewUrl = r.interactiveLiveViewUrl ?: s.interactiveLiveViewUrl,
                    )
                )
                addStep(sessionId, "agent", out)
                insertAssistant(s.chatId, out)
            } catch (e: Exception) {
                softFail(cleared, e.message ?: "Sending failed.")
            }
        }
    }

    /** Cancel a pending confirmation/handoff. Keeps the browser open if one exists. */
    fun cancelPending(sessionId: String) {
        scope.launch {
            val s = sessionDao.getById(sessionId) ?: return@launch
            if (!s.hasLiveBrowser) { stopInternal(s, expired = false); return@launch }
            addStep(sessionId, "user", "Cancelled.")
            save(
                s.copy(
                    status = STATUS_AWAITING_INSTRUCTION,
                    phase = "Waiting for next instruction",
                    pendingKind = null,
                    pendingDraft = null,
                    lastActivityAt = System.currentTimeMillis(),
                )
            )
        }
    }

    /** Finish: ask for a closing summary, save it, then close the session. */
    fun finish(sessionId: String) { scope.launch { finishNow(sessionId) } }

    suspend fun finishNow(sessionId: String) {
        val s = sessionDao.getById(sessionId) ?: return
        if (s.isTerminal) return
        save(s.copy(status = STATUS_RUNNING, phase = "Wrapping up…"))
        if (s.hasLiveBrowser) {
            val key = settings.getSearchApiKeyDirect("firecrawl")
            runCatching {
                val r = firecrawl.interact(key, s.scrapeId!!, SystemPrompts.browserFinishPrompt())
                if (r.output.isNotBlank()) {
                    addStep(sessionId, "agent", r.output)
                    insertAssistant(s.chatId, r.output)
                }
            }
            firecrawl.stop(key, s.scrapeId!!)
        }
        addStep(sessionId, "system", "Session finished.")
        save(s.copy(status = STATUS_COMPLETED, phase = "Finished", pendingKind = null, pendingDraft = null))
        if (_openWorkspaceFor.value == s.chatId) _openWorkspaceFor.value = null
    }

    /** Stop now: close immediately, no summary. */
    fun stop(sessionId: String) {
        scope.launch {
            val s = sessionDao.getById(sessionId) ?: return@launch
            stopInternal(s, expired = false)
        }
    }

    // ── Resolution ─────────────────────────────────────────────────────────────────────

    /**
     * Resolve a website from [searchText] (a URL, the first instruction, or a typed reply during
     * disambiguation). The *task* to run once a browser opens is always [BrowserSession.goal], so
     * the original objective survives any number of resolution round-trips.
     */
    private suspend fun resolveAndProceed(session: BrowserSession, searchText: String) {
        BrowserResolver.extractUrl(searchText)?.let { direct ->
            if (BrowserResolver.isSensitive(direct)) askConfirmDomain(session, direct)
            else beginScrape(session.copy(resolvedUrl = direct), session.goal)
            return
        }

        val provider = settings.resolveChipSearchProvider()?.takeIf { it in CLIENT_SEARCH_PROVIDERS }
        val searchKey = provider?.let { settings.getSearchApiKeyDirect(it) }.orEmpty()
        if (provider == null || searchKey.isBlank()) {
            askForUrl(session, "I can't look that up — no web-search key is set. Paste the website URL.")
            return
        }

        val results = runCatching {
            webSearch.search(provider, searchKey, BrowserResolutionPolicy.websiteQuery(searchText), 6)
        }
            .getOrNull().orEmpty()
        val candidates = BrowserResolver.rankCandidates(results, searchText)
        val top = candidates.firstOrNull()
        when {
            top == null -> askForUrl(session, "I couldn't find that site. Paste the URL or type the exact name.")
            BrowserResolutionPolicy.isConfident(top, searchText) -> {
                if (BrowserResolver.isSensitive(top.url)) askConfirmDomain(session, top.url)
                else beginScrape(session.copy(resolvedUrl = top.url), session.goal)
            }
            else -> askDisambiguation(session, candidates)
        }
    }

    private suspend fun askForUrl(session: BrowserSession, message: String) {
        addStep(session.id, "system", message)
        save(
            session.copy(
                status = STATUS_AWAITING_USER,
                pendingKind = PENDING_DISAMBIGUATION,
                candidatesJson = null,
                phase = "Which site?",
            )
        )
    }

    private suspend fun askDisambiguation(session: BrowserSession, candidates: List<BrowserCandidate>) {
        addStep(session.id, "system", "Found a few sites — choose which one to open.")
        save(
            session.copy(
                status = STATUS_AWAITING_USER,
                pendingKind = PENDING_DISAMBIGUATION,
                candidatesJson = BrowserJson.candidatesToJson(candidates),
                phase = "Which site?",
            )
        )
    }

    private suspend fun askConfirmDomain(session: BrowserSession, url: String) {
        addStep(session.id, "system", "${BrowserResolver.domainOf(url)} looks sensitive — confirm before I open it.")
        save(
            session.copy(
                status = STATUS_AWAITING_USER,
                pendingKind = PENDING_CONFIRM_DOMAIN,
                resolvedUrl = url,
                candidatesJson = null,
                phase = "Confirm sensitive site",
            )
        )
    }

    // ── Browser open + interact ─────────────────────────────────────────────────────────

    private suspend fun beginScrape(session: BrowserSession, instruction: String) {
        val url = session.resolvedUrl ?: return
        save(
            session.copy(
                status = STATUS_STARTING,
                phase = "Opening browser…",
                pendingKind = null,
                pendingInstruction = null,
                candidatesJson = null,
            )
        )
        addStep(session.id, "system", "Opening ${BrowserResolver.domainOf(url)}…")
        try {
            val start = firecrawl.startSession(settings.getSearchApiKeyDirect("firecrawl"), url)
            val now = System.currentTimeMillis()
            val opened = session.copy(
                status = STATUS_RUNNING,
                phase = "Running instruction…",
                scrapeId = start.scrapeId,
                liveViewUrl = start.liveViewUrl,
                interactiveLiveViewUrl = start.interactiveLiveViewUrl,
                resolvedUrl = url,
                openedAt = now,
                pendingKind = null,
                pendingInstruction = null,
                candidatesJson = null,
                lastActivityAt = now,
            )
            save(opened)
            requestOpenWorkspace(session.chatId) // auto-open on first start
            runInteract(opened, instruction, isFirst = true)
        } catch (e: Exception) {
            fail(session, e.message ?: "Couldn't open the browser.")
        }
    }

    private suspend fun runTurn(session: BrowserSession, text: String) {
        if (!session.hasLiveBrowser) {
            softFail(session, "The browser session is no longer available. Start a new one.")
            return
        }
        when (BrowserResolver.classifyInstruction(text)) {
            BrowserResolver.RiskKind.HARD -> handoff(
                session,
                "This step looks like a payment or an irreversible action. Open the live browser and " +
                    "complete it yourself — I won't automate payments, checkout or account changes.",
            )
            BrowserResolver.RiskKind.SEND -> runInteract(session, text, isFirst = false, draftMode = true)
            null -> runInteract(session, text, isFirst = false)
        }
    }

    private suspend fun runInteract(session: BrowserSession, instruction: String, isFirst: Boolean, draftMode: Boolean = false) {
        val scrapeId = session.scrapeId ?: run { softFail(session, "Lost the browser session."); return }
        save(
            session.copy(
                status = STATUS_RUNNING,
                phase = if (draftMode) "Composing…" else if (isFirst) "Running instruction…" else "Running…",
                lastActivityAt = System.currentTimeMillis(),
            )
        )
        try {
            val r = firecrawl.interact(
                settings.getSearchApiKeyDirect("firecrawl"),
                scrapeId,
                SystemPrompts.browserInteractPrompt(instruction, draftMode),
            )
            val out = r.output.ifBlank { "Done." }
            val withUrls = session.copy(
                liveViewUrl = r.liveViewUrl ?: session.liveViewUrl,
                interactiveLiveViewUrl = r.interactiveLiveViewUrl ?: session.interactiveLiveViewUrl,
                lastActivityAt = System.currentTimeMillis(),
            )
            val blocker = BrowserResolver.detectBlocker(out)
            when {
                draftMode -> {
                    save(
                        withUrls.copy(
                            status = STATUS_AWAITING_USER,
                            pendingKind = PENDING_DRAFT_CONFIRM,
                            pendingDraft = out,
                            phase = "Confirm before sending",
                            lastOutput = out,
                        )
                    )
                    addStep(session.id, "agent", "Drafted a message — awaiting your confirmation.")
                }
                blocker != null -> handoff(withUrls.copy(lastOutput = out), blocker)
                else -> {
                    save(
                        withUrls.copy(
                            status = STATUS_AWAITING_INSTRUCTION,
                            phase = "Waiting for next instruction",
                            pendingKind = null,
                            pendingDraft = null,
                            lastOutput = out,
                            error = null,
                        )
                    )
                    addStep(session.id, "agent", out)
                    insertAssistant(session.chatId, out)
                }
            }
        } catch (e: Exception) {
            softFail(session, e.message ?: "The browser action failed.")
        }
    }

    private suspend fun handoff(session: BrowserSession, message: String) {
        save(
            session.copy(
                status = STATUS_AWAITING_USER,
                pendingKind = PENDING_HANDOFF,
                phase = "Needs you",
                lastOutput = message,
                lastActivityAt = System.currentTimeMillis(),
            )
        )
        addStep(session.id, "system", message)
        insertAssistant(session.chatId, message)
    }

    // ── Termination + housekeeping ──────────────────────────────────────────────────────

    private suspend fun stopInternal(session: BrowserSession, expired: Boolean) {
        if (session.hasLiveBrowser) {
            val key = settings.getSearchApiKeyDirect("firecrawl")
            if (key.isNotBlank()) firecrawl.stop(key, session.scrapeId!!)
        }
        addStep(session.id, "system", if (expired) "Session expired or was interrupted." else "Session stopped.")
        save(
            session.copy(
                status = if (expired) STATUS_EXPIRED else STATUS_STOPPED,
                phase = if (expired) "Expired" else "Stopped",
                pendingKind = null,
                pendingDraft = null,
            )
        )
        if (_openWorkspaceFor.value == session.chatId) _openWorkspaceFor.value = null
    }

    /** Terminal failure (no browser, or session gone): close and mark failed. */
    private suspend fun fail(session: BrowserSession, message: String) {
        if (session.hasLiveBrowser) {
            val key = settings.getSearchApiKeyDirect("firecrawl")
            if (key.isNotBlank()) runCatching { firecrawl.stop(key, session.scrapeId!!) }
        }
        addStep(session.id, "system", "Error: $message")
        save(session.copy(status = STATUS_FAILED, phase = "Failed", error = message, pendingKind = null, pendingDraft = null))
        if (_openWorkspaceFor.value == session.chatId) _openWorkspaceFor.value = null
    }

    /** Transient action failure: keep the live browser open unless the session is gone. */
    private suspend fun softFail(session: BrowserSession, message: String) {
        val sessionGone = message.contains("expired", ignoreCase = true) ||
            message.contains("start a new", ignoreCase = true)
        if (sessionGone || !session.hasLiveBrowser) {
            fail(session, message)
            return
        }
        addStep(session.id, "system", "Action failed: $message")
        insertAssistant(session.chatId, "That action failed: $message. Try again, or open the live browser to do it yourself.")
        save(
            session.copy(
                status = STATUS_AWAITING_INSTRUCTION,
                phase = "Waiting for next instruction",
                error = message,
                pendingKind = null,
                pendingDraft = null,
                lastActivityAt = System.currentTimeMillis(),
            )
        )
    }

    /** On relaunch: never auto-resume — best-effort close + mark any leftover session expired. */
    private suspend fun sweepOrphans() {
        val key = settings.getSearchApiKeyDirect("firecrawl")
        sessionDao.getAllActive().forEach { s ->
            if (s.hasLiveBrowser && key.isNotBlank()) runCatching { firecrawl.stop(key, s.scrapeId!!) }
            addStep(s.id, "system", "Previous browser session expired or was interrupted.")
            save(s.copy(status = STATUS_EXPIRED, phase = "Expired", pendingKind = null, pendingDraft = null))
        }
    }

    /** Cost guard: auto-stop a session idling at awaiting_instruction past the configured timeout. */
    private suspend fun idleWatcher() {
        while (true) {
            delay(30_000)
            val s = activeSession.value ?: continue
            val minutes = settings.getBrowserIdleMinutesDirect()
            if (minutes <= 0 || s.status != STATUS_AWAITING_INSTRUCTION) continue
            if (System.currentTimeMillis() - s.lastActivityAt > minutes * 60_000L) {
                addStep(s.id, "system", "Closed after $minutes min of inactivity.")
                stopInternal(s, expired = false)
            }
        }
    }

    // ── small helpers ───────────────────────────────────────────────────────────────────

    private suspend fun save(session: BrowserSession) {
        sessionDao.upsert(session.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun addStep(sessionId: String, role: String, text: String) {
        if (text.isBlank()) return
        stepDao.insert(BrowserStep(UUID.randomUUID().toString(), sessionId, role, text.trim(), System.currentTimeMillis()))
    }

    private suspend fun insertUser(chatId: String, text: String) {
        messageDao.insertMessage(ChatMessage(UUID.randomUUID().toString(), chatId, "user", text, System.currentTimeMillis()))
        touchThread(chatId)
    }

    private suspend fun insertAssistant(chatId: String, text: String) {
        if (text.isBlank()) return
        messageDao.insertMessage(ChatMessage(UUID.randomUUID().toString(), chatId, "assistant", text, System.currentTimeMillis()))
        touchThread(chatId)
    }

    private suspend fun touchThread(chatId: String) {
        chatDao.touchUpdatedAt(chatId, System.currentTimeMillis())
    }

    companion object {
        private val CLIENT_SEARCH_PROVIDERS = setOf("exa", "parallel", "firecrawl")
    }
}
