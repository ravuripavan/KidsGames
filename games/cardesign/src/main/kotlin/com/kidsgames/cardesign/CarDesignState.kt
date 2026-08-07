package com.kidsgames.cardesign

/**
 * One selectable thing on the tray. Each is identified by a stable
 * [DesignItem.paletteIndex] into its own category's fixed-size list --
 * never by colour alone, since colour is never the sole carrier of meaning
 * anywhere in the suite (and this game is ABOUT colour, so it matters more
 * here). Every [PaintColor] and every [WheelStyle]/[StickerShape]/
 * [BodyShapeItem] also carries a distinct MARKER (shape/pip-pattern) drawn
 * by the Composable, so a colour-blind child can tell any two palette
 * entries apart by something other than hue.
 */
sealed class DesignItem {
    abstract val paletteIndex: Int

    data class PaintColor(override val paletteIndex: Int) : DesignItem()
    data class WheelStyle(override val paletteIndex: Int) : DesignItem()
    data class StickerShape(override val paletteIndex: Int) : DesignItem()
    data class BodyShapeItem(override val paletteIndex: Int) : DesignItem()
}

/**
 * A sticker the child has placed on the car, at a fractional position
 * (0f..1f on each axis) within the car canvas -- fractional so the same
 * placement reads correctly regardless of the canvas's measured size.
 * [id] is a monotonically increasing counter, never reused, so stickers can
 * be told apart even if two share a shape and a near-identical position.
 */
data class PlacedSticker(
    val id: Int,
    val shapeIndex: Int,
    val x: Float,
    val y: Float,
)

/**
 * Plain-Kotlin, Android-free state machine for `:games:cardesign`. No
 * Android imports, so it is unit-tested without an emulator.
 *
 * SANDBOX: there is no wrong choice, ever. [selectItem] on a locked item is a
 * silent no-op (never a fail state, matching every other sandbox module).
 * [applyAt] never fails and never removes anything: paint colour, wheel
 * style, and body shape are each a single overwritable slot (there is only
 * ever ONE current colour, ONE current wheel style, ONE current body
 * shape -- picking a new one simply changes which one is showing, not a
 * "decoration" being erased), while [stickers] is an additive LIST that only
 * ever grows via [applyAt] and only ever shrinks via the child's own
 * [removeStickerNear] tap directly on an existing sticker -- nothing else in
 * this class ever drops an entry from it.
 */
