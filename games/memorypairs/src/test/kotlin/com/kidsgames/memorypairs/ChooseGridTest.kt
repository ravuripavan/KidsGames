package com.kidsgames.memorypairs

import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.MinTapTarget
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the round-4 device finding: `chooseGrid` must never hand back a
 * column count whose row count overflows the measured HEIGHT, even though
 * only width used to be checked. Every config below derives its content
 * box the same way the real chain does -- screen size minus GameHost's
 * fixed 96dp [com.kidsgames.shell.ExitZoneHeight]-equivalent exit
 * reservation and a representative ~48dp safeDrawing allowance (status +
 * nav bars) -- rather than hand-picking a content box the game would never
 * actually receive. That combined 144dp vertical reservation is the same
 * convention `:games:cardesign`'s layout test uses.
 *
 * Level 5's 20 cards is the worst case for every module at every level, so
 * every config below is checked at 20 cards; smaller card counts (this
 * module's other four levels: 6, 8, 12, 16) are always easier to fit than
 * their level-5 superset and are covered separately below for completeness,
 * not because they are expected to be tighter.
 */
class ChooseGridTest {

    private val exitZoneHeight = 96.dp
    private val safeDrawingHeight = 48.dp
    private val reservedHeight = exitZoneHeight + safeDrawingHeight

    private fun contentHeight(screenHeight: androidx.compose.ui.unit.Dp) = screenHeight - reservedHeight

    /** Every returned cell (both axes) must clear the 64dp tap-target
     *  floor, and the whole board (padding + gaps + cards) must fit inside
     *  the box with room to spare -- i.e. nothing would ever need to
     *  scroll to reach it. */
    private fun assertFitsWithoutScrolling(
        cardCount: Int,
        maxWidth: androidx.compose.ui.unit.Dp,
        maxHeight: androidx.compose.ui.unit.Dp,
    ): GridSpec {
        val spec = chooseGrid(cardCount, maxWidth, maxHeight)
        assertNotNull("expected a fitting grid for $cardCount cards in ${maxWidth}x$maxHeight", spec)
        spec!!
        val boardWidth = 24.dp + 10.dp * (spec.columns - 1) + MinTapTarget * spec.columns
        val boardHeight = 24.dp + 10.dp * (spec.rows - 1) + MinTapTarget * spec.rows
        assertTrue(
            "board width $boardWidth exceeds available $maxWidth",
            boardWidth <= maxWidth,
        )
        assertTrue(
            "board height $boardHeight exceeds available $maxHeight",
            boardHeight <= maxHeight,
        )
        return spec
    }

    @Test
    fun `level five fits at 360x640 default`() {
        val spec = assertFitsWithoutScrolling(20, 360.dp, contentHeight(640.dp))
        assertTrue(spec.columns >= 3)
    }

    @Test
    fun `level five fits at 320dp width`() {
        assertFitsWithoutScrolling(20, 320.dp, contentHeight(640.dp))
    }

    @Test
    fun `level five fits at the e2e device 426x952 default`() {
        assertFitsWithoutScrolling(20, 426.dp, contentHeight(952.dp))
    }

    @Test
    fun `level five fits at the e2e device 328x732, the raised Display size that scrolled on device`() {
        // 426x952 at 1.3x Display size, taken directly from the device
        // finding: this is the exact configuration that used to lay out
        // 3 columns x 7 rows and cut row 7 in half.
        val spec = assertFitsWithoutScrolling(20, 328.dp, contentHeight(732.dp))
        // Must no longer be the 3x7 layout that scrolled.
        assertTrue("expected more than 3 columns, got ${spec.columns}", spec.columns > 3)
    }

    @Test
    fun `level five fits at 1_15x of the e2e device`() {
        assertFitsWithoutScrolling(20, 426.dp / 1.15f, contentHeight(952.dp / 1.15f))
    }

    @Test
    fun `every level's card count fits at the e2e device raised Display size`() {
        for (cardCount in listOf(6, 8, 12, 16, 20)) {
            assertFitsWithoutScrolling(cardCount, 328.dp, contentHeight(732.dp))
        }
    }

    @Test
    fun `chosen cells always clear the 64dp tap-target floor on both axes`() {
        val configs = listOf(
            360.dp to contentHeight(640.dp),
            320.dp to contentHeight(640.dp),
            426.dp to contentHeight(952.dp),
            328.dp to contentHeight(732.dp),
        )
        for ((width, height) in configs) {
            for (cardCount in listOf(6, 8, 12, 16, 20)) {
                val spec = chooseGrid(cardCount, width, height)
                assertNotNull("no fitting grid for $cardCount cards in ${width}x$height", spec)
                spec!!
                val cellWidth = (width - 24.dp - 10.dp * (spec.columns - 1)) / spec.columns
                val cellHeight = (height - 24.dp - 10.dp * (spec.rows - 1)) / spec.rows
                assertTrue("cellWidth $cellWidth < 64dp at ${width}x$height", cellWidth >= MinTapTarget)
                assertTrue("cellHeight $cellHeight < 64dp at ${width}x$height", cellHeight >= MinTapTarget)
            }
        }
    }

    @Test
    fun `rows times columns always covers every card without ever exceeding it`() {
        for (cardCount in listOf(6, 8, 12, 16, 20)) {
            val spec = chooseGrid(cardCount, 360.dp, contentHeight(640.dp))
            assertNotNull(spec)
            spec!!
            assertTrue(spec.columns * spec.rows >= cardCount)
            assertTrue(spec.columns * (spec.rows - 1) < cardCount)
        }
    }
}
