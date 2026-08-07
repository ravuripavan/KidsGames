package com.kidsgames.matchshapes

/**
 * The shapes a hole/shape pair can take. Ten kinds exist so level 5 can pick
 * a pool that deliberately puts visually-similar pairs on screen together
 * (SQUARE next to RECTANGLE, CIRCLE next to OVAL, TRIANGLE next to DIAMOND)
 * -- the "more discrimination" axis the spec calls for, never a smaller
 * target or a tighter tolerance.
 */
enum class ShapeKind { CIRCLE, SQUARE, TRIANGLE, STAR, HEXAGON, PENTAGON, CROSS, RECTANGLE, OVAL, DIAMOND }

/**
 * One draggable shape. Immutable -- [MatchShapesState.drop] and
 * [MatchShapesState.rotate] never mutate this in place, they return a NEW
 * [MatchShapesState] built via `copy`.
 *
 * [requiredRotation] and [currentRotation] are always one of 0/90/180/270.
 * At L1-L3 [requiredRotation] is always 0, so any shape fits its hole as
 * soon as the kind matches -- there is no rotation puzzle yet. At L4/L5 a
 * shape only fits once [currentRotation] equals [requiredRotation]; the
 * child gets there by tapping a rotate control that turns the shape exactly
 * 90 degrees per tap, so "does this fit" is a discrete, unambiguous match
 * rather than a continuous angle within some tolerance -- there is no
 * tolerance here to accidentally tighten as levels get harder.
 */
data class ShapeItem(
    val id: Int,
    val kind: ShapeKind,
    val requiredRotation: Int = 0,
    val currentRotation: Int = 0,
    val matched: Boolean = false,
)

/**
 * One hole on the board. A shape fits a hole only when [kind] matches AND,
 * at L4/L5, the shape's [ShapeItem.currentRotation] equals
 * [requiredRotation]. The hole is always DRAWN at [requiredRotation] (see
 * `HoleView`) so the hole itself shows the target orientation -- "turn the
 * shape until it looks like the hole" is the correct and sufficient
 * strategy, never a trap where the visually-matching orientation is wrong.
 */
data class HoleItem(
    val id: Int,
    val kind: ShapeKind,
    val requiredRotation: Int = 0,
)

/**
 * The match-shapes state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. The Composable is the only thing that
 * touches this class.
 *
 * Fully immutable, same shape as `PopBalloonsState`: [drop] and [rotate]
 * never mutate `this`, they return a NEW [MatchShapesState]. Callers hold it
 * in `mutableStateOf` and write the result back (`state = state.drop(...)`),
 * so Compose recomposition is driven entirely by state identity -- there is
 * no separate "version" or "trigger" counter anywhere in this module.
 *
 * There is no score, no timer, no life count, and no drop tolerance that
 * shrinks with level -- a shape either matches its hole's kind (and, at
 * L4/L5, its required rotation) or the drop is silently ignored and play
 * continues.
 *
 * - L1: 3 shapes, no rotation.
 * - L2: 5 shapes, no rotation.
 * - L3: 7 shapes, no rotation.
 * - L4: the same 7 shapes as L3, but every one now needs rotating to its
 *   [ShapeItem.requiredRotation] before it fits -- strictly more required
 *   work than L3, never less (see the non-decreasing-work test).
 * - L5: 7 shapes drawn from a pool that mixes visually-similar kinds
 *   (square/rectangle, circle/oval, triangle/diamond) and still requires
 *   rotation, so the harder step is discrimination, not tighter mechanics.
 */
