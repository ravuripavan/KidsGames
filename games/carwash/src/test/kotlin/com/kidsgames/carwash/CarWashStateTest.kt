package com.kidsgames.carwash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWashStateTest {

    @Test
    fun `level one offers only the sponge`() {
        assertEquals(listOf(Tool.SPONGE), CarWashState.toolsFor(1))
    }

    @Test
    fun `level two adds the hose`() {
        assertEquals(listOf(Tool.SPONGE, Tool.HOSE), CarWashState.toolsFor(2))
    }

    @Test
    fun `level three adds the dryer`() {
        assertEquals(listOf(Tool.SPONGE, Tool.HOSE, Tool.DRYER), CarWashState.toolsFor(3))
    }

    @Test
    fun `level four adds wax and polish`() {
        assertEquals(
            listOf(Tool.SPONGE, Tool.HOSE, Tool.DRYER, Tool.WAX, Tool.POLISH),
            CarWashState.toolsFor(4),
        )
    }

    @Test
    fun `level five keeps the same five tools as level four`() {
        assertEquals(CarWashState.toolsFor(4), CarWashState.toolsFor(5))
    }

    @Test
    fun `tool count is non-decreasing across levels one through five`() {
        // Literal, spec-mandated invariant: 1, 2, 3, 5, 5.
        val counts = (1..5).map { CarWashState.toolsFor(it).size }
        assertEquals(listOf(1, 2, 3, 5, 5), counts)
        for (i in 1 until counts.size) {
            assertTrue(
                "tool count dropped from level $i (${counts[i - 1]}) to level ${i + 1} (${counts[i]})",
                counts[i] >= counts[i - 1],
            )
        }
    }

    @Test
    fun `initial state starts fully dirty and not clean`() {
        val s = CarWashState.initial(1)
        assertEquals(CarWashState.GRID_ROWS * CarWashState.GRID_COLS, s.dirt.size)
        assertTrue(s.dirt.all { it > 0f })
        assertFalse(s.isClean)
    }

    @Test
    fun `initial state equips the sponge, which is always unlocked`() {
        assertEquals(Tool.SPONGE, CarWashState.initial(1).selectedTool)
        assertEquals(Tool.SPONGE, CarWashState.initial(5).selectedTool)
    }

    @Test
    fun `level five starts dirtier than earlier levels`() {
        val l4 = CarWashState.initial(4)
        val l5 = CarWashState.initial(5)
        assertTrue(l5.dirt.first() > l4.dirt.first())
    }

    @Test
    fun `selecting an unlocked tool changes selectedTool`() {
        val s = CarWashState.initial(2).selectTool(Tool.HOSE)
        assertEquals(Tool.HOSE, s.selectedTool)
    }

    @Test
    fun `selecting a locked tool is a silent no-op, never a fail state`() {
        val s = CarWashState.initial(1)
        val after = s.selectTool(Tool.WAX)
        assertEquals(Tool.SPONGE, after.selectedTool)
        assertEquals(s, after)
    }

    @Test
    fun `scrubbing a cell reduces its dirt`() {
        val s = CarWashState.initial(1)
        val before = s.dirt[0]
        val after = s.scrub(0, 0)
        assertTrue(after.dirt[0] < before)
    }

    @Test
    fun `scrub returns a new state, never mutates the one it was called on`() {
        val s = CarWashState.initial(1)
        val beforeDirt = s.dirt
        s.scrub(0, 0)
        assertEquals(beforeDirt, s.dirt)
    }

    @Test
    fun `scrub off the grid is a no-op`() {
        val s = CarWashState.initial(1)
        val after = s.scrub(-1, -1)
        assertEquals(s, after)
        val after2 = s.scrub(999, 999)
        assertEquals(s, after2)
    }

    @Test
    fun `dirt can never fall below zero`() {
        var s = CarWashState.initial(1)
        // Bounded loop: scrub the same cell a fixed, generous number of
        // times -- enough to drive it to zero and past, never unbounded.
        repeat(50) {
            s = s.scrub(2, 2)
        }
        assertTrue(s.dirt.all { it >= 0f })
    }

    @Test
    fun `scrubbing removes dirt monotonically and never restores it, across every cell`() {
        var s = CarWashState.initial(3)
        // Bounded sweep: walk a fixed, finite path across every grid cell
        // exactly once with the hose (widest radius), checking after each
        // stroke that no cell's dirt increased since the previous state.
        var previous = s.dirt
        for (row in 0 until CarWashState.GRID_ROWS) {
            for (col in 0 until CarWashState.GRID_COLS) {
                s = s.scrub(row, col)
                for (i in s.dirt.indices) {
                    assertTrue(
                        "cell $i increased from ${previous[i]} to ${s.dirt[i]}",
                        s.dirt[i] <= previous[i],
                    )
                }
                previous = s.dirt
            }
        }
    }

    @Test
    fun `enough scrubbing with any unlocked tool reaches fully clean`() {
        // Bounded: a fixed number of passes over the whole grid, well more
        // than enough to drive every cell to zero at the highest starting
        // dirt value (level 5), then assert isClean -- never a while(!clean)
        // loop that could fail to terminate.
        var s = CarWashState.initial(5).selectTool(Tool.HOSE)
        repeat(20) {
            for (row in 0 until CarWashState.GRID_ROWS) {
                for (col in 0 until CarWashState.GRID_COLS) {
                    s = s.scrub(row, col)
                }
            }
        }
        assertTrue(s.isClean)
    }

    @Test
    fun `the hose covers a wider neighbourhood than the sponge`() {
        val sponge = CarWashState.initial(2).selectTool(Tool.SPONGE).scrub(3, 4)
        val hose = CarWashState.initial(2).selectTool(Tool.HOSE).scrub(3, 4)

        fun changedCount(before: CarWashState, after: CarWashState) =
            before.dirt.indices.count { after.dirt[it] < before.dirt[it] }

        val spongeChanged = changedCount(CarWashState.initial(2), sponge)
        val hoseChanged = changedCount(CarWashState.initial(2), hose)
        assertTrue(hoseChanged > spongeChanged)
    }

    @Test
    fun `strokeCount only increases when a scrub actually changes dirt`() {
        var s = CarWashState.initial(1)
        val before = s.strokeCount
        s = s.scrub(-1, -1) // off-grid, no-op
        assertEquals(before, s.strokeCount)
        s = s.scrub(0, 0)
        assertEquals(before + 1, s.strokeCount)
    }

    @Test
    fun `accumulatePlayMillis reaches the target rather than looping forever`() {
        var accumulated = 0L
        var iterations = 0
        while (accumulated < 180_000L) {
            accumulated = CarWashState.accumulatePlayMillis(accumulated, 500L, 180_000L)
            iterations += 1
            assertTrue("did not terminate", iterations <= 1_000)
        }
        assertEquals(180_000L, accumulated)
        assertEquals(360, iterations)
    }

    @Test
    fun `accumulatePlayMillis never overshoots the target on an uneven final tick`() {
        val result = CarWashState.accumulatePlayMillis(179_900L, 500L, 180_000L)
        assertEquals(180_000L, result)
    }

    @Test
    fun `accumulatePlayMillis resumes from the accumulated value, not zero`() {
        val afterOneTick = CarWashState.accumulatePlayMillis(0L, 500L, 180_000L)
        val afterASimulatedBackgroundGap = afterOneTick // value alone carries state across a suspend/resume
        val afterAnotherTick = CarWashState.accumulatePlayMillis(afterASimulatedBackgroundGap, 500L, 180_000L)
        assertEquals(1_000L, afterAnotherTick)
    }
}
