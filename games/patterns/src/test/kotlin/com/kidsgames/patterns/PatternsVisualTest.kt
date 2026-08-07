package com.kidsgames.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsVisualTest {

    @Test
    fun `every category within a level gets a distinct shape`() {
        for (level in 1..5) {
            val shapes = (0 until 3).map { PatternsVisual.visualFor(level, it).shape }
            assertEquals(shapes.toSet().size, shapes.size)
        }
    }

    @Test
    fun `level five's colour set differs from level four's`() {
        val level4Colors = (0 until 3).map { PatternsVisual.visualFor(4, it).color }.toSet()
        val level5Colors = (0 until 3).map { PatternsVisual.visualFor(5, it).color }.toSet()
        assertNotEquals(level4Colors, level5Colors)
    }

    @Test
    fun `level five's shape-per-category assignment differs from level four's`() {
        // Assert the PROPERTY (some category maps to a different shape),
        // not a specific example pairing.
        val differsSomewhere = (0 until 3).any {
            PatternsVisual.visualFor(4, it).shape != PatternsVisual.visualFor(5, it).shape
        }
        assertTrue(differsSomewhere)
    }

    @Test
    fun `levels one through four share the same visual set`() {
        for (category in 0 until 3) {
            val base = PatternsVisual.visualFor(1, category)
            for (level in 2..4) {
                assertEquals(base, PatternsVisual.visualFor(level, category))
            }
        }
    }
}
