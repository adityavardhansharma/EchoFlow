package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class ResearchResumePolicyTest {
    @Test fun `partial research resumes unfinished and failed searches`() {
        val plan = listOf("first query","second query","third query")
        val timeline = listOf(
            ResearchStep("search-0", label=plan[0],state=ResearchStep.STATE_DONE),
            ResearchStep("search-1", label=plan[1],state=ResearchStep.STATE_ACTIVE),
            ResearchStep("search-2", label=plan[2],state=ResearchStep.STATE_FAILED))
        assertEquals(setOf("search-0"),ResearchResumePolicy.completed(plan,timeline))
    }
    @Test fun `a stale plan or missing completion never skips research`() {
        val done = ResearchStep("search-0",label="old question",state=ResearchStep.STATE_DONE)
        assertTrue(ResearchResumePolicy.completed(listOf("new question"),listOf(done)).isEmpty())
        assertTrue(ResearchResumePolicy.completed(listOf("new question"),emptyList()).isEmpty())
    }
}
