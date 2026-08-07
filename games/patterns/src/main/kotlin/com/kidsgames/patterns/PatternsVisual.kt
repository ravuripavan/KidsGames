package com.kidsgames.patterns

import androidx.compose.ui.graphics.Color
import com.kidsgames.designkit.KidPalette

/**
 * Pure (level, category) -> (shape, colour) mapping, deliberately split out
 * from the Composables in `PatternsGame.kt` so it is unit-testable on the
 * plain JVM without an emulator (see `PatternsVisualTest`).
 *
 * The device finding this fixes: L4 and L5 both rendered category 0 as a
 * blue circle, category 1 as an orange square and category 2 as a green
 * triangle, so a longer L5 sequence was the ONLY thing distinguishing it
 * from L4 -- a child could not tell the levels apart at a glance. L5 now
 * uses a genuinely different (shape, colour) set than L4: different colours
 * AND a different shape-per-category assignment, while still giving every
 * category its own distinct shape (never colour alone), so a colour-blind
 * child can still read L5 exactly as they read every other level.
 */
enum class PatternShape { CIRCLE, SQUARE, TRIANGLE }

data class PatternVisual(val shape: PatternShape, val color: Color)

object PatternsVisual {

    // L1-L4 share this set: circle/blue, square/orange, triangle/green.
    private val standardSet = listOf(
        PatternVisual(PatternShape.CIRCLE, KidPalette.Blue),
        PatternVisual(PatternShape.SQUARE, KidPalette.Orange),
        PatternVisual(PatternShape.TRIANGLE, KidPalette.Green),
    )

    // L5's own set: every shape and colour differs from `standardSet`, and
    // the shape<->category assignment is rearranged too (not just recoloured
    // in place), so it reads as its own distinct pattern rather than a
    // longer L4.
    private val level5Set = listOf(
        PatternVisual(PatternShape.SQUARE, KidPalette.Purple),
        PatternVisual(PatternShape.TRIANGLE, KidPalette.Pink),
        PatternVisual(PatternShape.CIRCLE, KidPalette.Yellow),
    )

    fun visualFor(level: Int, category: Int): PatternVisual {
        val set = if (level == 5) level5Set else standardSet
        return set[category % set.size]
    }
}
