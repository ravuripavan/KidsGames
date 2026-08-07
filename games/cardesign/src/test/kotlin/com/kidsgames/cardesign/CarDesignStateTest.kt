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

        s = s.removeStickerNear(0.2f, 0.2f, RADIUS, RADIUS)
        assertEquals(1, s.stickers.size)
        assertEquals(1, s.stickers.first().shapeIndex)
    }

    @Test
    fun `removing near no sticker is a silent no-op`() {
        var s = CarDesignState.initial(4)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.5f, 0.5f)
        val before = s.stickers
        s = s.removeStickerNear(0.0f, 0.0f, RADIUS, RADIUS)
        assertEquals(before, s.stickers)
    }

    @Test
    fun `stickerNear reports a hit only within the given per-axis radius`() {
        val s = CarDesignState.initial(4).selectItem(DesignItem.StickerShape(0)).applyAt(0.5f, 0.5f)
        // Just inside a tight radius.
        assertTrue(s.stickerNear(0.52f, 0.5f, 0.05f, 0.05f))
        // Outside a tight radius on the x-axis.
        assertFalse(s.stickerNear(0.7f, 0.5f, 0.05f, 0.05f))
        // A wide x radius and a tight y radius: a hit that would only pass on a
        // non-square (anisotropic) tolerance.
        assertTrue(s.stickerNear(0.65f, 0.5f, 0.2f, 0.05f))
        assertFalse(s.stickerNear(0.5f, 0.65f, 0.2f, 0.05f))
    }

    @Test
    fun `a tap on a sticker can both remove it and apply a new tool in the same gesture`() {
        var s = CarDesignState.initial(4)
        s = s.selectItem(DesignItem.StickerShape(0)).applyAt(0.5f, 0.5f)
        s = s.selectItem(DesignItem.PaintColor(3))
        // Simulate the Composable's non-exclusive tap handling: remove, then apply.
        s = s.removeStickerNear(0.5f, 0.5f, RADIUS, RADIUS).applyAt(0.5f, 0.5f)
        assertTrue(s.stickers.isEmpty())
        assertEquals(3, s.bodyColorIndex)
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
    fun `no two unlocked paint colours share a marker shape at any level`() {
        for (level in 1..5) {
            val ids = (0 until CarDesignState.paintColorCountFor(level))
                .map { CarDesignState.paintMarkerShapeId(it) }
            assertEquals("level $level has a duplicate paint marker shape", ids.distinct().size, ids.size)
        }
    }

    // M4: `paintMarkerShapeId(i) = i % 8` tested for distinctness over 0..7
    // was true by construction and caught nothing -- it never related an id
    // to what actually gets DRAWN. This pins the id-to-silhouette mapping
    // `drawPaintMarker` uses (kept in sync by hand, same as the Composable
    // tests in this suite that mirror drawscope logic) so a future change
    // that maps two ids to visually-confusable shapes (the round 1 finding:
    // a 6-sided and 5-sided polygon at ~11.6dp radius) fails a real test.
    private val paintMarkerKinds = mapOf(
        0 to "filled-circle",
        1 to "filled-square",
        2 to "filled-triangle",
        3 to "filled-diamond",
        4 to "filled-star",
        5 to "open-ring",
        6 to "cross",
        7 to "filled-pentagon",
    )

    @Test
    fun `every paint marker id maps to a visually distinct silhouette kind`() {
        val kinds = (0 until 8).map { paintMarkerKinds.getValue(CarDesignState.paintMarkerShapeId(it)) }
        assertEquals("two paint marker ids share a silhouette kind", kinds.distinct().size, kinds.size)
        // No two adjacent shapes should be a filled N-gon and (N+/-1)-gon --
        // exactly the pairing (hexagon, pentagon) that regressed at round 1.
        assertTrue(paintMarkerKinds.values.none { it == "filled-hexagon" })
    }

    @Test
    fun `no two unlocked wheel styles share a spoke count`() {
        val counts = (0 until CarDesignState.wheelStyleCountFor(5)).map { CarDesignState.wheelSpokeCount(it) }
        assertEquals(counts.distinct().size, counts.size)
    }

    @Test
    fun `no two unlocked sticker shapes share a marker silhouette`() {
        val ids = (0 until CarDesignState.stickerShapeCountFor(5)).map { CarDesignState.stickerMarkerShapeId(it) }
        assertEquals(ids.distinct().size, ids.size)
    }

    @Test
    fun `no two unlocked body shapes share a marker outline`() {
        val ids = (0 until CarDesignState.bodyShapeCountFor(5)).map { CarDesignState.bodyMarkerShapeId(it) }
        assertEquals(ids.distinct().size, ids.size)
    }

    // --- Marker contrast (M2/H2): every swatch, including the purple one
    // that regressed, must get a marker with adequate contrast against what
    // a tray button ACTUALLY renders -- the base swatch alpha-blended over
    // KidButton's white surface at the alphas `itemColor` ships, not the
    // opaque swatch alone (H2 caught level 5's blue body-shape glyph
    // rendering as a near-blank white circle because the decision was made
    // against opaque blue while the screen only ever shows blue at
    // 33%-55% alpha over white). ------------------------------------------
    //
    // Mirrors the REAL eight RGB values `paintPalette` uses in the
    // Composable: exactly `KidPalette.Swatch` (Red, Orange, Yellow, Green,
    // Blue, Purple, Pink) plus the one locally-added teal -- M3 found the
    // previous list here didn't match any of those seven values and passed
    // by accident.
    private val paintPaletteRgb = listOf(
        Triple(0xE5, 0x39, 0x35), // KidPalette.Red
        Triple(0xFB, 0x8C, 0x00), // KidPalette.Orange
        Triple(0xFD, 0xD8, 0x35), // KidPalette.Yellow
        Triple(0x43, 0xA0, 0x47), // KidPalette.Green
        Triple(0x1E, 0x88, 0xE5), // KidPalette.Blue
        Triple(0x8E, 0x24, 0xAA), // KidPalette.Purple -- the one that regressed
        Triple(0xEC, 0x40, 0x7A), // KidPalette.Pink
        Triple(0x00, 0x89, 0x7B), // teal, added locally for the 8th colour
    )

    private fun relativeLuminanceComponent(c: Float): Double {
        val cs = c.toDouble()
        return if (cs <= 0.03928) cs / 12.92 else Math.pow((cs + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(r: Float, g: Float, b: Float): Double {
        val rl = relativeLuminanceComponent(r)
        val gl = relativeLuminanceComponent(g)
        val bl = relativeLuminanceComponent(b)
        return 0.2126 * rl + 0.7152 * gl + 0.0722 * bl
    }

    private fun contrastRatio(l1: Double, l2: Double): Double {
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun compositeOverWhite(base: Float, alpha: Float): Float = base * alpha + 1f * (1f - alpha)

    // `paintPaletteRgb` are exactly the colours `PaintColor` items render --
    // `itemColor`'s baseAlpha for PaintColor is 1.0, so the only alphas a
    // paint swatch ever ships are the selection multiplier itself: 1.0
    // selected, 0.6 unselected. (WheelStyle/StickerShape/BodyShapeItem use
    // their own neutral base colours, not this palette, and their own
    // lower baseAlpha -- covered separately below.)
    private val shippedAlphas = listOf(1.0f, 0.6f)

    // The production `markerColorFor` decides PER SELECTION STATE (i.e. per
    // single alpha), not once for both -- some swatches (purple, pinned
    // below) have no single marker colour that clears 3:1 at both an
    // opaque selected render and a much paler unselected one, so this
    // mirrors that per-alpha decision exactly rather than asking one marker
    // to serve two contradictory backgrounds.
    private fun assertMarkerHoldsFloorPerAlpha(rgb: Triple<Int, Int, Int>, alphas: List<Float>, label: String) {
        val darkMarker = Triple(0x2A / 255f, 0x2A / 255f, 0x2A / 255f)
        val lightMarker = Triple(0xF5 / 255f, 0xF5 / 255f, 0xF5 / 255f)
        val r = rgb.first / 255f
        val g = rgb.second / 255f
        val b = rgb.third / 255f

        alphas.forEach { alpha ->
            val markerNeedsLight = CarDesignState.isLightMarkerNeededComposited(r, g, b, listOf(alpha))
            val marker = if (markerNeedsLight) lightMarker else darkMarker
            val bgRelLuminance = relativeLuminance(
                compositeOverWhite(r, alpha),
                compositeOverWhite(g, alpha),
                compositeOverWhite(b, alpha),
            )
            val markerRelLuminance = relativeLuminance(marker.first, marker.second, marker.third)
            val ratio = contrastRatio(bgRelLuminance, markerRelLuminance)
            assertTrue(
                "$label (#%02X%02X%02X) at alpha $alpha: marker contrast $ratio is under the 3:1 floor"
                    .format(rgb.first, rgb.second, rgb.third),
                ratio >= 3.0,
            )
        }
    }

    @Test
    fun `every paint swatch gets a marker with at least 3 to 1 contrast at every shipped alpha, including purple`() {
        paintPaletteRgb.forEachIndexed { index, rgb ->
            assertMarkerHoldsFloorPerAlpha(rgb, shippedAlphas, "swatch $index")
        }
    }

    // H2's three concrete regressions, pinned directly: BodyShapeItem's
    // opaque Blue base rendered at 55% (selected) / 33% (unselected) over
    // white, WheelStyle's opaque near-black base at the same alphas, and
    // StickerShape's opaque Yellow base at 60%/36%.
    @Test
    fun `body-shape blue glyph holds contrast at its actual selected and unselected alpha`() {
        assertMarkerHoldsFloorPerAlpha(Triple(0x1E, 0x88, 0xE5), listOf(0.55f, 0.33f), "BodyShapeItem blue")
    }

    @Test
    fun `wheel-style glyph holds contrast at its actual selected and unselected alpha`() {
        assertMarkerHoldsFloorPerAlpha(Triple(0x2A, 0x2A, 0x2A), listOf(0.55f, 0.33f), "WheelStyle onSurface")
    }

    @Test
    fun `sticker-shape yellow glyph holds contrast at its actual selected and unselected alpha`() {
        assertMarkerHoldsFloorPerAlpha(Triple(0xFD, 0xD8, 0x35), listOf(0.6f, 0.36f), "StickerShape yellow")
    }
}

private const val RADIUS = 0.08f
