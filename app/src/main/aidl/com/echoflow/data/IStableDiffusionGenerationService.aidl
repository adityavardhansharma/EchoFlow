package com.echoflow.data;

import com.echoflow.data.IStableDiffusionGenerationCallback;

/**
 * Small cross-process API around stable-diffusion.cpp. Image bytes are written to an
 * app-private temporary PNG instead of crossing Binder's transaction-size limit.
 */
interface IStableDiffusionGenerationService {
    void generate(
        String modelPath,
        String prompt,
        String negativePrompt,
        int width,
        int height,
        int steps,
        float cfgScale,
        long seed,
        IStableDiffusionGenerationCallback callback
    );

    void cancel();
    void release();
}
