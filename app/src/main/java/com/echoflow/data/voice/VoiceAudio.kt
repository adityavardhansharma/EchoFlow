package com.echoflow.data.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlin.math.sqrt

/** Loudness of 16-bit PCM as 0..1 on a speech-friendly log scale (−50 dBFS → 0, 0 dBFS → 1). */
internal object PcmLevel {
    fun of(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size): Float {
        val samples = length / 2
        if (samples == 0) return 0f
        var sum = 0.0
        var i = offset
        val end = offset + samples * 2
        while (i < end) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toDouble()
            sum += s * s
            i += 2
        }
        val rms = sqrt(sum / samples) / 32768.0
        if (rms <= 0.0) return 0f
        val db = 20 * log10(rms)
        return ((db + 50.0) / 50.0).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * The microphone for a live voice session: 16-bit mono PCM in ~100 ms chunks, from the
 * voice-communication source so the platform's echo canceller keeps the agent's own voice out of
 * what the agent hears. While [muted], audio is still read (so the stream stays warm and unmuting
 * is instant) but nothing is handed on.
 */
internal class VoiceMicrophone(
    private val sampleRate: Int,
    private val onChunk: (pcm: ByteArray) -> Unit,
    private val onLevel: (Float) -> Unit,
) {
    @Volatile var muted: Boolean = false
    @Volatile private var running = false
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null

    /** Caller has already checked RECORD_AUDIO. Returns false when the mic can't be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val chunkBytes = sampleRate / 10 * 2
        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, chunkBytes * 4),
            )
        }.getOrNull() ?: return false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }
        val session = recorder.audioSessionId
        if (AcousticEchoCanceler.isAvailable()) echo = runCatching { AcousticEchoCanceler.create(session)?.apply { enabled = true } }.getOrNull()
        if (NoiseSuppressor.isAvailable()) noise = runCatching { NoiseSuppressor.create(session)?.apply { enabled = true } }.getOrNull()
        runCatching { recorder.startRecording() }.onFailure {
            recorder.release()
            return false
        }
        record = recorder
        running = true
        thread = Thread({
            val buffer = ByteArray(chunkBytes)
            while (running) {
                var filled = 0
                while (running && filled < chunkBytes) {
                    val read = recorder.read(buffer, filled, chunkBytes - filled)
                    if (read <= 0) break
                    filled += read
                }
                if (!running || filled <= 0) {
                    if (running) Thread.sleep(10)
                    continue
                }
                if (muted) {
                    onLevel(0f)
                    continue
                }
                onLevel(PcmLevel.of(buffer, 0, filled))
                onChunk(buffer.copyOf(filled))
            }
        }, "voice-mode-mic").apply { start() }
        return true
    }

    fun stop() {
        running = false
        runCatching { record?.stop() }
        thread?.join(500)
        thread = null
        runCatching { record?.release() }
        record = null
        runCatching { echo?.release() }
        runCatching { noise?.release() }
        echo = null
        noise = null
        onLevel(0f)
    }
}

/**
 * Plays the agent's voice as it streams in. Chunks are queued and written in ~20 ms slices so
 * the reported [onLevel] tracks what is actually coming out of the speaker, and so [interrupt]
 * (the user talked over the agent) drops the rest of the reply within a slice.
 */
internal class VoicePlayer(
    private val sampleRate: Int,
    private val onLevel: (Float) -> Unit,
) {
    private val queue = LinkedBlockingQueue<Pair<Int, ByteArray>>()
    private val generation = AtomicInteger(0)
    @Volatile private var running = false
    @Volatile private var lastWriteAt = 0L
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    /** True while audio is queued or was written in the last moment: the agent is talking. */
    val isSpeaking: Boolean
        get() = queue.isNotEmpty() || System.currentTimeMillis() - lastWriteAt < SPEAKING_TAIL_MS

    var muted: Boolean = false
        set(value) {
            field = value
            runCatching { track?.setVolume(if (value) 0f else 1f) }
        }

    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val audioTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer, sampleRate / 5 * 2))
                .build()
        }.getOrNull() ?: return false
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            return false
        }
        if (muted) runCatching { audioTrack.setVolume(0f) }
        audioTrack.play()
        track = audioTrack
        running = true
        thread = Thread({
            val slice = sampleRate / 50 * 2
            while (running) {
                val next = runCatching { queue.poll(100, TimeUnit.MILLISECONDS) }.getOrNull()
                if (next == null) {
                    if (System.currentTimeMillis() - lastWriteAt > SPEAKING_TAIL_MS) onLevel(0f)
                    continue
                }
                val (gen, pcm) = next
                var offset = 0
                while (running && offset < pcm.size && gen == generation.get()) {
                    val length = minOf(slice, pcm.size - offset)
                    onLevel(if (muted) 0f else PcmLevel.of(pcm, offset, length))
                    val written = audioTrack.write(pcm, offset, length)
                    if (written <= 0) break
                    offset += written
                    lastWriteAt = System.currentTimeMillis()
                }
            }
        }, "voice-mode-player").apply { start() }
        return true
    }

    fun enqueue(pcm: ByteArray) {
        if (running && pcm.isNotEmpty()) queue.offer(generation.get() to pcm)
    }

    /** The user cut in: drop everything queued and whatever is still in the track's buffer. */
    fun interrupt() {
        generation.incrementAndGet()
        queue.clear()
        track?.let { t ->
            runCatching {
                t.pause()
                t.flush()
                t.play()
            }
        }
        lastWriteAt = 0L
        onLevel(0f)
    }

    fun stop() {
        running = false
        generation.incrementAndGet()
        queue.clear()
        thread?.join(500)
        thread = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        onLevel(0f)
    }

    private companion object {
        const val SPEAKING_TAIL_MS = 350L
    }
}

