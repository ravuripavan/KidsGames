package com.kidsgames.puzzleboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleBoardStateTest {

    @Test
    fun `level one has four pieces`() {
        assertEquals(4, PuzzleBoardState(level = 1).pieces.size)
    }

    @Test
    fun `level two has six pieces`() {
        assertEquals(6, PuzzleBoardState(level = 2).pieces.size)
    }

    @Test
    fun `level three has nine pieces`() {
        assertEquals(9, PuzzleBoardState(level = 3).pieces.size)
    }

    @Test
    fun `level four and five have twelve pieces`() {
        assertEquals(12, PuzzleBoardState(level = 4).pieces.size)
        assertEquals(12, PuzzleBoardState(level = 5).pieces.size)
    }

    @Test
    fun `grid dimensions match the piece count at every level`() {
        for (level in 1..5) {
            val state = PuzzleBoardState(level)
            assertEquals(
                "level $level grid must hold exactly as many cells as pieces",
                state.pieces.size,
                state.grid.pieceCount,
            )
        }
    }

    @Test
    fun `required work is non-decreasing across levels one through five`() {
        val requiredByLevel = (1..5).map { PuzzleBoardState(it).pieces.size }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required pieces dropped from level $i (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `only level five hides the outline guide`() {
        assertTrue(PuzzleBoardState(level = 1).showOutline)
        assertTrue(PuzzleBoardState(level = 2).showOutline)
        assertTrue(PuzzleBoardState(level = 3).showOutline)
        assertTrue(PuzzleBoardState(level = 4).showOutline)
        assertFalse(PuzzleBoardState(level = 5).showOutline)
    }

    @Test
    fun `snap tolerance is constant across levels and never shrinks`() {
        val toleranceByLevel = (1..5).map { PuzzleBoardGeometry.toleranceDpFor(it) }
        for (i in 1 until toleranceByLevel.size) {
            assertTrue(
                "tolerance shrank from level $i (${toleranceByLevel[i - 1]}) " +
                    "to level ${i + 1} (${toleranceByLevel[i]}); tolerance must never shrink as levels rise",
                toleranceByLevel[i] >= toleranceByLevel[i - 1],
            )
        }
        // Also pin it as genuinely generous, not just non-decreasing -- a
        // tolerance that is constant-but-tiny would technically pass the
        // check above while still failing a wobbly 4 year old's drag.
        assertTrue(toleranceByLevel.all { it >= 48f })
    }

    @Test
    fun `a drop within tolerance of its target counts`() {
        val target = Point(100f, 100f)
        val drop = Point(130f, 100f) // 30 units off
        assertTrue(PuzzleBoardGeometry.isWithinTolerance(drop, target, toleranceDp = 40f))
    }

    @Test
    fun `a drop outside tolerance of its target does not count`() {
        val target = Point(100f, 100f)
        val drop = Point(500f, 500f)
        assertFalse(PuzzleBoardGeometry.isWithinTolerance(drop, target, toleranceDp = 72f))
    }

    @Test
    fun `target center is the middle of the correct grid cell`() {
        val center = PuzzleBoardGeometry.targetCenter(
            boardLeft = 0f,
            boardTop = 0f,
            boardWidth = 200f,
            boardHeight = 100f,
            rows = 2,
            cols = 2,
            row = 0,
            col = 1,
        )
        assertEquals(150f, center.x, 0.01f)
        assertEquals(25f, center.y, 0.01f)
    }

    @Test
    fun `placing a piece marks it placed and leaves the rest untouched`() {
        val state = PuzzleBoardState(level = 1)
        val target = state.pieces.first()
        val next = state.place(target.id)
        assertTrue(next.pieces.first { it.id == target.id }.placed)
        // every other piece is bit-for-bit unaffected
        state.pieces.filter { it.id != target.id }.forEach { original ->
            assertEquals(original, next.pieces.first { it.id == original.id })
        }
    }

    @Test
    fun `place does not mutate the state it was called on`() {
        val state = PuzzleBoardState(level = 1)
        val target = state.pieces.first()
        state.place(target.id)
        // the original reference must be untouched -- place() is pure
        assertFalse(state.pieces.first { it.id == target.id }.placed)
    }

    @Test
    fun `placing an already-placed piece again is a no-op`() {
        val state = PuzzleBoardState(level = 1)
        val target = state.pieces.first()
        val once = state.place(target.id)
        val twice = once.place(target.id)
        assertEquals(once, twice)
    }

    @Test
    fun `a misdrop costs nothing`() {
        // A misdrop in this game is simply never calling place() -- the
        // Composable only calls it once a drop has already been judged
        // within tolerance of the piece's own correct slot. Simulate a
        // rejected drop by confirming the state after "no call" is
        // identical to before, with no penalty field anywhere on the class.
        val before = PuzzleBoardState(level = 3)
        val afterNoOpAttempt = before // nothing to call; misdrops never reach place()
        assertEquals(before, afterNoOpAttempt)
        assertFalse(before.isComplete)
        // placing a piece that doesn't exist is also a safe no-op
        val unaffected = before.place(pieceId = -1)
        assertEquals(before, unaffected)
    }

    @Test
    fun `placing every piece completes the level`() {
        var state = PuzzleBoardState(level = 1)
        state.pieces.map { it.id }.forEach { state = state.place(it) }
        assertTrue(state.isComplete)
    }

    @Test
    fun `a correctly placed piece is never un-placed`() {
        var state = PuzzleBoardState(level = 2)
        val piece = state.pieces.first()
        state = state.place(piece.id)
        assertTrue(state.pieces.first { it.id == piece.id }.placed)
        // simulate more play happening around it
        state.pieces.drop(1).map { it.id }.forEach { state = state.place(it) }
        assertTrue(state.pieces.first { it.id == piece.id }.placed)
    }

    @Test
    fun `tray order and piece identity never change across placements`() {
        // Mirrors PopBalloonsState's "pop never removes a balloon or
        // reorders the list" invariant: PuzzleBoardGame renders
        // state.pieces directly, unfiltered, into the tray. If place() ever
        // shrank or reordered the list, every unplaced piece after the
        // placed one would shift to a new tray slot mid-drag.
        var state = PuzzleBoardState(level = 4)
        val originalIds = state.pieces.map { it.id }
        val originalSize = state.pieces.size

        state.pieces.map { it.id }.forEach { id ->
            state = state.place(id)
            assertEquals(originalSize, state.pieces.size)
            assertEquals(originalIds, state.pieces.map { it.id })
        }
    }
}
