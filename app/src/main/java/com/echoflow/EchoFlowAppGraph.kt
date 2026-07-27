package com.echoflow

import android.app.Application
import com.echoflow.data.AppDatabase
import com.echoflow.data.LegacyImageModelCleanup
import com.echoflow.data.LocalInferenceGate
import com.echoflow.data.ModelDownloadManager
import com.echoflow.data.SettingsRepository
import com.echoflow.ui.ChatViewModel
import com.echoflow.ui.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    // One gate app-wide: only one on-device LLM generation runs at a time.
    private val localInferenceGate = LocalInferenceGate()

    init {
        // On-device image generation left its model bundles on disk — potentially several GB
        // for anyone who had downloaded one. Room migrations cannot touch the filesystem, so
        // the sweep runs once here, off the startup path.
        CoroutineScope(Dispatchers.IO).launch {
            LegacyImageModelCleanup.run(application.applicationContext)
        }
    }

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
            database.imageModelDao(),
            database.videoModelDao(),
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
            database.generatedImageDao(),
            database.generatedVideoDao(),
            localInferenceGate,
        )
    }
}
