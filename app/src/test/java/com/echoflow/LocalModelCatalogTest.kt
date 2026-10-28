package com.echoflow

import com.echoflow.data.LiteRtLoadPlan
import com.echoflow.data.LocalModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    private val newIds = listOf(
        "local/echoswift-700m",
        "local/echoswift-700m-8k",
        "local/gemma-4-e2b-it",
        "local/gemma-4-e4b-it",
        "local/qwen3.5-4b",
    )

    @Test
    fun newInstallsGetGemma4AndTheNewerOnDeviceModels() {
        assertEquals(newIds, LocalModelCatalog.recommendationsFor(emptySet()).map { it.id })
        val names = LocalModelCatalog.recommendationsFor(emptyList()).map { it.name }
        assertTrue(names.containsAll(listOf("EchoSwift 4K", "EchoSwift 8K", "Qwen 3.5 4B")))
        assertTrue(names.none { it.startsWith("LFM") })
        assertTrue(names.none { it.contains("DeepSeek") || it.startsWith("Qwen3") || it.startsWith("Qwen 2.5") })
    }

    @Test
    fun aGemma4DownloadStillShowsTheNewList() {
        assertEquals(newIds, LocalModelCatalog.recommendationsFor(setOf("local/gemma-4-e2b-it")).map { it.id })
        assertEquals(
            newIds,
            LocalModelCatalog.recommendationsFor(listOf("local/gemma-4-e4b-it", "local/imported-other")).map { it.id },
        )
    }

    @Test
    fun aRemovedDownloadStaysOnTheNewList() {
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
    fun aDownloadedLfmModelKeepsItsTick() {
        val ids = LocalModelCatalog.recommendationsFor(setOf("local/lfm-2.5-2.6b")).map { it.id }
        assertEquals(newIds + "local/lfm-2.5-2.6b", ids)
    }

    @Test
    fun anUnrelatedInstallGetsTheNewList() {
        assertEquals(newIds, LocalModelCatalog.recommendationsFor(setOf("local/imported-custom")).map { it.id })
    }

    @Test
    fun previewKeepsADownloadedModelThatWouldOtherwiseBeHidden() {
        val recommendations = LocalModelCatalog.recommendationsFor(setOf("local/gemma-4-e4b-it"))
        val preview = LocalModelCatalog.preview(recommendations, setOf("local/gemma-4-e4b-it"), limit = 3)
        assertEquals(recommendations.take(3).map { it.id } + "local/gemma-4-e4b-it", preview.map { it.id })
    }

    @Test
    fun gemma4E4bAndDeepSeekKeepTheirTicksAndStillHideQwen() {
        val installed = setOf("local/gemma-4-e4b-it", "local/deepseek-r1-distill-1.5b")
        val recommendations = LocalModelCatalog.recommendationsFor(installed)
        val preview = LocalModelCatalog.preview(recommendations, installed, limit = 3)
        assertEquals(
            newIds.take(3) + listOf("local/gemma-4-e4b-it", "local/deepseek-r1-distill-1.5b"),
            preview.map { it.id },
        )
        assertTrue(LocalModelCatalog.previewOmitsRows(recommendations, installed, limit = 3))
    }

    @Test
    fun showAllIsHiddenWhenInstalledModelsAlreadyFillTheList() {
        val installed = setOf("local/gemma-4-e4b-it", "local/qwen3.5-4b", "local/deepseek-r1-distill-1.5b")
        val recommendations = LocalModelCatalog.recommendationsFor(installed)
        val preview = LocalModelCatalog.preview(recommendations, installed, limit = 3)
        assertEquals(recommendations.map { it.id }, preview.map { it.id })
        assertFalse(LocalModelCatalog.previewOmitsRows(recommendations, installed, limit = 3))
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

    @Test
    fun echoSwiftContextMatchesEachFilesKvCache() {
        assertEquals(4096, LocalModelCatalog.maxTokensFor("local/echoswift-700m"))
        assertEquals(8192, LocalModelCatalog.maxTokensFor("local/echoswift-700m-8k"))
        assertEquals(4096, LocalModelCatalog.maxTokensFor("local/recovered", "EchoSwift-700M_int8.litertlm"))
        assertEquals(8192, LocalModelCatalog.maxTokensFor("local/recovered", "EchoSwift-700M_int8_8k_ekv8192.litertlm"))
    }

    @Test
    fun liteRtPlanSkipsVisionAndUnverifiedGpu() {
        assertEquals(LiteRtLoadPlan(vision = true, gpu = true), LocalModelCatalog.liteRtPlanFor("local/gemma-4-e2b-it", ""))
        assertEquals(LiteRtLoadPlan(vision = false, gpu = true), LocalModelCatalog.liteRtPlanFor("local/lfm-2.5-2.6b", ""))
        assertEquals(LiteRtLoadPlan(vision = false, gpu = false), LocalModelCatalog.liteRtPlanFor("local/lfm-2.5-1.2b-instruct", ""))
        assertEquals(LiteRtLoadPlan(vision = false, gpu = false), LocalModelCatalog.liteRtPlanFor("local/recovered", "Qwen3.5-4B_mixed_int4.litertlm"))
        assertEquals(LiteRtLoadPlan(vision = false, gpu = false), LocalModelCatalog.liteRtPlanFor("local/echoswift-700m", ""))
        assertEquals(LiteRtLoadPlan(vision = false, gpu = false), LocalModelCatalog.liteRtPlanFor("local/echoswift-700m-8k", ""))
        assertEquals(null, LocalModelCatalog.liteRtPlanFor("local/imported", "Some-Model.litertlm"))
        assertEquals(null, LocalModelCatalog.liteRtPlanFor("local/gemma3-1b-it", ""))
    }
}
