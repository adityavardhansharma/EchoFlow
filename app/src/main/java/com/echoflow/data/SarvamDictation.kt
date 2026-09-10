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

    fun wavChunks(wav: ByteArray, overlapBytes: Int = 0): List<ByteArray> {
        require(wav.size > 44 && (wav.size - 44) % 2 == 0) { "Invalid dictation recording" }
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        require(String(wav, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(wav, 8, 4, Charsets.US_ASCII) == "WAVE" &&
            String(wav, 36, 4, Charsets.US_ASCII) == "data" &&
            header.getShort(20).toInt() == 1 && header.getShort(22).toInt() == 1 &&
            header.getInt(24) == 16_000 && header.getShort(34).toInt() == 16
        ) { "Expected 16 kHz mono PCM WAV" }
        val maxBytes = 29 * 16_000 * 2
        require(overlapBytes in 0..32_000 && overlapBytes % 2 == 0)

        return buildList {
            var offset = 44
            while (offset < wav.size) {
                val limit = minOf(offset + maxBytes, wav.size)
                // Prefer a quiet 200 ms window in the last four seconds of this request.
                val end = if (limit == wav.size) limit else quietBoundary(header, limit) ?: limit
                val size = end - offset
                val chunk = wav.copyOfRange(0, 44) + wav.copyOfRange(offset, offset + size)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(4, size + 36)
                    putInt(40, size)
                }
                add(chunk)
                offset = if (end == wav.size) end else end - overlapBytes
            }
        }
    }

    private fun quietBoundary(audio: ByteBuffer, limit: Int): Int? {
        val windowBytes = 6_400 // 200 ms of 16 kHz mono, 16-bit PCM
        var bestEnergy = 200.0 * 200.0
        var boundary: Int? = null
        for (start in (limit - 4 * 32_000)..(limit - windowBytes) step windowBytes) {
            var energy = 0.0
            for (sample in start until start + windowBytes step 2) {
                val value = audio.getShort(sample).toDouble()
                energy += value * value
            }
            energy /= windowBytes / 2
            if (energy <= bestEnergy) {
                bestEnergy = energy
                boundary = start + windowBytes / 2
            }
        }
        return boundary
    }

    /** Remove duplicated boundary words from the one-second audio overlap. */
    fun stitch(transcripts: List<String>): String = transcripts.fold("") { previous, next ->
        if (previous.isBlank()) return@fold next.trim()
        if (next.isBlank()) return@fold previous
        val left = Regex("\\S+").findAll(previous).toList()
        val right = Regex("\\S+").findAll(next).toList()
        fun normalized(word: String) = word.lowercase().filter { it.isLetterOrDigit() ||
            Character.getType(it) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(it) == Character.COMBINING_SPACING_MARK.toInt() }
        val overlap = (minOf(12, left.size, right.size) downTo 1).firstOrNull { count ->
            (0 until count).all { index ->
                val word = normalized(left[left.size - count + index].value)
                val incoming = normalized(right[index].value)
                word.isNotEmpty() && (word == incoming ||
                    (index == count - 1 && word.length >= 3 && incoming.startsWith(word)))
            }
        } ?: 0
        // Prefer the newer rendering: it has the full word if the earlier chunk cut it short.
        val prefix = if (overlap == 0) previous
            else previous.substring(0, left[left.size - overlap].range.first).trimEnd()
        if (prefix.isEmpty()) next.trim() else "$prefix ${next.trim()}"
    }
}
