package com.echoflow.data

/**
 * Turns Parallel's SSE firehose into a short chapter timeline.
 *
 * Parallel emits one `task_run.progress_msg.*` event per plan line, search **Objective**,
 * individual **Query**, tool call, and intermediate result. Treating each as a [ResearchStep]
 * floods the UI (dozens–hundreds of rows). Firecrawl and the agentic path already speak in a
 * handful of beats; this collapser makes Parallel match that shape **before** steps are
 * persisted.
 *
 * Chapters:
 *  - **one** plan step for all `.plan` messages
 *  - **one** search step per `Objective:` (or free-form search that opens a new beat)
 *  - `Query:` lines and tool/result chatter fold into the **active** chapter's [ResearchStep.detail]
 *  - `progress_stats` refresh the active chapter's meta (`12 of 40 read`)
 *
 * Ids are stable for the life of a stream (`parallel-plan`, `parallel-obj-N`, …) so replay and
 * upserts stay coherent. Pure: no IO — the engine feeds events and emits the returned steps.
 */
internal class ParallelProgressCollapser {

    private var activeId: String? = null
    private var activeLabel: String? = null
    private var activeKind: String = ResearchStep.KIND_ANALYZE
    private var planOpened = false
    private var objectiveIndex = 0
    private var workIndex = 0
    private val queries = mutableListOf<String>()
    private val notes = mutableListOf<String>()
    private var stats: String? = null

