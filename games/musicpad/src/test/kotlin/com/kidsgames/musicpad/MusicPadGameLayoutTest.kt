package com.kidsgames.musicpad

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies [chooseColumns] holds the 64dp tap-target floor in BOTH width and
 * height for the worst-case screen -- L3/L4/L5, all needing 8 pads (maxColumns
 * = 4) -- across a spread of screen configurations, including the ones a
 * raised system Display (font/density) size produces on an otherwise
 * ordinary 360x640dp phone. Each case reproduces the arithmetic: screen size
 * minus [com.kidsgames.shell]'s 96dp exit zone (not present in this module,
 * modelled here as a constant) minus this module's own 12dp outer padding on
 * every side, minus whatever control rows are showing for that level.
 */
class MusicPadGameLayoutTest {

    private val padCellPadding = 12.dp
    private val minTapTarget = 64.dp
    private val exitZoneHeight = 96.dp
    private val outerPadding = 12.dp
    // One control row at the floor (64dp) with no slack -- see ControlRowHeight.
    private val controlRowHeight = 64.dp

    private fun contentSize(screenWidth: androidx.compose.ui.unit.Dp, screenHeight: androidx.compose.ui.unit.Dp) =
        (screenWidth - outerPadding * 2) to (screenHeight - exitZoneHeight - outerPadding * 2)

    private fun assertPadFloor(padCount: Int, controlRows: Int, availableWidth: androidx.compose.ui.unit.Dp, availableHeight: androidx.compose.ui.unit.Dp, label: String) {
        val maxColumns = when {
            padCount <= 4 -> 2
            padCount <= 6 -> 3
            else -> 4
        }
        val gridHeight = availableHeight - controlRowHeight * controlRows
        val columns = chooseColumns(padCount, maxColumns, availableWidth, gridHeight)
        val rows = kotlin.math.ceil(padCount / columns.toFloat()).toInt()
        val padWidth = availableWidth / columns - padCellPadding
        val padHeight = gridHeight / rows - padCellPadding

        assertTrue("$label: pad width ${padWidth.value}dp below floor", padWidth >= minTapTarget)
        assertTrue("$label: pad height ${padHeight.value}dp below floor", padHeight >= minTapTarget)
    }

    @Test
    fun `L3, L4 and L5 hold the tap-target floor in both axes on a default 360x640dp phone`() {
        val (w, h) = contentSize(360.dp, 640.dp)
        assertPadFloor(padCount = 8, controlRows = 0, availableWidth = w, availableHeight = h, label = "L3 default")
        assertPadFloor(padCount = 8, controlRows = 1, availableWidth = w, availableHeight = h, label = "L4 default")
        assertPadFloor(padCount = 8, controlRows = 2, availableWidth = w, availableHeight = h, label = "L5 default")
    }

    @Test
    fun `L3, L4 and L5 hold the tap-target floor at a raised 1point15x Display size`() {
        val (w, h) = contentSize((360 / 1.15f).dp, (640 / 1.15f).dp)
        assertPadFloor(padCount = 8, controlRows = 0, availableWidth = w, availableHeight = h, label = "L3 @1.15x")
        assertPadFloor(padCount = 8, controlRows = 1, availableWidth = w, availableHeight = h, label = "L4 @1.15x")
        assertPadFloor(padCount = 8, controlRows = 2, availableWidth = w, availableHeight = h, label = "L5 @1.15x")
    }

    @Test
    fun `L3, L4 and L5 hold the tap-target floor at a raised 1point3x Display size`() {
        val (w, h) = contentSize((360 / 1.3f).dp, (640 / 1.3f).dp)
        assertPadFloor(padCount = 8, controlRows = 0, availableWidth = w, availableHeight = h, label = "L3 @1.3x")
        assertPadFloor(padCount = 8, controlRows = 1, availableWidth = w, availableHeight = h, label = "L4 @1.3x")
        assertPadFloor(padCount = 8, controlRows = 2, availableWidth = w, availableHeight = h, label = "L5 @1.3x")
    }

    @Test
    fun `L3, L4 and L5 hold the tap-target floor at a 320dp-wide phone`() {
        val (w, h) = contentSize(320.dp, 640.dp)
        assertPadFloor(padCount = 8, controlRows = 0, availableWidth = w, availableHeight = h, label = "L3 320dp")
        assertPadFloor(padCount = 8, controlRows = 1, availableWidth = w, availableHeight = h, label = "L4 320dp")
        assertPadFloor(padCount = 8, controlRows = 2, availableWidth = w, availableHeight = h, label = "L5 320dp")
    }
}
