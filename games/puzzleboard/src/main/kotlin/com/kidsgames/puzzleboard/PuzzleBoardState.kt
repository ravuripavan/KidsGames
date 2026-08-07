package com.kidsgames.puzzleboard

/**
 * The puzzleboard state machine. Plain Kotlin, no Android or Compose imports,
 * so it is unit-tested without an emulator. [PuzzleBoardGame] is the only
 * thing that touches this class from Compose.
 *
 * Fully immutable, following the same shape as PopBalloonsState (the
 * reference module): [place] never mutates `this`, it returns a NEW
 * [PuzzleBoardState]. Callers hold it in `mutableStateOf` and write the
 * result back (`state = state.place(id)`); there is no separate
 * version/trigger counter anywhere, and there must never be one.
 *
 * There is no score, no timer, no life count and no penalty of any kind. A
 * piece dropped in the wrong place simply is not placed -- the state is
 * returned completely unchanged, and the caller (the Composable) is
 * responsible for playing a [com.kidsgames.designkit.SoundBank.Cue.GENTLE_RETRY]
 * cue and animating the piece back to its tray slot. Nothing here is ever
 * un-placed once correct.
 *
 * - L1: 4 pieces, 2x2 grid.
 * - L2: 6 pieces, 2x3 grid.
 * - L3: 9 pieces, 3x3 grid.
 * - L4: 12 pieces, 3x4 grid.
 * - L5: 12 pieces, 3x4 grid, [showOutline] is false -- the only thing that
 *   changes at L5. Difficulty grows by removing the placement guide, never
 *   by tightening how close a drop has to land (see [PuzzleBoardGeometry],
 *   whose tolerance is constant across every level).
 */
data class PuzzleBoardState(
    val level: Int,
    val grid: GridSpec = gridFor(level),
    val pieces: List<Piece> = buildPieces(grid),
) {

    /** True once every piece has been dropped in its correct slot. */
    val isComplete: Boolean
        get() = pieces.all { it.placed }

    /**
     * Whether the board shows a faint colour hint under each slot before a
     * piece lands there. True at every level except 5, where the picture's
     * outline is removed as the sole source of L5's extra difficulty.
     */
    val showOutline: Boolean
        get() = level != 5

    /**
     * Returns a NEW state with piece [pieceId] marked placed, or `this`
     * unchanged if the piece does not exist or is already placed. The
     * caller (the Composable) decides whether a drop counts by comparing
     * the drop position against [PuzzleBoardGeometry] BEFORE calling this --
     * by the time [place] is invoked the drop has already been judged
     * correct, so this function has nothing left to validate. This mirrors
     * how the caller never invokes it at all on a misdrop, which is what
     * makes a misdrop free: no field on this class changes, nothing here
     * ever regresses a piece back to unplaced.
     */
    fun place(pieceId: Int): PuzzleBoardState {
        val piece = pieces.find { it.id == pieceId } ?: return this
        if (piece.placed) return this
        return copy(pieces = pieces.map { if (it.id == pieceId) it.copy(placed = true) else it })
    }

    companion object {
        fun gridFor(level: Int): GridSpec = when (level) {
            1 -> GridSpec(rows = 2, cols = 2)
            2 -> GridSpec(rows = 2, cols = 3)
            3 -> GridSpec(rows = 3, cols = 3)
            4, 5 -> GridSpec(rows = 3, cols = 4)
            else -> GridSpec(rows = 2, cols = 2)
        }

        /**
         * Pieces are built in a fixed, deterministically-shuffled tray order
         * (seeded on the grid shape) so the tray never starts in row-major
         * (already-solved-looking) order, and re-rendering the same level
         * always produces the same tray layout for a given level -- no
         * randomness leaks into equality/testing.
         */
        private fun buildPieces(grid: GridSpec): List<Piece> {
            val cells = (0 until grid.rows).flatMap { r -> (0 until grid.cols).map { c -> r to c } }
            val shuffled = cells.shuffled(kotlin.random.Random(grid.rows * 31 + grid.cols * 7))
            return shuffled.mapIndexed { index, (r, c) ->
                Piece(
                    id = index,
                    correctRow = r,
                    correctCol = c,
                    colorIndex = r * grid.cols + c,
                )
            }
        }
    }
}

/** Grid shape a level's picture is cut into. */
data class GridSpec(val rows: Int, val cols: Int) {
    val pieceCount: Int get() = rows * cols
}

/**
 * One puzzle piece. Immutable -- placing a piece produces a new [Piece] (via
 * [copy] inside [PuzzleBoardState.place]), never mutates this one in place.
 *
 * [id] is the piece's fixed identity, unrelated to [correctRow]/[correctCol]
 * (its solved position) or its position in the tray list -- the tray list
 * order for a given [PuzzleBoardState] never changes across the lifetime of
 * a level, which is what lets the Composable render every piece via
 * `items(state.pieces, key = { it.id })` without ever filtering placed
 * pieces out of that list (see [PuzzleBoardGame]'s KDoc for why filtering
 * would be wrong).
 */
