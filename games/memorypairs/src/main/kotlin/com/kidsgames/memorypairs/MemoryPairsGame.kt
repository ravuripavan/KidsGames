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
 * division: `Play` measures the real available width with
 * [BoxWithConstraints] and [computeColumns] picks the largest column count
 * whose resulting cell is still >= [MinTapTarget] (64dp) after the 24dp
 * side padding and 16dp gutters on both sides. `Adaptive` was tried first
 * and rejected -- its own division silently drops a column a few dp before
 * the width you'd expect (e.g. 351dp, not exactly 4*80=320dp), which at
 * L5 turns 4 columns into 3, turns 5 rows into 7, and reintroduces the
 * scroll this grid must never have. Picking the column count by measurement
 * means the highest column count the ACTUAL viewport supports is always
 * used, which also minimises row count and therefore total board height.
 * On a 360dp-wide device that yields 4 columns at 66dp/cell, same as
 * before. See the module's final review notes for the arithmetic at other
 * widths.
 */
/** Content padding on each side of the grid. Not part of the tap-target
 *  floor, so unlike [GridGap] it is free to be tuned, but it is kept
 *  identical to what shipped in round 2 -- the fix here is column
 *  selection, not padding. */
private val GridPadding = 24.dp

/** Gap between touch nodes, both directions. Verified in prior review
 *  rounds and must not shrink. */
private val GridGap = 16.dp

/**
 * Picks the largest column count whose resulting cell width is still
 * `>= MinTapTarget` inside [availableWidth], given [GridPadding] on both
 * sides and [GridGap] between columns. Pure Dp arithmetic, no Compose
 * dependency beyond the `Dp` type, so it is trivial to reason about (and
 * unit-testable) independent of any real layout pass.
 *
 * Cell width for `c` columns is `(availableWidth - 2*GridPadding -
 * GridGap*(c-1)) / c`, which is monotonically decreasing in `c`, so the
 * search can stop at the first `c` that fails the floor. `cardCount` caps
 * the search -- there is never a reason to ask for more columns than there
 * are cards on the board.
 */
private fun computeColumns(availableWidth: Dp, cardCount: Int): Int {
    if (cardCount <= 0) return 1
    var best = 1
    for (c in 1..cardCount) {
        val cellWidth = (availableWidth - GridPadding * 2 - GridGap * (c - 1)) / c
        if (cellWidth >= MinTapTarget) {
            best = c
        } else {
            break
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
                // Measure the REAL available width every recomposition and
                // pick the column count from it, rather than trusting
                // GridCells.Adaptive's own division (see class KDoc for why
                // that silently regresses the scroll bug below ~352dp).
                val columns = remember(maxWidth, state.cards.size) {
                    computeColumns(maxWidth, state.cards.size)
                }
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
