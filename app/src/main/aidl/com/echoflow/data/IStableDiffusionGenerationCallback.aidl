package com.echoflow.data;

/** Result channel for the isolated stable-diffusion.cpp process. */
oneway interface IStableDiffusionGenerationCallback {
    void onSuccess(String pngPath);
    void onError(String message);
}
