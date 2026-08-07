package com.kidsgames.patterns

/**
 * Pure, Android-import-free layout math for [SequenceRow]'s scroll
 * behaviour, unit-tested without an emulator.
 *
 * The device finding this fixes: `SequenceRow` used to render one LazyRow
 * item per REPEAT GROUP and key its scroll effect on the group index, so
 * the blank slot -- which moves within a group on two fills out of every
 * three -- was never re-scrolled into view except on the fill that starts a
 * new group. It also grew a group's rendered width every fill (one more
 * slot inside the same item), so even a group-boundary scroll that WAS
 * triggered could leave the freshly-widened group's trailing edge, and
 * therefore the blank, past the viewport.
 *
 * The fix renders one LazyRow item per SLOT instead of per group (see
 * `SequenceRow`), which makes an item's width constant regardless of how
 * many fills have happened. That turns "keep the blank visible" into
 * [anchorIndexFor]: a scroll target expressed purely in slot indices, with
 * no item ever wide enough to not trivially fit inside any supported
 * viewport, and no dependency on the group index at all -- it fires (and is
 * correct) on every single fill, not just every third one.
 */
object PatternsLayout {

    /**
     * How many already-resolved slots stay visible immediately before the
     * blank when it is scrolled into view, so the blank reads as "next in
     * the row" rather than sitting flush against the viewport's leading
     * edge. Chosen small enough that even the tightest supported viewport
     * (320dp wide, minus the row's own leading/trailing content padding)
     * comfortably fits this many slots plus the blank itself -- a single
     * slot is a fixed ~68dp (see `SequenceSlot`), so three slots of context
     * plus the blank is still well under 320dp.
     */
    const val SCROLL_CONTEXT_SLOTS: Int = 1

    /**
     * The LazyRow item index to scroll to (with `scrollOffset = 0`) so that
     * [blankIndex] -- itself always the last rendered slot while a level is
     * incomplete -- ends up visible with [SCROLL_CONTEXT_SLOTS] of
     * preceding context rather than pinned to the viewport's very edge.
     * Clamped to 0 so it never asks to scroll before the start of the row.
     */
    fun anchorIndexFor(blankIndex: Int): Int = maxOf(0, blankIndex - SCROLL_CONTEXT_SLOTS)
}
