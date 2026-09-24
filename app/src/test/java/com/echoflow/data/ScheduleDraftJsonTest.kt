package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json is an Android API; Robolectric supplies the real implementation. */
@RunWith(RobolectricTestRunner::class)
class ScheduleDraftJsonTest {
    @Test fun draftJsonRoundTrips() {
        val draft = ScheduleDraft(title = "Brief", instruction = "News", unit = ScheduleTask.WEEK, interval = 2,
            hour = 7, minute = 30, weekdays = ScheduleDays.WEEKDAYS, endDate = "2027-01-01", modelId = "a/b")
        assertEquals(draft, ScheduleDraft.fromJson(draft.toJson(), "fallback"))
    }
}
