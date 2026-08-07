package com.kidsgames.memorypairs

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.designkit.rememberSoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Picture memory match: flip two cards, matching pairs stay face up. Copies
 * `:games:popballoons`'s shape -- see that module's KDoc for the pattern this
 * follows. An `object : GameModule` exposing the frozen properties, and a
 * `Play` composable that owns nothing but a plain-Kotlin, pure state machine
 * ([MemoryPairsState]) held in a single `mutableStateOf`. [MemoryPairsState.flip]
 * and [MemoryPairsState.resolve] each return a brand-new state object rather
 * than mutating anything in place; recomposition is driven entirely by that
 * new object's identity being written back to the `var`. There is no separate
 * "version" or "trigger" counter anywhere in this module.
 *
 * CRITICAL for this game specifically: there is no attempt limit, move
 * counter, score, or timer -- a memory game is where fail states hide most
 * easily, and getting a pair wrong must cost literally nothing. A
 * non-matching pair simply flips back after a generous pause with
 * [SoundBank.Cue.GENTLE_RETRY]; nothing about the board or the child's
 * progress changes because of it.
 *
 * INVARIANT this Play composable must hold, same as popballoons: a card
 * NEVER changes screen position for the life of a level. [items] below is
 * called on the FULL (unfiltered) card list for exactly this reason --
 * matched cards stay in the grid, rendered face-up and non-clickable, in
 * their original cell. Filtering matched cards out would reflow the grid on
 * every match, which in a memory game would also destroy the very spatial
 * memory the game is built on -- worse here than in popballoons, not just
 * equally bad.
 *
 * The column count is CHOSEN, not left to [GridCells.Adaptive]'s own
 * division: `Play` measures the real available box with
 * [BoxWithConstraints] and [chooseGrid] picks the column count whose
 * resulting cell is still >= [MinTapTarget] (64dp) on BOTH the width axis
 * AND the height axis the column choice implies (see [chooseGrid]'s KDoc).
 * `Adaptive` was tried first and rejected -- its own division silently
 * drops a column a few dp before the width you'd expect, which at L5 turns
 * 4 columns into 3, turns 5 rows into 7, and reintroduces the scroll this
 * grid must never have. A width-only [chooseGrid] predecessor was rejected
 * for the same reason at a different axis: it let row count grow
 * unchecked against the measured height, which is exactly what let a
 * raised Display size scroll a real device in round-4 review (see
 * [chooseGrid]'s KDoc for the arithmetic).
 */
/** Content padding on each side of the grid. Tuned down from round-3's
 *  24dp in round 4 specifically to buy back the vertical headroom a raised
 *  Display size eats into (see [chooseGrid]'s KDoc) -- 12dp per side is
 *  still real, visible dead space around the board, just not as generous
 *  as before. Not part of the tap-target floor, so unlike [GridGap] it
 *  stays free to tune further if a future device needs it. */
private val GridPadding = 12.dp

/** Gap between touch nodes, both directions. Round-4 also trimmed this
 *  from 16dp to 10dp for the same reason as [GridPadding] -- still a
 *  clearly visible gap between adjacent 64dp cards, not a squeeze against
 *  the tap-target floor itself, which this constant never touches. */
private val GridGap = 10.dp

/**
 * A chosen column/row layout for the board. [rows] is derived from
 * [columns] and the card count (`ceil(cardCount / columns)`), never picked
 * independently -- the grid is still a single [GridCells.Fixed] column
 * count, [rows] just exists so callers (and tests) can assert the whole
 * board's height without recomputing the ceiling division themselves.
 */
internal data class GridSpec(val columns: Int, val rows: Int)

/**
 * Picks a column count for [cardCount] cards such that BOTH axes hold: the
 * resulting cell width (dividing [maxWidth] by the column count, after
 * [GridPadding] on both sides and [GridGap] between columns) and the
 * resulting cell height (dividing [maxHeight] by the row count the column
 * choice implies, same padding/gap rule) are each `>= MinTapTarget`. Pure Dp
 * arithmetic, no Compose dependency beyond the `Dp` type, so it is
 * unit-testable independent of any real layout pass.
 *
 * This is the round-4 fix: the previous version ([computeColumns], now
 * folded into this function) measured width alone. Picking columns from
 * width only chooses row count as a SIDE EFFECT (`rows = ceil(cardCount /
 * columns)`), and nothing checked that side effect against the measured
 * height -- at a raised Display size, a device can be narrow enough to
 * force fewer columns (more rows) while still being tall on paper, and the
 * fixed 96dp exit-zone reservation plus safeDrawing insets eat further into
 * that height without shrinking together with the raised density. The
 * result was a grid that silently scrolled, which this game's own
 * invariant (see class KDoc) forbids outright.
 *
 * Cell width for `c` columns is monotonically decreasing in `c`, so once it
 * drops below the floor, no larger `c` can recover it and the search stops.
 * Row count is non-increasing as `c` grows (more columns means the same
 * card count spreads over fewer rows), so among the column counts whose
 * width clears the floor, the search keeps the one that also clears the
 * height floor and yields the fewest rows -- preferring more columns only
 * as a tiebreaker, since fewer rows is what actually relieves the height
 * constraint. If NO column count clears both floors, this returns `null`
 * rather than silently degrading to a scroll -- see [Play] for how that
 * is handled.
 */
internal fun chooseGrid(cardCount: Int, maxWidth: Dp, maxHeight: Dp): GridSpec? {
    if (cardCount <= 0) return GridSpec(1, 1)
    var best: GridSpec? = null
    for (c in 1..cardCount) {
        val cellWidth = (maxWidth - GridPadding * 2 - GridGap * (c - 1)) / c
        if (cellWidth < MinTapTarget) break
        val rows = (cardCount + c - 1) / c
        val cellHeight = (maxHeight - GridPadding * 2 - GridGap * (rows - 1)) / rows
        if (cellHeight < MinTapTarget) continue
        val current = GridSpec(c, rows)
        best = when {
            best == null -> current
            rows < best.rows -> current
            rows == best.rows && c > best.columns -> current
            else -> best
        }
    }
    return best
}

object MemoryPairsGame : GameModule {

    override val id: String = "memorypairs"
    override val icon: Int = R.drawable.ic_memorypairs
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    /** How long a resolved (matched or mismatched) pair stays visible before
     *  the mismatch case flips back down. Generous on purpose -- long enough
     *  for a four year old to actually look at both cards, not a snappy UI
     *  timing. Matches take the same pause so success also gets time to land. */
    private const val RESOLVE_DELAY_MS = 1400L

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()

        // Immutable state machine: flip()/resolve() each return a NEW
        // MemoryPairsState, never mutating cards in place. Writing the
        // result back to this `var` is what drives recomposition -- there
        // is no separate version/trigger counter to remember to bump, and
        // there must never be one.
        // Drawn once per level ENTRY (remember(level) re-keys only when the
        // shell hands us a new level), then folded into the shuffle seed so
        // every fresh play of a level deals a new layout while staying
        // stable across recomposition -- cards never reshuffle under a
        // finger mid-game. Same pattern as :games:countanimals.
        val shuffleSeed = remember(level) { Random.nextInt() }
        var state by remember(level) { mutableStateOf(MemoryPairsState(level, shuffleSeed = shuffleSeed)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        // While two cards are face-up and unresolved, hold them visible for
        // a generous, non-negotiable pause, then resolve. This is the ONLY
        // place a delay exists in this game, and it is not shortened at any
        // level -- difficulty grows only by adding pairs, never by a
        // shorter look-time.
        LaunchedEffect(state.flippedIds) {
            if (state.isPendingResolution) {
                delay(RESOLVE_DELAY_MS)
                val beforeResolve = state
                val resolved = beforeResolve.resolve()
                state = resolved
                val firstId = beforeResolve.flippedIds[0]
                val matched = resolved.cards.first { it.id == firstId }.matched
                soundBank.play(if (matched) SoundBank.Cue.SUCCESS else SoundBank.Cue.GENTLE_RETRY)
            }
        }

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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Measure the REAL available box every recomposition -- both
                // axes, not width alone -- and pick the column count from
                // it, rather than trusting GridCells.Adaptive's own division
                // (see class KDoc for why that silently regresses the
                // scroll bug below ~352dp) or a width-only search (see
                // chooseGrid's KDoc for the round-4 device finding that
                // fixed). This BoxWithConstraints already sits inside
                // GameHost's exit-zone padding and KidsApp's safeDrawing
                // insets, so maxWidth/maxHeight here ARE the real content
                // box the game receives -- nothing is hand-subtracted.
                val gridSpec = remember(maxWidth, maxHeight, state.cards.size) {
                    chooseGrid(state.cards.size, maxWidth, maxHeight)
                        // No column count clears the 64dp floor on both
                        // axes at this box size. Falling back to a single
                        // column would still scroll (worse), so fall back
                        // to the narrowest board (most columns the width
                        // alone allows) -- this only degrades cell size
                        // below the 64dp floor in a box this game was never
                        // going to fit in either way, and is a last resort,
                        // not the expected path at any shipped level.
                        ?: GridSpec(
                            columns = maxOf(1, state.cards.size),
                            rows = 1,
                        )
                }
                val columns = gridSpec.columns
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    // No extra bottom reserve here: GameHost already composes
                    // Play() inside a Modifier.padding(bottom = ExitZoneHeight),
                    // so the shell's exit button is structurally outside the
                    // space this grid ever receives. A local reservation on top
                    // of that would just be dead space that pushes the board
                    // toward a scroll it must never have.
                    contentPadding = PaddingValues(GridPadding),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalArrangement = Arrangement.spacedBy(GridGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Emit EVERY card, matched or not, to items(...) -- never
                    // filter the list. A matched card renders face-up and
                    // non-clickable in its own cell forever, so no still-live
                    // card ever moves. See the class KDoc for why this matters
                    // more here than in any other game in the suite.
                    items(state.cards, key = { it.id }) { card ->
                        CardView(
                            card = card,
                            clickable = !state.isPendingResolution && !card.faceUp && !card.matched,
                            onTap = {
                                soundBank.play(SoundBank.Cue.TAP)
                                state = state.flip(card.id)
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

/** Shape + colour combinations used as card faces. Round 3 fix: every one of
 *  the ten entries below uses a DIFFERENT [CardShape] -- no shape repeats
 *  across the whole list, at any level. That was not true before: shapes
 *  used to repeat with a different colour (e.g. a green star and a red
 *  star both in play at L5), which made colour the SOLE discriminator for
 *  that pair and put the single most common colour-blind confusion (red vs
 *  green) directly on the two cards a child would need to tell apart.
 *  With one shape per symbol, no two card faces in play at any level can
 *  ever be distinguishable by colour alone -- colour is decoration on top
 *  of an already-unique shape, never the thing carrying the distinction. */
internal enum class CardShape {
    CIRCLE, SQUARE, TRIANGLE, STAR, DIAMOND,
    HEART, CROSS, HEXAGON, PENTAGON, CRESCENT,
}

internal val CardSymbols: List<Pair<CardShape, Color>> = listOf(
    CardShape.CIRCLE to KidPalette.Red,
    CardShape.SQUARE to KidPalette.Orange,
    CardShape.TRIANGLE to KidPalette.Yellow,
    CardShape.STAR to KidPalette.Green,
    CardShape.DIAMOND to KidPalette.Blue,
    CardShape.HEART to KidPalette.Purple,
    CardShape.CROSS to KidPalette.Pink,
    CardShape.HEXAGON to KidPalette.Blue,
    CardShape.PENTAGON to KidPalette.Red,
    CardShape.CRESCENT to KidPalette.Orange,
)

@Composable
private fun CardView(card: MemoryCard, clickable: Boolean, onTap: () -> Unit) {
    val revealed = card.faceUp || card.matched
    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.92f,
        animationSpec = tween(180),
        label = "card-flip-scale",
    )

    KidButton(
        onClick = { if (clickable) onTap() },
        modifier = Modifier
            .size(MinTapTarget)
            .scale(scale)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (revealed) KidPalette.Surface else KidPalette.OnSurface.copy(alpha = 0.15f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (revealed) {
                val (shape, color) = CardSymbols[card.symbolIndex % CardSymbols.size]
                SymbolGlyph(shape = shape, color = color, size = MinTapTarget * 0.55f)
            }
        }
    }
}

@Composable
private fun SymbolGlyph(shape: CardShape, color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        when (shape) {
            CardShape.CIRCLE -> drawCircle(color = color)
            CardShape.SQUARE -> drawRect(color = color)
            CardShape.TRIANGLE -> drawPolygonPath(color, sides = 3)
            CardShape.STAR -> drawStarPath(color)
            CardShape.DIAMOND -> drawPolygonPath(color, sides = 4, rotationOffset = PI / 4)
            CardShape.HEART -> drawHeartPath(color)
            CardShape.CROSS -> drawCrossPath(color)
            CardShape.HEXAGON -> drawPolygonPath(color, sides = 6)
            CardShape.PENTAGON -> drawPolygonPath(color, sides = 5)
            CardShape.CRESCENT -> drawCrescentPath(color)
        }
    }
}

private fun DrawScope.drawPolygonPath(color: Color, sides: Int, rotationOffset: Double = 0.0) {
    val radius = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    val angleStep = 2 * PI / sides
    for (i in 0 until sides) {
        val angle = -PI / 2 + rotationOffset + i * angleStep
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color)
}

private fun DrawScope.drawStarPath(color: Color) {
    val points = 5
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.4f
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

/** A plus/cross shape, built as one 12-point outline rather than two
 *  overlapping rects, so it fills evenly with a single [drawPath] call. */
private fun DrawScope.drawCrossPath(color: Color) {
    val s = min(size.width, size.height)
    val half = s / 2f
    val armHalf = s * 0.17f
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path().apply {
        moveTo(center.x - armHalf, center.y - half)
        lineTo(center.x + armHalf, center.y - half)
        lineTo(center.x + armHalf, center.y - armHalf)
        lineTo(center.x + half, center.y - armHalf)
        lineTo(center.x + half, center.y + armHalf)
        lineTo(center.x + armHalf, center.y + armHalf)
        lineTo(center.x + armHalf, center.y + half)
        lineTo(center.x - armHalf, center.y + half)
        lineTo(center.x - armHalf, center.y + armHalf)
        lineTo(center.x - half, center.y + armHalf)
        lineTo(center.x - half, center.y - armHalf)
        lineTo(center.x - armHalf, center.y - armHalf)
        close()
    }
    drawPath(path, color = color)
}

/** A simple two-lobe heart, built from two cubic Bezier curves meeting at a
 *  bottom point -- deliberately plain and chunky so it reads at a glance to
 *  a four year old, not an ornate illustration. */
private fun DrawScope.drawHeartPath(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w / 2f, h * 0.85f)
        cubicTo(-w * 0.1f, h * 0.55f, w * 0.1f, h * 0.05f, w / 2f, h * 0.3f)
        cubicTo(w * 0.9f, h * 0.05f, w * 1.1f, h * 0.55f, w / 2f, h * 0.85f)
        close()
    }
    drawPath(path, color = color)
}

/** A crescent moon: a filled circle with a smaller, offset circle cut out
 *  via [PathOperation.Difference]. Shares no silhouette with any other
 *  symbol in [CardSymbols], so it stays a distinct shape even next to
 *  [CardShape.CIRCLE]. */
private fun DrawScope.drawCrescentPath(color: Color) {
    val radius = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val outer = Path().apply {
        addOval(Rect(Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f)))
    }
    val biteOffset = radius * 0.6f
    val inner = Path().apply {
        addOval(
            Rect(
                Offset(center.x - radius + biteOffset, center.y - radius),
                Size(radius * 2f, radius * 2f),
            ),
        )
    }
    val crescent = Path()
    crescent.op(outer, inner, PathOperation.Difference)
    drawPath(crescent, color = color)
}
