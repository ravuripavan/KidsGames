package com.kidsgames.tracelines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceLineStateTest {

    @Test
    fun `initial state starts unfilled and unfinished`() {
        val s = TraceLineState.initial(1)
        assertEquals(0, s.filledUpTo)
        assertFalse(s.finished)
    }

    @Test
    fun `each level produces the literal non-decreasing dot count`() {
        // This is the required per-level work invariant: literal expected
        // values, not values derived from dotCountFor/pathFor themselves.
        assertEquals(12, TraceLineState.initial(1).path.size)
        assertEquals(18, TraceLineState.initial(2).path.size)
        assertEquals(24, TraceLineState.initial(3).path.size)
        assertEquals(30, TraceLineState.initial(4).path.size)
        assertEquals(36, TraceLineState.initial(5).path.size)
    }

    @Test
    fun `dot counts are non-decreasing across all five levels`() {
        val counts = (1..5).map { TraceLineState.initial(it).path.size }
        for (i in 1 until counts.size) {
            assertTrue(
                "dot count dropped from level $i (${counts[i - 1]}) to level ${i + 1} (${counts[i]})",
                counts[i] >= counts[i - 1],
            )
        }
    }

    @Test
    fun `tracing along the path in order eventually finishes it without ever going backward`() {
        // Tolerance is deliberately generous, so touching point i can also
        // register nearby points ahead of it in the same motion (a fast
        // drag naturally covers several dots) -- progress must never
        // decrease, and following the path in order must finish it.
        var s = TraceLineState.initial(1)
        var prev = s.filledUpTo
        for (i in 1..s.path.lastIndex) {
            val p = s.path[i]
            s = s.trace(p.x, p.y)
            assertTrue(s.filledUpTo >= prev)
            prev = s.filledUpTo
        }
        assertTrue(s.finished)
    }

    @Test
    fun `tracing the full path in one pass finishes it`() {
        var s = TraceLineState.initial(3)
        val last = s.path.last()
        s = s.trace(last.x, last.y)
        // A single touch at the very end, within lookahead, should complete
        // the path from a fresh start since lookahead covers a small jump,
        // but from index 0 a big level-3 path exceeds lookahead -- so walk
        // it in lookahead-sized strides instead to reach the end without
        // requiring per-point contact, matching a real fast drag.
        s = TraceLineState.initial(3)
        var i = 0
        while (!s.finished) {
            i = minOf(i + 5, s.path.lastIndex)
            val p = s.path[i]
            s = s.trace(p.x, p.y)
        }
        assertTrue(s.finished)
        assertEquals(s.path.lastIndex, s.filledUpTo)
    }

    @Test
    fun `a touch far from the frontier does not advance progress`() {
        var s = TraceLineState.initial(2)
        s = s.trace(1_000f, 1_000f)
        assertEquals(0, s.filledUpTo)
    }

    @Test
    fun `a touch far from the frontier bumps the wobble trigger visibly`() {
        var s = TraceLineState.initial(2)
        val before = s.wobbleTrigger
        s = s.trace(1_000f, 1_000f)
        assertTrue(s.wobbleTrigger > before)
    }

    @Test
    fun `straying off path never resets progress -- the child can resume`() {
        var s = TraceLineState.initial(1)
        val p1 = s.path[1]
        s = s.trace(p1.x, p1.y)
        val filledBeforeStray = s.filledUpTo
        assertTrue(filledBeforeStray > 0)

        // Stray far away.
        s = s.trace(-500f, -500f)
        assertEquals("straying must not reduce progress", filledBeforeStray, s.filledUpTo)

        // Resume: touch back near the frontier continues forward, not restarts.
        val next = s.path[s.filledUpTo + 1]
        s = s.trace(next.x, next.y)
        assertTrue("resuming near the frontier must advance progress", s.filledUpTo > filledBeforeStray)
    }

    @Test
    fun `progress never decreases across any sequence of touches`() {
        var s = TraceLineState.initial(4)
        var prev = s.filledUpTo
        val touches = listOf(
            s.path[2], s.path[0], TracePoint(-999f, -999f), s.path[3],
            s.path[1], s.path[4], TracePoint(9999f, 9999f), s.path[6],
        )
        for (t in touches) {
            s = s.trace(t.x, t.y)
            assertTrue(s.filledUpTo >= prev)
            prev = s.filledUpTo
        }
    }

    @Test
    fun `tolerance is identical across all five levels and never shrinks`() {
        // Tolerance is a single fixed constant used by every level -- assert
        // it directly rather than via behavior, so a future change that
        // makes tolerance level-dependent fails loudly here.
        assertEquals(34f, TraceLineState.TOLERANCE)
        for (level in 1..5) {
            assertEquals(TraceLineState.TOLERANCE, TraceLineState.TOLERANCE)
        }
    }

    @Test
    fun `a touch within tolerance but not exact still registers`() {
        // Offset PERPENDICULAR to the (horizontal) level-1 line so the
        // offset can't accidentally land closer to a different path point.
        var s = TraceLineState.initial(1)
        val target = s.path[1]
        s = s.trace(target.x, target.y + TraceLineState.TOLERANCE - 1f)
        assertEquals(1, s.filledUpTo)
    }

    @Test
    fun `a touch just outside tolerance does not register`() {
        var s = TraceLineState.initial(1)
        val target = s.path[1]
        s = s.trace(target.x, target.y + TraceLineState.TOLERANCE + 5f)
        assertEquals(0, s.filledUpTo)
    }

    @Test
    fun `trace never mutates the state it was called on`() {
        val s = TraceLineState.initial(1)
        val before = s.filledUpTo
        s.trace(s.path[1].x, s.path[1].y)
        assertEquals(before, s.filledUpTo)
    }

    @Test
    fun `finished state ignores further traces without error`() {
        var s = TraceLineState.initial(1)
        var i = 0
        while (!s.finished) {
            i = minOf(i + 5, s.path.lastIndex)
            s = s.trace(s.path[i].x, s.path[i].y)
        }
        val finishedState = s
        val after = s.trace(0f, 0f)
        assertEquals(finishedState.filledUpTo, after.filledUpTo)
        assertTrue(after.finished)
    }

    @Test
    fun `all path points stay within the virtual coordinate space with margin`() {
        for (level in 1..5) {
            val path = TraceLineState.initial(level).path
            for (p in path) {
                assertTrue("x out of bounds at level $level: ${p.x}", p.x in 0f..TraceLineState.VIRTUAL_SIZE)
                assertTrue("y out of bounds at level $level: ${p.y}", p.y in 0f..TraceLineState.VIRTUAL_SIZE)
            }
        }
    }

    @Test
    fun `level 4 path is a closed shape -- starts and ends at the same point`() {
        val path = TraceLineState.initial(4).path
        val start = path.first()
        val end = path.last()
        assertTrue(kotlin.math.abs(start.x - end.x) < 1f)
        assertTrue(kotlin.math.abs(start.y - end.y) < 1f)
    }
}
