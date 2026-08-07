package com.kidsgames.musicpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPadStateTest {

    @Test
    fun `pad count grows from four to eight across levels`() {
        assertEquals(4, MusicPadState.padCountFor(1))
        assertEquals(6, MusicPadState.padCountFor(2))
        assertEquals(8, MusicPadState.padCountFor(3))
        assertEquals(8, MusicPadState.padCountFor(4))
        assertEquals(8, MusicPadState.padCountFor(5))
    }

    @Test
    fun `second instrument unlocks at level four`() {
        assertEquals(1, MusicPadState.instrumentCountFor(1))
        assertEquals(1, MusicPadState.instrumentCountFor(2))
        assertEquals(1, MusicPadState.instrumentCountFor(3))
        assertEquals(2, MusicPadState.instrumentCountFor(4))
        assertEquals(2, MusicPadState.instrumentCountFor(5))
    }

    @Test
    fun `recording only unlocks at level five`() {
        assertFalse(MusicPadState.recordingSupportedFor(4))
        assertTrue(MusicPadState.recordingSupportedFor(5))
    }

    @Test
    fun `tools available never decrease across levels one through five`() {
        // "Tools" for this sandbox = pad count, instrument count, and
        // whether record-and-replay is available. None of these may ever
        // shrink from one level to the next.
        val padCounts = (1..5).map { MusicPadState.padCountFor(it) }
        val instrumentCounts = (1..5).map { MusicPadState.instrumentCountFor(it) }
        val recordingFlags = (1..5).map { if (MusicPadState.recordingSupportedFor(it)) 1 else 0 }

        for (i in 1 until 5) {
            assertTrue("pad count dropped at level ${i + 1}", padCounts[i] >= padCounts[i - 1])
            assertTrue("instrument count dropped at level ${i + 1}", instrumentCounts[i] >= instrumentCounts[i - 1])
            assertTrue("recording support dropped at level ${i + 1}", recordingFlags[i] >= recordingFlags[i - 1])
        }
    }

    @Test
    fun `tapping a pad is always accepted and never fails`() {
        var s = MusicPadState.initial(3)
        for (pad in 0 until s.padCount) {
            s = s.tapPad(pad)
        }
        assertEquals(s.padCount - 1, s.lastTap?.padId ?: -1)
    }

    @Test
    fun `every tap increments that pad's trigger so a keyed animation can react`() {
        var s = MusicPadState.initial(1)
        s = s.tapPad(0)
        assertEquals(1, s.tapTriggers[0 to 0])
        s = s.tapPad(0)
        assertEquals(2, s.tapTriggers[0 to 0])
    }

    @Test
    fun `tapping an out-of-range pad id is a harmless no-op, never a crash or error state`() {
        val s = MusicPadState.initial(1)
        val result = s.tapPad(999)
        assertEquals(s, result)
    }

    @Test
    fun `state is immutable -- tapPad never mutates the receiver`() {
        val s = MusicPadState.initial(2)
        val before = s.lastTap
        s.tapPad(1)
        assertEquals(before, s.lastTap)
    }

    @Test
    fun `instrument selection is a no-op below level four`() {
        val s = MusicPadState.initial(3)
        val result = s.selectInstrument(1)
        assertEquals(0, result.selectedInstrument)
    }

    @Test
    fun `instrument selection switches which instrument taps sound on at level four`() {
        var s = MusicPadState.initial(4)
        s = s.selectInstrument(1)
        assertEquals(1, s.selectedInstrument)
        s = s.tapPad(2)
        assertEquals(1, s.lastTap?.instrumentIndex)
    }

    @Test
    fun `instrument index clamps to a valid instrument, never leaving one undefined`() {
        val s = MusicPadState.initial(4)
        assertEquals(1, s.selectInstrument(99).selectedInstrument)
        assertEquals(0, s.selectInstrument(-5).selectedInstrument)
    }

    @Test
    fun `recording an empty tune and replaying it round-trips cleanly`() {
        var s = MusicPadState.initial(5)
        s = s.startRecording(atMillis = 0L)
        assertTrue(s.isRecording)
        s = s.stopRecording()
        assertFalse(s.isRecording)
        assertTrue(s.recordedEvents.isEmpty())

        s = s.startReplay()
        assertTrue(s.isReplaying)
        // Replaying zero events is a completely valid outcome -- nothing to
        // iterate, no crash, no error state.
        assertTrue(s.recordedEvents.isEmpty())
    }

    @Test
    fun `recording taps round-trips through replay in the same order`() {
        var s = MusicPadState.initial(5)
        s = s.startRecording(atMillis = 1000L)
        s = s.tapPad(0, atMillis = 1000L)
        s = s.tapPad(2, atMillis = 1500L)
        s = s.tapPad(5, atMillis = 2200L)
        s = s.stopRecording()

        assertEquals(3, s.recordedEvents.size)
        assertEquals(listOf(0, 2, 5), s.recordedEvents.map { it.padId })
        assertEquals(listOf(0L, 500L, 1200L), s.recordedEvents.map { it.elapsedMillis })

        s = s.startReplay()
        var replayed = MusicPadState.initial(5)
        for (event in s.recordedEvents) {
            replayed = replayed.replayTap(event)
        }
        assertEquals(s.recordedEvents.last(), replayed.lastTap)
        // Replaying never appends to the recording itself.
        assertTrue(replayed.recordedEvents.isEmpty())
    }

    @Test
    fun `starting a new recording discards the previous one without any warning or failure`() {
        var s = MusicPadState.initial(5)
        s = s.startRecording(atMillis = 0L).tapPad(0, atMillis = 0L).stopRecording()
        assertEquals(1, s.recordedEvents.size)

        s = s.startRecording(atMillis = 5000L)
        assertTrue(s.recordedEvents.isEmpty())
    }

    @Test
    fun `taps made while replaying do not silently corrupt an in-progress recording`() {
        var s = MusicPadState.initial(5)
        s = s.startRecording(atMillis = 0L)
        s = s.startReplay()
        assertFalse(s.isRecording)
    }

    @Test
    fun `recording and replay are unavailable below level five and never crash when called anyway`() {
        var s = MusicPadState.initial(4)
        s = s.startRecording(atMillis = 0L)
        assertFalse(s.isRecording)
        s = s.startReplay()
        assertFalse(s.isReplaying)
    }
}
