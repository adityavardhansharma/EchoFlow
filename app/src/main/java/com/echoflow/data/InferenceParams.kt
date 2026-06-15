package com.echoflow.data

/**
 * The sampler knobs the user can tune globally. One set applies to every on-device model,
 * a second set to every OpenRouter (cloud) model — they don't share values because their
 * sane ranges differ (on-device top-k is capped low; cloud top-k is usually disabled).
 *
 * Values here are the *user's chosen* numbers. They are run through [InferenceLimits.coerce]
 * against the active model's [ModelCapabilities] before they reach an engine, so a value
 * that's too big for the model currently loaded is quietly brought back to a safe default
 * (see the class doc on [InferenceLimits.coerce]).
 *
 * @param temperature softmax temperature. 0 = greedy/deterministic.
 * @param topK keep only the K most-likely tokens. 0 = disabled (no top-k cut).
 * @param topP nucleus sampling mass in (0, 1]. 1.0 = disabled.
 * @param maxTokens response/context budget. 0 = "use the model's own default":
 *   for on-device models that means the file's exported kv-cache size; for cloud models it
 *   means "don't send max_tokens" (let the provider decide).
 */
data class InferenceParams(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val maxTokens: Int,
)

/**
 * What the currently-selected model can actually accept. Used to clamp [InferenceParams]
 * so the user can keep one global preference while every model still gets legal values.
 *
 * @param maxContextTokens the largest token budget this model supports (0 = unknown, so
 *   don't clamp on it — e.g. a cloud model whose context length we couldn't read).
 * @param maxTopK the largest legal top-k for this runtime.
 */
data class ModelCapabilities(
    val maxContextTokens: Int,
    val maxTopK: Int,
)

/**
 * Defaults we ship, the UI slider ranges, and the clamping rule. Kept in one place so the
 * repository, the engines, and the Settings sliders agree on every number.
 */
object InferenceLimits {

    // Defaults shipped for on-device models (mirror the values the engines used to hardcode:
    // Gemma/Qwen LiteRT bundles are tuned for temperature 1.0, topK 64, topP 0.95).
    val LOCAL_DEFAULTS = InferenceParams(temperature = 1.0f, topK = 64, topP = 0.95f, maxTokens = 0)

    // Defaults shipped for OpenRouter cloud models (conventional chat defaults; top-k off,
    // top-p off, no explicit max so the provider streams a full answer).
    val CLOUD_DEFAULTS = InferenceParams(temperature = 0.7f, topK = 0, topP = 1.0f, maxTokens = 0)

    // Slider bounds. Temperature/top-p are universal; top-k and max-tokens differ per side.
    const val TEMP_MIN = 0.0f
    const val TEMP_MAX = 2.0f
    const val TOP_P_MIN = 0.0f
    const val TOP_P_MAX = 1.0f

    const val LOCAL_TOP_K_MAX = 128
    const val CLOUD_TOP_K_MAX = 200

    // Max-tokens sliders run from 0 ("model default") up to these ceilings, stepped.
    // LiteRT-LM Gemma 4 mobile bundles support up to 32K context, so expose that full
    // range while still clamping each selected model to its own catalog/filename limit.
    const val LOCAL_MAX_TOKENS_CEIL = 32768
    const val CLOUD_MAX_TOKENS_CEIL = 32768
    const val MAX_TOKENS_STEP = 256

    /**
     * Brings [params] into what [caps] allows, falling back to [defaults] when a value is
     * out of its legal range. This is where the "reset to defaults on a smaller model"
     * behaviour lives:
     *
     * - **temperature / top-p**: clamped to their universal ranges; a NaN/garbage value
     *   falls back to the default.
     * - **top-k**: if above the runtime's [ModelCapabilities.maxTopK] (or negative) it
     *   falls back to the default; 0 (disabled) is always allowed.
     * - **max-tokens**: 0 means "model default", which resolves to the model's own
     *   [ModelCapabilities.maxContextTokens]. A non-zero value larger than the model can
     *   hold is reset to that default — so a budget set for a big-context model is honored
     *   when you go back to it, but a smaller model you switch to gets the safe default
     *   instead of an illegal value. A non-zero value that *fits* is kept as-is.
     *
     * The result is always safe to hand straight to an engine.
     */
    fun coerce(params: InferenceParams, caps: ModelCapabilities, defaults: InferenceParams): InferenceParams {
        val temperature = params.temperature
            .takeIf { it.isFinite() && it in TEMP_MIN..TEMP_MAX }
            ?: defaults.temperature

        val topP = params.topP
            .takeIf { it.isFinite() && it > 0f && it <= TOP_P_MAX }
            ?: defaults.topP

        val topK = when {
            params.topK == 0 -> 0
            params.topK < 0 || params.topK > caps.maxTopK -> defaults.topK.coerceAtMost(caps.maxTopK)
            else -> params.topK
        }

        val modelDefaultTokens = if (caps.maxContextTokens > 0) caps.maxContextTokens else params.maxTokens
        val maxTokens = when {
            params.maxTokens <= 0 -> modelDefaultTokens
            caps.maxContextTokens > 0 && params.maxTokens > caps.maxContextTokens -> modelDefaultTokens
            else -> params.maxTokens
        }

        return InferenceParams(temperature = temperature, topK = topK, topP = topP, maxTokens = maxTokens)
    }
}
