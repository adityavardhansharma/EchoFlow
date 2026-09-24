package com.echoflow.data

import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleToolsTest {
    private val now = ZonedDateTime.parse("2026-09-23T12:00:00Z").toInstant().toEpochMilli() // a Wednesday

    private class FakeWorkspace(override var saved: ScheduleTask? = null, private val now: Long) : ScheduleWorkspace {
        override var draft: ScheduleDraft? = null
        override val modelId = "test/model"
        var runs = 0
        override fun updateDraft(draft: ScheduleDraft) { this.draft = draft }
        override suspend fun save(): ScheduleTask =
            draft!!.resolve(saved, now = now).also { saved = it; draft = null }
        override fun discard() { draft = null }
        override suspend fun setStatus(status: String): ScheduleTask = saved!!.copy(status = status).also { saved = it }
        override suspend fun runNow() { runs++ }
    }

    private fun tools(workspace: FakeWorkspace) = ScheduleTools(workspace, zoneId = { "UTC" }, now = { now }, locale = Locale.US)
    private fun call(text: String) = ScheduleToolProtocol.calls(text).single()

    @Test fun parsesCallsAndHidesThemFromTheReader() {
        val text = """Sure — every weekday.
            <tool name="update_schedule">{"days": ["mon","tue","wed","thu","fri"], "time": "08:00"}</tool>
            <tool name='save_schedule'/>""".trimIndent()
        val calls = ScheduleToolProtocol.calls(text)
        assertEquals(listOf("update_schedule", "save_schedule"), calls.map { it.name })
        assertEquals("08:00", calls[0].arguments.getString("time"))
        assertEquals("Sure — every weekday.", ScheduleToolProtocol.visible(text))
    }

    @Test fun streamingHoldsBackAPartialTag() {
        assertEquals("Done", ScheduleToolProtocol.visible("Done\n<to", streaming = true))
        assertEquals("Done", ScheduleToolProtocol.visible("Done <tool name=\"save_sch", streaming = true))
        assertEquals("a < b", ScheduleToolProtocol.visible("a < b", streaming = true))
    }

    @Test fun fencedArgumentsStillParse() {
        val parsed = call("<tool name=\"update_schedule\">```json\n{\"title\": \"Brief\"}\n```</tool>")
        assertEquals("Brief", parsed.arguments.getString("title"))
    }

    @Test fun updateDraftsANewScheduleWithEditChips() = runBlocking {
        val workspace = FakeWorkspace(now = now)
        val outcome = tools(workspace).execute(call("""<tool name="update_schedule">{"title":"Morning brief","instruction":"Summarize tech news","repeat":"weekdays","time":"7:30 am","ends":{"after":{"amount":4,"unit":"week"}}}</tool>"""))
        val result = JSONObject(outcome.result)
        assertTrue(result.getBoolean("ok"))
        val draft = workspace.draft!!
        assertEquals(ScheduleTask.WEEK, draft.unit)
        assertEquals(ScheduleDays.WEEKDAYS, draft.weekdays)
        assertEquals(7 to 30, draft.hour to draft.minute)
        assertEquals("2026-10-20", draft.endDate)
        assertEquals("Thu, Sep 24 · 7:30 AM", result.getJSONArray("next_runs").getString(0))
        assertEquals(listOf("Drafted · Weekdays at 7:30 AM"), outcome.edits)
    }

    @Test fun invalidFieldsAreReportedNotGuessed() = runBlocking {
        val workspace = FakeWorkspace(now = now)
        workspace.draft = ScheduleDraft(title = "x", instruction = "y", modelId = "m")
        val outcome = tools(workspace).execute(call("""<tool name="update_schedule">{"time":"25:00","title":"Renamed"}</tool>"""))
        val result = JSONObject(outcome.result)
        assertEquals("Renamed", workspace.draft!!.title)
        assertEquals(9, workspace.draft!!.hour)
        assertTrue(result.getJSONArray("rejected").getString(0).contains("time"))
    }

    @Test fun saveCreatesThenStatusAndRunNowWork() = runBlocking {
        val workspace = FakeWorkspace(now = now)
        val tools = tools(workspace)
        tools.execute(call("""<tool name="update_schedule">{"title":"Water","instruction":"Remind me to drink water","repeat":{"every":2,"unit":"hour"}}</tool>"""))
        val saved = tools.execute(call("<tool name=\"save_schedule\">{}</tool>"))
        assertTrue(saved.saved)
        assertEquals("created", JSONObject(saved.result).getString("status"))
        assertNull(workspace.draft)
        assertEquals(ScheduleTask.PAUSED, tools.execute(call("""<tool name="set_schedule_status">{"status":"pause"}</tool>""")).let { workspace.saved!!.status })
        tools.execute(call("<tool name=\"run_schedule_now\"/>"))
        assertEquals(1, workspace.runs)
    }

    @Test fun saveReportsWhyADraftCannotBeScheduled() = runBlocking {
        val workspace = FakeWorkspace(now = now)
        workspace.draft = ScheduleDraft(title = "", instruction = "y", modelId = "m")
        val result = JSONObject(tools(workspace).execute(call("<tool name=\"save_schedule\"/>")).result)
        assertFalse(result.getBoolean("ok"))
        assertEquals("Give the schedule a name.", result.getString("error"))
    }

    @Test fun clockParsing() {
        assertEquals(20 to 15, ScheduleDraftPatch.parseClock("20:15"))
        assertEquals(20 to 0, ScheduleDraftPatch.parseClock("8pm"))
        assertEquals(0 to 30, ScheduleDraftPatch.parseClock("12:30 a.m."))
        assertNull(ScheduleDraftPatch.parseClock("13 pm"))
    }

    @Test fun eventsRoundTrip() {
        val event = ScheduleEvent(ScheduleEvent.RUN, listOf("a"), "run-1", 42L)
        assertEquals(event, ScheduleEvent.parse(event.toJson()))
        assertNull(ScheduleEvent.parse("not json"))
        assertEquals(Calendar.FRIDAY, ScheduleDays.parse("fri"))
    }
}
