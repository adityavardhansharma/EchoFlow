package com.echoflow.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.echoflow.data.*
import com.echoflow.testSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatSendOwnershipTest {
    @Test fun `navigation during artifact lookup cannot move the pending message`() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        var vm: ChatViewModel? = null
        try {
            db.chatDao().insertThread(ChatThread("a", "A", 1, 1))
            db.chatDao().insertThread(ChatThread("b", "B", 2, 2))
            val settings = SettingsRepository(app, testSettings(app))
            settings.saveApiKey("test-key-never-sent")
            settings.saveSelectedModel("test/model")
            val lookupStarted = CompletableDeferred<Unit>()
            val releaseLookup = CompletableDeferred<Unit>()
            val inserted = CompletableDeferred<ChatMessage>()
            val artifacts = object : ArtifactDao by db.artifactDao() {
                override suspend fun getLatestForChat(chatId: String): Artifact? {
                    lookupStarted.complete(Unit)
                    releaseLookup.await()
                    return null
                }
            }
            val messages = object : MessageDao by db.messageDao() {
                override suspend fun insertMessage(message: ChatMessage) {
                    inserted.complete(message)
                    // Stop before any provider request; this test exercises preparation only.
                    throw CancellationException("Preparation captured")
                }
            }
            vm = ChatViewModel(app, db.chatDao(), messages, settings, db.localModelDao(),
                db.researchRunDao(), db.deepResearchModelDao(), db.advisorProfileDao(), db.fusionPanelDao(),
                db.agentProfileDao(), db.browserSessionDao(), db.browserStepDao(), artifacts,
                db.artifactVersionDao(), db.generatedImageDao(), db.generatedVideoDao(), db.projectDao(),
                db.projectDocumentDao(), LocalInferenceGate())
            vm.selectThread("a")
            vm.toggleArtifact()
            vm.sendMessage("Build a page")
            withTimeout(5000) { lookupStarted.await() }
            vm.selectThread("b")
            releaseLookup.complete(Unit)
            assertEquals("a", withTimeout(5000) { inserted.await() }.chatId)
        } finally {
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            db.close()
            Dispatchers.resetMain()
        }
    }
}
