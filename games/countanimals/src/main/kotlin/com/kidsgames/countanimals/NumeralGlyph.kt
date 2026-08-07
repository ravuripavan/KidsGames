package com.kidsgames.countanimals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Digits are allowed in this game (the spec's numeral exception), but they
 * must never be rendered via `Text(...)` -- the repo-wide no-text build gate
 * fails on any literal passed to `Text`. This file draws every digit as a
 * seven-segment glyph on a [Canvas], the same approach [Celebration]'s
 * `Star` uses for its shape: a vector path, not typeset text, so rendering
 * is identical everywhere regardless of font or emoji-fallback behaviour.
 */
private enum class Segment { TOP, TOP_LEFT, TOP_RIGHT, MIDDLE, BOTTOM_LEFT, BOTTOM_RIGHT, BOTTOM }

private val DIGIT_SEGMENTS: Map<Int, Set<Segment>> = mapOf(
    0 to setOf(Segment.TOP, Segment.TOP_LEFT, Segment.TOP_RIGHT, Segment.BOTTOM_LEFT, Segment.BOTTOM_RIGHT, Segment.BOTTOM),
    1 to setOf(Segment.TOP_RIGHT, Segment.BOTTOM_RIGHT),
    2 to setOf(Segment.TOP, Segment.TOP_RIGHT, Segment.MIDDLE, Segment.BOTTOM_LEFT, Segment.BOTTOM),
    3 to setOf(Segment.TOP, Segment.TOP_RIGHT, Segment.MIDDLE, Segment.BOTTOM_RIGHT, Segment.BOTTOM),
    4 to setOf(Segment.TOP_LEFT, Segment.TOP_RIGHT, Segment.MIDDLE, Segment.BOTTOM_RIGHT),
    5 to setOf(Segment.TOP, Segment.TOP_LEFT, Segment.MIDDLE, Segment.BOTTOM_RIGHT, Segment.BOTTOM),
    6 to setOf(Segment.TOP, Segment.TOP_LEFT, Segment.MIDDLE, Segment.BOTTOM_LEFT, Segment.BOTTOM_RIGHT, Segment.BOTTOM),
    7 to setOf(Segment.TOP, Segment.TOP_RIGHT, Segment.BOTTOM_RIGHT),
    8 to Segment.entries.toSet(),
    9 to setOf(Segment.TOP, Segment.TOP_LEFT, Segment.TOP_RIGHT, Segment.MIDDLE, Segment.BOTTOM_RIGHT, Segment.BOTTOM),
)

/**
 * Draws a single digit 0-9 as a seven-segment glyph. Anything outside 0-9
 * draws no segments (an intentionally blank glyph) rather than throwing, so
 * a caller passing a stray value fails silently instead of crashing a
 * child's game.
 */
@Composable
fun NumeralGlyph(digit: Int, modifier: Modifier = Modifier, color: Color, width: Dp = 36.dp, height: Dp = 56.dp) {
    Canvas(modifier = modifier.width(width).height(height)) {
        val segments = DIGIT_SEGMENTS[digit] ?: return@Canvas
        drawSevenSegmentDigit(segments, color)
    }
}

/**
 * Draws a whole non-negative number as a row of [NumeralGlyph]s, one per
 * digit, so counts above 9 (this game never exceeds 10) render correctly
 * without any special-casing by callers.
 */
@Composable
fun NumberGlyph(value: Int, modifier: Modifier = Modifier, color: Color, digitWidth: Dp = 36.dp, digitHeight: Dp = 56.dp) {
    val digits = value.coerceAtLeast(0).toString().map { it - '0' }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(digitWidth * 0.18f)) {
        digits.forEach { digit ->
            NumeralGlyph(digit = digit, color = color, width = digitWidth, height = digitHeight)
        }
    }
}

private fun DrawScope.drawSevenSegmentDigit(segments: Set<Segment>, color: Color) {
    val strokeWidth = size.width * 0.22f
    val w = size.width
    val h = size.height
    val midY = h / 2f
    val inset = strokeWidth / 2f

    fun horizontal(y: Float) = Offset(inset, y) to Offset(w - inset, y)
    fun vertical(x: Float, yTop: Float, yBottom: Float) = Offset(x, yTop) to Offset(x, yBottom)

    val lines: Map<Segment, Pair<Offset, Offset>> = mapOf(
        Segment.TOP to horizontal(inset),
        Segment.MIDDLE to horizontal(midY),
        Segment.BOTTOM to horizontal(h - inset),
        Segment.TOP_LEFT to vertical(inset, inset, midY),
        Segment.BOTTOM_LEFT to vertical(inset, midY, h - inset),
        Segment.TOP_RIGHT to vertical(w - inset, inset, midY),
        Segment.BOTTOM_RIGHT to vertical(w - inset, midY, h - inset),
    )

    segments.forEach { segment ->
        val (start, end) = lines.getValue(segment)
        drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
}
