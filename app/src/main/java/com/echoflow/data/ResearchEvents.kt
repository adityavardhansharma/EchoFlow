package com.echoflow.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Progress/result events emitted by [DeepResearchEngine] for the service to persist. */
sealed class ResearchEvent {
    /** Provider-native job created — persist [jobId] so the run can resume by re-polling. */
    data class ProviderJob(val jobId: String) : ResearchEvent()
    data class Plan(val steps: List<String>) : ResearchEvent()
    data class Phase(
        val status: String,
        val label: String,
        val done: Int = 0,
        val total: Int = 0,
    ) : ResearchEvent()

    /**
     * Timeline steps to upsert by [ResearchStep.id] — emit a step as active, re-emit the same id
     * to resolve it. Carries a list so an engine can seed a whole plan (all rows pending) in one
     * write instead of one Room round-trip per sub-question.
     *
     * [Phase] is still emitted alongside this: the foreground notification needs a single line,
     * and legacy runs resuming across the update render from `phase` alone.
     */
    data class Steps(val steps: List<ResearchStep>) : ResearchEvent()

    /** Newly discovered sources to merge into the accumulated set. */
    data class Sources(val sources: List<SearchSource>) : ResearchEvent()

    /** Final report markdown. */
    data class Report(val text: String) : ResearchEvent()

    /** Final structured result (JSON string) from the Data Agent. */
    data class Structured(val json: String) : ResearchEvent()

    /** Live cost/credits meter text, e.g. "$0.12" or "84 credits". */
    data class Cost(val label: String) : ResearchEvent()

    data class Failed(val message: String) : ResearchEvent()
}