/**
 * Tells when the phone is held to the ear, from the proximity sensor. Phones without one never
 * report it, and the call simply stays on the loudspeaker.
 */
internal class VoiceProximity(context: Context, private val onChange: (atEar: Boolean) -> Unit) {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
    private val sensor = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_PROXIMITY)
    private var atEar = false
    private val listener = object : android.hardware.SensorEventListener {
        override fun onSensorChanged(event: android.hardware.SensorEvent) {
            val distance = event.values.firstOrNull() ?: return
            val near = distance < minOf(event.sensor.maximumRange, NEAR_CM)
            if (near != atEar) {
                atEar = near
                onChange(near)
            }
        }

        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val s = sensor ?: return
        runCatching { sensors?.registerListener(listener, s, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        runCatching { sensors?.unregisterListener(listener) }
        atEar = false
    }

    private companion object {
        const val NEAR_CM = 5f
    }
}

/**
 * Puts the phone in call mode for the session — what makes echo cancellation work — and plays
 * through the loudspeaker unless headphones or a headset are connected. Everything is restored
 * on [release].
 */
internal class VoiceAudioRouting(context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var focusRequest: AudioFocusRequest? = null
    private var speakerForced = false

    fun acquire() {
        runCatching {
            previousMode = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            requestFocus()
            routeToSpeakerUnlessHeadset()
        }.onFailure { Log.w(TAG, "Couldn't set up call audio", it) }
    }

    fun release() {
        runCatching {
            if (speakerForced) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audio.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audio.isSpeakerphoneOn = false
                }
            }
            speakerForced = false
            abandonFocus()
            audio.mode = previousMode
        }.onFailure { Log.w(TAG, "Couldn't restore audio", it) }
    }

    /**
     * The phone is held to the ear (or taken away): play through the earpiece like a normal call,
     * then back to the loudspeaker. Only when the app chose the loudspeaker itself; a headset
     * stays as it is.
     */
    fun holdToEar(atEar: Boolean) {
        if (!speakerForced) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val type = if (atEar) AudioDeviceInfo.TYPE_BUILTIN_EARPIECE else AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                audio.availableCommunicationDevices.firstOrNull { it.type == type }?.let { audio.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = !atEar
            }
        }.onFailure { Log.w(TAG, "Couldn't switch the earpiece", it) }
    }

    private fun routeToSpeakerUnlessHeadset() {
        val headsetTypes = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (outputs.any { it.type in headsetTypes }) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null && audio.setCommunicationDevice(speaker)) speakerForced = true
        } else {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = true
            speakerForced = true
        }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .build()
            focusRequest = request
            audio.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audio.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(null)
        }
    }

    private companion object {
        const val TAG = "VoiceAudioRouting"
    }
}
