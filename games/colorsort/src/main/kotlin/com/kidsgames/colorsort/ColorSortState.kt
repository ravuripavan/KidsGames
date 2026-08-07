package com.kidsgames.colorsort

/**
 * Colours an item can take. Ordered; also drives [SortItem.colorMark], a
 * colour-independent dot badge (`ordinal + 1` dots) rendered on every item
 * and bin at every level. Colour vision deficiency is undiagnosed at this
 * age, so colour must never be the sole carrier of meaning -- the dot badge
 * exists purely so a colour-blind child can tell two colours apart even at
 * L4/L5, where [ItemShape] is repurposed as an independent sorting
 * dimension and can no longer be relied on as colour's stand-in.
 */
enum class ItemColor { RED, BLUE, YELLOW, GREEN }

/** Silhouette an item is drawn with. */
enum class ItemShape { CIRCLE, SQUARE, TRIANGLE, STAR }

/** Physical size an item renders at. */
enum class ItemSize { SMALL, LARGE }

/**
 * One draggable item. Immutable -- placing an item produces a new [SortItem]
 * (via [copy] inside [ColorSortState.place]), never mutates this one.
 *
 * [colorMark] is a colour-independent badge (dot count) that is *always*
 * present, at every level, regardless of whether [shape] is currently doing
 * sorting work or not. It is what keeps colour from ever being the sole
 * carrier of meaning: at L1-L3 [shape] already ties 1:1 to [color] (see
 * [ColorSortState.canonicalShapeFor]), so [colorMark] is redundant there but
 * costs nothing; at L4-L5 [shape] (and [size]) become genuine independent
 * sorting dimensions that can repeat across colours, and [colorMark] is the
 * only remaining non-hue signal for which colour an item is.
 */
data class SortItem(
    val id: Int,
    val color: ItemColor,
    val shape: ItemShape,
    val size: ItemSize,
    val placed: Boolean = false,
) {
    val colorMark: Int get() = color.ordinal + 1
}

/** A drop target. An item matches a bin per [ColorSortState.matches]. */
data class BinSpec(val color: ItemColor, val shape: ItemShape, val size: ItemSize)

/**
 * The colour-sort state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. `ColorSortGame` is the only thing that
 * touches this class from Compose.
 *
 * Fully immutable, like every state machine in this suite: [place] never
 * mutates `this`, it returns a NEW [ColorSortState]. Callers hold it in
 * `mutableStateOf` and write the result back (`state = state.place(...)`),
 * so recomposition is driven entirely by real state identity -- there is no
 * separate "version" counter.
 *
 * There is no score, no timer, no life count, and dropping an item in the
 * wrong bin is a pure no-op on state (see [place]) -- the caller plays a
 * gentle-retry cue and the item stays exactly where it was, in play, forever
 * droppable again. Nothing is ever wrong.
 *
 * - L1: 2 colours, 3 items each (6 total). Shape is the colour's fixed
 *   canonical shape, so the task is solvable by shape alone.
 * - L2: 3 colours, 3 items each (9 total). Same canonical-shape pairing.
 * - L3: 4 colours, 3 items each (12 total). Same canonical-shape pairing.
 * - L4: 2 colours x 2 shapes (4 bins), 3 items each (12 total). Shape now
 *   varies independently of colour -- sorting requires colour AND shape.
 * - L5: 2 colours x 2 shapes x 2 sizes (8 bins), 3 items each (24 total).
 *   Sorting requires colour, shape, AND size.
 *
 * Required placements (item count) and bin count are both non-decreasing
 * across levels 1-5: 6, 9, 12, 12, 24 items and 2, 3, 4, 4, 8 bins. L4 asks
 * for the same item count as L3 but strictly more discrimination (bins),
 * which is what "harder" means for this game per the design spec -- never
 * time pressure, never tighter tolerance, never a shrinking drop target.
 */
data class ColorSortState(
    val level: Int,
    val items: List<SortItem> = buildItems(level),
) {

    /** Always zero. There is no such thing as a penalty in this game. */
    val penalties: Int = 0

    val isComplete: Boolean get() = items.all { it.placed }

    /** The distinct set of bins a child must fill this level, derived from the items themselves. */
    val bins: List<BinSpec> get() = items.map { targetBin(it) }.distinct()

    /** The one bin [item] belongs in at this state's level. */
    fun targetBin(item: SortItem): BinSpec = BinSpec(
        color = item.color,
        shape = if (level >= 4) item.shape else canonicalShapeFor(item.color),
        size = if (level >= 5) item.size else ItemSize.LARGE,
    )

    /** Whether [item] belongs in [bin] at this state's level. */
    fun matches(item: SortItem, bin: BinSpec): Boolean = targetBin(item) == bin

    /**
     * Returns a NEW state with item [itemId] placed into [bin], or `this`
     * unchanged if the drop doesn't count (already placed, item not found,
     * or [bin] isn't where [itemId] belongs). A wrong drop is never a fail
     * state -- the caller plays [com.kidsgames.designkit.SoundBank.Cue.GENTLE_RETRY]
     * and the item remains exactly as draggable as before.
     */
    fun place(itemId: Int, bin: BinSpec): ColorSortState {
        val item = items.find { it.id == itemId } ?: return this
        if (item.placed) return this
        if (!matches(item, bin)) return this

        return copy(items = items.map { if (it.id == itemId) it.copy(placed = true) else it })
    }

    /** The fixed shape a colour renders as when shape is not (yet) a sorting dimension. */
    fun canonicalShapeFor(color: ItemColor): ItemShape = when (color) {
        ItemColor.RED -> ItemShape.CIRCLE
        ItemColor.BLUE -> ItemShape.SQUARE
        ItemColor.YELLOW -> ItemShape.TRIANGLE
        ItemColor.GREEN -> ItemShape.STAR
    }

    companion object {
        private const val ITEMS_PER_BIN = 3

        private fun buildItems(level: Int): List<SortItem> {
            val palette = ItemColor.entries
            val shapes = ItemShape.entries

            var nextId = 0
            fun nextItem(color: ItemColor, shape: ItemShape, size: ItemSize): SortItem =
                SortItem(id = nextId++, color = color, shape = shape, size = size)

            return when (level) {
                1, 2, 3 -> {
                    val colorCount = when (level) {
                        1 -> 2
                        2 -> 3
                        else -> 4
                    }
                    val colors = palette.take(colorCount)
                    colors.flatMap { color ->
                        val shape = canonicalShapeForColor(color)
                        (0 until ITEMS_PER_BIN).map { nextItem(color, shape, ItemSize.LARGE) }
                    }
                }
                4 -> {
                    val colors = palette.take(2)
                    val itemShapes = shapes.take(2)
                    colors.flatMap { color ->
                        itemShapes.flatMap { shape ->
                            (0 until ITEMS_PER_BIN).map { nextItem(color, shape, ItemSize.LARGE) }
                        }
                    }
                }
                else -> {
                    val colors = palette.take(2)
                    val itemShapes = shapes.take(2)
                    val sizes = ItemSize.entries
                    colors.flatMap { color ->
                        itemShapes.flatMap { shape ->
                            sizes.flatMap { size ->
                                (0 until ITEMS_PER_BIN).map { nextItem(color, shape, size) }
                            }
                        }
                    }
                }
            }
        }

        private fun canonicalShapeForColor(color: ItemColor): ItemShape = when (color) {
            ItemColor.RED -> ItemShape.CIRCLE
            ItemColor.BLUE -> ItemShape.SQUARE
            ItemColor.YELLOW -> ItemShape.TRIANGLE
            ItemColor.GREEN -> ItemShape.STAR
        }
    }
}
