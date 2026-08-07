package com.kidsgames.patterns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the device finding: at L5, the blank slot scrolled
 * off the right edge on two fills out of every three because the scroll
 * effect was keyed on the repeat-group index rather than something that
 * changes on every fill. [PatternsLayout.anchorIndexFor] is the pure
 * arithmetic that replaced it -- these tests simulate every fill of every
 * level and assert the blank is always within [PatternsLayout.SCROLL_CONTEXT_SLOTS]
 * of the scroll anchor, i.e. always inside the viewport, never just on group
 * boundaries.
 */
class PatternsLayoutTest {

    @Test
    fun `anchor never asks to scroll past the start of the row`() {
        for (blankIndex in 0..20) {
            assertTrue(PatternsLayout.anchorIndexFor(blankIndex) >= 0)
        }
    }

    @Test
    fun `anchor keeps the blank within a small, constant window of context`() {
        for (blankIndex in 0..20) {
            val anchor = PatternsLayout.anchorIndexFor(blankIndex)
            val distance = blankIndex - anchor
            assertTrue(
                "blank at $blankIndex is $distance slots from anchor $anchor, expected <= ${PatternsLayout.SCROLL_CONTEXT_SLOTS}",
                distance <= PatternsLayout.SCROLL_CONTEXT_SLOTS,
            )
        }
    }

    @Test
    fun `anchor is monotonically non-decreasing as the blank advances`() {
        // Every fill only ever grows blankIndex (see PatternsState.visibleLength's
        // KDoc); the scroll target must never jump backwards as a result, or the
        // row would visibly lurch left after a correct tap.
        var previousAnchor = -1
        for (blankIndex in 0..20) {
            val anchor = PatternsLayout.anchorIndexFor(blankIndex)
            assertTrue(anchor >= previousAnchor)
            previousAnchor = anchor
        }
    }

    @Test
    fun `every fill of every level keeps the blank inside the scroll window, not just group boundaries`() {
        // This is the exact defect: a version keyed on group index only moved
        // the anchor when blankIndex crossed a group boundary. Walking every
        // single fill (not just the fill that starts a new group) and
        // asserting the anchor tracks each one is what would have caught it.
        for (level in 1..5) {
            var state = PatternsState(level)
            var previousAnchor = PatternsLayout.anchorIndexFor(state.blankIndex!!)
            while (!state.isComplete) {
                state = state.choose(state.nextCategory)
                val blank = state.blankIndex
                if (blank != null) {
                    val anchor = PatternsLayout.anchorIndexFor(blank)
                    assertTrue(
                        "level $level: blank $blank not within window of anchor $anchor",
                        blank - anchor <= PatternsLayout.SCROLL_CONTEXT_SLOTS,
                    )
                    assertTrue("level $level: anchor moved backwards", anchor >= previousAnchor)
                    previousAnchor = anchor
                }
            }
        }
    }

    @Test
    fun `L5 blank is within the scroll window after every single fill, including intra-group fills`() {
        // Directly reproduces the reported observation: at L5 (group size 3),
        // fills 1 and 2 out of every 3 do not cross a group boundary.
        var state = PatternsState(level = 5)
        val groupSize = 3
        var fillsInCurrentGroup = 0
        while (!state.isComplete) {
            state = state.choose(state.nextCategory)
            fillsInCurrentGroup = (fillsInCurrentGroup + 1) % groupSize
            val blank = state.blankIndex ?: continue
            val anchor = PatternsLayout.anchorIndexFor(blank)
            assertTrue(
                "intra-group fill left the blank at $blank outside anchor $anchor's window",
                blank - anchor <= PatternsLayout.SCROLL_CONTEXT_SLOTS,
            )
        }
    }
}
