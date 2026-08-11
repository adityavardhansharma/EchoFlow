package com.echoflow.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.echoflow.MainActivity
import com.echoflow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that runs Deep Research to completion independently of the UI.
 *
 * It is the orchestration owner: it drives [DeepResearchEngine], writes every progress
 * update to the [ResearchRun] row (the UI observes that, never this service directly),
 * shows an ongoing notification with a Cancel action, and on completion writes the final
 * assistant [ChatMessage]. Because all state is in Room, a run survives the app being
 * minimized or killed and is resumed via [ACTION_RESUME] on next launch.
 */
class DeepResearchForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val db by lazy { AppDatabase.getDatabase(applicationContext) }
    private val settings by lazy { SettingsRepository(applicationContext) }
    private val engine by lazy {
        DeepResearchEngine(OpenRouterService(applicationContext), WebSearchService())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Deep Research", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Shows progress of long-running research" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always post a notification synchronously so the FGS start window is satisfied.
        startForegroundNow("Deep Research", "Starting…")

        when (intent?.action) {
            ACTION_CANCEL -> {
                val runId = intent.getStringExtra(EXTRA_RUN_ID)
                if (runId != null) cancelRun(runId)
            }
            ACTION_RESUME -> scope.launch {
                db.researchRunDao().getInterrupted().forEach { startRun(it.id) }
                stopIfIdle()
            }
            else -> {
                val runId = intent?.getStringExtra(EXTRA_RUN_ID)
                if (runId != null) scope.launch { startRun(runId) }
                else scope.launch { stopIfIdle() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startRun(runId: String) {
        if (jobs.containsKey(runId)) return
        val run = db.researchRunDao().getById(runId) ?: return
        if (run.isTerminal) return

        val job = scope.launch {
            try {
                execute(run)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // cancellation is handled by cancelRun(); never a "failure"
            } catch (e: Exception) {
                finishFailed(runId, e.message ?: "Deep research failed.")
            } finally {
                jobs.remove(runId)
                stopIfIdle()
            }
        }
        jobs[runId] = job
    }

    private suspend fun execute(initial: ResearchRun) {
        val runId = initial.id
        val config = buildConfig(initial) ?: run {
            finishFailed(runId, "This research engine is no longer available.")
            return
        }

        val openRouterKey = settings.getApiKeyDirect()
        val searchKey = when (config.engineKind) {
            "provider" -> settings.getSearchApiKeyDirect(config.provider)
            "data-agent" -> settings.getSearchApiKeyDirect("firecrawl")
            else -> config.searchProvider?.let { settings.getSearchApiKeyDirect(it) }.orEmpty()
        }

        // Local mutable accumulators mirrored into Room after every event.
        var current = initial.copy(status = ResearchRun.STATUS_RESEARCHING, updatedAt = now())
        val sources = LinkedHashMap<String, SearchSource>()
        ResearchJson.sourcesFromJson(initial.sourcesJson).forEach { sources[it.url] = it }
        // Timeline steps, keyed by id and ordered by first appearance — seeded from the row so a
        // resumed run continues its history instead of starting a second one.
        val timeline = LinkedHashMap<String, ResearchStep>()
        ResearchJson.timelineFromJson(initial.stepsJson).forEach { timeline[it.id] = it }
        persist(current)
        updateNotification(current)

        engine.run(config, current.topic, openRouterKey, searchKey, initial).collect { event ->
            when (event) {
                is ResearchEvent.ProviderJob -> {
                    current = current.copy(providerJobId = event.jobId, updatedAt = now())
                }
                is ResearchEvent.Plan -> {
                    current = current.copy(planJson = ResearchJson.stepsToJson(event.steps), updatedAt = now())
                }
                is ResearchEvent.Phase -> {
                    current = current.copy(
                        status = event.status,
                        phase = event.label,
                        progressDone = event.done,
                        progressTotal = event.total,
                        updatedAt = now(),
                    )
                }
                is ResearchEvent.Steps -> {
                    // A re-planned run resets the kinds it is about to re-seed, so rows from the
                    // abandoned plan can't linger as phantom pending steps.
                    if (event.replaceKinds.isNotEmpty()) {
                        timeline.entries.removeAll { it.value.kind in event.replaceKinds }
                    }
                    event.steps.forEach { incoming ->
                        val existing = timeline[incoming.id]
                        timeline[incoming.id] = if (existing == null) incoming else incoming.copy(
                            // Keep the moment the step first started; the engine re-stamps
                            // startedAt on every upsert because it has no memory of its own.
                            startedAt = existing.startedAt.takeIf { it > 0L } ?: incoming.startedAt,
                        )
                    }
                    current = current.copy(stepsJson = ResearchJson.timelineToJson(timeline.values.toList()), updatedAt = now())
                }
                is ResearchEvent.Sources -> {
                    event.sources.forEach { if (it.url !in sources) sources[it.url] = it }
                    current = current.copy(sourcesJson = ResearchJson.sourcesToJson(sources.values.toList()), updatedAt = now())
                }
                is ResearchEvent.Cost -> {
                    current = current.copy(costInfo = event.label, updatedAt = now())
                }
                is ResearchEvent.Report -> {
                    finishCompleted(current, event.text, sources.values.toList())
                    return@collect
                }
                is ResearchEvent.Structured -> {
                    finishStructured(current, event.json, sources.values.toList())
                    return@collect
                }
                is ResearchEvent.Failed -> {
                    finishFailed(runId, event.message)
                    return@collect
                }
            }
            persist(current)
            updateNotification(current)
        }
    }

    private fun buildConfig(run: ResearchRun): DeepResearchConfig? {
        return when (run.engineKind) {
            "provider" -> {
                val eng = DeepResearchCatalog.providerEngineById(run.engineId) ?: return null
                DeepResearchConfig(
                    engineId = run.engineId,
                    engineKind = "provider",
                    engineLabel = run.engineLabel,
                    provider = eng.provider,
                    providerModel = eng.providerModel,
                    searchProvider = null,
                    level = run.level,
                    maxSearches = run.maxSearches,
                    maxSources = run.maxSources,
                    maxCredits = run.maxCredits,
                )
            }
            "data-agent" -> {
                val eng = DataAgentCatalog.byId(run.engineId) ?: return null
                DeepResearchConfig(
                    engineId = run.engineId,
                    engineKind = "data-agent",
                    engineLabel = run.engineLabel,
                    provider = eng.provider,
                    providerModel = eng.providerModel,
                    searchProvider = null,
                    level = run.level,
                    maxSearches = run.maxSearches,
                    maxSources = run.maxSources,
                    maxCredits = run.maxCredits,
                )
            }
            else -> DeepResearchConfig(
                engineId = run.engineId,
                engineKind = "agent",
                engineLabel = run.engineLabel,
                provider = run.engineId, // chat model id
                providerModel = "",
                searchProvider = run.searchProvider,
                level = run.level,
                maxSearches = run.maxSearches,
                maxSources = run.maxSources,
                maxCredits = run.maxCredits,
            )
        }
    }

    private suspend fun finishCompleted(run: ResearchRun, report: String, sources: List<SearchSource>) =
        finishWithResult(run, report, sources, structured = false)

    private suspend fun finishStructured(run: ResearchRun, json: String, sources: List<SearchSource>) =
        finishWithResult(run, json, sources, structured = true)

    /**
     * Write the answer into the conversation and close out the run.
     *
     * This is the one place the old and new research UIs diverge. A run stamped
     * [ResearchRun.UI_VERSION_LEGACY] — which is every run that predates the redesign, plus any
     * that was already in flight when the app updated — keeps writing the original
     * `"plan"` + `"report"` / `"data"` segments, so it renders through `ui/legacy` exactly as it
     * always did. New runs write a single `"research"` segment carrying a [ResearchRef], which is
     * what the result card and workspace read. Dispatch happens on the segment type at render
     * time, so no existing message can ever be reclassified.
     */
    private suspend fun finishWithResult(
        run: ResearchRun,
        payload: String,
        sources: List<SearchSource>,
        structured: Boolean,
    ) {
        val citations = sources.distinctBy { it.url }.map { Citation(it.title, it.url) }
        // One timestamp for the closed steps, the message, the chat bump and the run, so the
        // steps can't end fractionally before the run they belong to.
        val finishedAt = now()
        val timeline = closedTimeline(run, ResearchStep.STATE_DONE, finishedAt)
        val segments = if (run.usesLegacyUi) {
            val planSteps = ResearchJson.stepsFromJson(run.planJson)
            buildList {
                if (!structured && planSteps.isNotEmpty()) {
                    add(PersistedSegment(type = "plan", text = planSteps.joinToString("\n")))
                }
                add(PersistedSegment(type = if (structured) "data" else "report", text = payload))
            }
        } else {
            listOf(
                PersistedSegment(
                    type = "research",
                    research = ResearchRef(
                        runId = run.id,
                        topic = run.topic,
                        report = payload,
                        engineLabel = run.engineLabel,
                        structured = structured,
                        sourceCount = citations.size,
                        stepCount = timeline.size,
                        durationMs = (finishedAt - run.createdAt).coerceAtLeast(0L),
                        costInfo = run.costInfo,
                    ),
                )
            )
        }
        val messageId = UUID.randomUUID().toString()
        db.messageDao().insertMessage(
            ChatMessage(
                id = messageId,
                chatId = run.chatId,
                role = "assistant",
                content = payload,
                createdAt = finishedAt,
                citationsJson = ToolEventJson.citationsToJson(citations),
                segmentsJson = ToolEventJson.segmentsToJson(segments),
            )
        )
        db.chatDao().touchUpdatedAt(run.chatId, finishedAt)
        persist(
            run.copy(
                status = ResearchRun.STATUS_COMPLETED,
                phase = "Completed",
                report = payload,
                stepsJson = ResearchJson.timelineToJson(timeline),
                sourcesJson = ResearchJson.sourcesToJson(sources),
                assistantMessageId = messageId,
                updatedAt = finishedAt,
            )
        )
    }

    private suspend fun finishFailed(runId: String, message: String) {
        val run = db.researchRunDao().getById(runId) ?: return
        if (run.isTerminal) return
        val failedAt = now()
        val timeline = closedTimeline(run, ResearchStep.STATE_FAILED, failedAt)
        var messageId: String? = null
        if (run.usesLegacyUi) {
            writeNote(run.chatId, "⚠️ Deep Research couldn't finish: $message")
        } else {
            // New runs surface the failure as the result card's error variant (with a retry)
            // rather than a loose warning line in the transcript.
            messageId = UUID.randomUUID().toString()
            db.messageDao().insertMessage(
                ChatMessage(
                    id = messageId,
                    chatId = run.chatId,
                    role = "assistant",
                    content = "Deep Research couldn't finish: $message",
                    createdAt = failedAt,
                    segmentsJson = ToolEventJson.segmentsToJson(
                        listOf(
                            PersistedSegment(
                                type = "research",
                                research = ResearchRef(
                                    runId = run.id,
                                    topic = run.topic,
                                    report = "",
                                    engineLabel = run.engineLabel,
                                    structured = run.engineKind == "data-agent",
                                    stepCount = timeline.size,
                                    durationMs = (failedAt - run.createdAt).coerceAtLeast(0L),
                                    costInfo = run.costInfo,
                                    error = message,
                                ),
                            )
                        )
                    ),
                )
            )
            db.chatDao().touchUpdatedAt(run.chatId, failedAt)
        }
        persist(
            run.copy(
                status = ResearchRun.STATUS_FAILED,
                phase = "Failed",
                error = message,
                stepsJson = ResearchJson.timelineToJson(timeline),
                assistantMessageId = messageId ?: run.assistantMessageId,
                updatedAt = failedAt,
            )
        )
    }

    /**
     * Close out a timeline when the run reaches a terminal status.
     *
     * A step that was running is resolved to [state]. A step that was still *pending* never ran at
     * all, so it is dropped rather than closed — marking an unstarted sub-question "done" would
     * put work in the workspace's Steps tab that the run never did.
     */
    private fun closedTimeline(run: ResearchRun, state: String, closedAt: Long): List<ResearchStep> =
        ResearchJson.timelineFromJson(run.stepsJson)
            .filter { it.isTerminal || it.state != ResearchStep.STATE_PENDING }
            .map { if (it.isTerminal) it else it.copy(state = state, endedAt = closedAt) }

    private fun cancelRun(runId: String) {
        jobs.remove(runId)?.cancel()
        scope.launch {
            val run = db.researchRunDao().getById(runId)
            if (run != null && !run.isTerminal) {
                // Close the timeline too, or the step that was mid-flight keeps its spinner
                // forever on a run the user already stopped.
                val cancelledAt = now()
                writeNote(run.chatId, "🛑 Deep Research was cancelled.")
                persist(
                    run.copy(
                        status = ResearchRun.STATUS_CANCELLED,
                        phase = "Cancelled",
                        stepsJson = ResearchJson.timelineToJson(
                            closedTimeline(run, ResearchStep.STATE_FAILED, cancelledAt)
                        ),
                        updatedAt = cancelledAt,
                    )
                )
            }
            stopIfIdle()
        }
    }

    /** A plain assistant message so the chat always reflects a research outcome. */
    private suspend fun writeNote(chatId: String, text: String) {
        db.messageDao().insertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                role = "assistant",
                content = text,
                createdAt = now(),
            )
        )
        db.chatDao().touchUpdatedAt(chatId, now())
    }

    private suspend fun persist(run: ResearchRun) {
        db.researchRunDao().upsert(run)
    }

    private fun stopIfIdle() {
        if (jobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Notification ─────────────────────────────────────────────────────────────────

    private fun startForegroundNow(title: String, text: String) {
        val notification = baseNotification(title, text, null).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(run: ResearchRun) {
        val text = buildString {
            append(run.phase ?: "Researching…")
            if (run.progressTotal > 0) append(" (${run.progressDone}/${run.progressTotal})")
        }
        val builder = baseNotification("🔬 ${run.topic.take(40)}", text, run.id)
        if (run.progressTotal > 0) builder.setProgress(run.progressTotal, run.progressDone, false)
        else builder.setProgress(0, 0, true)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, builder.build())
    }

    private fun baseNotification(title: String, text: String, cancelRunId: String?): NotificationCompat.Builder {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (cancelRunId != null) {
            val cancelIntent = PendingIntent.getService(
                this, cancelRunId.hashCode(),
                Intent(this, DeepResearchForegroundService::class.java)
                    .setAction(ACTION_CANCEL).putExtra(EXTRA_RUN_ID, cancelRunId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Cancel", cancelIntent)
        }
        return builder
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun now() = System.currentTimeMillis()

    companion object {
        private const val CHANNEL_ID = "deep_research"
        private const val NOTIFICATION_ID = 73
        internal const val ACTION_CANCEL = "com.echoflow.research.CANCEL"
        internal const val ACTION_RESUME = "com.echoflow.research.RESUME"
        internal const val EXTRA_RUN_ID = "run_id"

        /** Start (or continue) a specific run. Must be called from the foreground (a tap). */
        fun start(context: Context, runId: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DeepResearchForegroundService::class.java).putExtra(EXTRA_RUN_ID, runId),
                )
            }
        }

        /** Resume any interrupted runs (call on app launch). */
        fun resume(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DeepResearchForegroundService::class.java).setAction(ACTION_RESUME),
                )
            }
        }

        fun cancel(context: Context, runId: String) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DeepResearchForegroundService::class.java)
                        .setAction(ACTION_CANCEL).putExtra(EXTRA_RUN_ID, runId),
                )
            }
        }
    }
}
