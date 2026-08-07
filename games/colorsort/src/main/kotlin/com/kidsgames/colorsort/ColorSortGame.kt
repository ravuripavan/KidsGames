package com.kidsgames.colorsort

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 * [items] below is called on the FULL (unfiltered) item list for exactly
 * this reason -- placed items stay in the grid as invisible, non-clickable,
 * same-size placeholders instead of being removed, so no still-unplaced
 * item's grid cell ever shifts because a sibling finished first.
 *
 * WARNING this module has to get right where `popballoons` could get away
 * with drift alone: [ItemView]'s drag offset is applied via
 * `Modifier.offset { IntOffset(...) }`, which -- like every modifier passed
 * to a composable's own `modifier` parameter -- affects layout and drawing,
 * NOT some separate "touch target" that could silently lag behind it. This
 * module does not use [com.kidsgames.designkit.KidButton] for the draggable
 * items (a `clickable` and a `pointerInput` drag detector on the same node
 * would race each other for the pointer-down/up stream, exactly the
 * conflict [com.kidsgames.designkit.KidTapHaptics]'s KDoc warns about for
 * taps); instead each item's own `Box` carries both the visual offset and
 * the drag [Modifier.pointerInput], so the hitbox and the drawn position are
 * structurally the same node and can never diverge. Both items and bins are
 * still sized to at least [MinTapTarget] explicitly, satisfying the same
 * 64dp minimum [com.kidsgames.designkit.KidButton] enforces for taps.
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

        // Immutable state machine: place() returns a NEW ColorSortState, it
        // never mutates items in place. Writing the result back to this
        // `var` is what drives recomposition -- there is no separate
        // version/trigger counter, and there must never be one.
        var state by remember(level) { mutableStateOf(ColorSortState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped per-item on a wrong-bin drop so ItemView can play a visible
        // wobble even with sound off -- audio alone is silent at the spec's
        // zero-volume baseline.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }
        // Root-coordinate bounds of each rendered bin, refreshed as bins lay
        // out. A drop is resolved by testing the item's current root
        // position against these rectangles, never by re-deriving layout
        // math -- the on-screen bin IS the hit target.
        var binBounds by remember(level) { mutableStateOf(emptyMap<BinSpec, Rect>()) }

        LaunchedEffect(state) {
            if (state.isComplete && !celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(if (level == 5) 1600L else 1100L)
                onFinished(Outcome.Completed)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.bins.forEach { bin ->
                        BinTarget(
                            bin = bin,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    binBounds = binBounds + (bin to coords.boundsInRoot())
                                },
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = MinTapTarget + 16.dp),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Emit EVERY item, placed or not -- never filter the
                    // list fed to items(...). See the class KDoc: removing a
                    // placed item reflows every later item back one slot,
                    // moving still-unplaced items out from under a child
                    // mid-reach. Placed items render as an invisible,
                    // non-draggable placeholder of the same size instead.
                    items(state.items, key = { it.id }) { item ->
                        ItemView(
                            item = item,
                            state = state,
                            binBounds = binBounds,
                            wobbleTrigger = wobbleTriggers[item.id] ?: 0,
                            onDropped = { bin ->
                                val next = state.place(item.id, bin)
                                val placedNow = next.items.first { it.id == item.id }.placed
                                if (placedNow) {
                                    state = next
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                } else {
                                    wobbleTriggers = wobbleTriggers +
                                        (item.id to (wobbleTriggers[item.id] ?: 0) + 1)
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
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
}

@Composable
private fun BinTarget(bin: BinSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(MinTapTarget + 16.dp)
            .background(bin.color.toComposeColor().copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .border(4.dp, bin.color.toComposeColor(), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ShapeGlyph(shape = bin.shape, color = bin.color.toComposeColor(), size = binGlyphSize(bin.size))
        ColorMarkBadge(mark = bin.color.ordinal + 1, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

private fun binGlyphSize(size: ItemSize): Dp = if (size == ItemSize.SMALL) 28.dp else 40.dp

@Composable
private fun ItemView(
    item: SortItem,
    state: ColorSortState,
    binBounds: Map<BinSpec, Rect>,
    wobbleTrigger: Int,
    onDropped: (BinSpec) -> Unit,
) {
    val visualSize = MinTapTarget * if (item.size == ItemSize.SMALL) 0.8f else 1f

    // A placed item still occupies its grid cell (see the caller's comment
    // on why the item list is never filtered) but must neither be visible
    // nor draggable. A same-size, alpha-0, non-interactive Box keeps the
    // cell's dimensions in the grid layout without drawing or reacting to
    // touch.
    if (item.placed) {
        Box(modifier = Modifier.size(visualSize).padding(4.dp))
        return
    }

    val scope = rememberCoroutineScope()
    val offset = remember(item.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var currentBounds by remember(item.id) { mutableStateOf(Rect.Zero) }
    // detectDragGestures runs its callbacks outside normal recomposition, so
    // closures inside it must read the LATEST state/binBounds rather than
    // whatever was captured when the gesture started -- otherwise a drop
    // resolved against a stale `state` could silently disagree with what's
    // on screen.
    val latestState = rememberUpdatedState(state)
    val latestBounds = rememberUpdatedState(binBounds)

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
            .size(visualSize)
            .offset { IntOffset(offset.value.x.roundToInt(), offset.value.y.roundToInt()) }
            .scale(wobble.value)
            .padding(4.dp)
            .onGloballyPositioned { coords -> currentBounds = coords.boundsInRoot() }
            .pointerInput(item.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dragAmount) }
                    },
                    onDragEnd = {
                        val center = currentBounds.center
                        val dropBin = latestBounds.value.entries
                            .firstOrNull { (_, rect) -> rect.contains(center) }
                            ?.key
                        if (dropBin != null) {
                            onDropped(dropBin)
                        }
                        // Whether the drop matched or missed entirely, the
                        // visual returns to its grid cell -- a match makes
                        // the item disappear next recomposition (it becomes
                        // the placeholder branch above), and a miss simply
                        // un-drags it. Either way there is nothing to snap
                        // back to but the origin.
                        scope.launch { offset.animateTo(Offset.Zero, animationSpec = tween(200)) }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(Offset.Zero, animationSpec = tween(200)) }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ShapeGlyph(shape = item.shape, color = item.color.toComposeColor(), size = visualSize * 0.7f)
        ColorMarkBadge(mark = item.colorMark, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/**
 * A small dot-count badge, independent of hue, that identifies colour by
 * count rather than colour. Always rendered alongside the colour swatch on
 * both items and bins so colour is never the sole carrier of meaning.
 */
@Composable
private fun ColorMarkBadge(mark: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(mark) {
            Box(
                modifier = Modifier
                    .size(6.dp)
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
