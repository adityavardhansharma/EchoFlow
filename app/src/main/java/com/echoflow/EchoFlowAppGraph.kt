package com.echoflow

import android.app.Application
import com.echoflow.data.AppDatabase
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel

/**
 * Application-scoped composition root.
 *
 * Keeping construction here prevents Activities and composables from knowing the complete data
 * graph while retaining the existing factories and object lifetimes.
 */
class EchoFlowAppGraph(application: Application) {
    val database: AppDatabase = AppDatabase.getDatabase(application)
    val settingsRepository = SettingsRepository(application.applicationContext)
    private val modelDownloadManager =
        ModelDownloadManager(application.applicationContext, database.localModelDao())

    val settingsViewModelFactory by lazy {
        SettingsViewModel.provideFactory(
            settingsRepository,
            database.customModelDao(),
            database.localModelDao(),
            modelDownloadManager,
            database.deepResearchModelDao(),
            database.advisorProfileDao(),
            database.fusionPanelDao(),
            database.agentProfileDao(),
        )
    }

    val chatViewModelFactory by lazy {
        ChatViewModel.provideFactory(
            application,
            database.chatDao(),
            database.messageDao(),
            settingsRepository,
            database.localModelDao(),
            database.researchRunDao(),
            database.deepResearchModelDao(),
            database.advisorProfileDao(),
            database.fusionPanelDao(),
            database.agentProfileDao(),
            database.browserSessionDao(),
            database.browserStepDao(),
            database.artifactDao(),
            database.artifactVersionDao(),
        )
    }
}
