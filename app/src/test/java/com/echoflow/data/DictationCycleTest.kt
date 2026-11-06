package com.echoflow.data

import org.junit.Assert.*
import org.junit.Test

class DictationCycleTest {
    private val cycle = DictationCycle()

    @Test fun `a full dictation goes idle, recording, transcribing, idle`() {
        assertEquals(DictationCycle.Tap.Start, cycle.tap())
        val gen = cycle.newCapture()
        cycle.recording()
        cycle.startSettled()
        assertEquals(DictationCycle.Tap.Stop, cycle.tap())
        assertTrue(cycle.stopRecording())
        assertEquals(DictationPhase.Transcribing, cycle.phase)
        assertTrue(cycle.finished(gen))
        assertEquals(DictationPhase.Idle, cycle.phase)
    }

    @Test fun `taps are ignored while starting or transcribing`() {
        cycle.tap()
        assertEquals(DictationCycle.Tap.Ignore, cycle.tap())
        cycle.newCapture(); cycle.recording(); cycle.startSettled()
        cycle.stopRecording()
        assertEquals(DictationCycle.Tap.Ignore, cycle.tap())
    }

    @Test fun `a transcription finishing after an abort changes nothing`() {
        cycle.tap()
        val old = cycle.newCapture()
        cycle.recording(); cycle.startSettled(); cycle.stopRecording()
        cycle.abort()
        cycle.tap()
        cycle.newCapture(); cycle.recording(); cycle.startSettled()
        assertFalse(cycle.finished(old))
        assertEquals(DictationPhase.Recording, cycle.phase)
    }

    @Test fun `stopping when not recording does nothing`() {
        assertFalse(cycle.stopRecording())
        assertEquals(DictationPhase.Idle, cycle.phase)
    }

    @Test fun `every capture and abort moves the generation on`() {
        val a = cycle.newCapture()
        cycle.abort()
        val b = cycle.newCapture()
        assertTrue(b > a + 1)
        assertTrue(cycle.isCurrent(b))
        assertFalse(cycle.isCurrent(a))
    }
}
