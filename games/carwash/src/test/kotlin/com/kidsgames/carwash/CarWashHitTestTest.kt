package com.kidsgames.carwash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the dirt grid's hit test.
 *
 * These assert the PROPERTY the game needs -- "only the car can be washed,
 * and a blob is always drawn on the cell that cleaning it will clear" --
 * rather than re-testing the specific coordinates a device report happened
 * to mention. Two earlier fixes on this project validated against the
 * examples in a report instead of the whole input space and shipped fresh
 * defects; the one that held enumerated every combination. These enumerate.
 *
 * Every loop below runs over a fixed integer range, so none of them can fail
 * to terminate.
 */
class CarWashHitTestTest {

    private val sizes = listOf(
        400f to 700f,      // portrait phone
        1080f to 1600f,    // dense portrait
        340f to 480f,      // small/short
        1280f to 900f,     // wide
    )

    @Test
    fun `every cell centre maps back to its own cell, at every surface size`() {
        for ((w, h) in sizes) {
            for (row in 0 until CarWashState.GRID_ROWS) {
                for (col in 0 until CarWashState.GRID_COLS) {
                    val (cx, cy) = CarWashState.cellCenter(row, col, w, h)
                    val hit = CarWashState.cellAt(cx, cy, w, h)
                    assertEquals(
                        "cell ($row,$col) centre at ${w}x$h must hit its own cell",
                        row to col,
                        hit,
                    )
                }
            }
        }
    }

    @Test
    fun `a touch anywhere off the car is ignored, swept over the whole surface`() {
        // Sweep a dense lattice across the ENTIRE surface -- not just the
        // band above the roof the device report happened to name -- and
        // require the answer to agree with the car rectangle everywhere.
        val steps = 120
        for ((w, h) in sizes) {
            for (i in 0..steps) {
                for (j in 0..steps) {
                    val x = w * i / steps
                    val y = h * j / steps
                    val onCar = x >= CarWashState.CAR_LEFT * w &&
                        x < CarWashState.CAR_RIGHT * w &&
                        y >= CarWashState.CAR_TOP * h &&
                        y < CarWashState.CAR_BOTTOM * h
                    val hit = CarWashState.cellAt(x, y, w, h)
                    if (onCar) {
                        assertNotNull("($x,$y) on ${w}x$h is on the car", hit)
                    } else {
                        assertNull("($x,$y) on ${w}x$h is NOT on the car", hit)
                    }
                }
            }
        }
    }

    @Test
    fun `the regression itself - the empty bands above and below the car are dead`() {
        // The reported defect: a horizontal scrub above the roof, touching
        // no part of the vehicle, cleaned a whole row of dirt.
        val w = 400f
        val h = 700f
        for (i in 0..100) {
            val x = w * i / 100
            assertNull("above the roof must be dead", CarWashState.cellAt(x, h * 0.05f, w, h))
            assertNull("below the wheels must be dead", CarWashState.cellAt(x, h * 0.95f, w, h))
        }
        for (j in 0..100) {
            val y = h * j / 100
            assertNull("left of the car must be dead", CarWashState.cellAt(w * 0.01f, y, w, h))
            assertNull("right of the car must be dead", CarWashState.cellAt(w * 0.99f, y, w, h))
        }
    }

    @Test
    fun `every cell is reachable by some touch, so no dirt is unwashable`() {
        // The inverse hazard of the fix: shrinking the live area must not
        // strand a cell that can be drawn but never cleaned.
        for ((w, h) in sizes) {
            val reached = mutableSetOf<Pair<Int, Int>>()
            val steps = 200
            for (i in 0..steps) {
                for (j in 0..steps) {
                    CarWashState.cellAt(w * i / steps, h * j / steps, w, h)?.let { reached += it }
                }
            }
            assertEquals(
                "every cell must be reachable at ${w}x$h",
                CarWashState.GRID_ROWS * CarWashState.GRID_COLS,
                reached.size,
            )
        }
    }

    @Test
    fun `a degenerate surface is ignored rather than crashing`() {
        assertNull(CarWashState.cellAt(0f, 0f, 0f, 0f))
        assertNull(CarWashState.cellAt(10f, 10f, -5f, 100f))
    }

    @Test
    fun `the car rectangle is a sane sub-region of the surface`() {
        assertTrue(CarWashState.CAR_LEFT >= 0f && CarWashState.CAR_LEFT < CarWashState.CAR_RIGHT)
        assertTrue(CarWashState.CAR_RIGHT <= 1f)
        assertTrue(CarWashState.CAR_TOP >= 0f && CarWashState.CAR_TOP < CarWashState.CAR_BOTTOM)
        assertTrue(CarWashState.CAR_BOTTOM <= 1f)
    }
}
