@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.echoflow.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.echoflow.R
import com.echoflow.data.CustomProviderConfig
import com.echoflow.ui.theme.RoundedPolygonShape

/**
 * Who runs a model: their logo, set in a shaped medallion with a tonal colour pair.
 *
 * In a multi-provider app, provenance *is* information — it is the privacy story, the cost
 * story and the latency story at once — so every model needs to be identifiable at a glance,
 * and nothing is as fast to read as the logo someone already knows.
 *
 * The medallion is what keeps a wall of other people's trademarks from looking like a sponsor
 * page. Every mark is drawn monochrome and **tinted by a theme role**, never a brand hex, so a
 * list of twenty models stays one palette — the user's palette, surviving dynamic colour and
 * all six accents — instead of twenty competing corporate ones. The [MaterialShapes] container
 * adds a second axis of distinction and ties the whole set back to the app's own language.
 *
 * A provider with no logo on file falls back to its shape plus a generic glyph, which is why
 * the set never looks half-finished. Adding one is a drawable and a constructor argument:
 * drop a monochrome `logo_<name>.xml` into `res/drawable` and name it here.
 */
enum class ProviderIdentity(
    val label: String,
    val shape: RoundedPolygon,
    private val tone: Tone,
    @DrawableRes val logo: Int? = null,
) {
    OpenAi("OpenAI", MaterialShapes.Cookie6Sided, Tone.Secondary, R.drawable.logo_openai),
    Anthropic("Anthropic", MaterialShapes.Sunny, Tone.Primary, R.drawable.logo_claude),
    Google("Google", MaterialShapes.Clover4Leaf, Tone.Tertiary, R.drawable.logo_gemini),
    Meta("Meta", MaterialShapes.Oval, Tone.Secondary),
    Mistral("Mistral", MaterialShapes.Pentagon, Tone.Tertiary),
    DeepSeek("DeepSeek", MaterialShapes.Gem, Tone.Primary),
    Qwen("Qwen", MaterialShapes.Puffy, Tone.Secondary),
    XAi("xAI", MaterialShapes.Diamond, Tone.Primary, R.drawable.logo_xai),
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
        val logo = identity.logo
        if (logo != null) {
            // Logos carry detail a UI glyph does not, so they get more of the medallion —
            // below roughly this size OpenAI's knot and Anthropic's burst turn to mush.
            Icon(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.size(size * 0.58f),
                tint = content,
            )
        } else {
            Icon(
                imageVector = identity.glyph,
                contentDescription = null,
                modifier = Modifier.size(size * 0.46f),
                tint = content,
            )
        }
    }
}

/**
 * The stand-in for a provider with no logo on file. Deliberately generic: it says what kind of
 * thing the model is and leaves the *shape* to say who made it, so a missing logo reads as a
 * quieter member of the set rather than a hole in it.
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
