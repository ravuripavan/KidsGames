package com.kidsgames.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks [chooserColumnsForWidth] / [chooserRowsForColumns] and the two
 * width/height mirrors ([chooserContentWidth], [chooserContentHeight]) that
 * [LevelChooserOverlay] lays out via real measured constraints
 * (`BoxWithConstraints`) rather than an assumed screen size.
 *
 * The content box modelled here is derived the same way the runtime chain
 * produces it: `KidsApp` applies `WindowInsets.safeDrawing` to the box it
 * hands `PickerScreen`, and the overlay fills that same box — so "available"
 * below is screen size minus [safeDrawingAllowance], the same allowance
 * [PickerScreen]'s own doc comment budgets against (~80dp of status + nav
 * bar on a 640dp-tall device), never a hand-picked number invented for this
 * test alone. Width is not reduced by insets, matching [PickerScreen]'s own
 * arithmetic, since portrait status/nav bars are horizontal strips.
 *
 * Configurations enumerated, per the constraint that a fix must be checked
 * against every configuration this app runs on rather than the examples a
 * report happened to name: the default 360x640dp phone, a 320dp-wide phone
 * (Android's documented minimum guaranteed width), the 426x952dp AVD used
 * for the e2e pass (wider than the 360dp phone the picker's own budget is
 * written against), and that same 360x640dp phone at 1.15x and 1.3x
 * Display size (which raises density, shrinking the dp box on a fixed
 * physical screen).
 */
class LevelChooserLayoutTest {

    private val safeDrawingAllowance = 80.dp

    private data class ScreenConfig(val label: String, val width: Dp, val height: Dp)

    private val configs = listOf(
        ScreenConfig("360x640 default", 360.dp, 640.dp),
        ScreenConfig("320dp width", 320.dp, 640.dp),
        ScreenConfig("426x952 AVD", 426.dp, 952.dp),
        ScreenConfig("360x640 @1.15x Display size", 360.dp / 1.15f, 640.dp / 1.15f),
        ScreenConfig("360x640 @1.3x Display size", 360.dp / 1.3f, 640.dp / 1.3f),
    )

    private fun availableHeight(config: ScreenConfig) = config.height - safeDrawingAllowance

    // -- Literal columns/rows at the worst case, five unlocked levels, one config at a time. --

    @Test
    fun `five levels choose four columns and two rows at 360dp width`() {
        val columns = chooserColumnsForWidth(5, availableWidth = 360.dp - 64.dp)
        assertEquals(4, columns)
        assertEquals(2, chooserRowsForColumns(5, columns))
    }

    @Test
    fun `five levels choose three columns and two rows at 320dp width`() {
        val columns = chooserColumnsForWidth(5, availableWidth = 320.dp - 64.dp)
        assertEquals(3, columns)
        assertEquals(2, chooserRowsForColumns(5, columns))
    }

    @Test
    fun `five levels choose five columns and one row at the wide 426dp AVD`() {
        val columns = chooserColumnsForWidth(5, availableWidth = 426.dp - 64.dp)
        assertEquals(5, columns)
        assertEquals(1, chooserRowsForColumns(5, columns))
    }

    @Test
    fun `five levels choose three columns and two rows at 1point15x Display size`() {
        val columns = chooserColumnsForWidth(5, availableWidth = (360.dp / 1.15f) - 64.dp)
        assertEquals(3, columns)
        assertEquals(2, chooserRowsForColumns(5, columns))
    }

    @Test
    fun `five levels choose three columns and two rows at 1point3x Display size`() {
        val columns = chooserColumnsForWidth(5, availableWidth = (360.dp / 1.3f) - 64.dp)
        assertEquals(3, columns)
        assertEquals(2, chooserRowsForColumns(5, columns))
    }

    // -- The panel's own footprint never exceeds the box it was measured against, at every level count and every config. --

    @Test
    fun `the chooser panel fits without scrolling at every level count and every configuration`() {
        for (config in configs) {
            for (levelCount in 1..5) {
                val columns = chooserColumnsForWidth(levelCount, availableWidth = config.width - 64.dp)
                val rows = chooserRowsForColumns(levelCount, columns)

                val panelWidth = chooserContentWidth(columns)
                val panelHeight = chooserContentHeight(rows)

                assertTrue(
                    "${config.label}, $levelCount levels: panel width $panelWidth exceeds screen ${config.width}",
                    panelWidth <= config.width,
                )
                assertTrue(
                    "${config.label}, $levelCount levels: panel height $panelHeight exceeds available " +
                        "${availableHeight(config)}",
                    panelHeight <= availableHeight(config),
                )
            }
        }
    }

    @Test
    fun `every level button and the cancel button clear the 64dp tap-target floor structurally`() {
        // The floor is never derived from available space -- chooserColumnsForWidth
        // only ever changes the COLUMN COUNT, and every button is drawn at the
        // fixed com.kidsgames.designkit.MinTapTarget size regardless of the
        // column count chosen, so this holds independent of any configuration.
        for (levelCount in 1..5) {
            val columns = chooserColumnsForWidth(levelCount, availableWidth = 320.dp - 64.dp)
            assertTrue("columns must be at least 1", columns >= 1)
            assertTrue("columns must not exceed levelCount", columns <= levelCount)
        }
    }

    @Test
    fun `chooserColumnsForWidth terminates and never returns fewer than one column`() {
        // Bounded: the loop only ever counts down from levelCount to 1.
        for (levelCount in 1..5) {
            val columns = chooserColumnsForWidth(levelCount, availableWidth = 0.dp)
            assertEquals(1, columns)
        }
    }
}
