package com.echoflow.weave.runtime

import com.echoflow.BuildConfig
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.catalog.Catalog
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.core.prompt.PromptForm
import com.echoflow.weave.core.prompt.WeaveDocs
import java.util.concurrent.ConcurrentHashMap

/**
 * The Echo UI prompt section, cached by (tier, level, form, catalog, index) (05 §12). The catalog
 * is this build's phase filtered to registered renderers, so the prompt never offers a component
 * the app can't draw (05 §3.3).
 */
object EchoUiPromptCache {
    private val cache = ConcurrentHashMap<String, String>()

    /** The catalog a turn at [tier] is built from; `weave_docs` answers from the same one. */
    fun catalog(tier: Int): Catalog = BuiltinCatalog.restrictedTo(tier, BuildConfig.ECHO_UI_PHASE).registeredOnly(Registry.names)

    /** Whether a turn at [tier] gets the trimmed prompt and the `weave_docs` tool (06 §2.1). */
    fun usesDocsIndex(tier: Int, toolUse: Boolean): Boolean = WeaveDocs.usesIndex(tier, BuildConfig.ECHO_UI_PHASE, toolUse)

    fun section(tier: Int, level: IntelligentUiLevel, form: PromptForm = PromptForm.SOURCE, docsIndex: Boolean = false): String =
        cache.getOrPut("$tier:${level.storageKey}:$form:${BuildConfig.ECHO_UI_PHASE}:$docsIndex") {
            EchoUiPrompt.section(tier, level, form, catalog(tier), docsIndex)
        }
}
