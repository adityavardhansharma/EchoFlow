package com.echoflow.weave.runtime

import com.echoflow.BuildConfig
import com.echoflow.weave.compose.render.Registry
import com.echoflow.weave.core.catalog.BuiltinCatalog
import com.echoflow.weave.core.prompt.EchoUiPrompt
import com.echoflow.weave.core.prompt.IntelligentUiLevel
import com.echoflow.weave.core.prompt.PromptForm
import java.util.concurrent.ConcurrentHashMap

/**
 * The Echo UI prompt section, cached by (tier, level, form, catalog) (05 §12). The catalog is
 * this build's phase filtered to registered renderers, so the prompt never offers a component the
 * app can't draw (05 §3.3).
 */
object EchoUiPromptCache {
    private val cache = ConcurrentHashMap<String, String>()

    fun section(tier: Int, level: IntelligentUiLevel, form: PromptForm = PromptForm.SOURCE): String =
        cache.getOrPut("$tier:${level.storageKey}:$form:${BuildConfig.ECHO_UI_PHASE}") {
            EchoUiPrompt.section(tier, level, form, BuiltinCatalog.restrictedTo(tier, BuildConfig.ECHO_UI_PHASE).registeredOnly(Registry.names))
        }
}
