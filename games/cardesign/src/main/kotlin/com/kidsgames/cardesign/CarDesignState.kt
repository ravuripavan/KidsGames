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
     * Removes the sticker nearest to ([x], [y]) if it is within
     * [REMOVE_RADIUS] fractional units -- this is the ONLY way any sticker
     * ever leaves [stickers]. A tap that lands near no sticker is a no-op,
     * never a fail state.
     */
    fun removeStickerNear(x: Float, y: Float): CarDesignState {
        val nearest = stickers.minByOrNull { s ->
            val dx = s.x - x
            val dy = s.y - y
            dx * dx + dy * dy
        } ?: return this
        val dx = nearest.x - x
        val dy = nearest.y - y
        return if (dx * dx + dy * dy <= REMOVE_RADIUS * REMOVE_RADIUS) {
            copy(stickers = stickers.filterNot { it.id == nearest.id })
        } else {
            this
        }
    }

    private fun isUnlocked(item: DesignItem): Boolean = when (item) {
        is DesignItem.PaintColor -> item.paletteIndex < paintColorCountFor(level)
        is DesignItem.WheelStyle -> item.paletteIndex < wheelStyleCountFor(level)
        is DesignItem.StickerShape -> item.paletteIndex < stickerShapeCountFor(level)
        is DesignItem.BodyShapeItem -> item.paletteIndex < bodyShapeCountFor(level)
    }

    companion object {
        private const val REMOVE_RADIUS = 0.08f

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
