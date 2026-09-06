package com.echoflow.ui

import com.echoflow.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickTaskControllerTest {
    private class Dao : QuickTaskDao {
        val rows = MutableStateFlow<List<QuickTask>>(emptyList())
        override fun observeAll() = rows
        override suspend fun get(id: String) = rows.value.find { it.id == id }
        override suspend fun save(task: QuickTask) { rows.value = rows.value.filterNot { it.id == task.id } + task }
        override suspend fun interruptOrphans() { rows.value = rows.value.map { if (it.status == "running") it.copy(status = "interrupted") else it } }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    }
    private val models = listOf(TaskModel("provider/a", "A"), TaskModel("provider/b", "B"))
    @Test fun `both models receive identical input and failure does not discard the other answer`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val dao = Dao()
            val seen = mutableListOf<Pair<String, SharedInput>>()
            val controller = QuickTaskController(dao, scope) { model, prompt, input ->
                seen += prompt to input
                flow { if (model.endsWith("b")) error("provider failed") else {
                    emit(StreamChunk.Content("answer A")); emit(StreamChunk.Usage(10, 4, 0.01))
                } }
            }
            val input = SharedInput("share", "same reference")
            controller.start(input, "explain", models)
            withTimeout(2000) { controller.busy.first { !it } }
            val answers = QuickTaskJson.answers(controller.current.value!!.answersJson)
            assertEquals(setOf("explain" to input), seen.toSet())
            assertEquals("answer A", answers[0].text)
            assertEquals(0.01, answers[0].costUsd!!, 0.00001)
            assertEquals("failed", answers[1].status)
            controller.prefer(models[0].id)
            assertEquals(models[0].id, dao.get(controller.current.value!!.id)!!.preferredModelId)
        } finally { scope.cancel() }
    }
    @Test fun `invalid second selection prevents any model request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            val dao = Dao()
            val controller = QuickTaskController(dao, scope) { model, _, _ ->
                require(!model.endsWith("b")) { "Missing credentials" }
                flow { requests++; emit(StreamChunk.Content("should not run")) }
            }
            controller.start(SharedInput("share"), "explain", models)
            withTimeout(2000) { controller.busy.first { !it } }
            assertEquals(0, requests)
            assertTrue(dao.rows.value.isEmpty())
            assertEquals("Missing credentials", controller.error.value)
        } finally { scope.cancel() }
    }
    @Test fun `stop preserves partial answers and duplicate send is rejected`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val dao = Dao()
            var accepted = 0
            val controller = QuickTaskController(dao, scope) { _, _, _ -> flow { emit(StreamChunk.Content("partial")); awaitCancellation() } }
            controller.start(SharedInput("share"), "explain", models) { accepted++ }
            controller.start(SharedInput("share"), "explain", models) { accepted++ }
            controller.cancel()
            withTimeout(2000) { controller.busy.first { !it } }
            assertEquals(1, accepted)
            assertEquals(1, dao.rows.value.size)
            assertEquals("cancelled", dao.rows.value.single().status)
        } finally { scope.cancel() }
    }
    @Test fun `two local models run sequentially`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var active = 0
            var peak = 0
            val controller = QuickTaskController(Dao(), scope) { _, _, _ -> flow {
                active++; peak = maxOf(peak, active)
                delay(20); emit(StreamChunk.Content("local answer")); active--
            } }
            controller.start(SharedInput("share"), "explain", listOf(TaskModel("local/a", "A"), TaskModel("local/b", "B")))
            withTimeout(2000) { controller.busy.first { !it } }
            assertEquals(1, peak)
        } finally { scope.cancel() }
    }
}