data class CarDesignState(
    val level: Int,
    val bodyColorIndex: Int? = null,
    val wheelStyleIndex: Int? = null,
    val bodyShapeIndex: Int = 0,
    val stickers: List<PlacedSticker> = emptyList(),
    val selected: DesignItem = DesignItem.PaintColor(0),
    private val nextStickerId: Int = 0,
) {

    /** Selects a different tray item. Ignored (no-op) if not unlocked at [level]. */
    fun selectItem(item: DesignItem): CarDesignState =
        if (isUnlocked(item)) copy(selected = item) else this

    /**
     * Applies the currently [selected] item at fractional canvas position
     * ([x], [y]) (only meaningful for stickers; paint/wheel/body-shape apply
     * globally regardless of where on the car the tap landed). Always
     * succeeds; there is no location on the car where an item cannot be
     * applied.
     */
    fun applyAt(x: Float, y: Float): CarDesignState = when (val item = selected) {
        is DesignItem.PaintColor -> copy(bodyColorIndex = item.paletteIndex)
        is DesignItem.WheelStyle -> copy(wheelStyleIndex = item.paletteIndex)
        is DesignItem.BodyShapeItem -> copy(bodyShapeIndex = item.paletteIndex)
        is DesignItem.StickerShape -> copy(
            stickers = stickers + PlacedSticker(nextStickerId, item.paletteIndex, x, y),
            nextStickerId = nextStickerId + 1,
        )
    }

    /**
     * Removes the sticker nearest to ([x], [y]) if it is within the given
     * per-axis tolerance -- this is the ONLY way any sticker ever leaves
     * [stickers]. A tap that lands near no sticker is a no-op, never a fail
     * state.
     *
     * [radiusX]/[radiusY] are fractional, one per axis, so the caller can
     * pass a hit zone that matches the sticker's REAL on-screen bounds
     * (the canvas is rarely square, so a single isotropic radius in
     * fractional space becomes an elongated ellipse in dp -- see
     * [nearestStickerWithin] in the Composable, which computes these from
     * the actual measured canvas size).
     */
    fun removeStickerNear(x: Float, y: Float, radiusX: Float, radiusY: Float): CarDesignState {
        val nearest = stickers
            .map { s ->
                val dx = (s.x - x) / radiusX
                val dy = (s.y - y) / radiusY
                s to (dx * dx + dy * dy)
            }
            .filter { (_, distanceSquared) -> distanceSquared <= 1f }
            .minByOrNull { (_, distanceSquared) -> distanceSquared }
            ?.first ?: return this
        return copy(stickers = stickers.filterNot { it.id == nearest.id })
    }

    /** True if some sticker's real bounds (per-axis [radiusX]/[radiusY]) contain ([x], [y]). */
    fun stickerNear(x: Float, y: Float, radiusX: Float, radiusY: Float): Boolean =
        stickers.any { s ->
            val dx = (s.x - x) / radiusX
            val dy = (s.y - y) / radiusY
            dx * dx + dy * dy <= 1f
        }

    private fun isUnlocked(item: DesignItem): Boolean = when (item) {
        is DesignItem.PaintColor -> item.paletteIndex < paintColorCountFor(level)
        is DesignItem.WheelStyle -> item.paletteIndex < wheelStyleCountFor(level)
        is DesignItem.StickerShape -> item.paletteIndex < stickerShapeCountFor(level)
        is DesignItem.BodyShapeItem -> item.paletteIndex < bodyShapeCountFor(level)
    }

    companion object {
        /**
         * The marker shape id an item's [DesignItem.paletteIndex] maps to
         * within its own category -- the SAME mapping the Composable's
         * `ItemGlyph` uses to pick which shape to draw, extracted here as a
         * pure function so distinctness can be verified without an
         * emulator (see the state test).
         */
        fun paintMarkerShapeId(index: Int): Int = index % 8

        fun wheelSpokeCount(index: Int): Int = 3 + index

        fun stickerMarkerShapeId(index: Int): Int = index % 4

        fun bodyMarkerShapeId(index: Int): Int = index % 4

        /**
         * True if a marker needs to be LIGHT to stay legible against a
         * swatch of the given approximate luminance (0f..1f, weighted-RGB) --
         * a dark marker on a dark swatch (e.g. the purple swatch, M2) is
         * under the 3:1 contrast floor, so this flips the marker colour
         * rather than keeping a single fixed dark tone everywhere. Kept for
         * callers that only ever see an opaque colour; tray buttons never
         * render one (see [isLightMarkerNeededComposited]).
         */
        fun isLightMarkerNeeded(luminance: Float): Boolean = luminance <= 0.55f

        private const val DARK_MARKER = 0x2A / 255f
        private const val LIGHT_MARKER = 0xF5 / 255f

        private fun srgbToLinear(c: Float): Float =
            if (c <= 0.03928f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

        private fun relativeLuminance(r: Float, g: Float, b: Float): Float =
            0.2126f * srgbToLinear(r) + 0.7152f * srgbToLinear(g) + 0.0722f * srgbToLinear(b)

        private fun contrastRatio(l1: Float, l2: Float): Float {
            val lighter = maxOf(l1, l2)
            val darker = minOf(l1, l2)
            return (lighter + 0.05f) / (darker + 0.05f)
        }

        private fun compositeOverWhite(base: Float, alpha: Float): Float = base * alpha + 1f * (1f - alpha)

        /**
         * True if a LIGHT marker gives a better worst-case WCAG contrast
         * ratio than a dark one, against the base swatch ([baseR]/[baseG]/
         * [baseB], each 0f..1f) alpha-blended over a white surface at every
         * alpha in [alphas] -- i.e. what a tray button ACTUALLY renders at
         * each selection state, not the opaque swatch alone (H2). Picks the
         * single direction that maximises the MINIMUM contrast across every
         * alpha given, so one marker colour stays legible whether or not the
         * item is currently selected.
         */
        fun isLightMarkerNeededComposited(baseR: Float, baseG: Float, baseB: Float, alphas: List<Float>): Boolean {
            val darkLuminance = relativeLuminance(DARK_MARKER, DARK_MARKER, DARK_MARKER)
            val lightLuminance = relativeLuminance(LIGHT_MARKER, LIGHT_MARKER, LIGHT_MARKER)
            var worstDark = Float.MAX_VALUE
            var worstLight = Float.MAX_VALUE
            for (alpha in alphas) {
                val bgLuminance = relativeLuminance(
                    compositeOverWhite(baseR, alpha),
                    compositeOverWhite(baseG, alpha),
                    compositeOverWhite(baseB, alpha),
                )
                worstDark = minOf(worstDark, contrastRatio(bgLuminance, darkLuminance))
                worstLight = minOf(worstLight, contrastRatio(bgLuminance, lightLuminance))
            }
            return worstLight > worstDark
        }

        /** 4 colours at L1, 8 from L2 onward. */
        fun paintColorCountFor(level: Int): Int = if (level <= 1) 4 else 8

        /** Wheels unlock at L3. */
        fun wheelStyleCountFor(level: Int): Int = if (level >= 3) 3 else 0

        /** Stickers unlock at L4. */
        fun stickerShapeCountFor(level: Int): Int = if (level >= 4) 4 else 0

        /** Body shapes unlock at L5. */
        fun bodyShapeCountFor(level: Int): Int = if (level >= 5) 4 else 0

        /**
         * Total tool/item count unlocked at [level], summed across every
         * category. Strictly non-decreasing across levels 1-5
         * (4, 8, 11, 15, 19) -- see the literal invariant test.
         */
        fun totalItemCountFor(level: Int): Int =
            paintColorCountFor(level) + wheelStyleCountFor(level) +
                stickerShapeCountFor(level) + bodyShapeCountFor(level)

        /** All items unlocked at [level], grouped by category, in tray order. */
        fun unlockedItemsFor(level: Int): List<DesignItem> =
            (0 until paintColorCountFor(level)).map { DesignItem.PaintColor(it) } +
                (0 until wheelStyleCountFor(level)).map { DesignItem.WheelStyle(it) } +
                (0 until stickerShapeCountFor(level)).map { DesignItem.StickerShape(it) } +
                (0 until bodyShapeCountFor(level)).map { DesignItem.BodyShapeItem(it) }

        /** Starting state for [level]: nothing painted, default body shape, no stickers, paint tool selected. */
        fun initial(level: Int): CarDesignState = CarDesignState(level = level)
    }
}
