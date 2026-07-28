@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.data.CustomProviderConfig
import com.echoflow.ui.theme.RoundedPolygonShape

/**
 * Who runs a model, expressed as a shape and a tonal colour pair.
 *
 * In a multi-provider app, provenance *is* information — it is the privacy story, the cost
 * story and the latency story at once — so every model needs to be identifiable at a glance.
 * Real vendor logos are a trademark minefield, but the app already owns a better answer:
 * [MaterialShapes]. A stable shape per provider is legally clean, instantly scannable, and is
 * literally the design language the rest of the app is built from.
 *
 * Colour comes from **theme roles** rather than brand hex values, so identity survives dynamic
 * colour and all six accents intact. Shape carries most of the distinction; the colour role
 * adds a second axis without ever fighting the user's palette.
 */
enum class ProviderIdentity(
    val label: String,
    val shape: RoundedPolygon,
    private val tone: Tone,
) {
    OpenAi("OpenAI", MaterialShapes.Cookie6Sided, Tone.Secondary),
    Anthropic("Anthropic", MaterialShapes.Sunny, Tone.Primary),
    Google("Google", MaterialShapes.Clover4Leaf, Tone.Tertiary),
    Meta("Meta", MaterialShapes.Oval, Tone.Secondary),
    Mistral("Mistral", MaterialShapes.Pentagon, Tone.Tertiary),
    DeepSeek("DeepSeek", MaterialShapes.Gem, Tone.Primary),
    Qwen("Qwen", MaterialShapes.Puffy, Tone.Secondary),
    XAi("xAI", MaterialShapes.Diamond, Tone.Primary),
    Kling("Kling", MaterialShapes.Flower, Tone.Tertiary),
    ByteDance("ByteDance", MaterialShapes.Burst, Tone.Secondary),
    OnDevice("On device", MaterialShapes.Cookie9Sided, Tone.Tertiary),
    Other("Cloud", MaterialShapes.Circle, Tone.Secondary);

    private enum class Tone { Primary, Secondary, Tertiary }

    val container: Color
        @Composable get() = when (tone) {
            Tone.Primary -> MaterialTheme.colorScheme.primaryContainer
            Tone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
            Tone.Tertiary -> MaterialTheme.colorScheme.tertiaryContainer
        }

    val onContainer: Color
        @Composable get() = when (tone) {
            Tone.Primary -> MaterialTheme.colorScheme.onPrimaryContainer
            Tone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
            Tone.Tertiary -> MaterialTheme.colorScheme.onTertiaryContainer
        }

    companion object {
        /**
         * Reads the provider out of a model id. Handles both OpenRouter's `vendor/model` form
         * and the app's own `openai:`-style prefixes for direct-API models; falls back to
         * matching the vendor name anywhere in the id, which covers ids that route a vendor's
         * model through someone else's infrastructure.
         */
        fun of(modelId: String): ProviderIdentity {
            val id = modelId.lowercase()
            return when {
                id.startsWith("local/") -> OnDevice
                id.startsWith(CustomProviderConfig.PREFIX_OPENAI) || id.contains("openai") || id.contains("gpt-") -> OpenAi
                id.startsWith(CustomProviderConfig.PREFIX_CLAUDE) || id.contains("anthropic") || id.contains("claude") -> Anthropic
                id.startsWith(CustomProviderConfig.PREFIX_GEMINI) || id.contains("google") || id.contains("gemini") || id.contains("gemma") || id.contains("veo") -> Google
                id.startsWith(CustomProviderConfig.PREFIX_XAI) || id.contains("x-ai") || id.contains("grok") -> XAi
                id.contains("meta-llama") || id.contains("llama") -> Meta
                id.contains("mistral") || id.contains("magistral") -> Mistral
                id.contains("deepseek") -> DeepSeek
                id.contains("qwen") || id.contains("alibaba") || id.contains("wan-") -> Qwen
                id.contains("kwaivgi") || id.contains("kling") -> Kling
                id.contains("bytedance") || id.contains("seedance") -> ByteDance
                else -> Other
            }
        }
    }
}

/**
 * The provider monogram. Used in the composer pill, picker rows and Imagine cards from one
 * definition, so the same model wears the same mark everywhere it appears.
 */
@Composable
fun ProviderMark(
    modelId: String,
    size: Dp = 24.dp,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val identity = remember(modelId) { ProviderIdentity.of(modelId) }
    val container = if (selected) MaterialTheme.colorScheme.primary else identity.container
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else identity.onContainer
    Box(
        modifier
            .size(size)
            .clip(RoundedPolygonShape(identity.shape))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = identity.glyph,
            contentDescription = null,
            modifier = Modifier.size(size * 0.46f),
            tint = content,
        )
    }
}

/**
 * A tiny glyph inside the shape. Deliberately generic — the *shape* is what identifies the
 * provider; the glyph only says what kind of thing it is, so nothing here can be mistaken for
 * a vendor's actual logo.
 */
private val ProviderIdentity.glyph: ImageVector
    get() = when (this) {
        ProviderIdentity.OnDevice -> Icons.Default.PhoneAndroid
        ProviderIdentity.Kling, ProviderIdentity.ByteDance -> Icons.Default.Movie
        else -> Icons.Default.Cloud
    }

/** The mark for a generative surface that has no single provider yet (empty Imagine states). */
@Composable
fun ImagineMark(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedPolygonShape(MaterialShapes.Flower))
            .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.AutoFixHigh, null,
            Modifier.size(size * 0.46f),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
