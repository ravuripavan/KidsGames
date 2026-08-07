package com.kidsgames.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsStateTest {

    @Test
    fun `level one is a two-category AB pattern`() {
        val s = PatternsState(level = 1)
        assertEquals(2, s.categories)
        assertEquals(0, s.categoryAt(0))
        assertEquals(1, s.categoryAt(1))
        assertEquals(0, s.categoryAt(2))
        assertEquals(1, s.categoryAt(3))
    }

    @Test
    fun `level three is an AABB pattern`() {
        val s = PatternsState(level = 3)
        assertEquals(2, s.categories)
        assertEquals(listOf(0, 0, 1, 1, 0, 0, 1, 1), (0 until 8).map { s.categoryAt(it) })
    }

    @Test
    fun `level four and five introduce a third category`() {
        assertEquals(3, PatternsState(level = 4).categories)
        assertEquals(3, PatternsState(level = 5).categories)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), (0 until 6).map { PatternsState(level = 5).categoryAt(it) })
    }

    @Test
    fun `choosing the correct next category advances progress`() {
        val s = PatternsState(level = 1)
        val next = s.choose(s.nextCategory)
        assertEquals(s.filledCount + 1, next.filledCount)
    }

    @Test
    fun `choose does not mutate the state it was called on`() {
        val s = PatternsState(level = 1)
        val before = s.filledCount
        s.choose(s.nextCategory)
        assertEquals(before, s.filledCount)
    }

    @Test
    fun `choosing the wrong category is a no-op and never regresses progress`() {
        var s = PatternsState(level = 1)
        // make some real progress first
        s = s.choose(s.nextCategory)
        val progressBefore = s.filledCount
        val wrongCategory = (0 until s.categories).first { it != s.nextCategory }

        val after = s.choose(wrongCategory)

        assertEquals(progressBefore, after.filledCount)
        assertFalse(after.isComplete)
    }

    @Test
    fun `completing all required fills with correct taps finishes the level`() {
        var s = PatternsState(level = 1)
        while (!s.isComplete) {
            s = s.choose(s.nextCategory)
        }
        assertTrue(s.isComplete)
        assertEquals(s.requiredFills, s.filledCount)
    }

    @Test
    fun `a wrong tap along the way still allows completion afterwards`() {
        var s = PatternsState(level = 2)
        s = s.choose(s.nextCategory) // one correct fill
        val wrongCategory = (0 until s.categories).first { it != s.nextCategory }
        s = s.choose(wrongCategory) // no-op
        while (!s.isComplete) {
            s = s.choose(s.nextCategory)
        }
        assertTrue(s.isComplete)
        assertEquals(s.requiredFills, s.filledCount)
    }

    @Test
    fun `choosing after completion is a no-op`() {
        var s = PatternsState(level = 1)
        while (!s.isComplete) {
            s = s.choose(s.nextCategory)
        }
        val completed = s
        val after = s.choose(s.nextCategory)
        assertEquals(completed.filledCount, after.filledCount)
        assertTrue(after.isComplete)
    }

    @Test
    fun `required fills are non-decreasing across levels one through five`() {
        val requiredByLevel = (1..5).map { PatternsState(level = it).requiredFills }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required fills dropped from level $i (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `visible sequence never shrinks as progress is made`() {
        var s = PatternsState(level = 4)
        var previousVisible = s.visibleLength
        while (!s.isComplete) {
            s = s.choose(s.nextCategory)
            assertTrue(s.visibleLength >= previousVisible)
            previousVisible = s.visibleLength
        }
    }

    @Test
    fun `blank index is present while incomplete and null once complete`() {
        var s = PatternsState(level = 1)
        while (!s.isComplete) {
            assertTrue(s.blankIndex != null)
            s = s.choose(s.nextCategory)
        }
        assertEquals(null, s.blankIndex)
    }
}
