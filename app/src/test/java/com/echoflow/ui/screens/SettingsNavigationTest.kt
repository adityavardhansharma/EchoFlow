package com.echoflow.ui.screens

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
            PageCloudModels,
            PageWebSearch,
            PageLocalModels,
            PageDeepResearch,
            PageImagine,
            PageEchoLabs,
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
        ).forEach { page ->
            assertEquals(PageEchoLabs, settingsParentPage(page))
        }
    }

    @Test
    fun customProviderPagesUseNestedParents() {
        assertEquals(PageEchoLabs, settingsParentPage(PageCustomProvider))
        assertEquals(PageCustomProvider, settingsParentPage(PageCustomProviderCloud))
        assertEquals(PageCustomProvider, settingsParentPage(PageCustomProviderOllama))
        assertEquals(PageCustomProvider, settingsParentPage(PageCustomProviderCompatible))
        assertEquals(PageCustomProviderCloud, settingsParentPage(PageCustomProviderOpenAi))
        assertEquals(PageCustomProviderCloud, settingsParentPage(PageCustomProviderClaude))
    }
}