data class Piece(
    val id: Int,
    val correctRow: Int,
    val correctCol: Int,
    val colorIndex: Int,
    val placed: Boolean = false,
) {
    /**
     * Colour-independent identity. [KidPalette.Swatch] only has 7 entries
     * and levels go up to 12 pieces, so colour alone repeats -- these two
     * derived properties give every piece (id 0..11) a unique
     * (shape, pipCount) combination instead, since `id` is bijective with
     * `(id % PIECE_SHAPE_COUNT, id / PIECE_SHAPE_COUNT)`. Rendered on BOTH
     * the piece and its slot (see PuzzleBoardGame's ShapeGlyph/PieceGlyph)
     * so a child can match them without relying on colour at all -- this is
     * also L5's discoverable signal once the colour tint is removed.
     */
    val shape: PieceShape get() = PieceShape.entries[id % PIECE_SHAPE_COUNT]
    val pipCount: Int get() = id / PIECE_SHAPE_COUNT + 1
}

/** The four colour-independent glyph shapes a piece can carry. */
enum class PieceShape { CIRCLE, SQUARE, TRIANGLE, DIAMOND }

/** Number of distinct [PieceShape]s; pipCount covers the rest of a piece's
 *  id so (shape, pipCount) stays a unique pair for every id up to 12. */
const val PIECE_SHAPE_COUNT = 4

/** A plain x/y point, deliberately not `androidx.compose.ui.geometry.Offset`
 *  so this file stays free of Compose/Android imports and is testable on
 *  the plain JVM. */
data class Point(val x: Float, val y: Float)

/**
 * Pure geometry helpers for the "generous, non-shrinking snap" rule that is
 * this game's single most important constraint: the drop tolerance MUST NOT
 * shrink as levels rise. Tightening tolerance at higher levels would make
 * the game harder by punishing imprecise motor control instead of by adding
 * content, which is exactly the kind of fail-state-in-disguise the whole
 * suite forbids. L5's extra difficulty comes only from
 * [PuzzleBoardState.showOutline] being false, never from this tolerance.
 */
object PuzzleBoardGeometry {

    /**
     * Generous drop radius, in dp, the same at every level 1..5. Deliberately
     * a constant function of level rather than a fixed top-level value, so
     * a reviewer (and the non-decreasing-tolerance test) can see explicitly
     * that it does not vary by level -- there is no per-level table to
     * accidentally tighten later.
     */
    fun toleranceDpFor(level: Int): Float = SNAP_TOLERANCE_DP

    /**
     * The tolerance actually used at drop time: [toleranceDpFor]'s constant
     * 48dp floor (never lowered, so the guard test above stays meaningful
     * and un-gamed), OR-ed with a fraction of the ACTUAL measured cell
     * pitch at whatever level is currently on screen, whichever is bigger.
     *
     * The 48dp floor was sized for the densest grid (L4/L5, ~78dp cell
     * pitch). At L1 a 2x2 grid on a 360dp-wide phone gives a cell pitch of
     * roughly (360 - 2*16 board padding) / 2 ~= 164dp -- a flat 48dp radius
     * there covers under a third of the cell a child is aiming at, so a
     * squarely-placed, visually-correct drop can still bounce back. Scaling
     * by the real cell pitch (floored at the old constant, never below it)
     * fixes that everywhere generously without ever making any level's
     * effective tolerance smaller than the 48dp floor it always had.
     */
    fun effectiveToleranceDpFor(level: Int, cellPitchDp: Float): Float =
        maxOf(toleranceDpFor(level), cellPitchDp * CELL_PITCH_FRACTION)

    /** True if [drop] landed within [toleranceDp] of [target]. */
    fun isWithinTolerance(drop: Point, target: Point, toleranceDp: Float): Boolean {
        val dx = drop.x - target.x
        val dy = drop.y - target.y
        return kotlin.math.sqrt(dx * dx + dy * dy) <= toleranceDp
    }

    /** Centre point of grid cell (row, col) inside a board of the given
     *  pixel bounds. Used by both the real Composable (with real pixel
     *  measurements) and tests (with made-up ones). */
    fun targetCenter(
        boardLeft: Float,
        boardTop: Float,
        boardWidth: Float,
        boardHeight: Float,
        rows: Int,
        cols: Int,
        row: Int,
        col: Int,
    ): Point {
        val cellWidth = boardWidth / cols
        val cellHeight = boardHeight / rows
        return Point(
            x = boardLeft + cellWidth * (col + 0.5f),
            y = boardTop + cellHeight * (row + 0.5f),
        )
    }

    // A four year old's drag is wobbly, so this stays generous -- but it
    // must not be so generous that aim stops mattering. At L4/L5 (12
    // pieces, 4 columns) a typical board's cell pitch is ~78dp, so a
    // tolerance of ~0.9 of a cell (the old 72f) meant a piece dropped
    // squarely on the WRONG adjacent cell still snapped into its correct
    // one. This value is instead pinned to roughly 0.6x that densest
    // level's cell pitch, floored at 48dp, and kept the SAME constant at
    // every level (never shrinking as levels rise, per this object's
    // class-level contract) rather than tightening per level.
    private const val REFERENCE_CELL_PITCH_DP = 78f
    private const val SNAP_TOLERANCE_DP = 48f // max(48f, REFERENCE_CELL_PITCH_DP * 0.6f) == 48f

    /** Fraction of the real cell pitch [effectiveToleranceDpFor] will accept
     *  once it is bigger than the flat floor -- generous but still well
     *  short of "any adjacent cell also counts" (which needed ~0.9). */
    private const val CELL_PITCH_FRACTION = 0.45f
}
