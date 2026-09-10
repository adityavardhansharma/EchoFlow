package com.echoflow.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Sarvam REST accepts at most 30 seconds. Input is the recorder's 16 kHz mono PCM WAV. */
internal object SarvamDictation {
    fun request(baseUrl: String, apiKey: String, wav: ByteArray): Request = Request.Builder()
        .url("${baseUrl.trimEnd('/')}/speech-to-text")
        .header("api-subscription-key", apiKey.trim())
        .post(MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", SttCatalog.SARVAM_MODEL_ID)
            .addFormDataPart("mode", "transcribe")
            .addFormDataPart("language_code", "unknown")
            .addFormDataPart("file", "dictation.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build())
        .build()

    fun wavChunks(wav: ByteArray): List<ByteArray> {
        require(wav.size > 44 && (wav.size - 44) % 2 == 0) { "Invalid dictation recording" }
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        require(String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(wav, 8, 4, Charsets.US_ASCII) == "WAVE" &&
            String(wav, 36, 4, Charsets.US_ASCII) == "data" &&
            header.getShort(20).toInt() == 1 && header.getShort(22).toInt() == 1 &&
            header.getInt(24) == 16_000 && header.getShort(34).toInt() == 16
        ) { "Expected 16 kHz mono PCM WAV" }
        val maxBytes = 29 * 16_000 * 2
        return buildList {
            var offset = 44
            while (offset < wav.size) {
                val size = minOf(maxBytes, wav.size - offset)
                val chunk = wav.copyOfRange(0, 44) + wav.copyOfRange(offset, offset + size)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(4, size + 36)
                    putInt(40, size)
                }
                add(chunk)
                offset += size
            }
        }
    }
}