    /**
     * @param subtype last segment of `task_run.progress_msg.<subtype>` (plan, search, tool, …)
     * @param message human line from the event payload
     * @return zero or more steps to upsert (close previous + open/update active)
     */
    fun onProgressMessage(subtype: String, message: String): List<ResearchStep> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when (subtype) {
            "exec_status" -> {
                // Status noise — keep the chapter, refresh meta only.
                activeId ?: return emptyList()
                listOf(emitActive(ResearchStep.STATE_ACTIVE))
            }
            "plan" -> onPlan(trimmed)
            "search" -> onSearch(trimmed)
            "tool" -> onWork(trimmed, kind = ResearchStep.KIND_READ, fallbackLabel = "Using tools")
            "result" -> onWork(trimmed, kind = ResearchStep.KIND_ANALYZE, fallbackLabel = "Gathering findings")
            else -> onWork(trimmed, kind = ResearchStep.KIND_ANALYZE, fallbackLabel = "Researching")
        }
    }

    fun onStats(sourcesRead: Int?, sourcesConsidered: Int?): List<ResearchStep> {
        stats = when {
            sourcesRead != null && sourcesConsidered != null -> "$sourcesRead of $sourcesConsidered read"
            sourcesRead != null -> "$sourcesRead read"
            sourcesConsidered != null -> "$sourcesConsidered found"
            else -> stats
        }
        val id = activeId ?: return emptyList()
        return listOf(
            ResearchStep(
                id = id,
                kind = activeKind,
                label = activeLabel ?: "Researching",
                state = ResearchStep.STATE_ACTIVE,
                detail = detailLine(),
                startedAt = now(),
            )
        )
    }

    fun onTerminal(failed: Boolean): List<ResearchStep> {
        val id = activeId ?: return emptyList()
        val state = if (failed) ResearchStep.STATE_FAILED else ResearchStep.STATE_DONE
        return listOf(emitActive(state))
    }

    private fun onPlan(message: String): List<ResearchStep> {
        val out = mutableListOf<ResearchStep>()
        if (activeId != null && activeId != PLAN_ID) {
            out += emitActive(ResearchStep.STATE_DONE)
            clearChapterExtras()
        }
        activeId = PLAN_ID
        activeKind = ResearchStep.KIND_PLAN
        if (!planOpened) {
            planOpened = true
            activeLabel = cleanLabel(message, fallback = "Planning the research")
        } else {
            // Keep the first plan line as the title; later plan chatter is detail.
            notes.add(message.take(NOTE_MAX))
            trimNotes()
        }
        out += emitActive(ResearchStep.STATE_ACTIVE)
        return out
    }

    private fun onSearch(message: String): List<ResearchStep> {
        parseObjective(message)?.let { objective ->
            return openChapter(
                id = "parallel-obj-${objectiveIndex++}",
                label = objective,
                kind = ResearchStep.KIND_SEARCH,
            )
        }
        parseQuery(message)?.let { query ->
            if (activeId == null || activeKind == ResearchStep.KIND_PLAN) {
                // Query without a prior Objective still opens a search chapter.
                return openChapter(
                    id = "parallel-obj-${objectiveIndex++}",
                    label = query,
                    kind = ResearchStep.KIND_SEARCH,
                )
            }
            queries.add(query)
            return listOf(emitActive(ResearchStep.STATE_ACTIVE))
        }
        // Free-form search line: new chapter if we aren't already searching.
        if (activeId == null || activeKind != ResearchStep.KIND_SEARCH) {
            return openChapter(
                id = "parallel-obj-${objectiveIndex++}",
                label = cleanLabel(message, fallback = "Searching"),
                kind = ResearchStep.KIND_SEARCH,
            )
        }
        notes.add(message.take(NOTE_MAX))
        trimNotes()
        return listOf(emitActive(ResearchStep.STATE_ACTIVE))
    }

    private fun onWork(message: String, kind: String, fallbackLabel: String): List<ResearchStep> {
        val out = mutableListOf<ResearchStep>()
        if (activeId == null) {
            // No chapter yet — open a single work row rather than one row per tool event.
            out += openChapter(
                id = "parallel-work-${workIndex++}",
                label = fallbackLabel,
                kind = kind,
            )
        }
        // Prefer folding tool/result into the open objective rather than spawning micro-rows.
        notes.add(message.take(NOTE_MAX))
        trimNotes()
        // Don't overwrite SEARCH/PLAN kind when we're nested under an objective.
        if (activeKind != ResearchStep.KIND_SEARCH && activeKind != ResearchStep.KIND_PLAN) {
            activeKind = kind
        }
        // Re-emit active so detail includes the new note (openChapter's last emit had empty detail).
        if (out.isNotEmpty()) out.removeAt(out.lastIndex)
        out += emitActive(ResearchStep.STATE_ACTIVE)
        return out
    }

    private fun openChapter(id: String, label: String, kind: String): List<ResearchStep> {
        val out = mutableListOf<ResearchStep>()
        if (activeId != null) {
            out += emitActive(ResearchStep.STATE_DONE)
        }
        clearChapterExtras()
        activeId = id
        activeLabel = label
        activeKind = kind
        out += emitActive(ResearchStep.STATE_ACTIVE)
        return out
    }

    private fun emitActive(state: String): ResearchStep {
        val id = activeId ?: error("emitActive without active chapter")
        val now = now()
        return ResearchStep(
            id = id,
            kind = activeKind,
            label = activeLabel ?: "Researching",
            state = state,
            detail = detailLine(),
            startedAt = now,
            endedAt = if (state == ResearchStep.STATE_DONE || state == ResearchStep.STATE_FAILED) now else null,
        )
    }

    private fun detailLine(): String? {
        val parts = mutableListOf<String>()
        when {
            queries.isEmpty() -> Unit
            queries.size == 1 -> parts.add(queries.first().take(QUERY_SHOW_MAX))
            else -> parts.add("${queries.size} queries")
        }
        notes.lastOrNull()?.let { parts.add(it) }
        stats?.let { parts.add(it) }
        return parts.joinToString(" · ").ifBlank { null }
    }

    private fun clearChapterExtras() {
        queries.clear()
        notes.clear()
        // stats stay run-wide — still useful on the next chapter
    }

    private fun trimNotes() {
        while (notes.size > MAX_NOTES) notes.removeAt(0)
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        internal const val PLAN_ID = "parallel-plan"
        private const val NOTE_MAX = 100
        private const val QUERY_SHOW_MAX = 80
        private const val MAX_NOTES = 4

        private val OBJECTIVE_PREFIX = Regex("""^(?i)objective:\s*(.+)$""")
        private val QUERY_PREFIX = Regex("""^(?i)query:\s*(.+)$""")

        internal fun parseObjective(message: String): String? =
            OBJECTIVE_PREFIX.matchEntire(message.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        internal fun parseQuery(message: String): String? =
            QUERY_PREFIX.matchEntire(message.trim())?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

        private fun cleanLabel(message: String, fallback: String): String {
            val t = message.trim()
            return when {
                t.isEmpty() -> fallback
                t.length <= 140 -> t
                else -> t.take(137) + "…"
            }
        }
    }
}
