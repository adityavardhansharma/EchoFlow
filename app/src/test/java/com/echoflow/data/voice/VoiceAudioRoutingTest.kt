package com.echoflow.data.voice

import android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
import android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
import android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
import android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
import android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAudioRoutingTest {

    /** A phone's audio system: what is connected, and what the routing did to it. */
    private class FakeCallAudio(override val choosesCommunicationDevice: Boolean) : CallAudio {
        override var mode = AudioManager.MODE_NORMAL
        val devices = mutableListOf(TYPE_BUILTIN_EARPIECE, TYPE_BUILTIN_SPEAKER)
        var current: Int? = null
        var focused = false
        var scoOn = false
        var scoFailure: (() -> Unit)? = null
        var speakerOn = false
        var onChange: (() -> Unit)? = null
        var clears = 0

        override fun requestFocus() { focused = true }
        override fun abandonFocus() { focused = false }
        override fun communicationDevices() = devices.toList()
        override fun communicationDevice() = current
        override fun setCommunicationDevice(type: Int): Boolean {
            if (type !in devices) return false
            current = type
            return true
        }
        override fun clearCommunicationDevice() {
            current = null
            clears++
        }
        override fun outputs() = devices.toList()
        override fun startBluetoothSco(onFailed: () -> Unit) {
            scoOn = true
            scoFailure = onFailed
        }
        override fun stopBluetoothSco() {
            scoOn = false
            scoFailure = null
        }
        override fun isSpeakerphoneOn() = speakerOn
        override fun setSpeakerphone(on: Boolean) { speakerOn = on }
        override fun watchDevices(onChange: () -> Unit) { this.onChange = onChange }
        override fun unwatchDevices() { onChange = null }

        fun connect(type: Int) {
            devices += type
            onChange?.invoke()
        }

        fun disconnect(type: Int) {
            devices -= type
            if (current == type) current = null
            onChange?.invoke()
        }
    }

    @Test
    fun `a headset wins over the built-in devices, Bluetooth first`() {
        val builtIn = listOf(TYPE_BUILTIN_EARPIECE, TYPE_BUILTIN_SPEAKER)
        assertEquals(TYPE_BUILTIN_SPEAKER, VoiceAudioRouting.pickDevice(builtIn, atEar = false))
        assertEquals(TYPE_BUILTIN_EARPIECE, VoiceAudioRouting.pickDevice(builtIn, atEar = true))
        assertEquals(TYPE_BLUETOOTH_SCO, VoiceAudioRouting.pickDevice(builtIn + TYPE_WIRED_HEADSET + TYPE_BLUETOOTH_SCO, atEar = false))
        assertEquals(TYPE_BLE_HEADSET, VoiceAudioRouting.pickDevice(builtIn + TYPE_BLUETOOTH_SCO + TYPE_BLE_HEADSET, atEar = true))
        assertEquals(TYPE_WIRED_HEADSET, VoiceAudioRouting.pickDevice(builtIn + TYPE_WIRED_HEADSET, atEar = true))
        assertNull(VoiceAudioRouting.pickDevice(emptyList(), atEar = false))
    }

    @Test
    fun `android 12+ routes the call to a connected Bluetooth headset`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = true).apply { devices += TYPE_BLUETOOTH_SCO }
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, audio.mode)
        assertTrue(audio.focused)
        assertEquals(TYPE_BLUETOOTH_SCO, audio.current)
        // Holding the phone to the ear doesn't take the call off the headset.
        routing.holdToEar(true)
        assertEquals(TYPE_BLUETOOTH_SCO, audio.current)
    }

    @Test
    fun `android 12+ follows a headset connecting and disconnecting mid-call`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = true)
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertEquals(TYPE_BUILTIN_SPEAKER, audio.current)
        audio.connect(TYPE_BLUETOOTH_SCO)
        assertEquals(TYPE_BLUETOOTH_SCO, audio.current)
        audio.disconnect(TYPE_BLUETOOTH_SCO)
        assertEquals(TYPE_BUILTIN_SPEAKER, audio.current)
        routing.holdToEar(true)
        assertEquals(TYPE_BUILTIN_EARPIECE, audio.current)
        routing.holdToEar(false)
        assertEquals(TYPE_BUILTIN_SPEAKER, audio.current)
    }

    @Test
    fun `android 12+ release restores everything and stops following devices`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = true).apply { mode = AudioManager.MODE_RINGTONE }
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        routing.acquire() // a second acquire is a no-op, so release still restores the first mode
        routing.release()
        assertEquals(AudioManager.MODE_RINGTONE, audio.mode)
        assertFalse(audio.focused)
        assertNull(audio.current)
        assertNull(audio.onChange)
        assertEquals(1, audio.clears)
        routing.release()
        assertEquals(1, audio.clears)
    }

    @Test
    fun `before android 12 a Bluetooth headset gets the call over SCO`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = false).apply { devices += TYPE_BLUETOOTH_SCO }
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertTrue(audio.scoOn)
        assertFalse(audio.speakerOn)
        routing.release()
        assertFalse(audio.scoOn)
        assertEquals(AudioManager.MODE_NORMAL, audio.mode)
    }

    @Test
    fun `before android 12 the loudspeaker is used without a headset, and SCO follows the headset`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = false)
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertTrue(audio.speakerOn)
        routing.holdToEar(true)
        assertFalse(audio.speakerOn)
        routing.holdToEar(false)
        audio.connect(TYPE_BLUETOOTH_SCO)
        assertTrue(audio.scoOn)
        assertFalse(audio.speakerOn)
        audio.disconnect(TYPE_BLUETOOTH_SCO)
        assertFalse(audio.scoOn)
        assertTrue(audio.speakerOn)
        routing.release()
        assertFalse(audio.speakerOn)
    }

    @Test
    fun `before android 12 a wired headset turns the loudspeaker off but media-only Bluetooth does not`() {
        val wired = FakeCallAudio(choosesCommunicationDevice = false).apply { devices += TYPE_WIRED_HEADSET }
        VoiceAudioRouting(wired).acquire()
        assertFalse(wired.speakerOn)
        assertFalse(wired.scoOn)

        // A2DP-only headphones can't carry a call, so it stays on the loudspeaker.
        val a2dp = FakeCallAudio(choosesCommunicationDevice = false).apply { devices += TYPE_BLUETOOTH_A2DP }
        VoiceAudioRouting(a2dp).acquire()
        assertTrue(a2dp.speakerOn)
        assertFalse(a2dp.scoOn)
    }

    @Test
    fun `before android 12 a failed headset link falls back to the loudspeaker until the headset reconnects`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = false).apply { devices += TYPE_BLUETOOTH_SCO }
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertTrue(audio.scoOn)
        audio.scoFailure!!.invoke()
        assertFalse(audio.scoOn)
        assertTrue(audio.speakerOn)
        // Another device change with the headset still there doesn't retry the failed link.
        audio.connect(TYPE_WIRED_HEADSET)
        audio.disconnect(TYPE_WIRED_HEADSET)
        assertFalse(audio.scoOn)
        // The headset reconnecting does.
        audio.disconnect(TYPE_BLUETOOTH_SCO)
        audio.connect(TYPE_BLUETOOTH_SCO)
        assertTrue(audio.scoOn)
        assertFalse(audio.speakerOn)
    }

    @Test
    fun `before android 12 release puts the loudspeaker back how it was`() {
        val audio = FakeCallAudio(choosesCommunicationDevice = false).apply {
            devices += TYPE_WIRED_HEADSET
            speakerOn = true
        }
        val routing = VoiceAudioRouting(audio)
        routing.acquire()
        assertFalse(audio.speakerOn)
        routing.release()
        assertTrue(audio.speakerOn)
    }
}
