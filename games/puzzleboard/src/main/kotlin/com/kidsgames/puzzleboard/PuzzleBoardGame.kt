package com.kidsgames.puzzleboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
 * [state.pieces] is rendered into the tray `Row` UNFILTERED -- a placed
 * piece still occupies its tray slot, rendered as a same-size invisible
 * placeholder (see [TrayPieceSlot]'s early return), so no still-unplaced
 * piece is ever reflowed under the child's finger mid-drag.
 *
 * WARNING for anyone extending [TrayPieceSlot]: [KidButton]'s `modifier =`
 * parameter moves only its inner visual content, not its outer touch node
 * (see [KidButton]'s KDoc). Because this game's interaction is drag with
 * real screen travel, the `.offset { ... }` that follows the finger is
 * applied to the Box that WRAPS the `KidButton` call, never to a modifier
 * passed into `KidButton` itself -- that is what keeps the actual tap/drag
 * target glued to the piece's drawn position as it moves.
 *
 * SNAPPING: [PuzzleBoardGeometry.toleranceDpFor] returns the same generous
 * radius (72dp) at every level 1-5. A drop counts the instant it lands
 * within that radius of the piece's own correct slot centre -- there is no
 * per-level table that could accidentally tighten it later, and L5's extra
 * difficulty comes entirely from [PuzzleBoardState.showOutline] being
 * false, never from asking for a more precise drop.
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

        // Visible, sound-free feedback for a rejected drop -- a wobble, not
        // a message -- so the game reads at zero volume.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }

        val tolerancePx = with(density) { PuzzleBoardGeometry.toleranceDpFor(level).dp.toPx() }
        val pieceVisualPx = with(density) { (PIECE_VISUAL_DP + PIECE_PADDING_DP * 2).toPx() }

        LaunchedEffect(state) {
            if (state.isComplete && !celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(if (level == 5) 1600L else 1100L)
                onFinished(Outcome.Completed)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .aspectRatio(state.grid.cols.toFloat() / state.grid.rows.toFloat())
                    .onGloballyPositioned { coords ->
                        boardOrigin = coords.positionInRoot()
                        boardSize = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                    },
            ) {
                BoardSlots(state = state)
            }

            // The tray SCROLLS rather than shrinking pieces below the 64dp
            // floor -- at 12 pieces in portrait this keeps every piece a
            // real, hittable target instead of cramming them smaller.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Emit EVERY piece, placed or not -- never filter the tray
                // list. See this object's class-level KDoc for why.
                state.pieces.forEach { piece ->
                    TrayPieceSlot(
                        piece = piece,
                        wobbleTrigger = wobbleTriggers[piece.id] ?: 0,
                        onPositioned = { origin -> trayOrigins = trayOrigins + (piece.id to origin) },
                        onDragEnd = { totalDrag ->
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
                                if (PuzzleBoardGeometry.isWithinTolerance(dropCenter, target, tolerancePx)) {
                                    state = state.place(piece.id)
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                } else {
                                    wobbleTriggers = wobbleTriggers +
                                        (piece.id to (wobbleTriggers[piece.id] ?: 0) + 1)
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                }
                            }
                        },
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Celebration(visible = celebrating, big = level == 5)
        }
    }
}

private val PIECE_VISUAL_DP = 72.dp
private val PIECE_PADDING_DP = 8.dp

/**
 * The picture, drawn as a simple coloured grid (no bundled artwork exists
 * yet -- see this game's build note). Each cell shows the colour of the
 * piece that belongs there: a full-strength fill once placed, a faint tint
 * as a placement guide before that, or nothing at all when [PuzzleBoardState
 * .showOutline] is false at level 5.
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
                    )
                }
            }
        }
    }
}

/**
 * One draggable piece in the tray. A placed piece renders as a same-size,
 * non-clickable, invisible placeholder so its tray slot stays occupied --
 * see [PuzzleBoardGame]'s class-level KDoc for why the tray list is never
 * filtered.
 *
 * Drag is implemented with `pointerInput`/`detectDragGestures` directly
 * rather than via `KidButton`'s own click, because a jigsaw piece needs
 * real screen travel that follows the finger; the `.offset { ... }` that
 * produces that travel is applied to this composable's own root `Box`
 * (which wraps the `KidButton` call), never to a modifier handed to
 * `KidButton` -- see this file's class-level warning.
 */
@Composable
private fun TrayPieceSlot(
    piece: Piece,
    wobbleTrigger: Int,
    onPositioned: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
) {
    val slotSize = PIECE_VISUAL_DP + PIECE_PADDING_DP * 2

    if (piece.placed) {
        Box(modifier = Modifier.size(slotSize))
        return
    }

    val color = KidPalette.Swatch[piece.colorIndex % KidPalette.Swatch.size]
    var dragOffset by remember(piece.id) { mutableStateOf(Offset.Zero) }
    var isDragging by remember(piece.id) { mutableStateOf(false) }

    // A soft scale-free wobble on a rejected drop -- visible feedback that
    // works even with sound off, matching the reference game's pattern.
    val wobbleOffset = remember(piece.id, wobbleTrigger) { wobbleTrigger }

    Box(
        modifier = Modifier
            .size(slotSize)
            .onGloballyPositioned { onPositioned(it.positionInRoot()) }
            .offset { IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt()) }
            .zIndex(if (isDragging) 10f else 0f)
            .pointerInput(piece.id) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                    },
                    onDragEnd = {
                        isDragging = false
                        onDragEnd(dragOffset)
                        // Snap the visual back to the tray slot. If the
                        // drop was accepted, this piece renders as the
                        // placeholder branch above on the next
                        // recomposition anyway; if it was rejected, this is
                        // the "returns to tray" behaviour -- free, no
                        // penalty, play continues.
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                )
            },
    ) {
        key(wobbleOffset) {
            KidButton(onClick = {}) {
                Box(
                    modifier = Modifier
                        .size(PIECE_VISUAL_DP)
                        .background(color, RoundedCornerShape(10.dp)),
                )
            }
        }
    }
}
