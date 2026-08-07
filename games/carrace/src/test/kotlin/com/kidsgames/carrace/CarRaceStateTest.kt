package com.kidsgames.carrace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarRaceStateTest {

    @Test
    fun `level one and two have two and three lanes`() {
        assertEquals(2, CarRaceState.initial(1).laneCount)
        assertEquals(3, CarRaceState.initial(2).laneCount)
    }

    @Test
    fun `levels three through five keep three lanes`() {
        assertEquals(3, CarRaceState.initial(3).laneCount)
        assertEquals(3, CarRaceState.initial(4).laneCount)
        assertEquals(3, CarRaceState.initial(5).laneCount)
    }

    @Test
    fun `car starts centred on the road`() {
        val s = CarRaceState.initial(2)
        assertTrue(s.carLane in 0 until s.laneCount)
    }

    @Test
    fun `car can never leave the road by moving left repeatedly`() {
        var s = CarRaceState.initial(3)
        repeat(20) { s = s.moveLeft() }
        assertTrue(s.carLane in 0 until s.laneCount)
        assertEquals(0, s.carLane)
    }

    @Test
    fun `car can never leave the road by moving right repeatedly`() {
        var s = CarRaceState.initial(3)
        repeat(20) { s = s.moveRight() }
        assertTrue(s.carLane in 0 until s.laneCount)
        assertEquals(s.laneCount - 1, s.carLane)
    }

    @Test
    fun `switchLane clamps out-of-range lanes onto the road`() {
        val s = CarRaceState.initial(1)
        assertEquals(0, s.switchLane(-5).carLane)
        assertEquals(1, s.switchLane(99).carLane)
    }

    @Test
    fun `car stays on the road across many ticks regardless of lane switching`() {
        var s = CarRaceState.initial(5)
        val laneSequence = listOf(0, 1, 2, 0, 2, 1)
        repeat(200) { i ->
            s = s.switchLane(laneSequence[i % laneSequence.size]).tick()
            assertTrue(s.carLane in 0 until s.laneCount)
        }
    }

    @Test
    fun `hitting an obstacle never ends the run`() {
        // Drive level 3 (obstacles present) staying in the same lane for a
        // long stretch, well short of TICKS_TO_COMPLETE, so any obstacle
        // contact along the way must not have finished the run early.
        var s = CarRaceState.initial(3)
        val shortOfCompletion = CarRaceState.TICKS_TO_COMPLETE - 100
        repeat(shortOfCompletion) {
            s = s.tick()
        }
        assertFalse(s.finished)
    }

    @Test
    fun `hitting an obstacle never reduces stars or resets progress`() {
        var s = CarRaceState.initial(3)
        var previousTicks = s.ticksElapsed
        var previousStars = s.starsCollected
        repeat(100) {
            s = s.tick()
            // ticks only ever go forward
            assertTrue(s.ticksElapsed >= previousTicks)
            // stars only ever go forward -- a bump never removes a collected star
            assertTrue(s.starsCollected >= previousStars)
            previousTicks = s.ticksElapsed
            previousStars = s.starsCollected
        }
    }

    @Test
    fun `an obstacle bump increments bumpTrigger but the drive keeps going`() {
        var s = CarRaceState.initial(3)
        var sawBump = false
        repeat(400) {
            val before = s.bumpTrigger
            s = s.tick()
            if (s.bumpTrigger != before) sawBump = true
        }
        assertTrue("expected at least one obstacle bump across 400 ticks", sawBump)
        // The run is still alive/progressing regardless of any bump.
        assertTrue(s.ticksElapsed > 0)
    }

    @Test
    fun `the drive completes on elapsed ticks alone, not on stars or distance`() {
        var s = CarRaceState.initial(1)
        repeat(CarRaceState.TICKS_TO_COMPLETE) { s = s.tick() }
        assertTrue(s.finished)
    }

    @Test
    fun `completion does not require collecting any stars`() {
        // Never move into a star lane deliberately -- just tick in place --
        // and confirm completion still happens purely on elapsed time.
        var s = CarRaceState.initial(1)
        repeat(CarRaceState.TICKS_TO_COMPLETE) { s = s.tick() }
        assertTrue(s.finished)
        // Completion is unconditional on stars: zero collected is a valid,
        // completed run.
        assertTrue(s.starsCollected >= 0)
    }

    @Test
    fun `tick does not mutate the state it was called on`() {
        val s = CarRaceState.initial(2)
        val before = s.ticksElapsed
        s.tick()
        assertEquals(before, s.ticksElapsed)
    }

    @Test
    fun `road features are non-decreasing across levels one through five`() {
        // "Required work / content" for carrace is expressed as lane count
        // plus the set of road features unlocked at each level. Both must
        // never shrink from one level to the next.
        fun featureCount(level: Int): Int {
            var count = CarRaceState.laneCountFor(level)
            if (level >= 3) count += 1 // obstacles unlocked
            if (level >= 4) count += 1 // ramps unlocked
            if (level >= 5) count += 1 // branching roads unlocked
            return count
        }

        val byLevel = (1..5).map { featureCount(it) }
        for (i in 1 until byLevel.size) {
            assertTrue(
                "feature count dropped from level ${i} (${byLevel[i - 1]}) to level ${i + 1} (${byLevel[i]})",
                byLevel[i] >= byLevel[i - 1],
            )
        }
    }

    @Test
    fun `lane count itself is non-decreasing across levels`() {
        val counts = (1..5).map { CarRaceState.laneCountFor(it) }
        for (i in 1 until counts.size) {
            assertTrue(counts[i] >= counts[i - 1])
        }
    }

    @Test
    fun `level one and two never contain obstacles`() {
        var s1 = CarRaceState.initial(1)
        var s2 = CarRaceState.initial(2)
        repeat(200) {
            s1 = s1.tick()
            s2 = s2.tick()
            assertFalse(s1.rows.any { row -> row.lanes.any { it == RoadItem.OBSTACLE } })
            assertFalse(s2.rows.any { row -> row.lanes.any { it == RoadItem.OBSTACLE } })
        }
    }

    @Test
    fun `level three onward can contain obstacles`() {
        var s = CarRaceState.initial(3)
        var sawObstacle = false
        repeat(200) {
            s = s.tick()
            if (s.rows.any { row -> row.lanes.any { it == RoadItem.OBSTACLE } }) sawObstacle = true
        }
        assertTrue(sawObstacle)
    }

    @Test
    fun `ramps only appear from level four onward`() {
        var s3 = CarRaceState.initial(3)
        repeat(200) { s3 = s3.tick() }
        assertFalse(s3.rows.any { row -> row.lanes.any { it == RoadItem.RAMP } })

        var s4 = CarRaceState.initial(4)
        var sawRamp = false
        repeat(200) {
            s4 = s4.tick()
            if (s4.rows.any { row -> row.lanes.any { it == RoadItem.RAMP } }) sawRamp = true
        }
        assertTrue(sawRamp)
    }

    @Test
    fun `no row ever blocks every lane`() {
        // The car must always have somewhere to go: at least one lane per
        // row must be non-obstacle.
        var s = CarRaceState.initial(3)
        repeat(300) {
            s = s.tick()
            s.rows.forEach { row ->
                assertTrue(row.lanes.any { it != RoadItem.OBSTACLE })
            }
        }
    }

    @Test
    fun `stars only ever accumulate, never decrease`() {
        var s = CarRaceState.initial(5)
        var prev = 0
        repeat(300) {
            s = s.switchLane(it % s.laneCount).tick()
            assertTrue(s.starsCollected >= prev)
            prev = s.starsCollected
        }
    }
}
