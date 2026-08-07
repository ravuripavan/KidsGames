package com.kidsgames.memorypairs

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * At L5 (10 pairs, 20 cards) the adaptive grid can run past one screen in
 * portrait; [LazyVerticalGrid] already scrolls, so cards keep their full
 * [MinTapTarget] size instead of shrinking below the 64dp floor to force
 * everything onto one screen.
 */
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
        var state by remember(level) { mutableStateOf(MemoryPairsState(level)) }
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MinTapTarget + 16.dp),
                // Extra bottom padding keeps every card clear of the shell's
                // bottom-left exit button (a 96x96dp zone that wins
                // hit-testing over anything drawn beneath it).
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

/** Shape + colour combinations used as card faces. Combining shape and
 *  colour (rather than colour alone) keeps every pair distinguishable
 *  without relying on colour as the sole carrier of meaning, per the shared
 *  palette's rule. Ten entries covers the largest level (L5, 10 pairs); a
 *  no-shape/no-colour repeat within the first ten keeps every pair unique
 *  even at that level. */
private enum class CardShape { CIRCLE, SQUARE, TRIANGLE, STAR, DIAMOND }

private val CardSymbols: List<Pair<CardShape, Color>> = listOf(
    CardShape.CIRCLE to KidPalette.Red,
    CardShape.SQUARE to KidPalette.Orange,
    CardShape.TRIANGLE to KidPalette.Yellow,
    CardShape.STAR to KidPalette.Green,
    CardShape.DIAMOND to KidPalette.Blue,
    CardShape.CIRCLE to KidPalette.Purple,
    CardShape.SQUARE to KidPalette.Pink,
    CardShape.TRIANGLE to KidPalette.Blue,
    CardShape.STAR to KidPalette.Red,
    CardShape.DIAMOND to KidPalette.Orange,
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
