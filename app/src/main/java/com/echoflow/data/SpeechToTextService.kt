package com.echoflow.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Records microphone audio into an in-memory 16 kHz mono PCM buffer and exposes a live amplitude
 * (0..1) so the composer can draw a real waveform while the user talks. On stop it returns a
 * complete WAV byte array ready to POST for transcription.
 *
 * This is a plain class, not a Compose or Android component — the composer's voice controller owns
 * one and drives start / stop / cancel. Callers must hold RECORD_AUDIO before [start].
 *
 * Capture is hard-capped at [MAX_SECONDS] so a forgotten recording cannot grow without bound.
 * [AudioRecord] teardown is owned by the recorder thread so [stop]/[cancel] never release a
 * handle another thread is still reading.
 */
class AudioWavRecorder(private val sampleRate: Int = 16_000) {
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile private var recording = false
    /** True from a successful [start] until [stop] or [cancel] has collected/discarded the capture. */
    @Volatile private var active = false
    private var recordThread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (active) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = if (minBuf > 0) minBuf * 2 else sampleRate
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize,
            )
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        pcm.reset()
        recording = true
        active = true
        rec.startRecording()
        val maxBytes = sampleRate * 2 * MAX_SECONDS
        recordThread = thread(name = "stt-record") {
            try {
                val buffer = ShortArray(bufSize / 2)
                while (recording) {
                    val n = rec.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        var sum = 0.0
                        for (i in 0 until n) {
                            val v = buffer[i].toDouble()
                            sum += v * v
                        }
                        val rms = sqrt(sum / n)
                        // ~8000 RMS is a loud normal voice; clamp so the waveform tops out cleanly.
                        _amplitude.value = min(1f, (rms / 8000.0).toFloat())
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            val s = buffer[i].toInt()
                            bytes[i * 2] = (s and 0xff).toByte()
                            bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                        }
                        pcm.write(bytes)
                        // Cap capture length so a forgotten recording cannot OOM the process.
                        if (pcm.size() >= maxBytes) {
                            recording = false
                            break
                        }
                    } else if (n < 0) {
                        break
                    }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
                _amplitude.value = 0f
            }
        }
        return true
    }

    /** Stops recording and returns a complete WAV, or null if nothing usable was captured. */
    fun stop(): ByteArray? {
        if (!active) return null
        active = false
        recording = false
        // Join without a timeout so the recorder thread can finish its last read and own teardown.
        recordThread?.join()
        recordThread = null
        val data = pcm.toByteArray()
        pcm.reset()
        if (data.size < sampleRate) return null // under ~0.5s of audio — treat as a mis-tap
        return wavHeader(data.size) + data
    }

    fun cancel() {
        active = false
        recording = false
        recordThread?.join()
        recordThread = null
        pcm.reset()
        _amplitude.value = 0f
    }

    private fun wavHeader(dataLen: Int): ByteArray {
        val h = ByteArray(44)
        fun putStr(off: Int, s: String) { for (i in s.indices) h[off + i] = s[i].code.toByte() }
        fun putIntLE(off: Int, v: Int) {
            h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte()
            h[off + 2] = ((v shr 16) and 0xff).toByte(); h[off + 3] = ((v shr 24) and 0xff).toByte()
        }
        fun putShortLE(off: Int, v: Int) { h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte() }
        putStr(0, "RIFF"); putIntLE(4, dataLen + 36); putStr(8, "WAVE")
        putStr(12, "fmt "); putIntLE(16, 16); putShortLE(20, 1); putShortLE(22, 1)
        putIntLE(24, sampleRate); putIntLE(28, sampleRate * 2); putShortLE(32, 2); putShortLE(34, 16)
        putStr(36, "data"); putIntLE(40, dataLen)
        return h
    }

    companion object {
        /** Hard cap on a single dictation capture (16 kHz mono PCM ≈ 32 KB/s → ~3.8 MB). */
        const val MAX_SECONDS = 120
    }
}

/**
 * Sends recorded audio to OpenRouter for transcription. STT is always billed to the OpenRouter
 * (Cloud models) key, regardless of the chat model.
 *
 * Wire shape matches OpenRouter's STT docs: JSON body with base64 `input_audio` posted to
 * `/api/v1/audio/transcriptions`. Response is `{ "text": ... }` (with a chat-completions content
 * fallback parse for defensive compatibility).
 */
class SpeechToTextTranscriber {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Moshi.Builder().add(KotlinJsonAdapterFactory()).build().adapter(Any::class.java)

    suspend fun transcribe(apiKey: String, modelId: String, wav: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalStateException("No OpenRouter key"))
            }
            val payload = mapOf(
                "model" to modelId,
                "input_audio" to mapOf(
                    "data" to Base64.encodeToString(wav, Base64.NO_WRAP),
                    "format" to "wav",
                ),
            )
            val body = json.toJson(payload).toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/audio/transcriptions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://echoflow.app")
                .header("X-Title", "EchoFlow")
                .post(body)
                .build()
            runCatching {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error(ProviderHttpSupport.errorMessage("Speech to text", resp.code, text))
                    }
                    parseTranscript(text) ?: error("Couldn't hear that — try again.")
                }
            }
        }

    private fun parseTranscript(body: String): String? {
        val map = runCatching { json.fromJson(body) as? Map<*, *> }.getOrNull() ?: return null
        (map["text"] as? String)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val choice = (map["choices"] as? List<*>)?.firstOrNull() as? Map<*, *>
        val content = (choice?.get("message") as? Map<*, *>)?.get("content") as? String
        return content?.trim()?.takeIf { it.isNotBlank() }
    }
}
