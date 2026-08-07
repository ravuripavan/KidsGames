package com.kidsgames.matchshapes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchShapesStateTest {

    @Test
    fun `level one starts with three shapes and three holes`() {
        val s = MatchShapesState(level = 1)
        assertEquals(3, s.shapes.size)
        assertEquals(3, s.holes.size)
    }

    @Test
    fun `level two starts with five shapes`() {
        assertEquals(5, MatchShapesState(level = 2).shapes.size)
    }

    @Test
    fun `level three starts with seven shapes`() {
        assertEquals(7, MatchShapesState(level = 3).shapes.size)
    }

    @Test
    fun `level four and five start with seven shapes`() {
        assertEquals(7, MatchShapesState(level = 4).shapes.size)
        assertEquals(7, MatchShapesState(level = 5).shapes.size)
    }

    @Test
    fun `every shape has exactly one hole of the same kind`() {
        for (level in 1..5) {
            val s = MatchShapesState(level)
            val shapeKinds = s.shapes.map { it.kind }.sortedBy { it.name }
            val holeKinds = s.holes.map { it.kind }.sortedBy { it.name }
            assertEquals("level $level shape/hole kinds must match 1:1", shapeKinds, holeKinds)
        }
    }

    @Test
    fun `levels one through three need no rotation`() {
        for (level in 1..3) {
            val s = MatchShapesState(level)
            assertTrue(
                "level $level should not require rotation",
                s.shapes.all { it.requiredRotation == 0 },
            )
        }
    }

    @Test
    fun `levels four and five require rotation only on shapes where rotation is visible`() {
        for (level in 4..5) {
            val s = MatchShapesState(level)
            for (shape in s.shapes) {
                if (MatchShapesState.hasVisibleRotation(shape.kind)) {
                    assertTrue(
                        "level $level shape ${shape.kind} has a visible rotation " +
                            "so it should require one",
                        shape.requiredRotation != 0,
                    )
                } else {
                    assertEquals(
                        "level $level shape ${shape.kind} has NO visible rotation " +
                            "(it looks identical at every 90-degree step), so requiring " +
                            "one would be an invisible, undiscoverable puzzle",
                        0,
                        shape.requiredRotation,
                    )
                }
            }
        }
    }

    @Test
    fun `required rotation is never assigned to a rotationally symmetric shape`() {
        // CIRCLE, SQUARE, DIAMOND and CROSS are pixel-identical at every
        // 90-degree step -- the exact defect that made the reference game's
        // rotation puzzle undiscoverable. No level may ever require rotating
        // one of these.
        val symmetric = setOf(ShapeKind.CIRCLE, ShapeKind.SQUARE, ShapeKind.DIAMOND, ShapeKind.CROSS)
        for (level in 1..5) {
            val s = MatchShapesState(level)
            for (shape in s.shapes) {
                if (shape.kind in symmetric) {
                    assertEquals(
                        "level $level: ${shape.kind} is rotationally symmetric and must " +
                            "never require rotation",
                        0,
                        shape.requiredRotation,
                    )
                }
            }
        }
    }

    @Test
    fun `level five includes visually similar shape pairs`() {
        val kinds = MatchShapesState(level = 5).shapes.map { it.kind }.toSet()
        // at least one confusable pair present together, e.g. square+rectangle
        val hasSquareRectanglePair = kinds.contains(ShapeKind.SQUARE) && kinds.contains(ShapeKind.RECTANGLE)
        val hasCircleOvalPair = kinds.contains(ShapeKind.CIRCLE) && kinds.contains(ShapeKind.OVAL)
        assertTrue(
            "level 5 must include at least one visually-similar confusable pair",
            hasSquareRectanglePair || hasCircleOvalPair,
        )
    }

    @Test
    fun `dropping a shape into its matching hole at level one marks it matched`() {
        val s = MatchShapesState(level = 1)
        val shape = s.shapes.first()
        val hole = s.holes.first { it.kind == shape.kind }
        val next = s.drop(shape.id, hole.id)
        assertTrue(next.shapes.first { it.id == shape.id }.matched)
    }

    @Test
    fun `drop does not mutate the state it was called on`() {
        val s = MatchShapesState(level = 1)
        val shape = s.shapes.first()
        val hole = s.holes.first { it.kind == shape.kind }
        s.drop(shape.id, hole.id)
        assertFalse(s.shapes.first { it.id == shape.id }.matched)
    }

    @Test
    fun `dropping a shape into a wrong-kind hole is a no-op`() {
        val s = MatchShapesState(level = 1)
        val shape = s.shapes.first()
        val wrongHole = s.holes.first { it.kind != shape.kind }
        val next = s.drop(shape.id, wrongHole.id)
        assertFalse(next.shapes.first { it.id == shape.id }.matched)
        assertEquals(s, next)
    }

    @Test
    fun `at level four dropping into the correct hole without rotating fails`() {
        val s = MatchShapesState(level = 4)
        val shape = s.shapes.first { it.requiredRotation != 0 }
        val hole = s.holes.first { it.kind == shape.kind }
        val next = s.drop(shape.id, hole.id)
        assertFalse(
            "shape needs correct rotation before it fits, even in the right hole",
            next.shapes.first { it.id == shape.id }.matched,
        )
    }

    @Test
    fun `rotate advances current rotation by ninety degrees and wraps at 360`() {
        val s = MatchShapesState(level = 4)
        val shape = s.shapes.first { it.requiredRotation != 0 }
        var current = s
        repeat(4) { current = current.rotate(shape.id) }
        assertEquals(0, current.shapes.first { it.id == shape.id }.currentRotation)
    }

    @Test
    fun `rotate does not mutate the state it was called on`() {
        val s = MatchShapesState(level = 4)
        val shape = s.shapes.first()
        s.rotate(shape.id)
        assertEquals(0, s.shapes.first { it.id == shape.id }.currentRotation)
    }

    @Test
    fun `rotating to the required rotation then dropping matches the shape`() {
        val s = MatchShapesState(level = 4)
        val shape = s.shapes.first { it.requiredRotation != 0 }
        val steps = shape.requiredRotation / 90
        var current = s
        repeat(steps) { current = current.rotate(shape.id) }
        val hole = current.holes.first { it.kind == shape.kind }
        val afterDrop = current.drop(shape.id, hole.id)
        assertTrue(afterDrop.shapes.first { it.id == shape.id }.matched)
    }

    @Test
    fun `completing every shape marks the level complete`() {
        var s = MatchShapesState(level = 1)
        for (shape in s.shapes) {
            val hole = s.holes.first { it.kind == shape.kind }
            s = s.drop(shape.id, hole.id)
        }
        assertTrue(s.isComplete)
    }

    @Test
    fun `completing a rotation level requires rotating every shape first`() {
        var s = MatchShapesState(level = 4)
        for (shape in s.shapes) {
            val steps = shape.requiredRotation / 90
            repeat(steps) { s = s.rotate(shape.id) }
            val hole = s.holes.first { it.kind == shape.kind }
            s = s.drop(shape.id, hole.id)
        }
        assertTrue(s.isComplete)
    }

    @Test
    fun `a matched shape ignores further drop and rotate calls`() {
        var s = MatchShapesState(level = 1)
        val shape = s.shapes.first()
        val hole = s.holes.first { it.kind == shape.kind }
        s = s.drop(shape.id, hole.id)
        val afterExtraDrop = s.drop(shape.id, hole.id)
        val afterExtraRotate = afterExtraDrop.rotate(shape.id)
        assertTrue(afterExtraRotate.shapes.first { it.id == shape.id }.matched)
        assertEquals(0, afterExtraRotate.shapes.first { it.id == shape.id }.currentRotation)
    }

    @Test
    fun `drop and rotate never remove a shape or reorder the list`() {
        var s = MatchShapesState(level = 4)
        val originalIds = s.shapes.map { it.id }
        val originalSize = s.shapes.size

        for (shape in s.shapes) {
            s = s.rotate(shape.id)
            assertEquals(originalSize, s.shapes.size)
            assertEquals(originalIds, s.shapes.map { it.id })
            val hole = s.holes.first { it.kind == shape.kind }
            s = s.drop(shape.id, hole.id)
            assertEquals(originalSize, s.shapes.size)
            assertEquals(originalIds, s.shapes.map { it.id })
        }
    }

    @Test
    fun `required work is non-decreasing across levels one through five`() {
        fun requiredWork(level: Int): Int {
            val s = MatchShapesState(level)
            return s.shapes.sumOf { 1 + (it.requiredRotation / 90) }
        }

        val workByLevel = (1..5).map { requiredWork(it) }
        for (i in 1 until workByLevel.size) {
            assertTrue(
                "required work dropped from level $i (${workByLevel[i - 1]}) " +
                    "to level ${i + 1} (${workByLevel[i]})",
                workByLevel[i] >= workByLevel[i - 1],
            )
        }
    }

    @Test
    fun `a hole's drawn rotation always equals the required rotation of its matching shape`() {
        for (level in 1..5) {
            val s = MatchShapesState(level)
            for (hole in s.holes) {
                val matchingShape = s.shapes.first { it.kind == hole.kind }
                assertEquals(
                    "level $level: hole for ${hole.kind} must be drawn at the same " +
                        "rotation the matching shape is required to reach, so turning the " +
                        "shape until it matches the hole is the correct strategy",
                    matchingShape.requiredRotation,
                    hole.requiredRotation,
                )
            }
        }
    }

    @Test
    fun `level four requires at least as much work as level three`() {
        fun requiredWork(level: Int): Int {
            val s = MatchShapesState(level)
            return s.shapes.sumOf { 1 + (it.requiredRotation / 90) }
        }
        assertTrue(requiredWork(4) >= requiredWork(3))
    }
}
