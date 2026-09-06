package com.echoflow.data

import com.echoflow.data.BrowserSession.Companion.PENDING_DISAMBIGUATION
import com.echoflow.data.BrowserSession.Companion.PENDING_CONFIRM_DOMAIN
import com.echoflow.data.BrowserSession.Companion.PENDING_HANDOFF
import com.echoflow.data.BrowserSession.Companion.PENDING_ACTION_CONFIRM
import com.echoflow.data.BrowserSession.Companion.STATUS_EXPIRED
import com.echoflow.data.BrowserSession.Companion.STATUS_RESOLVING
import com.echoflow.data.BrowserSession.Companion.STATUS_AWAITING_INSTRUCTION
import com.echoflow.data.BrowserSession.Companion.STATUS_FAILED
import com.echoflow.data.BrowserSession.Companion.STATUS_AWAITING_USER
import com.echoflow.data.BrowserSession.Companion.STATUS_STARTING
import com.echoflow.data.BrowserSession.Companion.STATUS_RUNNING
import com.echoflow.data.BrowserSession.Companion.STATUS_STOPPED
import com.echoflow.data.BrowserSession.Companion.STATUS_COMPLETED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** Plans one typed action, persists approval, then executes exactly that action once. */
class BrowserAgentManager(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val sessionDao: BrowserSessionDao,
    private val stepDao: BrowserStepDao,
    private val settings: SettingsRepository,
    private val webSearch: WebSearchService,
    private val scope: CoroutineScope,
    private val planner: suspend (String, String) -> String,
    private val firecrawl: FirecrawlBrowserService = FirecrawlBrowserService(),
) {
    private var commandJob: Job? = null
    private var closingJob: Job? = null
    val activeSession = sessionDao.observeAnyActive().stateIn(scope, SharingStarted.Eagerly, null)
    private val _openWorkspaceFor = MutableStateFlow<String?>(null)
    val openWorkspaceFor = _openWorkspaceFor.asStateFlow()
    fun requestOpenWorkspace(chatId: String) { _openWorkspaceFor.value = chatId }
    fun clearWorkspaceRequest() { _openWorkspaceFor.value = null }
    fun observeForChat(chatId: String) = sessionDao.observeActiveForChat(chatId)
    fun observeSteps(sessionId: String) = stepDao.observeForSession(sessionId)

    private val startup = scope.launch {
        sessionDao.getAllActive().forEach { close(it, STATUS_EXPIRED) }
    }
    init {
        scope.launch {
            while (true) {
                delay(30_000)
                val s = activeSession.value ?: continue
                val minutes = settings.getBrowserIdleMinutesDirect()
                if (minutes > 0 && commandJob?.isActive != true &&
                    System.currentTimeMillis() - s.lastActivityAt > minutes * 60_000L) stop(s.id)
            }
        }
    }

    private fun command(block: suspend () -> Unit) {
        if (commandJob?.isActive == true || closingJob?.isActive == true) return
        commandJob = scope.launch(start = CoroutineStart.LAZY) {
            startup.join()
            block()
        }.also { it.start() }
    }
    private fun key() = settings.getSearchApiKeyDirect("firecrawl").also {
        require(it.isNotBlank()) { "Add your Firecrawl API key in Settings → Web search." }
    }
    private suspend fun guarded(s: BrowserSession, block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            val current = sessionDao.getById(s.id) ?: return
            if (!current.isTerminal) {
                val error = e.message ?: "Browser action failed."
                save(current.copy(status = if (current.hasLiveBrowser) STATUS_AWAITING_INSTRUCTION else STATUS_FAILED,
                    phase = "Action did not complete", error = error, pendingKind = null,
                    pendingDraft = null, pendingInstruction = null))
                addStep(s.id, "system", error)
                insertMessage(s.chatId, "assistant", error)
            }
        }
    }

    fun startSession(chatId: String, instruction: String) = command {
        if (sessionDao.getAllActive().isNotEmpty()) return@command
        val now = System.currentTimeMillis()
        val s = BrowserSession(id = UUID.randomUUID().toString(), chatId = chatId, goal = instruction,
            status = STATUS_RESOLVING, phase = "Finding site…", createdAt = now, updatedAt = now, lastActivityAt = now)
        save(s)
        guarded(s) {
            key()
            insertMessage(chatId, "user", instruction)
            resolve(s, instruction)
        }
    }

    fun sendCommand(chatId: String, text: String) = command {
        val s = sessionDao.getActiveForChat(chatId) ?: return@command
        guarded(s) {
            insertMessage(chatId, "user", text)
            // A new command invalidates any older proposal. Typed 'yes' cannot approve it.
            val fresh = s.copy(pendingKind = null, pendingDraft = null, pendingInstruction = null,
                lastActivityAt = System.currentTimeMillis())
            save(fresh)
            if (s.hasLiveBrowser) plan(fresh, text)
            else resolve(fresh, text)
        }
    }

    private suspend fun resolve(s: BrowserSession, text: String) {
        BrowserResolver.extractUrl(text)?.let { askDomain(s, it); return }
        val provider = settings.resolveChipSearchProvider()
        val sources = if (provider != null && ClientSearchProviders.isReady(provider, settings.getSearchApiKeyDirect(provider))) {
            webSearch.search(provider, settings.getSearchApiKeyDirect(provider), BrowserResolutionPolicy.websiteQuery(text), 6)
        } else emptyList()
        val candidates = BrowserResolver.rankCandidates(sources, text).filter { BrowserActions.validUrl(it.url) }
        save(s.copy(status = STATUS_AWAITING_USER, pendingKind = PENDING_DISAMBIGUATION,
            candidatesJson = BrowserJson.candidatesToJson(candidates), phase = "Choose a site or paste its HTTPS URL"))
    }
    private suspend fun askDomain(s: BrowserSession, url: String) {
        require(BrowserActions.validUrl(url)) { "Use an HTTPS website URL without embedded credentials." }
        save(s.copy(status = STATUS_AWAITING_USER, pendingKind = PENDING_CONFIRM_DOMAIN,
            resolvedUrl = url, pendingInstruction = UUID.randomUUID().toString(),
            lastActivityAt = System.currentTimeMillis(), phase = "Approve opening this URL"))
        requestOpenWorkspace(s.chatId)
    }
    fun resolveCandidate(sessionId: String, url: String) = command {
        val s = sessionDao.getById(sessionId) ?: return@command
        if (s.pendingKind != PENDING_DISAMBIGUATION || s.isTerminal) return@command
        guarded(s) { askDomain(s, url) }
    }
    fun confirmDomain(sessionId: String, expectedToken: String?) = command {
        val s = sessionDao.getById(sessionId) ?: return@command
        if (expectedToken == null || s.pendingInstruction != expectedToken || s.pendingKind != PENDING_CONFIRM_DOMAIN || s.isTerminal) return@command
        guarded(s) {
            require(System.currentTimeMillis() - s.lastActivityAt <= BrowserActions.APPROVAL_LIFETIME_MS) { "Opening approval expired. Choose the URL again." }
            save(s.copy(status = STATUS_STARTING, pendingInstruction = null, pendingKind = null, phase = "Opening browser…"))
            val opened = firecrawl.startSession(key(), s.resolvedUrl!!)
            val current = s.copy(status = STATUS_RUNNING, scrapeId = opened.scrapeId,
                liveViewUrl = opened.liveViewUrl, interactiveLiveViewUrl = opened.interactiveLiveViewUrl,
                openedAt = System.currentTimeMillis(), pendingKind = null, pendingInstruction = null)
            save(current)
            requestOpenWorkspace(s.chatId)
            plan(current, s.goal)
        }
    }

    private suspend fun plan(s: BrowserSession, instruction: String) {
        save(s.copy(status = STATUS_RUNNING, phase = "Reading page and proposing next step…",
            pendingKind = null, pendingDraft = null, pendingInstruction = null))
        val (snapshot, live) = firecrawl.snapshot(key(), s.scrapeId!!)
        val raw = planner(BrowserActions.plannerPrompt,
            "User instruction:\n$instruction\n\nUntrusted page snapshot:\n${BrowserActions.snapshotJson(snapshot)}")
        val action = BrowserActions.parseAction(raw, snapshot)
        val current = s.copy(resolvedUrl = snapshot.url, liveViewUrl = live.liveViewUrl ?: s.liveViewUrl,
            interactiveLiveViewUrl = live.interactiveLiveViewUrl ?: s.interactiveLiveViewUrl,
            pendingDraft = null, pendingInstruction = null, error = null, lastActivityAt = System.currentTimeMillis())
        if (action.type == "answer" || action.type == "handoff") {
            save(current.copy(status = if (action.type == "handoff") STATUS_AWAITING_USER else STATUS_AWAITING_INSTRUCTION,
                pendingKind = if (action.type == "handoff") PENDING_HANDOFF else null,
                lastOutput = action.text, phase = "Waiting for you"))
            insertMessage(s.chatId, "assistant", action.text)
            addStep(s.id, "agent", action.text)
        } else {
            val proposed = BrowserActions.proposal(action, snapshot, instruction, System.currentTimeMillis())
            save(current.copy(status = STATUS_AWAITING_USER, pendingKind = PENDING_ACTION_CONFIRM,
                pendingInstruction = BrowserActions.encode(proposed), pendingDraft = BrowserActions.describe(proposed),
                phase = "Approve one browser action"))
            addStep(s.id, "agent", "Proposed ${action.type}; waiting for approval.")
        }
    }

    fun confirmSend(sessionId: String, expectedToken: String?) = command {
        val s = sessionDao.getById(sessionId) ?: return@command
        if (expectedToken == null || s.pendingKind != PENDING_ACTION_CONFIRM || s.pendingInstruction != expectedToken || s.isTerminal) return@command
        guarded(s) {
            val pending = BrowserActions.decode(expectedToken)
            require(System.currentTimeMillis() <= pending.expiresAt) { "Approval expired. Request the action again." }
            // Persist consumption BEFORE making the external call. No automatic retry after ambiguity.
            save(s.copy(status = STATUS_RUNNING, pendingKind = null, pendingInstruction = null,
                pendingDraft = null, phase = "Executing approved action…"))
            firecrawl.executeApproved(key(), s.scrapeId!!, pending)
            addStep(s.id, "user", "Approved and executed: ${BrowserActions.describe(pending)}")
            plan(s.copy(pendingKind = null, pendingInstruction = null, pendingDraft = null), pending.instruction)
        }
    }
    fun cancelPending(sessionId: String) = command {
        val s = sessionDao.getById(sessionId) ?: return@command
        if (s.isTerminal) return@command
        if (!s.hasLiveBrowser) close(s, STATUS_STOPPED)
        else save(s.copy(status = STATUS_AWAITING_INSTRUCTION, pendingKind = null, pendingDraft = null,
            pendingInstruction = null, phase = "Action cancelled", lastActivityAt = System.currentTimeMillis()))
    }
    fun finish(sessionId: String) { endSession(sessionId, STATUS_COMPLETED) }
    suspend fun finishNow(sessionId: String) { endSession(sessionId, STATUS_COMPLETED).join() }
    fun stop(sessionId: String) { endSession(sessionId, STATUS_STOPPED) }
    private fun endSession(sessionId: String, status: String): Job {
        closingJob?.takeIf { it.isActive }?.let { return it }
        val previous = commandJob
        val job = scope.launch(start = CoroutineStart.LAZY) {
            previous?.cancelAndJoin()
            startup.join()
            sessionDao.getById(sessionId)?.takeUnless { it.isTerminal }?.let { close(it, status) }
        }
        closingJob = job
        job.start()
        return job
    }
    private suspend fun close(s: BrowserSession, status: String) {
        // Mark terminal even if best-effort remote cleanup fails.
        save(s.copy(status = status, phase = if (status == STATUS_COMPLETED) "Finished" else "Closed",
            pendingKind = null, pendingDraft = null, pendingInstruction = null))
        if (s.hasLiveBrowser) firecrawl.stop(settings.getSearchApiKeyDirect("firecrawl"), s.scrapeId!!)
        if (_openWorkspaceFor.value == s.chatId) _openWorkspaceFor.value = null
    }
    private suspend fun save(s: BrowserSession) = sessionDao.upsert(s.copy(updatedAt = System.currentTimeMillis()))
    private suspend fun addStep(id: String, role: String, text: String) =
        stepDao.insert(BrowserStep(UUID.randomUUID().toString(), id, role, text, System.currentTimeMillis()))
    private suspend fun insertMessage(chatId: String, role: String, text: String) {
        messageDao.insertMessage(ChatMessage(UUID.randomUUID().toString(), chatId, role, text, System.currentTimeMillis()))
        chatDao.touchUpdatedAt(chatId, System.currentTimeMillis())
    }
}
