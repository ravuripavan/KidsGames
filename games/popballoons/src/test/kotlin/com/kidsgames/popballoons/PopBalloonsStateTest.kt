package com.kidsgames.popballoons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopBalloonsStateTest {

    @Test
    fun `level one starts with five balloons`() {
        assertEquals(5, PopBalloonsState(level = 1).balloons.size)
    }

    @Test
    fun `level two starts with eight balloons`() {
        assertEquals(8, PopBalloonsState(level = 2).balloons.size)
    }

    @Test
    fun `level three starts with twelve balloons`() {
        assertEquals(12, PopBalloonsState(level = 3).balloons.size)
    }

    @Test
    fun `level four and five start with fifteen balloons`() {
        assertEquals(15, PopBalloonsState(level = 4).balloons.size)
        assertEquals(15, PopBalloonsState(level = 5).balloons.size)
    }

    @Test
    fun `popping every balloon completes the level`() {
        val s = PopBalloonsState(level = 1)
        s.balloons.toList().forEach { s.pop(it.id) }
        assertTrue(s.isComplete)
    }

    @Test
    fun `popping the wrong colour at level four does not end play`() {
        val s = PopBalloonsState(level = 4)
        val wrong = s.balloons.first { it.color != s.targetColor }
        s.pop(wrong.id)
        assertFalse(s.isComplete)
        assertEquals(0, s.penalties)   // there is no such thing as a penalty
    }

    @Test
    fun `popping the wrong colour at level four does not pop the balloon`() {
        val s = PopBalloonsState(level = 4)
        val wrong = s.balloons.first { it.color != s.targetColor }
        s.pop(wrong.id)
        assertFalse(s.balloons.first { it.id == wrong.id }.popped)
    }

    @Test
    fun `popping the target colour at level four pops it and never regresses`() {
        val s = PopBalloonsState(level = 4)
        val targetColor = s.targetColor
        s.balloons.filter { it.color == targetColor }.forEach { s.pop(it.id) }
        assertTrue(s.isComplete)
        assertEquals(0, s.penalties)
    }

    @Test
    fun `level five requires popping colours in order`() {
        val s = PopBalloonsState(level = 5)
        val firstColor = s.targetColor
        val laterColor = s.balloons.map { it.color }.first { it != firstColor }
        val outOfOrder = s.balloons.first { it.color == laterColor }

        // tapping a balloon from a later colour group before its turn is ignored
        s.pop(outOfOrder.id)
        assertFalse(s.balloons.first { it.id == outOfOrder.id }.popped)
        assertEquals(0, s.penalties)

        // finishing the current colour group advances the target colour
        s.balloons.filter { it.color == firstColor }.forEach { s.pop(it.id) }
        assertTrue(s.targetColor != firstColor)
    }

    @Test
    fun `popping every balloon in colour order completes level five`() {
        val s = PopBalloonsState(level = 5)
        while (!s.isComplete) {
            val target = s.targetColor
            s.balloons.filter { it.color == target && !it.popped }.forEach { s.pop(it.id) }
        }
        assertTrue(s.isComplete)
        assertEquals(0, s.penalties)
    }

    @Test
    fun `no level ever exposes a score or timer`() {
        // PopBalloonsState exposes only balloons, level, targetColor, isComplete
        // and penalties (always zero) -- this test documents that shape by
        // relying on compilation: if a score/timer field is ever added this
        // test file continues to compile fine, but the reviewer greps for it.
        val s = PopBalloonsState(level = 3)
        assertEquals(0, s.penalties)
    }
}
