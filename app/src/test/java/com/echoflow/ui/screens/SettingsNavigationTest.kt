package com.echoflow.ui.screens

import com.echoflow.ui.screens.settings.PageAppearance
import com.echoflow.ui.screens.settings.PageBrowserFlow
import com.echoflow.ui.screens.settings.PageCloudModels
import com.echoflow.ui.screens.settings.PageCustomProvider
import com.echoflow.ui.screens.settings.PageCustomProviderClaude
import com.echoflow.ui.screens.settings.PageCustomProviderCloud
import com.echoflow.ui.screens.settings.PageCustomProviderCompatible
import com.echoflow.ui.screens.settings.PageCustomProviderOllama
import com.echoflow.ui.screens.settings.PageCustomProviderOpenAi
import com.echoflow.ui.screens.settings.PageCustomProviderSarvam
import com.echoflow.ui.screens.settings.PageDataAgent
import com.echoflow.ui.screens.settings.PageDeepResearch
import com.echoflow.ui.screens.settings.PageEchoAdviser
import com.echoflow.ui.screens.settings.PageEchoAgent
import com.echoflow.ui.screens.settings.PageEchoFusion
import com.echoflow.ui.screens.settings.PageEchoLabs
import com.echoflow.ui.screens.settings.PageHome
import com.echoflow.ui.screens.settings.PageImagine
import com.echoflow.ui.screens.settings.PageLicenses
import com.echoflow.ui.screens.settings.PageLocalModels
import com.echoflow.ui.screens.settings.PageModels
import com.echoflow.ui.screens.settings.PageSpeechToText
import com.echoflow.ui.screens.settings.PageWebSearch
import com.echoflow.ui.screens.settings.settingsParentPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsNavigationTest {

    @Test
    fun settingsHubHasNoParent() {
        assertNull(settingsParentPage(PageHome))
    }

    @Test
    fun topLevelPagesReturnToHub() {
        listOf(
            PageAppearance,
            PageModels,
            PageCloudModels,
            PageWebSearch,
            PageLocalModels,
            PageDeepResearch,
            PageImagine,
            PageSpeechToText,
            PageEchoLabs,
            PageCustomProviderCloud,
        ).forEach { page ->
            assertEquals(PageHome, settingsParentPage(page))
        }
    }

    @Test
    fun echoLabsPagesReturnToEchoLabs() {
        listOf(
            PageDataAgent,
            PageBrowserFlow,
            PageEchoAdviser,
            PageEchoFusion,
            PageEchoAgent,
            PageCustomProvider,
            PageLicenses,
        ).forEach { page ->
            assertEquals(PageEchoLabs, settingsParentPage(page))
        }
    }

    @Test
    fun customProviderPagesUseNestedParents() {
        assertEquals(PageEchoLabs, settingsParentPage(PageCustomProvider))
        assertEquals(PageHome, settingsParentPage(PageCustomProviderCloud))
        assertEquals(PageCustomProvider, settingsParentPage(PageCustomProviderOllama))
        assertEquals(PageCustomProvider, settingsParentPage(PageCustomProviderCompatible))
        assertEquals(PageCustomProviderCloud, settingsParentPage(PageCustomProviderOpenAi))
        assertEquals(PageCustomProviderCloud, settingsParentPage(PageCustomProviderClaude))
        assertEquals(PageCustomProviderCloud, settingsParentPage(PageCustomProviderSarvam))
    }
}
