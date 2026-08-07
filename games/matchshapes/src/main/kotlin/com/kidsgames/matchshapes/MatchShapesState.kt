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

/** One hole on the board. A shape fits a hole only when [kind] matches. */
data class HoleItem(
    val id: Int,
    val kind: ShapeKind,
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
    val holes: List<HoleItem> = buildHoles(level),
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

        /** Fixed rotation cycle so results are deterministic across calls. */
        private fun rotationForIndex(index: Int): Int = ((index % 3) + 1) * 90

        private fun buildShapes(level: Int): List<ShapeItem> {
            val kinds = kindsFor(level)
            val needsRotation = level >= 4
            return kinds.mapIndexed { index, kind ->
                ShapeItem(
                    id = index,
                    kind = kind,
                    requiredRotation = if (needsRotation) rotationForIndex(index) else 0,
                )
            }
        }

        private fun buildHoles(level: Int): List<HoleItem> {
            val kinds = kindsFor(level)
            // Holes are laid out in a different order than shapes so the
            // matching kind isn't just "same position" -- the child must
            // look at shape and hole, not just line up columns.
            val shuffled = kinds.reversed()
            return shuffled.mapIndexed { index, kind -> HoleItem(id = index, kind = kind) }
        }
    }
}
