package com.kidsgames.colorsort

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Drag items into the bin whose colour AND shape (and, at L5, size) match.
 *
 * Follows the same shape as `:games:popballoons`, the reference module: a
 * plain-Kotlin, pure state machine ([ColorSortState]) held in a single
 * `mutableStateOf`, mutated only by writing back the NEW state object
 * `place()` returns. There is no separate "version"/"trigger" counter
 * anywhere in this module.
 *
 * INVARIANT this Play composable holds: an item the child has not placed
 * never changes screen position (its grid cell, not its drag offset -- a
 * live drag obviously moves the visual under the finger, that's the game).
 * The full (unfiltered) item list is always rendered for exactly this
 * reason -- placed items stay in the grid as invisible, non-clickable,
 * same-size placeholders instead of being removed, so no still-unplaced
 * item's grid cell ever shifts because a sibling finished first.
 *
 * LAYOUT: bins lay out as plain, non-scrolling WRAPPED rows (like
 * `games/puzzleboard`'s tray) rather than a single `Row` with per-bin
 * `weight(1f)` -- that per-child weight is exactly the pattern this module
 * used to ship, and it was broken: `weight(fill = true)` hands a child FIXED
 * constraints that silently coerce away any `.size()` on it (so 8 bins in
 * one row squeezed to ~30dp regardless of what `.size(MinTapTarget)` said).
 * Wrapping bins into rows of [BINS_PER_ROW] keeps every bin at a real,
 * uncoerced [MinTapTarget] in an ordinary, unclipped `Row`.
 *
 * Items wrap the same way into rows of [ITEMS_PER_ROW], but the item SECTION
 * (not any individual item) carries a container-level `Modifier.weight(1f)`
 * in the outer `Column` so it claims whatever height the bin block didn't,
 * and scrolls if its own content -- L5's 24 items -- is taller than that.
 * This is a different `weight` use than the broken per-child pattern above:
 * it sizes one big scrollable region, not dozens of individual touch
 * targets, so nothing gets coerced below [MinTapTarget]. It also does not
 * revive the old `LazyVerticalGrid` defect (clipping the in-flight dragged
 * item as it crosses the grid's edge on the way to a bin rendered above it)
 * because the dragged item is drawn entirely OUTSIDE this scrolling
 * container -- see DRAG RENDERING below -- so nothing about the scroll
 * viewport's clip bounds can ever slice it. See the dp arithmetic in each
 * layout function's KDoc, and [BOTTOM_EXCLUSION_HEIGHT] for why the
 * scrollable region reserves dead space at its end.
 *
 * DRAG RENDERING: exactly `games/puzzleboard`'s solution. The item actually
 * being dragged is drawn in a top-level overlay `Box` spanning the whole
 * screen, OUTSIDE every clipping/scrolling parent, positioned at
 * `itemOrigin + liveDragOffset` -- the SAME arithmetic [handleDragEnd] below
 * uses to judge the drop, so what is drawn and what is judged can never
 * diverge. The touch-handling node inside [ItemSlot] stays stationary;
 * Compose's drag detector tracks the pointer by id, not by that node's
 * screen position, so nothing is lost by not visually offsetting it.
 *
 * DROP TOLERANCE: never `rect.contains(center)` against the raw bin rect --
 * that makes tolerance a function of bin size, which SHRINKS as levels add
 * more (smaller) bins, exactly the tightening-with-level the design spec
 * forbids. [ColorSortGeometry.marginDpFor] supplies a constant, level-
 * independent margin inflating every bin rect before the drop test runs.
 *
 * Colour is never the sole carrier of meaning: every [SortItem] carries a
 * [SortItem.colorMark] (a dot-count badge, independent of level) alongside
 * its [SortItem.color], and at L1-L3 [SortItem.shape] is additionally tied
 * 1:1 to colour via [ColorSortState.canonicalShapeFor]. A colour-blind child
 * can sort correctly at every level using shape and/or the dot badge alone.
 */
object ColorSortGame : GameModule {

    override val id: String = "colorsort"
    override val icon: Int = R.drawable.ic_colorsort
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val context = LocalContext.current
        val soundBank = remember(context) { SoundBank(context) }
        // SoundBank owns a SoundPool -- a native resource that outlives this
        // Composable unless explicitly released. Every entry into this
        // screen (a fresh `remember(context)` above) leaked one without
        // this.
        DisposableEffect(soundBank) {
            onDispose { soundBank.release() }
        }

        val density = LocalDensity.current
        val view = LocalView.current

        // Immutable state machine: place() returns a NEW ColorSortState, it
        // never mutates items in place. Writing the result back to this
        // `var` is what drives recomposition -- there is no separate
        // version/trigger counter, and there must never be one.
        var state by remember(level) { mutableStateOf(ColorSortState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped per-item on a wrong-bin drop so ItemSlot can play a visible
        // wobble even with sound off -- audio alone is silent at the spec's
        // zero-volume baseline.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }
        // Root-coordinate bounds of each rendered bin, refreshed as bins lay
        // out. A drop is resolved by testing the item's current root
        // position against these rectangles (inflated by
        // [ColorSortGeometry]'s margin), never by re-deriving layout math --
        // the on-screen bin IS the hit target.
        var binBounds by remember(level) { mutableStateOf(emptyMap<BinSpec, Rect>()) }
        // Root-coordinate origin of each item's stationary touch slot.
        var itemOrigins by remember(level) { mutableStateOf(emptyMap<Int, Offset>()) }
        var rootOrigin by remember(level) { mutableStateOf(Offset.Zero) }

        // Which item (if any) is airborne, and by how much, in root-window
        // pixels. Read by the overlay below to draw the item OUTSIDE every
        // clipping parent so it is never sliced off mid-drag.
        var draggingItemId by remember(level) { mutableStateOf<Int?>(null) }
        var liveDragOffset by remember(level) { mutableStateOf(Offset.Zero) }

        val marginPx = with(density) { ColorSortGeometry.marginDpFor(level).dp.toPx() }

        LaunchedEffect(state) {
            if (state.isComplete && !celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(if (level == 5) 1600L else 1100L)
                onFinished(Outcome.Completed)
            }
        }

        fun handleDragEnd(item: SortItem, totalDrag: Offset) {
            val origin = itemOrigins[item.id] ?: return
            val visualPx = with(density) { (MinTapTarget + ITEM_PADDING * 2).toPx() }
            val center = Offset(
                x = origin.x + totalDrag.x + visualPx / 2f,
                y = origin.y + totalDrag.y + visualPx / 2f,
            )
            // Inflated bin rects legitimately overlap their neighbours (see
            // [ColorSortGeometry.marginDpFor]'s KDoc) so more than one entry
            // can contain `center` at once. Resolving to the FIRST match in
            // map-iteration order (the previous bug) silently reassigns an
            // accurate drop near a shared edge to whichever bin happened to
            // be inserted first. Resolving to the NEAREST bin centre among
            // every candidate is order-independent and always credits the
            // bin the child was actually closest to -- same fix as
            // `games/matchshapes`.
            val dropBin = binBounds.entries
                .filter { (_, rect) -> rect.inflate(marginPx).contains(center) }
                .minByOrNull { (_, rect) -> (rect.center - center).getDistanceSquared() }
                ?.key
            if (dropBin != null) {
                val next = state.place(item.id, dropBin)
                val placedNow = next.items.first { it.id == item.id }.placed
                if (placedNow) {
                    state = next
                    soundBank.play(SoundBank.Cue.SUCCESS)
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                } else {
                    wobbleTriggers = wobbleTriggers +
                        (item.id to (wobbleTriggers[item.id] ?: 0) + 1)
                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(KidPalette.Background),
            ) {
                BinRows(
                    bins = state.bins,
                    onPositioned = { bin, rect -> binBounds = binBounds + (bin to rect) },
                )

                ItemRows(
                    items = state.items,
                    wobbleTriggers = wobbleTriggers,
                    draggingItemId = draggingItemId,
                    modifier = Modifier.weight(1f),
                    onPositioned = { id, origin -> itemOrigins = itemOrigins + (id to origin) },
                    onDragStart = { id ->
                        draggingItemId = id
                        liveDragOffset = Offset.Zero
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onDrag = { amount -> liveDragOffset += amount },
                    onDragEnd = { item ->
                        val total = liveDragOffset
                        draggingItemId = null
                        liveDragOffset = Offset.Zero
                        handleDragEnd(item, total)
                    },
                    onDragCancel = {
                        draggingItemId = null
                        liveDragOffset = Offset.Zero
                    },
                )
            }

            // The in-flight item, drawn OUTSIDE the Column above so nothing
            // in it (or in any future scrolling/clipping ancestor) can slice
            // it off before it reaches a bin. Position is
            // origin + live drag delta, translated into this Box's own
            // local coordinate space -- the SAME arithmetic [handleDragEnd]
            // uses to judge the drop.
            val draggingItem = state.items.firstOrNull { it.id == draggingItemId }
            if (draggingItem != null) {
                val origin = itemOrigins[draggingItem.id] ?: Offset.Zero
                val localX = origin.x + liveDragOffset.x - rootOrigin.x
                val localY = origin.y + liveDragOffset.y - rootOrigin.y
                Box(
                    modifier = Modifier
                        .offset { IntOffset(localX.toInt(), localY.toInt()) }
                        .size(MinTapTarget + ITEM_PADDING * 2)
                        .zIndex(20f),
                    contentAlignment = Alignment.Center,
                ) {
                    ItemGlyph(item = draggingItem)
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

// Bins wrap onto rows of at most four. At L5 (8 bins, the densest level)
// that is 2 rows of 4: 4 x 64dp bins + 3 x 12dp gaps + 32dp of container
// padding = 256 + 36 + 32 = 324dp... except a bin ROW's width is bounded by
// the viewport, not the bin block's height claim above (which is about
// vertical space) -- width check: 4 x 64 + 3 x 12 + 2 x 16 = 256 + 36 + 32 =
// 324dp, inside a 360dp portrait viewport with 36dp to spare.
private const val BINS_PER_ROW = 4

// Items wrap onto rows of four, NOT five -- five was round 2's bug. The
// touch node a row actually lays out is [ItemSlot]'s `slotSize`
// (MinTapTarget 64dp + ITEM_PADDING 4dp x 2 = 72dp), not the 64dp glyph
// round 2's comment substituted it for. Width check at ITEMS_PER_ROW = 5
// (the old value): 5 x 72 + 4 x ITEM_GAP_H + 2 x 8 container padding is
// already 392dp of slots+padding alone at zero gap, over a 360dp viewport --
// five never fit, at any gap. At ITEMS_PER_ROW = 4 with the M1-restored
// 16dp horizontal gap: 4 x 72 + 3 x 16 + 2 x 8 = 288 + 48 + 16 = 352dp,
// inside 360dp with 8dp to spare. This is the largest per-row count that
// fits a real 72dp slot in a 360dp viewport at any gap worth calling
// "meaningful" (5 columns needs <= -16dp of total gap, i.e. never).
private const val ITEMS_PER_ROW = 4

private val BIN_GAP = 12.dp
private val BIN_ROW_PADDING = 16.dp

// Restored to round 1's verified 16dp (round 2 regressed this to 4dp --
// see M1). 16dp of dead space between adjacent 72dp touch nodes is what
// keeps a clumsy hand from bridging two targets in one press.
private val ITEM_GAP_H = 16.dp
private val ITEM_GAP_V = 8.dp
private val ITEM_ROW_PADDING = 8.dp
private val ITEM_PADDING = 4.dp

// L5's item block (24 items / 4-per-row = 6 rows) is, on its own:
// 6 x 72 + 5 x 8 + 2 x 8 = 432 + 40 + 16 = 488dp. Added to the bin block's
// 172dp that is 660dp of content -- more than an entire 360x640dp device,
// let alone the ~568dp actually left after system bars. There is no
// non-decreasing-tolerance-safe way to shrink 24 real 64dp touch targets
// into what's left, so the item block scrolls (see [ItemRows]) rather than
// pretend an exact-fit budget exists. The in-flight dragged item is drawn
// by the caller's top-level overlay OUTSIDE this scrolling container (see
// the module KDoc's DRAG RENDERING section), so scrolling the static grid
// underneath cannot clip it -- the original objection to a scrollable/lazy
// item grid no longer applies now that dragging doesn't rely on this
// container's content staying unclipped.
//
// BOTTOM_EXCLUSION_HEIGHT reserves a dead band at the end of the scrollable
// content so a child who scrolls all the way down still lands with the last
// row's bottom edge clear of `GameHost`'s BottomStart exit button (64dp
// button + 16dp padding = 80dp tall; 96dp leaves real margin rather than an
// exact-fit 80dp, so a taller status bar or gesture inset never reopens this
// defect). It is pure empty space -- no interactive content is ever laid
// out inside it -- so the exit button can never have an accurate drop
// mistaken for it, at any scroll position that shows the end of the list.
private val BOTTOM_EXCLUSION_HEIGHT = 96.dp

/**
 * Bins laid out as plain wrapped rows of [BINS_PER_ROW], each bin a real,
 * uncoerced [MinTapTarget] square -- no `weight(1f)`, so nothing silently
 * squashes the size below the floor as bin count rises with level.
 */
@Composable
private fun BinRows(bins: List<BinSpec>, onPositioned: (BinSpec, Rect) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(BIN_ROW_PADDING),
        verticalArrangement = Arrangement.spacedBy(BIN_GAP),
    ) {
        bins.chunked(BINS_PER_ROW).forEach { rowBins ->
            Row(horizontalArrangement = Arrangement.spacedBy(BIN_GAP)) {
                rowBins.forEach { bin ->
                    BinTarget(
                        bin = bin,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            onPositioned(bin, coords.boundsInRoot())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BinTarget(bin: BinSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MinTapTarget)
            .background(bin.color.toComposeColor().copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .border(4.dp, bin.color.toComposeColor(), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ShapeGlyph(shape = bin.shape, color = bin.color.toComposeColor(), size = binGlyphSize(bin.size))
        ColorMarkBadge(mark = bin.color.ordinal + 1, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
    }
}

private fun binGlyphSize(size: ItemSize): Dp = if (size == ItemSize.SMALL) 22.dp else 32.dp

/**
 * Items laid out as plain wrapped rows of [ITEMS_PER_ROW] -- no
 * `LazyVerticalGrid`, so nothing clips the in-flight dragged item (that is
 * drawn separately, by the caller's top-level overlay, OUTSIDE this
 * [verticalScroll] container). Placed items still occupy their slot (see
 * [ItemSlot]'s placeholder branch) -- never filtered, so a still-unplaced
 * item's slot never shifts because a sibling finished first.
 *
 * This section takes the REMAINING space below the bin block (`weight(1f)`
 * in the caller's outer `Column`, not a per-row `weight` -- that's the
 * pattern round 2 verified safe, unlike weighting individual bins/items) and
 * scrolls if its content is taller than that -- L5's 24 items in 4 columns
 * (6 rows) never fit an on-screen budget honestly (see the arithmetic on
 * [BOTTOM_EXCLUSION_HEIGHT]), so scrolling is what replaces the false
 * "exact fit" round 2 asserted. A trailing [BOTTOM_EXCLUSION_HEIGHT] spacer
 * keeps the tail of the list clear of `GameHost`'s exit button once
 * scrolled to the end.
 */
@Composable
private fun ItemRows(
    items: List<SortItem>,
    wobbleTriggers: Map<Int, Int>,
    draggingItemId: Int?,
    onPositioned: (Int, Offset) -> Unit,
    onDragStart: (Int) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (SortItem) -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(ITEM_ROW_PADDING),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP_V),
    ) {
        items.chunked(ITEMS_PER_ROW).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(ITEM_GAP_H)) {
                rowItems.forEach { item ->
                    ItemSlot(
                        item = item,
                        wobbleTrigger = wobbleTriggers[item.id] ?: 0,
                        isAirborne = draggingItemId == item.id,
                        onPositioned = { origin -> onPositioned(item.id, origin) },
                        onDragStart = { onDragStart(item.id) },
                        onDrag = onDrag,
                        onDragEnd = { onDragEnd(item) },
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
        // Dead space, deliberately content-free -- see
        // [BOTTOM_EXCLUSION_HEIGHT]'s KDoc.
        Box(modifier = Modifier.fillMaxWidth().height(BOTTOM_EXCLUSION_HEIGHT))
    }
}

/**
 * One item's stationary touch slot, always exactly [MinTapTarget] regardless
 * of [SortItem.size] -- [MinTapTarget] is a FLOOR, not a base to scale down
 * from, so the SMALL/LARGE distinction is expressed purely by scaling the
 * drawn glyph inside [ItemGlyph], never by shrinking this node.
 *
 * A placed item renders as a same-size, non-interactive, invisible
 * placeholder so its slot stays occupied (see [ItemRows]'s KDoc). An
 * [isAirborne] item ALSO renders as an empty placeholder here -- its glyph
 * is drawn instead by the caller's top-level overlay, outside this node's
 * (potentially clipping) `Row`/`Column` ancestors, so it is never sliced off
 * mid-drag.
 *
 * The pointer-input node stays put; Compose's drag detector tracks the
 * pointer by id, not by this node's screen position, so nothing is lost by
 * not visually offsetting it -- only the overlay glyph moves.
 */
@Composable
private fun ItemSlot(
    item: SortItem,
    wobbleTrigger: Int,
    isAirborne: Boolean,
    onPositioned: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val slotSize = MinTapTarget + ITEM_PADDING * 2

    if (item.placed) {
        Box(modifier = Modifier.size(slotSize).onGloballyPositioned { onPositioned(it.positionInRoot()) })
        return
    }

    // A soft scale-bounce on a wrong drop -- visible, non-punitive feedback
    // that works even with sound off.
    val wobble = remember(item.id) { Animatable(1f) }
    LaunchedEffect(wobbleTrigger) {
        if (wobbleTrigger > 0) {
            wobble.animateTo(0.85f, animationSpec = tween(90))
            wobble.animateTo(1f, animationSpec = tween(150))
        }
    }

    Box(
        modifier = Modifier
            .size(slotSize)
            .onGloballyPositioned { onPositioned(it.positionInRoot()) }
            .pointerInput(item.id) {
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
            Box(modifier = Modifier.scale(wobble.value)) {
                ItemGlyph(item = item)
            }
        }
    }
}

@Composable
private fun ItemGlyph(item: SortItem) {
    // The touch/overlay node is always MinTapTarget -- only the drawn glyph
    // inside it shrinks for SMALL items, never the node itself. See
    // [ItemSlot]'s KDoc.
    val glyphSize = MinTapTarget * 0.7f * if (item.size == ItemSize.SMALL) 0.7f else 1f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(MinTapTarget)) {
        ShapeGlyph(shape = item.shape, color = item.color.toComposeColor(), size = glyphSize)
        ColorMarkBadge(mark = item.colorMark, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/**
 * A small dot-count badge, independent of hue, that identifies colour by
 * count rather than colour. Always rendered alongside the colour swatch on
 * both items and bins so colour is never the sole carrier of meaning. 8dp
 * dots (not the old 6dp) for legibility at arm's length in a moving car.
 */
@Composable
private fun ColorMarkBadge(mark: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(mark) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(KidPalette.OnSurface, CircleShape),
            )
        }
    }
}

@Composable
private fun ShapeGlyph(shape: ItemShape, color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        when (shape) {
            ItemShape.CIRCLE -> drawCircle(color = color)
            ItemShape.SQUARE -> drawRect(color = color)
            ItemShape.TRIANGLE -> drawTriangle(color)
            ItemShape.STAR -> drawStarShape(color)
        }
    }
}

private fun DrawScope.drawTriangle(color: Color) {
    val path = Path().apply {
        moveTo(size.width / 2f, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawStarShape(color: Color) {
    val points = 5
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.45f
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    val angleStep = PI / points
    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2 + i * angleStep
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

private fun ItemColor.toComposeColor(): Color = when (this) {
    ItemColor.RED -> KidPalette.Red
    ItemColor.BLUE -> KidPalette.Blue
    ItemColor.YELLOW -> KidPalette.Yellow
    ItemColor.GREEN -> KidPalette.Green
}
