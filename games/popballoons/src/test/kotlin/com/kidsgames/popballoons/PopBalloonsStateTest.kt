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
        var s = PopBalloonsState(level = 1)
        s.balloons.map { it.id }.forEach { s = s.pop(it) }
        assertTrue(s.isComplete)
    }

    @Test
    fun `pop does not mutate the state it was called on`() {
        val s = PopBalloonsState(level = 1)
        val target = s.balloons.first()
        s.pop(target.id)
        // the original reference must be untouched -- pop() is pure
        assertFalse(s.balloons.first { it.id == target.id }.popped)
    }

    @Test
    fun `popping the wrong colour at level four does not end play`() {
        val s = PopBalloonsState(level = 4)
        val wrong = s.balloons.first { it.color != s.targetColor }
        val next = s.pop(wrong.id)
        assertFalse(next.isComplete)
        assertEquals(0, next.penalties)   // there is no such thing as a penalty
    }

    @Test
    fun `popping the wrong colour at level four does not pop the balloon`() {
        val s = PopBalloonsState(level = 4)
        val wrong = s.balloons.first { it.color != s.targetColor }
        val next = s.pop(wrong.id)
        assertFalse(next.balloons.first { it.id == wrong.id }.popped)
    }

    @Test
    fun `popping the target colour at level four pops it and never regresses`() {
        var s = PopBalloonsState(level = 4)
        val targetColor = s.targetColor
        s.balloons.filter { it.color == targetColor }.map { it.id }.forEach { s = s.pop(it) }
        assertTrue(s.isComplete)
        assertEquals(0, s.penalties)
    }

    @Test
    fun `completing level four leaves no untouched balloons on screen`() {
        var s = PopBalloonsState(level = 4)
        val targetColor = s.targetColor
        s.balloons.filter { it.color == targetColor }.map { it.id }.forEach { s = s.pop(it) }
        assertTrue(s.isComplete)
        // decoy balloons must auto-clear once the target group empties --
        // completion has to look like completion, not a frozen board with
        // untouched balloons still showing.
        assertTrue(s.balloons.all { it.popped })
    }

    @Test
    fun `level four requires at least as many pops as level three`() {
        val level3Pops = PopBalloonsState(level = 3).balloons.size
        val s = PopBalloonsState(level = 4)
        val level4RequiredPops = s.balloons.count { it.color == s.targetColor }
        assertTrue(
            "level 4 required $level4RequiredPops pops, level 3 required $level3Pops; " +
                "level 4 must not be easier than level 3",
            level4RequiredPops >= level3Pops,
        )
    }

    @Test
    fun `required pops are non-decreasing across levels one through five`() {
        fun requiredPops(level: Int): Int {
            val s = PopBalloonsState(level)
            return when (level) {
                4 -> s.balloons.count { it.color == s.targetColor }
                else -> s.balloons.size
            }
        }

        val requiredByLevel = (1..5).map { requiredPops(it) }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required pops dropped from level ${i} (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `level five requires popping colours in order`() {
        val s = PopBalloonsState(level = 5)
        val firstColor = s.targetColor
        val laterColor = s.balloons.map { it.color }.first { it != firstColor }
        val outOfOrder = s.balloons.first { it.color == laterColor }

        // tapping a balloon from a later colour group before its turn is ignored
        val afterWrongTap = s.pop(outOfOrder.id)
        assertFalse(afterWrongTap.balloons.first { it.id == outOfOrder.id }.popped)
        assertEquals(0, afterWrongTap.penalties)

        // finishing the current colour group advances the target colour
        var advanced = s
        s.balloons.filter { it.color == firstColor }.map { it.id }.forEach { advanced = advanced.pop(it) }
        assertTrue(advanced.targetColor != firstColor)
    }

    @Test
    fun `popping every balloon in colour order completes level five`() {
        var s = PopBalloonsState(level = 5)
        while (!s.isComplete) {
            val target = s.targetColor
            s.balloons.filter { it.color == target && !it.popped }.map { it.id }.forEach { s = s.pop(it) }
        }
        assertTrue(s.isComplete)
        assertEquals(0, s.penalties)
    }

    @Test
    fun `level three sizes never collapse below the coerced minimum`() {
        // The Composable coerces size to at least 1.0 to protect the tap
        // target; generating sub-1.0 scales in state only produced two
        // visually-indistinguishable size classes on screen.
        val s = PopBalloonsState(level = 3)
        assertTrue(s.balloons.all { it.size >= 1f })
        // still genuinely varied, not collapsed to a single size
        assertTrue(s.balloons.map { it.size }.distinct().size > 1)
    }
}
