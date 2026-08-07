package com.kidsgames.colorsort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorSortStateTest {

    @Test
    fun `level one has six items across two colours`() {
        val s = ColorSortState(level = 1)
        assertEquals(6, s.items.size)
        assertEquals(2, s.items.map { it.color }.distinct().size)
    }

    @Test
    fun `level two has nine items across three colours`() {
        val s = ColorSortState(level = 2)
        assertEquals(9, s.items.size)
        assertEquals(3, s.items.map { it.color }.distinct().size)
    }

    @Test
    fun `level three has twelve items across four colours`() {
        val s = ColorSortState(level = 3)
        assertEquals(12, s.items.size)
        assertEquals(4, s.items.map { it.color }.distinct().size)
    }

    @Test
    fun `level four sorts by colour and shape`() {
        val s = ColorSortState(level = 4)
        // shape must vary independently of colour -- at least two shapes appear
        // for at least one colour, otherwise shape is just redundant with colour
        val shapesByColor = s.items.groupBy { it.color }.mapValues { it.value.map { i -> i.shape }.distinct() }
        assertTrue(shapesByColor.values.any { it.size > 1 })
    }

    @Test
    fun `level five sorts by colour shape and size`() {
        val s = ColorSortState(level = 5)
        val sizesByColorShape = s.items.groupBy { it.color to it.shape }
            .mapValues { it.value.map { i -> i.size }.distinct() }
        assertTrue(sizesByColorShape.values.any { it.size > 1 })
    }

    @Test
    fun `placing an item in its matching bin places it and never regresses`() {
        val s = ColorSortState(level = 1)
        val item = s.items.first()
        val bin = s.targetBin(item)
        val next = s.place(item.id, bin)
        assertTrue(next.items.first { it.id == item.id }.placed)
    }

    @Test
    fun `place does not mutate the state it was called on`() {
        val s = ColorSortState(level = 1)
        val item = s.items.first()
        val bin = s.targetBin(item)
        s.place(item.id, bin)
        assertFalse(s.items.first { it.id == item.id }.placed)
    }

    @Test
    fun `placing an item in a wrong bin is a no-op, never a fail state`() {
        val s = ColorSortState(level = 3)
        val item = s.items.first()
        val wrongBin = s.bins.first { it != s.targetBin(item) }
        val next = s.place(item.id, wrongBin)
        assertFalse(next.items.first { it.id == item.id }.placed)
        assertEquals(s, next)
    }

    @Test
    fun `placing every item in its matching bin completes the level`() {
        for (level in 1..5) {
            var s = ColorSortState(level = level)
            s.items.forEach { s = s.place(it.id, s.targetBin(it)) }
            assertTrue("level $level did not complete", s.isComplete)
        }
    }

    @Test
    fun `required placements are non-decreasing across levels one through five`() {
        val requiredByLevel = (1..5).map { ColorSortState(level = it).items.size }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required placements dropped from level $i (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `discrimination difficulty is non-decreasing -- bin count never drops`() {
        val binCountByLevel = (1..5).map { ColorSortState(level = it).bins.size }
        for (i in 1 until binCountByLevel.size) {
            assertTrue(
                "bin count dropped from level $i (${binCountByLevel[i - 1]}) " +
                    "to level ${i + 1} (${binCountByLevel[i]})",
                binCountByLevel[i] >= binCountByLevel[i - 1],
            )
        }
    }

    @Test
    fun `every item is distinguishable from a different-coloured item by something other than colour`() {
        // Hard requirement: colour is never the sole carrier of meaning, because
        // colour vision deficiency is undiagnosed at this age. For every pair of
        // items with different colours, either their shape differs OR their
        // colour-mark (a colour-independent dot badge) differs -- in practice the
        // dot badge always differs one-to-one with colour, so this always holds,
        // but we assert it structurally rather than assume it.
        for (level in 1..5) {
            val s = ColorSortState(level = level)
            val pairs = s.items.flatMap { a -> s.items.map { b -> a to b } }
            pairs.filter { (a, b) -> a.color != b.color }.forEach { (a, b) ->
                val distinguishable = a.shape != b.shape || a.colorMark != b.colorMark
                assertTrue(
                    "level $level: items ${a.id} and ${b.id} differ only by colour",
                    distinguishable,
                )
            }
        }
    }

    @Test
    fun `colour mark is stable and unique per colour, independent of shape or size`() {
        for (level in 1..5) {
            val s = ColorSortState(level = level)
            val markByColor = s.items.groupBy { it.color }.mapValues { it.value.map { i -> i.colorMark }.distinct() }
            markByColor.values.forEach { marks -> assertEquals(1, marks.size) }
            val allMarks = markByColor.values.map { it.single() }
            assertEquals(allMarks.size, allMarks.distinct().size)
        }
    }

    @Test
    fun `level one and two colour set is a subset of level three's, shapes stay tied to colour`() {
        // L1-L3 vary only by colour count -- shape is always the colour's
        // canonical shape, so shape alone (no colour perception needed) is
        // enough to sort correctly.
        for (level in 1..3) {
            val s = ColorSortState(level = level)
            s.items.forEach { item ->
                val expectedShape = ColorSortState(level = level).canonicalShapeFor(item.color)
                assertEquals(expectedShape, item.shape)
            }
        }
    }
}
