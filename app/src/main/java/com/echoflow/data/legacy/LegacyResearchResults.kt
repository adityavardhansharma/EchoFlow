package com.echoflow.data.legacy

import com.echoflow.data.PersistedSegment
import com.echoflow.data.ResearchJson
import com.echoflow.data.ResearchRun

/**
 * What a research run still stamped [ResearchRun.UI_VERSION_LEGACY] writes into the chat when it
 * finishes. Only a run that was already in flight across the app update reaches this; every newer
 * run writes a single `"research"` segment instead. The shapes here are what `ui/legacy` reads.
 */
object LegacyResearchResults {
    /** A `"plan"` segment (reports only) followed by the `"report"` or `"data"` payload. */
    fun resultSegments(run: ResearchRun, payload: String, structured: Boolean): List<PersistedSegment> {
        val planSteps = ResearchJson.stepsFromJson(run.planJson)
        return buildList {
            if (!structured && planSteps.isNotEmpty()) {
                add(PersistedSegment(type = "plan", text = planSteps.joinToString("\n")))
            }
            add(PersistedSegment(type = if (structured) "data" else "report", text = payload))
        }
    }

    /** A failed legacy run leaves a plain warning line; new runs use the result card's error state. */
    fun failureNote(message: String): String = "⚠️ Deep Research couldn't finish: $message"
}
