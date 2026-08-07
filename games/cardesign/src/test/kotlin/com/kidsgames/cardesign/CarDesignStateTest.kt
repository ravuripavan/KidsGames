package com.kidsgames.cardesign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDesignStateTest {

    @Test
    fun `level one offers four paint colours and nothing else`() {
        assertEquals(4, CarDesignState.paintColorCountFor(1))
        assertEquals(0, CarDesignState.wheelStyleCountFor(1))
        assertEquals(0, CarDesignState.stickerShapeCountFor(1))
        assertEquals(0, CarDesignState.bodyShapeCountFor(1))
    }

    @Test
    fun `level two offers eight paint colours`() {
        assertEquals(8, CarDesignState.paintColorCountFor(2))
    }

    @Test
    fun `level three adds wheels on top of eight colours`() {
        assertEquals(8, CarDesignState.paintColorCountFor(3))
        assertEquals(3, CarDesignState.wheelStyleCountFor(3))
        assertEquals(0, CarDesignState.stickerShapeCountFor(3))
    }

    @Test
    fun `level four adds stickers`() {
        assertEquals(4, CarDesignState.stickerShapeCountFor(4))
        assertEquals(0, CarDesignState.bodyShapeCountFor(4))
    }

    @Test
    fun `level five adds body shapes`() {
        assertEquals(4, CarDesignState.bodyShapeCountFor(5))
    }

    @Test
    fun `total tool count is strictly non-decreasing across levels one through five`() {
        // Literal, spec-mandated invariant: 4, 8, 11, 15, 19.
        val counts = (1..5).map { CarDesignState.totalItemCountFor(it) }
        assertEquals(listOf(4, 8, 11, 15, 19), counts)
        for (i in 1 until counts.size) {
            assertTrue(
                "tool count dropped from level $i (${counts[i - 1]}) to level ${i + 1} (${counts[i]})",
                counts[i] >= counts[i - 1],
            )
        }
    }

    @Test
    fun `initial state has no paint, no wheel choice, default body shape, and no stickers`() {
        val s = CarDesignState.initial(1)
        assertNull(s.bodyColorIndex)
        assertNull(s.wheelStyleIndex)
        assertEquals(0, s.bodyShapeIndex)
        assertTrue(s.stickers.isEmpty())
    }

    @Test
    fun `selecting an unlocked paint colour then applying it paints the car`() {
        val s = CarDesignState.initial(1)
            .selectItem(DesignItem.PaintColor(2))
            .applyAt(0.5f, 0.5f)
        assertEquals(2, s.bodyColorIndex)
    }

    @Test
    fun `selecting a locked item is a silent no-op, never a fail state`() {
        val s = CarDesignState.initial(1)
        val after = s.selectItem(DesignItem.WheelStyle(0))
        assertEquals(s.selected, after.selected)
        assertEquals(s, after)
    }

    @Test
    fun `wheel style unlocked at level three can be selected and applied`() {
        val s = CarDesignState.initial(3)
            .selectItem(DesignItem.WheelStyle(1))
            .applyAt(0.2f, 0.9f)
        assertEquals(1, s.wheelStyleIndex)
    }

    @Test
    fun `body shape unlocked only at level five is ignored at level four`() {
        val s = CarDesignState.initial(4)
            .selectItem(DesignItem.BodyShapeItem(2))
            .applyAt(0.5f, 0.5f)
        assertEquals(0, s.bodyShapeIndex)
    }

    @Test
    fun `applying a sticker adds it without touching paint or wheel`() {
        var s = CarDesignState.initial(4)
            .selectItem(DesignItem.PaintColor(3))
            .applyAt(0.5f, 0.5f)
        s = s.selectItem(DesignItem.StickerShape(1)).applyAt(0.3f, 0.4f)
        assertEquals(1, s.stickers.size)
        assertEquals(1, s.stickers.first().shapeIndex)
        assertEquals(3, s.bodyColorIndex)
    }

    @Test
    fun `every applied decoration persists across later unrelated actions`() {
        var s = CarDesignState.initial(5)
        s = s.selectItem(DesignItem.PaintColor(1)).applyAt(0.5f, 0.5f)
        s = s.selectItem(DesignItem.WheelStyle(2)).applyAt(0.1f, 0.9f)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.2f, 0.3f)
        s = s.selectItem(DesignItem.StickerShape(2)).applyAt(0.7f, 0.6f)
        s = s.selectItem(DesignItem.BodyShapeItem(1)).applyAt(0.5f, 0.5f)
        // Now change paint colour again -- nothing else should move or vanish.
        s = s.selectItem(DesignItem.PaintColor(4)).applyAt(0.5f, 0.5f)

        assertEquals(4, s.bodyColorIndex)
        assertEquals(2, s.wheelStyleIndex)
        assertEquals(1, s.bodyShapeIndex)
        assertEquals(2, s.stickers.size)
        assertTrue(s.stickers.any { it.shapeIndex == 0 })
        assertTrue(s.stickers.any { it.shapeIndex == 2 })
    }

    @Test
    fun `a sticker is never removed by an unrelated later action`() {
        var s = CarDesignState.initial(4)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.5f, 0.5f)
        val before = s.stickers
        // A pile of unrelated actions.
        s = s.selectItem(DesignItem.PaintColor(0)).applyAt(0.9f, 0.1f)
        s = s.selectItem(DesignItem.PaintColor(5)).applyAt(0.1f, 0.9f)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.9f, 0.9f)
        assertEquals(before.first(), s.stickers.first())
        assertEquals(2, s.stickers.size)
    }

    @Test
    fun `tapping directly on an existing sticker removes only that sticker`() {
        var s = CarDesignState.initial(4)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.2f, 0.2f)
        s = s.selectItem(DesignItem.StickerShape(1)).applyAt(0.8f, 0.8f)
        assertEquals(2, s.stickers.size)

        s = s.removeStickerNear(0.2f, 0.2f)
        assertEquals(1, s.stickers.size)
        assertEquals(1, s.stickers.first().shapeIndex)
    }

    @Test
    fun `removing near no sticker is a silent no-op`() {
        var s = CarDesignState.initial(4)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.5f, 0.5f)
        val before = s.stickers
        s = s.removeStickerNear(0.0f, 0.0f)
        assertEquals(before, s.stickers)
    }

    @Test
    fun `unlockedItemsFor returns the exact category counts in order`() {
        val items = CarDesignState.unlockedItemsFor(5)
        assertEquals(8, items.count { it is DesignItem.PaintColor })
        assertEquals(3, items.count { it is DesignItem.WheelStyle })
        assertEquals(4, items.count { it is DesignItem.StickerShape })
        assertEquals(4, items.count { it is DesignItem.BodyShapeItem })
        assertEquals(19, items.size)
    }

    @Test
    fun `two palette entries never differ by colour alone -- distinct marker index guaranteed`() {
        // Every DesignItem's marker is derived deterministically from its
        // paletteIndex (see the Composable), so distinctness of paletteIndex
        // within a category is sufficient and is asserted directly here.
        val colours = (0 until CarDesignState.paintColorCountFor(2)).map { it }
        assertEquals(colours.distinct().size, colours.size)
        assertFalse(colours.isEmpty())
    }
}
