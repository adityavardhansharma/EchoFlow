package com.echoflow.weave.core

import com.echoflow.weave.core.prompt.EchoUiPrompt

/** Programs for round-trip and streaming tests (07 §8): spec examples plus files under resources/corpus. */
object Corpus {
    fun programs(): List<String> {
        val res = javaClass.classLoader.getResource("corpus")?.let { java.io.File(it.toURI()) }
        val files = res?.listFiles { f -> f.name.endsWith(".weave") }?.sortedBy { it.name }?.map { it.readText() } ?: emptyList()
        return listOf(EchoUiPrompt.EXAMPLE_1, EchoUiPrompt.EXAMPLE_2) + files
    }
}
