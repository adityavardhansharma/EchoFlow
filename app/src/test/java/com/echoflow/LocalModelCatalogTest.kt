package com.echoflow

import com.echoflow.data.LocalModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    private val newIds = listOf(
        "local/lfm-2.5-1.2b-instruct",
        "local/lfm-2.5-2.6b",
        "local/gemma-4-e2b-it",
        "local/gemma-4-e4b-it",
    )

    private val previousIds = listOf(
        "local/gemma3-1b-it",
        "local/qwen3-0.6b",
        "local/qwen2.5-1.5b-instruct",
        "local/deepseek-r1-distill-1.5b",
        "local/gemma-4-e2b-it",
        "local/qwen3-4b-instruct-2507",
        "local/gemma-4-e4b-it",
    )

    @Test
    fun newInstallsGetGemma4AndTheNewerOnDeviceModels() {
        assertEquals(newIds, LocalModelCatalog.recommendationsFor(emptySet()).map { it.id })
        assertTrue(LocalModelCatalog.recommendationsFor(emptyList()).none { it.name.contains("Qwen") || it.name.contains("DeepSeek") })
    }

    @Test
    fun aKeptDownloadLeavesThePreviousListAlone() {
        assertEquals(
            previousIds,
            LocalModelCatalog.recommendationsFor(setOf("local/gemma-4-e2b-it")).map { it.id },
        )
        assertEquals(
            previousIds,
            LocalModelCatalog.recommendationsFor(listOf("local/gemma-4-e4b-it", "local/imported-other")).map { it.id },
        )
    }

    @Test
    fun aRemovedDownloadSwitchesTheListAndKeepsThatModel() {
        val ids = LocalModelCatalog.recommendationsFor(setOf("local/deepseek-r1-distill-1.5b")).map { it.id }
        assertEquals(newIds + "local/deepseek-r1-distill-1.5b", ids)
    }

    @Test
    fun everyRemovedDownloadStaysWhenTheListChanges() {
        val ids = LocalModelCatalog.recommendationsFor(
            setOf("local/qwen3-0.6b", "local/gemma3-1b-it", "local/gemma-4-e2b-it"),
        ).map { it.id }
        assertEquals(
            newIds + listOf("local/gemma3-1b-it", "local/qwen3-0.6b"),
            ids,
        )
        assertFalse(ids.contains("local/qwen2.5-1.5b-instruct"))
    }

    @Test
    fun anUnrelatedInstallDoesNotFreezeTheOldList() {
        assertEquals(newIds, LocalModelCatalog.recommendationsFor(setOf("local/imported-custom")).map { it.id })
    }

    @Test
    fun previewKeepsADownloadedModelThatWouldOtherwiseBeHidden() {
        val recommendations = LocalModelCatalog.recommendationsFor(setOf("local/gemma-4-e4b-it"))
        val preview = LocalModelCatalog.preview(recommendations, setOf("local/gemma-4-e4b-it"), limit = 3)
        assertEquals(recommendations.take(3).map { it.id } + "local/gemma-4-e4b-it", preview.map { it.id })
    }

    @Test
    fun retiredModelsStillResolveForPeopleWhoAlreadyHaveThem() {
        assertEquals(4096, LocalModelCatalog.entryById("local/deepseek-r1-distill-1.5b")?.maxTokens)
        assertEquals(2048, LocalModelCatalog.maxTokensFor("local/qwen3-0.6b"))
        assertEquals(4096, LocalModelCatalog.maxTokensFor("local/lfm-2.5-2.6b"))
    }

    @Test
    fun gemma4LiteRtModelsUseEightKMobileContextCap() {
        assertEquals(
            8192,
            LocalModelCatalog.maxTokensFor(
                "local/gemma-4-e2b-it",
                "gemma-4-E2B-it.litertlm"
            )
        )
        assertEquals(
            8192,
            LocalModelCatalog.maxTokensFor(
                "local/recovered-gemma-4-e2b",
                "gemma-4-E2B-it.litertlm"
            )
        )
    }

    @Test
    fun contextHintsCanExposeLargerImportedModelWindows() {
        assertEquals(
            8192,
            LocalModelCatalog.maxTokensFor(
                "local/imported",
                "Some-Model-ctx32k.litertlm"
            )
        )
        assertEquals(
            8192,
            LocalModelCatalog.maxTokensFor(
                "local/imported",
                "Some-Model-ekv8192.task"
            )
        )
    }
}
