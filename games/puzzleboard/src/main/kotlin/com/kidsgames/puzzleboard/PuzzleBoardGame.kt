package com.kidsgames.puzzleboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.SoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drag-and-drop jigsaw with GENEROUS, level-invariant snapping. Copies the
 * reference module's ([com.kidsgames.popballoons.PopBalloonsGame]) shape: an
 * `object : GameModule` plus a `Play` composable holding one pure, immutable
 * [PuzzleBoardState] in a single `mutableStateOf(...)`, rewritten wholesale
 * (`state = state.place(id)`) rather than mutated. There is no version or
 * trigger counter anywhere in this module.
 *
 * INVARIANT this Play composable must hold, mirroring the reference game's
 * balloon-list invariant: an unplaced piece never changes tray position.
 * [state.pieces] is rendered into the tray UNFILTERED -- a placed piece
 * still occupies its tray slot, rendered as a same-size invisible
 * placeholder (see [TrayPieceSlot]'s early return), so no still-unplaced
 * piece is ever reflowed under the child's finger mid-drag.
 *
 * DRAG RENDERING: a piece being dragged is drawn in a top-level overlay Box
 * that spans the whole screen -- NOT inside the tray's layout -- because the
 * tray wraps pieces onto several plain, unclipped `Row`s (see
 * [TrayRows]) and any container the in-flight piece is nested inside could
 * clip it before it reaches the board. The touch-handling Box inside
 * [TrayPieceSlot] stays stationary; only the drawn glyph moves, positioned
 * at `trayOrigin + dragOffset` -- the exact same arithmetic used to judge
 * the drop in [onDragEnd] below, so what is drawn and what is judged always
 * agree.
 *
 * SNAPPING: [PuzzleBoardGeometry.toleranceDpFor] returns the same generous,
 * non-shrinking 48dp FLOOR at every level 1-5; see that object's KDoc for
 * why it was lowered from a near-whole-cell 72dp. The actual radius used at
 * drop time is [PuzzleBoardGeometry.effectiveToleranceDpFor], which takes
 * that same floor and OR-s it with a fraction of the level's real measured
 * cell pitch, so low levels with big cells (L1's 2x2 grid) get proportionally
 * generous tolerance too, never just the L4/L5-sized floor.
 */
object PuzzleBoardGame : GameModule {

    override val id: String = "puzzleboard"
    override val icon: Int = R.drawable.ic_puzzleboard
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 6
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val context = LocalContext.current
        val soundBank = remember(context) { SoundBank(context) }
        val density = LocalDensity.current

        // Immutable state machine: place() returns a NEW PuzzleBoardState,
        // never mutates pieces in place. Writing the result back to this
        // `var` is what drives recomposition.
        var state by remember(level) { mutableStateOf(PuzzleBoardState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        // Measured, in root-window pixel coordinates, once layout settles.
        var boardOrigin by remember(level) { mutableStateOf(Offset.Zero) }
        var boardSize by remember(level) { mutableStateOf(Offset.Zero) }
        var trayOrigins by remember(level) { mutableStateOf(emptyMap<Int, Offset>()) }
        var rootOrigin by remember(level) { mutableStateOf(Offset.Zero) }

        // Visible, sound-free feedback for a rejected drop -- a wobble, not
        // a message -- so the game reads at zero volume.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }

        // Which piece (if any) is currently airborne, and by how much, in
        // root-window pixels. Read by the overlay below to draw the piece
        // OUTSIDE the tray's layout so nothing can clip it mid-drag.
        var draggingPieceId by remember(level) { mutableStateOf<Int?>(null) }
        var liveDragOffset by remember(level) { mutableStateOf(Offset.Zero) }

        val pieceVisualPx = with(density) { (PIECE_VISUAL_DP + PIECE_PADDING_DP * 2).toPx() }

        LaunchedEffect(state) {
            if (state.isComplete && !celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(if (level == 5) 1600L else 1100L)
                onFinished(Outcome.Completed)
            }
        }

        // [cellPitchDp] is this level's REAL on-screen cell pitch (computed
        // from the budgeted board size below), so the tolerance is generous
        // relative to the cell a child is actually aiming at -- see
        // PuzzleBoardGeometry.effectiveToleranceDpFor's KDoc.
        fun handleDragEnd(piece: Piece, totalDrag: Offset, cellPitchDp: Float) {
            val origin = trayOrigins[piece.id]
            if (origin != null && boardSize.x > 0f) {
                val dropCenter = Point(
                    x = origin.x + totalDrag.x + pieceVisualPx / 2f,
                    y = origin.y + totalDrag.y + pieceVisualPx / 2f,
                )
                val target = PuzzleBoardGeometry.targetCenter(
                    boardLeft = boardOrigin.x,
                    boardTop = boardOrigin.y,
                    boardWidth = boardSize.x,
                    boardHeight = boardSize.y,
                    rows = state.grid.rows,
                    cols = state.grid.cols,
                    row = piece.correctRow,
                    col = piece.correctCol,
                )
                val tolerancePx = with(density) {
                    PuzzleBoardGeometry.effectiveToleranceDpFor(level, cellPitchDp).dp.toPx()
                }
                if (PuzzleBoardGeometry.isWithinTolerance(dropCenter, target, tolerancePx)) {
                    state = state.place(piece.id)
                    soundBank.play(SoundBank.Cue.SUCCESS)
                } else {
                    wobbleTriggers = wobbleTriggers +
                        (piece.id to (wobbleTriggers[piece.id] ?: 0) + 1)
                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            // Everything below is sized against an EXPLICIT budget derived
            // from the real measured viewport (see this file's class KDoc
            // for the worst-case 360x640dp arithmetic this formula is built
            // to satisfy) -- board height is capped so that
            // board + tray + the bottom exit-button exclusion zone always
            // fit without scrolling, at every level.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KidPalette.Background),
            ) {
                val rows = state.grid.rows
                val cols = state.grid.cols
                val trayRowCount = kotlin.math.ceil(state.pieces.size / PIECES_PER_TRAY_ROW.toFloat()).toInt()
                val trayHeight = TRAY_SLOT_DP * trayRowCount +
                    TRAY_ROW_SPACING_DP * (trayRowCount - 1).coerceAtLeast(0) +
                    TRAY_COLUMN_PADDING_DP * 2

                val boardWidthCap = maxWidth - BOARD_PADDING_DP * 2
                val boardHeightCap = (maxHeight - trayHeight - EXIT_EXCLUSION_DP - BOARD_PADDING_DP * 2)
                    .coerceAtLeast(MIN_BOARD_CONTENT_DP)
                val boardHeightFromWidth = boardWidthCap * rows / cols
                val boardContentHeight = minOf(boardHeightFromWidth, boardHeightCap)
                val boardContentWidth = boardContentHeight * cols / rows

                // Cell pitch of THIS level's actual on-screen board, used to
                // make the drop tolerance generous relative to real cell
                // size (see PuzzleBoardGeometry.effectiveToleranceDpFor).
                val cellPitchDp = minOf(boardContentWidth / cols, boardContentHeight / rows)

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(BOARD_PADDING_DP),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(boardContentWidth)
                                .height(boardContentHeight)
                                .onGloballyPositioned { coords ->
                                    boardOrigin = coords.positionInRoot()
                                    boardSize = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                                },
                        ) {
                            BoardSlots(state = state)
                        }
                    }

                    // The tray WRAPS pieces onto several plain rows instead
                    // of scrolling -- at 12 pieces a single scrollable row
                    // is ~1250dp wide in a ~360dp viewport, so only ~3
                    // pieces are ever visible and a 4 year old has no way
                    // to discover that a horizontal swipe (which also
                    // starts a drag on whatever piece it lands on) reveals
                    // more. Wrapping shows every piece up front. Row count
                    // is budgeted above into [trayHeight], which the board
                    // cap already accounts for.
                    TrayRows(
                        pieces = state.pieces,
                        wobbleTriggers = wobbleTriggers,
                        draggingPieceId = draggingPieceId,
                        onPositioned = { id, origin -> trayOrigins = trayOrigins + (id to origin) },
                        onDragStart = { id ->
                            draggingPieceId = id
                            liveDragOffset = Offset.Zero
                        },
                        onDrag = { amount -> liveDragOffset += amount },
                        onDragEnd = { piece ->
                            val total = liveDragOffset
                            draggingPieceId = null
                            liveDragOffset = Offset.Zero
                            handleDragEnd(piece, total, cellPitchDp.value)
                        },
                        onDragCancel = {
                            draggingPieceId = null
                            liveDragOffset = Offset.Zero
                        },
                    )
                    // The Column is not filled/weighted, so whatever height
                    // remains below the tray is left blank -- that blank
                    // strip is what keeps the bottom-left exit button's
                    // touch target clear of every piece of interactive game
                    // content (see EXIT_EXCLUSION_DP above).
                }
            }

            // The in-flight piece, drawn OUTSIDE the tray's layout so it is
            // never clipped by a scrolling/wrapping container on its way to
            // the board. Position is origin + live drag delta, in this
            // Box's own local coordinate space (root coords minus this
            // Box's own root offset).
            val draggingPiece = state.pieces.firstOrNull { it.id == draggingPieceId }
            if (draggingPiece != null) {
                val origin = trayOrigins[draggingPiece.id] ?: Offset.Zero
                val localX = origin.x + liveDragOffset.x - rootOrigin.x
                val localY = origin.y + liveDragOffset.y - rootOrigin.y
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localX.toInt(), localY.toInt()) }
                        .size(PIECE_VISUAL_DP + PIECE_PADDING_DP * 2)
                        .zIndex(20f),
                    contentAlignment = Alignment.Center,
                ) {
                    PieceGlyph(piece = draggingPiece, size = PIECE_VISUAL_DP)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

// --- Layout budget -----------------------------------------------------
// Worked against the worst case named in review: 360dp wide x 640dp tall,
// minus system insets (~568dp usable height assumed by the reviewer).
// Every number below is arithmetic, not a guess -- see the KDoc at the top
// of this file and the derivation in each constant's comment.

// Piece visual + its padding = one 64dp tray slot -- exactly the minimum
// touch target, never below it.
private val PIECE_VISUAL_DP = 56.dp
private val PIECE_PADDING_DP = 4.dp
private val TRAY_SLOT_DP = PIECE_VISUAL_DP + PIECE_PADDING_DP * 2 // 64dp

// Width check (360dp phone, worst case): 4 slots * 64dp + 3 gaps * 10dp +
// 2 * 16dp column padding = 256 + 30 + 32 = 318dp <= 360dp, with 10dp of
// slack inside the 328dp content box (360 - 2*16) -- the previous 4-per-row
// layout at 88dp slots needed 432dp and clipped the 4th slot; this fits.
private const val PIECES_PER_TRAY_ROW = 4
private val TRAY_ROW_SPACING_DP = 10.dp
private val TRAY_COLUMN_PADDING_DP = 16.dp

// Board content is padded 16dp on every side (matches the tray's own
// padding, and is 8dp tighter than the old 24dp so more height budget is
// left for the tray + exit zone).
private val BOARD_PADDING_DP = 16.dp

// Bottom-left exit control is 64dp + 16dp margin (GameHost). Reserved as
// dead space below the tray so no piece can ever land under the child's
// thumb when they're reaching for Exit.
private val EXIT_EXCLUSION_DP = 80.dp

// Board-slot glyph size. The old 28dp glyph made its pip row do
// 28*0.12 = 3.4dp dots -- uncountable, and drawn in the SAME ink as the
// shape under them (0.4-over-0.4 composited to a smudge). 36dp gives the
// pip formula below (>= 8dp per pip, floor-enforced) real room, and every
// budgeted level above leaves a cell far bigger than 36dp (smallest is
// L3/L4/L5's ~212/3 ~= 70dp cell, minus 2*3dp padding = 64dp).
private val BOARD_SLOT_GLYPH_DP = 36.dp

// Never let the board collapse to nothing on a pathologically short
// viewport -- this is a floor, not a target; real budgets above always
// clear it comfortably in the reviewed worst case (see final report).
private val MIN_BOARD_CONTENT_DP = 120.dp

// Height check, worst case 360x640dp with ~568dp usable (640 minus system
// insets), board padded 16dp each side, tray padded/spaced as above,
// exit zone reserved at the bottom:
//
//   L1 (2x2, 4 pieces, 1 tray row):
//     tray = 1*64 + 0*10 + 2*16 = 96dp
//     board cap = 568 - 96 - 80 - 32 = 360dp; width-driven height
//       (328dp content width * rows/cols = 328*1) = 328dp <= cap -> 328dp
//     total = 328 + 32(pad) + 96(tray) + 80(exit) = 536dp <= 568dp
//
//   L2 (2x3, 6 pieces, 2 tray rows: 4+2):
//     tray = 2*64 + 1*10 + 32 = 170dp
//     board cap = 568 - 170 - 80 - 32 = 286dp; width-driven height
//       (328 * 2/3) = 219dp <= cap -> 219dp
//     total = 219 + 32 + 170 + 80 = 501dp <= 568dp
//
//   L3 (3x3, 9 pieces, 3 tray rows: 4+4+1):
//     tray = 3*64 + 2*10 + 32 = 244dp
//     board cap = 568 - 244 - 80 - 32 = 212dp; width-driven height
//       (328 * 1) = 328dp > cap -> capped to 212dp (width shrinks to
//       212dp too, since ratio is 1:1; board is narrower than the tray,
//       centered -- this is the level the old 24dp-padded, width-only
//       layout overflowed to 688dp on)
//     total = 212 + 32 + 244 + 80 = 568dp <= 568dp (fits exactly)
//
//   L4/L5 (3x4, 12 pieces, 3 tray rows: 4+4+4):
//     tray = 3*64 + 2*10 + 32 = 244dp
//     board cap = 568 - 244 - 80 - 32 = 212dp; width-driven height
//       (328 * 3/4) = 246dp > cap -> capped to 212dp; width becomes
//       212 * 4/3 = 283dp (< 328dp content width, centered)
//     total = 212 + 32 + 244 + 80 = 568dp <= 568dp (fits exactly)
//
// Every level fits inside 568dp of usable height with zero scrolling, and
// the 80dp exit zone stays free of board/tray content at every level.

/**
 * The picture, drawn as a simple coloured grid (no bundled artwork exists
 * yet -- see this game's build note). Each cell shows the colour of the
 * piece that belongs there as a background tint (hidden at L5, see
 * [PuzzleBoardState.showOutline]), but ALSO always shows that piece's
 * colour-independent glyph -- faint, in a neutral ink, regardless of level
 * or outline visibility. That glyph is L5's actual discoverable signal: a
 * child can match a piece's shape+pip-count against a slot's faint glyph
 * and form an intention before ever touching it, which the colour tint
 * alone could never provide once five of the twelve swatch colours repeat.
 */
@Composable
private fun BoardSlots(state: PuzzleBoardState) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (row in 0 until state.grid.rows) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                for (col in 0 until state.grid.cols) {
                    val piece = state.pieces.find { it.correctRow == row && it.correctCol == col }
                    val swatchColor = piece?.let { KidPalette.Swatch[it.colorIndex % KidPalette.Swatch.size] }
                        ?: KidPalette.Surface
                    val fill = when {
                        piece?.placed == true -> swatchColor
                        state.showOutline -> swatchColor.copy(alpha = 0.22f)
                        else -> KidPalette.Surface
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(3.dp)
                            .background(fill, RoundedCornerShape(8.dp))
                            .border(2.dp, KidPalette.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (piece != null && piece.placed != true) {
                            PieceGlyph(
                                piece = piece,
                                size = BOARD_SLOT_GLYPH_DP,
                                ink = KidPalette.OnSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps [pieces] onto multiple plain (unclipped, non-scrolling) rows so
 * every piece is visible without a swipe. Placed pieces still occupy their
 * slot (see [TrayPieceSlot]'s placeholder branch) -- never filtered, so tray
 * position never changes underneath a still-unplaced piece.
 */
@Composable
private fun TrayRows(
    pieces: List<Piece>,
    wobbleTriggers: Map<Int, Int>,
    draggingPieceId: Int?,
    onPositioned: (Int, Offset) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Piece) -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TRAY_COLUMN_PADDING_DP),
        verticalArrangement = Arrangement.spacedBy(TRAY_ROW_SPACING_DP),
    ) {
        pieces.chunked(PIECES_PER_TRAY_ROW).forEach { rowPieces ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TRAY_ROW_SPACING_DP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowPieces.forEach { piece ->
                    TrayPieceSlot(
                        piece = piece,
                        wobbleTrigger = wobbleTriggers[piece.id] ?: 0,
                        isAirborne = draggingPieceId == piece.id,
                        onPositioned = { origin -> onPositioned(piece.id, origin) },
                        onDragStart = { onDragStart(piece.id) },
                        onDrag = onDrag,
                        onDragEnd = { onDragEnd(piece) },
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
    }
}

/**
 * One tray slot. A placed piece renders as a same-size, non-clickable,
 * invisible placeholder so its tray slot stays occupied -- see
 * [PuzzleBoardGame]'s class-level KDoc for why the tray list is never
 * filtered. A piece currently [isAirborne] ALSO renders as an empty
 * placeholder here -- its glyph is drawn instead by the caller's top-level
 * overlay, outside this composable's (potentially clipping) container, so
 * it is never sliced off mid-drag.
 *
 * The pointer-input Box stays stationary; only the drawn glyph moves (in
 * the overlay). This keeps the touch target exactly where the finger
 * landed for the whole gesture -- Compose's drag detector tracks the
 * pointer by id, not by this Box's screen position, so nothing is lost by
 * not visually offsetting it.
 */
@Composable
private fun TrayPieceSlot(
    piece: Piece,
    wobbleTrigger: Int,
    isAirborne: Boolean,
    onPositioned: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val slotSize = PIECE_VISUAL_DP + PIECE_PADDING_DP * 2

    if (piece.placed) {
        Box(modifier = Modifier.size(slotSize).onGloballyPositioned { onPositioned(it.positionInRoot()) })
        return
    }

    // A real, visible scale-bounce on a rejected drop -- not just a
    // recomposition key -- so a miss is unmistakable even with sound off.
    // Mirrors games/popballoons's BalloonView wobble exactly.
    val wobble = remember(piece.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    suspend fun playWobble() {
        wobble.animateTo(0.85f, animationSpec = tween(90))
        wobble.animateTo(1.08f, animationSpec = tween(90))
        wobble.animateTo(1f, animationSpec = tween(120))
    }
    LaunchedEffect(wobbleTrigger) {
        if (wobbleTrigger > 0) playWobble()
    }

    Box(
        modifier = Modifier
            .size(slotSize)
            .onGloballyPositioned { onPositioned(it.positionInRoot()) }
            .pointerInput(piece.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!isAirborne) {
            // A tap is a 4 year old's most likely first action with any new
            // piece -- give it the same wobble a rejected drop gets, rather
            // than doing nothing at all.
            KidButton(onClick = { scope.launch { playWobble() } }, modifier = Modifier.scale(wobble.value)) {
                PieceGlyph(piece = piece, size = PIECE_VISUAL_DP)
            }
        }
    }
}

/**
 * A piece's full visual: its swatch colour PLUS a colour-independent glyph
 * (shape outline + pip-count dots), so no two pieces are ever
 * indistinguishable even when their colours repeat (only 7 swatches for up
 * to 12 pieces). Reused for the tray piece, the in-flight overlay piece,
 * and (at a smaller, fainter scale) the board slot hint -- the same glyph
 * drawn in both places is what lets a child match piece to slot.
 */
@Composable
private fun PieceGlyph(piece: Piece, size: androidx.compose.ui.unit.Dp, ink: Color? = null) {
    val fillColor = ink ?: KidPalette.Swatch[piece.colorIndex % KidPalette.Swatch.size]
    // When [ink] is passed (the board-slot hint), the pips must NOT be
    // drawn in that same translucent ink -- 0.4-over-0.4 alpha composites
    // to an unreadable smudge, which was the exact bug: a child holding a
    // circle piece saw three identical faint circles at L4/L5. Punching
    // the pips out in the opaque board background colour instead makes
    // them read as distinct holes, genuinely contrasting against the
    // shape fill underneath regardless of level or tint.
    val pipColor = if (ink != null) KidPalette.Background else KidPalette.OnSurface.copy(alpha = 0.5f)
    // Old formula (size * 0.12f) gave a 28dp board glyph a 3.4dp pip --
    // uncountable at arm's length. Floor every pip at 8dp (the same floor
    // games/colorsort uses for its dot badge) regardless of glyph size.
    val pipDiameter = maxOf(size * 0.16f, 8.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            when (piece.shape) {
                PieceShape.CIRCLE -> drawCircle(color = fillColor)
                PieceShape.SQUARE -> drawRoundRect(
                    color = fillColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.toPx() * 0.15f),
                )
                PieceShape.TRIANGLE -> drawPath(trianglePath(size.toPx()), color = fillColor)
                PieceShape.DIAMOND -> drawPath(diamondPath(size.toPx()), color = fillColor)
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(piece.pipCount) {
                Box(
                    modifier = Modifier
                        .size(pipDiameter)
                        .background(pipColor, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

private fun trianglePath(sizePx: Float): Path = Path().apply {
    moveTo(sizePx / 2f, 0f)
    lineTo(sizePx, sizePx)
    lineTo(0f, sizePx)
    close()
}

private fun diamondPath(sizePx: Float): Path = Path().apply {
    moveTo(sizePx / 2f, 0f)
    lineTo(sizePx, sizePx / 2f)
    lineTo(sizePx / 2f, sizePx)
    lineTo(0f, sizePx / 2f)
    close()
}
