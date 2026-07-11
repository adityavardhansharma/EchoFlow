package com.echoflow.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormattingCharacterizationTest {
    @Test
    fun localModelPickerRemovesLongRuntimeAndQuantizationSuffixes() {
        assertEquals(
            "Llama-3.2-3B-Instruct",
            modelPickerDisplayName("Llama-3.2-3B-Instruct-Q4_K_M.gguf", isLocal = true),
        )
    }

    @Test
    fun cloudAndShortLocalNamesRemainUntouched() {
        assertEquals("provider/model-Q4.gguf", modelPickerDisplayName("provider/model-Q4.gguf", false))
        assertEquals("tiny.task", modelPickerDisplayName("tiny.task", true))
    }

    @Test
    fun settingsSliderSnapsToConfiguredIntervals() {
        assertEquals(0.5f, snapToStep(0.49f, 0f..1f, steps = 3), 0.0001f)
        assertEquals(0f, snapToStep(0.1f, 0f..1f, steps = 3), 0.0001f)
        assertEquals(1f, snapToStep(0.9f, 0f..1f, steps = 3), 0.0001f)
    }

    @Test
    fun settingsFormattingContractsRemainStable() {
        assertEquals("128K ctx", formatContext(128_000))
        assertEquals("1M ctx", formatContext(1_000_000))
        assertEquals("Off", endpointSubtitle(false, "Connected"))
        assertEquals("Connected", endpointSubtitle(true, "Connected"))
    }
}
