package com.echoflow.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
class BrowserApprovalTest {
    private class Sessions : BrowserSessionDao {
        val rows = MutableStateFlow<List<BrowserSession>>(emptyList())
        override fun observeActiveForChat(chatId: String) = rows.map { it.firstOrNull { s -> s.chatId == chatId && !s.isTerminal } }
        override fun observeAnyActive() = rows.map { it.firstOrNull { s -> !s.isTerminal } }
        override suspend fun getById(id: String) = rows.value.firstOrNull { it.id == id }
        override suspend fun getActiveForChat(chatId: String) = rows.value.firstOrNull { it.chatId == chatId && !it.isTerminal }
        override suspend fun getAllActive() = rows.value.filterNot { it.isTerminal }
        override suspend fun upsert(session: BrowserSession) { rows.value = rows.value.filterNot { it.id == session.id } + session }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    }
    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> unusedDao(): T = Proxy.newProxyInstance(T::class.java.classLoader,arrayOf(T::class.java)) { _,_,_ -> Unit } as T
    private class Browser : FirecrawlBrowserService() {
        var executed = 0
        var opened = 0
        val page = BrowserSnapshot("https://example.com/","Example","Hello",listOf(
            BrowserElement("one","input","text","Message","","https://example.com/send","")))
        override suspend fun startSession(apiKey: String,url: String): StartResult { opened++; return StartResult("remote",null,null,"Example") }
        override suspend fun snapshot(apiKey: String,scrapeId: String) = page to InteractResult(true,"",null,null)
        override suspend fun executeApproved(apiKey: String,scrapeId: String,action: PendingBrowserAction): InteractResult {
            executed++; return InteractResult(true,"Executed",null,null)
        }
        override suspend fun stop(apiKey: String,scrapeId: String) {}
    }
    @Test fun `first instruction waits for opening and exact action approvals and cannot replay`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = com.echoflow.testSettings(context).also { it.edit().clear().commit() }
        val settings = SettingsRepository(context,prefs).also { it.saveSearchApiKey("firecrawl","test") }
        val sessions = Sessions()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val browser = Browser()
        val manager = BrowserAgentManager(unusedDao(),unusedDao(),sessions,unusedDao(),settings,WebSearchService(),scope,
            planner = { _,_ -> if (browser.executed == 0) "{\"type\":\"fill\",\"target\":\"one\",\"text\":\"Hello\"}" else "{\"type\":\"answer\",\"text\":\"Filled.\"}" },firecrawl=browser)
        try {
            manager.startSession("chat","send a message at https://example.com/")
            val initial = sessions.rows.value.single()
            assertEquals(0,browser.opened)
            assertEquals(BrowserSession.PENDING_CONFIRM_DOMAIN,initial.pendingKind)
            manager.confirmDomain(initial.id, "stale")
            assertEquals(0,browser.opened)
            manager.confirmDomain(initial.id, initial.pendingInstruction)
            val proposed = sessions.rows.value.single()
            assertEquals(1,browser.opened)
            assertEquals(0,browser.executed)
            assertEquals(BrowserSession.PENDING_ACTION_CONFIRM,proposed.pendingKind)
            manager.confirmSend(proposed.id,"wrong-token")
            assertEquals(0,browser.executed)
            manager.confirmSend(proposed.id,proposed.pendingInstruction)
            manager.confirmSend(proposed.id,proposed.pendingInstruction)
            assertEquals(1,browser.executed)
            assertNull(sessions.rows.value.single().pendingInstruction)
        } finally { scope.cancel() }
    }
}
