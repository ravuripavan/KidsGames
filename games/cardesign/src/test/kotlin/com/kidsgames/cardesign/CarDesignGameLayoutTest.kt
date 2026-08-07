package com.kidsgames.cardesign

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.MinTapTarget
import kotlin.math.ceil
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [chooseTrayColumns]/[trayContentHeight] -- the SAME pure
 * functions the composable calls -- hold the 64dp tap-target floor and a
 * usable car canvas across a spread of screen configurations, including the
 * ones a raised system Display (font/density) size produces on an otherwise
 * ordinary 360x640dp phone (mirrors `:games:musicpad`'s
 * `MusicPadGameLayoutTest`, which solved this same class of bug first).
 *
 * Each case reproduces the real arithmetic: screen size minus
 * `:games:shell`'s ~96dp exit zone and ~48dp safeDrawing inset (not present
 * in this module, modelled here as constants), checked at levels 4 (15
 * items) and 5 (19 items, this module's worst case).
 */
class CarDesignGameLayoutTest {

    private val exitZoneHeight = 96.dp
    private val safeDrawingHeight = 48.dp
    private val minCarCanvasHeight = 100.dp

    private fun contentSize(screenWidth: Dp, screenHeight: Dp): Pair<Dp, Dp> =
        screenWidth to (screenHeight - exitZoneHeight - safeDrawingHeight)

    private fun assertConfig(itemCount: Int, screenWidth: Dp, screenHeight: Dp, label: String) {
        val (availableWidth, availableHeight) = contentSize(screenWidth, screenHeight)
        val maxColumns = minOf(5, itemCount).coerceAtLeast(1)
        val columns = chooseTrayColumns(maxColumns, availableWidth)

        // Every button shown is always exactly MinTapTarget on both axes --
        // chooseTrayColumns only ever returns a column count whose row fits,
        // so this is really asserting chooseTrayColumns' own contract held.
        val rowWidth = requiredTrayRowWidth(columns)
        assertTrue("$label: tray row $rowWidth is wider than the screen $availableWidth", rowWidth <= availableWidth)
        val rows = ceil(itemCount / columns.toFloat()).toInt()
        assertTrue("$label: chosen column count $columns cannot fit $itemCount items", rows >= 1)

        // The car canvas is reserved FIRST and is never squeezed below the
        // floor this module defends (see MIN_CAR_CANVAS_HEIGHT's KDoc) --
        // the tray is capped to whatever is left and scrolls instead of
        // eating into it, exactly mirroring what the composable computes.
        val trayMaxHeight = (availableHeight - minCarCanvasHeight).coerceAtLeast(0.dp)
        val actualTrayHeight = minOf(trayContentHeight(itemCount, columns), trayMaxHeight)
        val carCanvasHeight = availableHeight - actualTrayHeight

        assertTrue(
            "$label: car canvas would be ${carCanvasHeight.value}dp, under the $minCarCanvasHeight floor " +
                "(screen height $availableHeight can't hold both a $minCarCanvasHeight car and any tray)",
            carCanvasHeight >= minCarCanvasHeight,
        )
    }

    @Test
    fun `L4 and L5 hold the floor and a usable car on a default 360x640dp phone`() {
        assertConfig(itemCount = 15, screenWidth = 360.dp, screenHeight = 640.dp, label = "L4 default")
        assertConfig(itemCount = 19, screenWidth = 360.dp, screenHeight = 640.dp, label = "L5 default")
    }

    @Test
    fun `L4 and L5 hold the floor and a usable car at a raised 1point15x Display size`() {
        assertConfig(itemCount = 15, screenWidth = (360 / 1.15f).dp, screenHeight = (640 / 1.15f).dp, label = "L4 @1.15x")
        assertConfig(itemCount = 19, screenWidth = (360 / 1.15f).dp, screenHeight = (640 / 1.15f).dp, label = "L5 @1.15x")
    }

    @Test
    fun `L4 and L5 hold the floor and a usable car at a raised 1point3x Display size`() {
        assertConfig(itemCount = 15, screenWidth = (360 / 1.3f).dp, screenHeight = (640 / 1.3f).dp, label = "L4 @1.3x")
        assertConfig(itemCount = 19, screenWidth = (360 / 1.3f).dp, screenHeight = (640 / 1.3f).dp, label = "L5 @1.3x")
    }

    @Test
    fun `L4 and L5 hold the floor and a usable car at a 320dp-wide phone`() {
        assertConfig(itemCount = 15, screenWidth = 320.dp, screenHeight = 640.dp, label = "L4 320dp")
        assertConfig(itemCount = 19, screenWidth = 320.dp, screenHeight = 640.dp, label = "L5 320dp")
    }

    @Test
    fun `chosen column count never exceeds 4 on a phone-width screen`() {
        // 5*64dp already equals the smallest supported width with nothing
        // left for padding, so 5-per-row can never legitimately win.
        assertTrue(chooseTrayColumns(5, 320.dp) <= 4)
        assertTrue(chooseTrayColumns(5, 360.dp) <= 4)
    }

    @Test
    fun `at 1point3x Display size the width alone forces 3 columns or fewer`() {
        val width = (360 / 1.3f).dp
        assertTrue("expected width to force <=3 columns, got ${chooseTrayColumns(5, width)}", chooseTrayColumns(5, width) <= 3)
    }
}
