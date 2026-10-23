package com.echoflow.data.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
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
import android.os.Handler
import android.os.Looper
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
    /** Frames handed to the track since it started or was last flushed. */
    @Volatile private var framesWritten = 0L
    /** Where the current reply starts, in the same frames as [framesWritten]. */
    @Volatile private var replyStartFrame = 0L
    /** Bytes of the current reply received so far. */
    @Volatile private var replyBytes = 0L
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    /** A new reply begins: [playedMillis] counts from here, after anything still queued. */
    fun markReplyStart() {
        replyStartFrame = framesWritten + queue.sumOf { it.second.size.toLong() } / 2
        replyBytes = 0L
    }

    /**
     * How much of the current reply the user has heard, in milliseconds: the track's playback
     * position, not what was written to it, since the track buffers ahead and [interrupt] drops that.
     */
    val playedMillis: Long
        get() {
            val head = runCatching { track?.playbackHeadPosition?.toLong()?.and(0xFFFFFFFFL) }.getOrNull() ?: 0L
            val frames = (head - replyStartFrame).coerceIn(0L, (framesWritten - replyStartFrame).coerceAtLeast(0L))
            return frames * 1_000L / sampleRate
        }

    /** How long the current reply is so far, in milliseconds of audio received. */
    val replyMillis: Long
        get() = replyBytes / 2 * 1_000L / sampleRate

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
                    framesWritten += written / 2
                    lastWriteAt = System.currentTimeMillis()
                }
            }
        }, "voice-mode-player").apply { start() }
        return true
    }

    fun enqueue(pcm: ByteArray) {
        if (!running || pcm.isEmpty()) return
        replyBytes += pcm.size
        queue.offer(generation.get() to pcm)
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
        // A flush resets the track's playback position, so the counts start over too.
        framesWritten = 0L
        replyStartFrame = 0L
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
 * What [VoiceAudioRouting] needs from the platform's audio system, so the routing can be tested
 * without a phone. Devices are named by their [AudioDeviceInfo] type.
 */
internal interface CallAudio {
    /** Android 12+: the call's device is chosen with setCommunicationDevice. */
    val choosesCommunicationDevice: Boolean
    var mode: Int
    fun requestFocus()
    fun abandonFocus()

    /** Android 12+: the devices a call can be routed to. */
    fun communicationDevices(): List<Int>
    /** Android 12+: the device the call is routed to now, if the app or the system chose one. */
    fun communicationDevice(): Int?
    fun setCommunicationDevice(type: Int): Boolean
    fun clearCommunicationDevice()

    /** Before Android 12: the connected outputs. */
    fun outputs(): List<Int>
    fun startBluetoothSco()
    fun stopBluetoothSco()
    fun setSpeakerphone(on: Boolean)

    /** Calls [onChange] when an audio device connects or disconnects, until [unwatchDevices]. */
    fun watchDevices(onChange: () -> Unit)
    fun unwatchDevices()
}

/** [CallAudio] on the phone's [AudioManager]. */
internal class AndroidCallAudio(context: Context) : CallAudio {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var deviceCallback: AudioDeviceCallback? = null

    override val choosesCommunicationDevice: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override var mode: Int
        get() = audio.mode
        set(value) {
            audio.mode = value
        }

    override fun requestFocus() {
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

    override fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audio.abandonAudioFocus(null)
        }
    }

    override fun communicationDevices(): List<Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.availableCommunicationDevices.map { it.type } else emptyList()

    override fun communicationDevice(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.communicationDevice?.type else null

    override fun setCommunicationDevice(type: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val device = audio.availableCommunicationDevices.firstOrNull { it.type == type } ?: return false
        return audio.setCommunicationDevice(device)
    }

    override fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
    }

    override fun outputs(): List<Int> = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }

    @Suppress("DEPRECATION")
    override fun startBluetoothSco() {
        audio.startBluetoothSco()
        audio.isBluetoothScoOn = true
    }

    @Suppress("DEPRECATION")
    override fun stopBluetoothSco() {
        audio.isBluetoothScoOn = false
        audio.stopBluetoothSco()
    }

    @Suppress("DEPRECATION")
    override fun setSpeakerphone(on: Boolean) {
        audio.isSpeakerphoneOn = on
    }

    override fun watchDevices(onChange: () -> Unit) {
        unwatchDevices()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChange()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChange()
        }
        deviceCallback = callback
        audio.registerAudioDeviceCallback(callback, handler)
    }

    override fun unwatchDevices() {
        deviceCallback?.let { audio.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }
}

