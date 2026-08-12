package com.echoflow.data

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 */
class AudioWavRecorder(private val sampleRate: Int = 16_000) {
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    @Volatile private var recording = false
    private var recordThread: Thread? = null
    private var record: AudioRecord? = null
    private val pcm = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
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
        record = rec
        pcm.reset()
        recording = true
        rec.startRecording()
        recordThread = thread(name = "stt-record") {
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
                }
            }
        }
        return true
    }

    /** Stops recording and returns a complete WAV, or null if nothing usable was captured. */
    fun stop(): ByteArray? {
        if (!recording) return null
        recording = false
        recordThread?.join(500)
        recordThread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        _amplitude.value = 0f
        val data = pcm.toByteArray()
        if (data.size < sampleRate) return null // under ~0.5s of audio — treat as a mis-tap
        return wavHeader(data.size) + data
    }

    fun cancel() {
        recording = false
        recordThread?.join(500)
        recordThread = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
        pcm.reset()
        _amplitude.value = 0f
    }

    private fun wavHeader(dataLen: Int): ByteArray {
        val byteRate = sampleRate * 2
        val h = ByteArray(44)
        fun putStr(off: Int, s: String) { for (i in s.indices) h[off + i] = s[i].code.toByte() }
        fun putIntLE(off: Int, v: Int) {
            h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte()
            h[off + 2] = ((v shr 16) and 0xff).toByte(); h[off + 3] = ((v shr 24) and 0xff).toByte()
        }
        fun putShortLE(off: Int, v: Int) { h[off] = (v and 0xff).toByte(); h[off + 1] = ((v shr 8) and 0xff).toByte() }
        putStr(0, "RIFF"); putIntLE(4, dataLen + 36); putStr(8, "WAVE")
        putStr(12, "fmt "); putIntLE(16, 16); putShortLE(20, 1); putShortLE(22, 1)
        putIntLE(24, sampleRate); putIntLE(28, byteRate); putShortLE(32, 2); putShortLE(34, 16)
        putStr(36, "data"); putIntLE(40, dataLen)
        return h
    }
}

/**
 * Sends recorded audio to OpenRouter for transcription. STT is always billed to the OpenRouter
 * (Cloud models) key, regardless of the chat model.
 *
 * NOTE: the exact OpenRouter STT wire shape has not been verified against a live key yet (the
 * three catalog models are curated, not searched). This uses the conventional OpenAI-style
 * multipart `/audio/transcriptions` endpoint and parses `{ "text": ... }`, falling back to a
 * chat-completions `choices[0].message.content` shape — verify in Android Studio against a real
 * key and adjust the endpoint/parse if the provider differs.
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
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("model", modelId)
                .addFormDataPart("response_format", "json")
                .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
                .build()
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
