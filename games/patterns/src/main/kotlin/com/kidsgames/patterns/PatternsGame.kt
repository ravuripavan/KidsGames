package com.kidsgames.patterns

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay

/**
 * Complete a repeating sequence by tapping the piece that comes next.
 *
 * Copies the reference module's (`:games:popballoons`) shape: an
 * `object : GameModule` exposing the frozen properties, and a `Play`
 * composable that owns nothing but a plain-Kotlin, pure state machine
 * ([PatternsState]) held in a single `mutableStateOf`. `choose()` returns a
 * brand-new state rather than mutating anything in place; recomposition is
 * driven entirely by that new object's identity being written back to the
 * `var`. There is no separate "version" or "trigger" counter anywhere in
 * this module.
 *
 * A pre-reader cannot be told the rule, so this whole game is built around
 * making the repeating unit visually self-evident rather than described:
 * the sequence is laid out left-to-right exactly as it plays, resolved
 * pieces stay on screen (never removed or reordered -- see the invariant
 * note on [SequenceRow]), and every [visibleReps]-length repeat is wrapped
 * in its own faintly-tinted, alternating group box. A child watching the
 * boxes tile left-to-right sees the SAME shapes-in-the-same-order recur
 * inside each box, which is the whole rule made visible without a word.
 * The next blank is a highlighted, pulsing dashed outline -- it is the only
 * slot that is ever empty, so "what goes here" is always legible even with
 * the sound off.
 *
 * Colour is never the sole carrier of meaning: every category the state
 * machine hands out (an abstract `Int`) is rendered here as a fixed
 * (shape, colour) pair -- see [categoryShape] and [categoryColor] -- so a
 * child with colour vision deficiency can still read the pattern purely by
 * shape. This holds at every level, L5 included; L5's extra difficulty is
 * a third category and a longer sequence, never colour becoming the only
 * distinguishing feature.
 */
object PatternsGame : GameModule {

    override val id: String = "patterns"
    override val icon: Int = R.drawable.ic_patterns
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val context = LocalContext.current
        val soundBank = remember(context) { SoundBank(context) }

        // Immutable state machine: choose() returns a NEW PatternsState, it
        // never mutates filledCount in place. Writing the result back to
        // this `var` is what drives recomposition -- there is no separate
        // version/trigger counter to remember to bump, and there must never
        // be one.
        var state by remember(level) { mutableStateOf(PatternsState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped on a wrong tap so the blank slot can play a visible wobble
        // even with sound off -- the only feedback used to be an audio cue,
        // which is silent at the spec's zero-volume baseline.
        var wobbleTrigger by remember(level) { mutableStateOf(0) }

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
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
            ) {
                SequenceRow(
                    state = state,
                    wobbleTrigger = wobbleTrigger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    (0 until state.categories).forEach { category ->
                        ChoiceButton(
                            category = category,
                            onTap = {
                                if (!celebrating) {
                                    val next = state.choose(category)
                                    if (next.filledCount != state.filledCount) {
                                        state = next
                                        soundBank.play(SoundBank.Cue.SUCCESS)
                                    } else {
                                        wobbleTrigger += 1
                                        soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
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

/**
 * Renders the sequence left-to-right, grouped visually into repeat blocks
 * so the cycle is something the child can SEE, not something they must be
 * told. Groups alternate a faint background tint purely as a grouping cue
 * (never the sole signal of anything -- shape still carries the pattern).
 *
 * INVARIANT: a resolved sequence slot never changes screen position or
 * disappears. [state]'s `visibleLength` only ever grows (see its KDoc), so
 * this is a pure append -- every slot the child has already seen keeps its
 * original place in the row for the rest of the level.
 */
@Composable
private fun SequenceRow(state: PatternsState, wobbleTrigger: Int, modifier: Modifier = Modifier) {
    val groupSize = groupSizeFor(state)
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(groupCountFor(state, groupSize)) { groupIndex ->
            SequenceGroup(
                state = state,
                groupIndex = groupIndex,
                groupSize = groupSize,
                wobbleTrigger = wobbleTrigger,
            )
        }
    }
}

private fun groupSizeFor(state: PatternsState): Int {
    // The group box always spans exactly one full repeat of the underlying
    // unit, which is what makes "this box == one cycle" visually true.
    return when (state.level) {
        1 -> 2
        2 -> 2
        3 -> 4
        4, 5 -> 3
        else -> 2
    }
}

private fun groupCountFor(state: PatternsState, groupSize: Int): Int {
    val slots = state.visibleLength
    return (slots + groupSize - 1) / groupSize
}

@Composable
private fun SequenceGroup(state: PatternsState, groupIndex: Int, groupSize: Int, wobbleTrigger: Int) {
    val tint = if (groupIndex % 2 == 0) KidPalette.Surface else KidPalette.Background
    Row(
        modifier = Modifier
            .padding(6.dp)
            .background(tint, RoundedCornerShape(20.dp))
            .border(2.dp, KidPalette.OnSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(10.dp),
    ) {
        val start = groupIndex * groupSize
        val end = minOf(start + groupSize, state.visibleLength)
        (start until end).forEach { index ->
            val isBlank = index == state.blankIndex
            SequenceSlot(
                category = if (isBlank) null else state.categoryAt(index),
                isBlank = isBlank,
                wobbleTrigger = if (isBlank) wobbleTrigger else 0,
            )
        }
    }
}

@Composable
private fun SequenceSlot(category: Int?, isBlank: Boolean, wobbleTrigger: Int) {
    val wobble = remember { Animatable(1f) }
    LaunchedEffect(wobbleTrigger) {
        if (wobbleTrigger > 0) {
            wobble.animateTo(0.85f, animationSpec = tween(90))
            wobble.animateTo(1f, animationSpec = tween(150))
        }
    }

    Box(
        modifier = Modifier
            .padding(6.dp)
            .size(56.dp)
            .scale(if (isBlank) wobble.value else 1f),
        contentAlignment = Alignment.Center,
    ) {
        if (isBlank || category == null) {
            // The single open slot: a dashed-look outline (drawn as a thin
            // ring, since a true dashed stroke needs no extra vocabulary
            // here) so it reads as "empty, waiting" without any text.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, KidPalette.OnSurface.copy(alpha = 0.35f), CircleShape),
            )
        } else {
            PieceGlyph(category = category, size = 56.dp)
        }
    }
}

@Composable
private fun ChoiceButton(category: Int, onTap: () -> Unit, modifier: Modifier = Modifier) {
    KidButton(onClick = onTap, modifier = modifier) {
        PieceGlyph(category = category, size = 48.dp)
    }
}

/** Renders a category as its fixed (shape, colour) pair -- shape always carries the meaning colour reinforces. */
@Composable
private fun PieceGlyph(category: Int, size: Dp) {
    val color = categoryColor(category)
    when (categoryShape(category)) {
        PatternShape.CIRCLE -> Box(
            modifier = Modifier.size(size).background(color, CircleShape),
        )

        PatternShape.SQUARE -> Box(
            modifier = Modifier.size(size).background(color, RoundedCornerShape(8.dp)),
        )

        PatternShape.TRIANGLE -> androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            drawTriangle(color)
        }
    }
}

private enum class PatternShape { CIRCLE, SQUARE, TRIANGLE }

private fun categoryShape(category: Int): PatternShape = when (category) {
    0 -> PatternShape.CIRCLE
    1 -> PatternShape.SQUARE
    else -> PatternShape.TRIANGLE
}

private fun categoryColor(category: Int): Color = when (category) {
    0 -> KidPalette.Blue
    1 -> KidPalette.Orange
    else -> KidPalette.Green
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