/**
 * Puts the phone in call mode for the session (what makes echo cancellation work) and picks where
 * the call is heard: a Bluetooth or wired headset when one is connected, otherwise the
 * loudspeaker, or the earpiece while the phone is held to the ear. In call mode the system does
 * not move audio to a Bluetooth headset by itself, so the headset is selected explicitly; the
 * mic follows the same device. Headsets connecting or disconnecting mid-call are followed.
 * Everything is restored on [release].
 */
internal class VoiceAudioRouting(private val audio: CallAudio) {
    constructor(context: Context) : this(AndroidCallAudio(context))

    private var previousMode = AudioManager.MODE_NORMAL
    private var active = false
    private var atEar = false

    /** Android 12+: the app chose the call's device, so [release] hands the choice back. */
    private var deviceChosen = false

    /** Before Android 12: the Bluetooth headset's voice link was opened by the app. */
    private var scoStarted = false

    /** Before Android 12: the loudspeaker was turned on by the app. */
    private var speakerOn = false

    @Synchronized
    fun acquire() {
        if (active) return
        runCatching {
            previousMode = audio.mode
            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            audio.requestFocus()
            active = true
            applyRoute()
            audio.watchDevices(::reroute)
        }.onFailure { Log.w(TAG, "Couldn't set up call audio", it) }
    }

    @Synchronized
    fun release() {
        if (!active) return
        active = false
        atEar = false
        runCatching {
            audio.unwatchDevices()
            if (audio.choosesCommunicationDevice) {
                if (deviceChosen) audio.clearCommunicationDevice()
                deviceChosen = false
            } else {
                stopSco()
                setSpeaker(false)
            }
            audio.abandonFocus()
            audio.mode = previousMode
        }.onFailure { Log.w(TAG, "Couldn't restore audio", it) }
    }

    /**
     * The phone is held to the ear (or taken away): play through the earpiece like a normal call,
     * then back to the loudspeaker. A connected headset stays as it is.
     */
    @Synchronized
    fun holdToEar(atEar: Boolean) {
        if (this.atEar == atEar) return
        this.atEar = atEar
        if (active) runCatching { applyRoute() }.onFailure { Log.w(TAG, "Couldn't switch the earpiece", it) }
    }

    @Synchronized
    private fun reroute() {
        if (active) runCatching { applyRoute() }.onFailure { Log.w(TAG, "Couldn't follow the headset", it) }
    }

    private fun applyRoute() {
        if (audio.choosesCommunicationDevice) chooseCommunicationDevice() else routeLegacy()
    }

    private fun chooseCommunicationDevice() {
        val target = pickDevice(audio.communicationDevices(), atEar) ?: return
        if (audio.communicationDevice() == target) {
            deviceChosen = true
            return
        }
        if (audio.setCommunicationDevice(target)) {
            deviceChosen = true
        } else {
            Log.w(TAG, "Couldn't route the call to device type $target")
        }
    }

    private fun routeLegacy() {
        val outputs = audio.outputs()
        if (AudioDeviceInfo.TYPE_BLUETOOTH_SCO in outputs) {
            setSpeaker(false)
            if (!scoStarted) {
                audio.startBluetoothSco()
                scoStarted = true
            }
        } else {
            stopSco()
            // A wired headset takes the call by itself in call mode.
            setSpeaker(outputs.none { it in WIRED_TYPES } && !atEar)
        }
    }

    private fun stopSco() {
        if (!scoStarted) return
        audio.stopBluetoothSco()
        scoStarted = false
    }

    private fun setSpeaker(on: Boolean) {
        if (on == speakerOn) return
        audio.setSpeakerphone(on)
        speakerOn = on
    }

    internal companion object {
        private const val TAG = "VoiceAudioRouting"

        private val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

        /** Where a call goes when several are connected, most preferred first. */
        private val HEADSET_PRIORITY = listOf(
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        )

        /** Android 12+: the device a call goes to, from those [available]; null when none fits. */
        fun pickDevice(available: List<Int>, atEar: Boolean): Int? {
            HEADSET_PRIORITY.firstOrNull { it in available }?.let { return it }
            val builtIn = if (atEar) AudioDeviceInfo.TYPE_BUILTIN_EARPIECE else AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            return builtIn.takeIf { it in available }
        }
    }
}
