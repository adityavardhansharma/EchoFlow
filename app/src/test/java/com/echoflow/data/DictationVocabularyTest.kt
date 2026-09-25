package com.echoflow.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationVocabularyTest {

    @Test fun `comma, semicolon, and newline separated input becomes clean terms`() {
        assertEquals(
            listOf("Aditya", "Jyoti", "Jetpack Compose", "EchoFlow"),
            DictationVocabulary.split("Aditya, Jyoti ;  Jetpack   Compose\n,EchoFlow,"),
        )
    }

    @Test fun `merge keeps the first spelling and drops case-insensitive duplicates`() {
        assertEquals(
            listOf("Aditya", "Jyoti", "EchoFlow"),
            DictationVocabulary.merge(listOf("Aditya", "Jyoti"), listOf("aditya", "EchoFlow", "JYOTI")),
        )
    }

    @Test fun `list is capped at the maximum term count`() {
        val many = (1..DictationVocabulary.MAX_TERMS + 20).map { "term$it" }
        assertEquals(DictationVocabulary.MAX_TERMS, DictationVocabulary.normalize(many).size)
    }

    @Test fun `stored form round-trips`() {
        val terms = listOf("Aditya", "Jetpack Compose", "EchoFlow")
        assertEquals(terms, DictationVocabulary.decode(DictationVocabulary.encode(terms)))
        assertEquals(emptyList<String>(), DictationVocabulary.decode(null))
    }

    @Test fun `only Gemini Transcribe accepts a vocabulary`() {
        assertEquals(
            listOf(SttCatalog.GEMINI_TRANSCRIBE_MODEL_ID),
            (SttCatalog.CLOUD_MODELS + SttCatalog.SARVAM_MODEL).filter { it.supportsCustomVocabulary }.map { it.id },
        )
    }
}
