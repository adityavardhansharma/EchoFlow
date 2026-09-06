package com.echoflow.data

internal object ResearchResumePolicy {
    /** Only completed entries from this exact persisted plan may skip a search on recovery. */
    fun completed(plan: List<String>, timeline: List<ResearchStep>): Set<String> =
        plan.mapIndexedNotNull { index, question ->
            val id = DeepResearchEngine.searchStepId(index)
            id.takeIf { timeline.any { it.id == id && it.label == question &&
                it.kind == ResearchStep.KIND_SEARCH && it.state == ResearchStep.STATE_DONE } }
        }.toSet()
}