data class MatchShapesState(
    val level: Int,
    val shapes: List<ShapeItem> = buildShapes(level),
    val holes: List<HoleItem> = buildHoles(shapes = buildShapes(level), level = level),
) {

    val isComplete: Boolean
        get() = shapes.all { it.matched }

    /**
     * Returns a NEW state with shape [shapeId] matched into hole [holeId],
     * or `this` unchanged if the drop doesn't count: shape already matched,
     * hole of the wrong kind, or (at L4/L5) the shape isn't rotated to its
     * required orientation yet. A caller sees "unchanged state" as the
     * signal to play `Cue.GENTLE_RETRY` and let the shape spring back --
     * there is no fail state, only a drop that didn't take.
     */
    fun drop(shapeId: Int, holeId: Int): MatchShapesState {
        val shape = shapes.find { it.id == shapeId } ?: return this
        if (shape.matched) return this
        val hole = holes.find { it.id == holeId } ?: return this
        if (hole.kind != shape.kind) return this
        if (shape.currentRotation != shape.requiredRotation) return this

        return copy(shapes = shapes.map { if (it.id == shapeId) it.copy(matched = true) else it })
    }

    /**
     * Returns a NEW state with shape [shapeId] rotated 90 degrees further
     * (wrapping at 360), or `this` unchanged if the shape is already
     * matched or doesn't exist. A matched shape stays exactly as placed --
     * rotating it further would visually un-fit a shape the child already
     * solved.
     */
    fun rotate(shapeId: Int): MatchShapesState {
        val shape = shapes.find { it.id == shapeId } ?: return this
        if (shape.matched) return this

        val nextRotation = (shape.currentRotation + 90) % 360
        return copy(shapes = shapes.map { if (it.id == shapeId) it.copy(currentRotation = nextRotation) else it })
    }

    companion object {
        private val baseKinds = listOf(
            ShapeKind.CIRCLE,
            ShapeKind.SQUARE,
            ShapeKind.TRIANGLE,
            ShapeKind.STAR,
            ShapeKind.HEXAGON,
            ShapeKind.PENTAGON,
            ShapeKind.CROSS,
        )

        private val similarKinds = listOf(
            ShapeKind.SQUARE,
            ShapeKind.RECTANGLE,
            ShapeKind.CIRCLE,
            ShapeKind.OVAL,
            ShapeKind.TRIANGLE,
            ShapeKind.DIAMOND,
            ShapeKind.STAR,
        )

        private fun kindsFor(level: Int): List<ShapeKind> = when (level) {
            1 -> baseKinds.take(3)
            2 -> baseKinds.take(5)
            3, 4 -> baseKinds
            5 -> similarKinds
            else -> baseKinds.take(3)
        }

        /**
         * The 90/180/270 rotations of [kind] that are actually visible on
         * screen, in a fixed deterministic order. A shape with 4-fold (or
         * finer) rotational symmetry -- CIRCLE, SQUARE, DIAMOND, CROSS here
         * -- looks pixel-identical at every 90-degree step, so it has NO
         * visible rotations: requiring one of those would be the exact
         * "correct-looking shape bounces back for no visible reason" defect
         * this game must never reproduce. HEXAGON, RECTANGLE and OVAL have
         * 180-degree symmetry, so only their 90/270 steps are visible.
         * TRIANGLE, STAR and PENTAGON have no rotational symmetry at any
         * 90-degree step, so all three are visible.
         */
        internal fun visibleRotations(kind: ShapeKind): List<Int> = when (kind) {
            ShapeKind.CIRCLE, ShapeKind.SQUARE, ShapeKind.DIAMOND, ShapeKind.CROSS -> emptyList()
            ShapeKind.HEXAGON, ShapeKind.RECTANGLE, ShapeKind.OVAL -> listOf(90, 270)
            ShapeKind.TRIANGLE, ShapeKind.STAR, ShapeKind.PENTAGON -> listOf(90, 180, 270)
        }

        /** Whether rotating [kind] ever changes how it looks on screen. */
        fun hasVisibleRotation(kind: ShapeKind): Boolean = visibleRotations(kind).isNotEmpty()

        /**
         * Fixed, deterministic pick of a visible rotation for [kind], cycled
         * by [index] so results vary across shapes of the same kind. Returns
         * 0 (no rotation required) for any kind with no visible rotation --
         * see [visibleRotations].
         */
        private fun requiredRotationFor(kind: ShapeKind, index: Int): Int {
            val options = visibleRotations(kind)
            if (options.isEmpty()) return 0
            return options[index % options.size]
        }

        private fun buildShapes(level: Int): List<ShapeItem> {
            val kinds = kindsFor(level)
            val needsRotation = level >= 4
            return kinds.mapIndexed { index, kind ->
                ShapeItem(
                    id = index,
                    kind = kind,
                    requiredRotation = if (needsRotation) requiredRotationFor(kind, index) else 0,
                )
            }
        }

        private fun buildHoles(shapes: List<ShapeItem>, level: Int): List<HoleItem> {
            val kinds = kindsFor(level)
            // Holes are laid out in a different order than shapes so the
            // matching kind isn't just "same position" -- the child must
            // look at shape and hole, not just line up columns.
            val shuffled = kinds.reversed()
            // Each kind appears at most once per level (see kindsFor), so the
            // hole's required rotation is simply the matching shape's --
            // drawing the hole at that rotation is what makes "turn the
            // shape until it looks like the hole" the correct strategy.
            return shuffled.mapIndexed { index, kind ->
                val requiredRotation = shapes.firstOrNull { it.kind == kind }?.requiredRotation ?: 0
                HoleItem(id = index, kind = kind, requiredRotation = requiredRotation)
            }
        }
    }
}
