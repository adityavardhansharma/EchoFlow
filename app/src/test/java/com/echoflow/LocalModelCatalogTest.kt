package com.echoflow

import com.echoflow.data.LocalModelCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun gemma4LiteRtModelsUseFullThirtyTwoKContext() {
        assertEquals(
            32768,
            LocalModelCatalog.maxTokensFor(
                "local/gemma-4-e2b-it",
                "gemma-4-E2B-it.litertlm"
            )
        )
        assertEquals(
            32768,
            LocalModelCatalog.maxTokensFor(
                "local/recovered-gemma-4-e2b",
                "gemma-4-E2B-it.litertlm"
            )
        )
    }

    @Test
    fun contextHintsCanExposeLargerImportedModelWindows() {
        assertEquals(
            32768,
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
