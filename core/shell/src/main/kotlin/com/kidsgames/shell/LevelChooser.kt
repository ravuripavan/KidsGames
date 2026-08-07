package com.kidsgames.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.gameapi.GameModule
import androidx.compose.foundation.Canvas

/** The most levels any game in the suite has, and therefore the most dots or
 * chooser buttons ever drawn at once. Kept independent of [ProgressStore]'s
 * own private constant so this file has no compile dependency on it. */
const val MaxLevelsPerGame = 5

private val LevelDotSize = 7.dp
private val LevelDotSpacing = 5.dp
private val LevelDotStroke = 1.5.dp

/**
 * One-to-five dot row showing the highest level reached, per the spec text
 * verbatim: unlocked levels are dots, not text. A reached level draws as a
 * solid filled disc; an unreached one draws as a hollow ring of the same
 * size — a real shape difference (filled vs. outline), not merely a lighter
 * shade of the same colour, so the distinction survives for a colour-vision-
 * deficient child too.
 *
 * Fixed at [LevelDotSize] regardless of tile size: the row is always
 * `5x7 + 4x5 = 55dp` wide and `7dp` tall, comfortably inside the smallest
 * tile the picker ever lays out (the grid's own `minSize` floor is 80dp on
 * every axis, by construction of `GridCells.Adaptive`).
 */
@Composable
fun LevelDots(highestLevel: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.testTag("level_dots"),
        horizontalArrangement = Arrangement.spacedBy(LevelDotSpacing),
    ) {
        for (levelNumber in 1..MaxLevelsPerGame) {
            val reached = levelNumber <= highestLevel
            Box(
                modifier = Modifier
                    .size(LevelDotSize)
                    .then(
                        if (reached) {
                            Modifier.background(KidPalette.OnSurface, CircleShape)
                        } else {
                            Modifier.border(LevelDotStroke, KidPalette.OnSurface, CircleShape)
                        },
                    ),
            )
        }
    }
}

/**
 * Chooses the widest row of fixed-[buttonSize] level buttons (with
 * [spacing] between them) that still fits [availableWidth], falling back to
 * narrower rows only as far as it must. Buttons are never resized to fit —
 * only the column count changes — so the 64dp tap-target floor is
 * structural, never squeezed. Bottoms out at one column, which always fits
 * because [availableWidth] is assumed to be at least [buttonSize] (true at
 * every configuration this app runs on; a screen narrower than one 64dp
 * button is out of scope).
 */
fun chooserColumnsForWidth(
    levelCount: Int,
    availableWidth: Dp,
    buttonSize: Dp = MinTapTarget,
    spacing: Dp = 8.dp,
): Int {
    var columns = levelCount.coerceAtLeast(1)
    while (columns > 1) {
        val rowWidth = buttonSize * columns + spacing * (columns - 1)
        if (rowWidth <= availableWidth) break
        columns--
    }
    return columns
}

/** Rows needed to lay [levelCount] buttons out at [columns] per row. */
fun chooserRowsForColumns(levelCount: Int, columns: Int): Int =
    kotlin.math.ceil(levelCount.toFloat() / columns.toFloat()).toInt()

/**
 * Total width the panel needs at [columns] columns — mirrors exactly what
 * [LevelChooserOverlay] measures via `BoxWithConstraints` at runtime, so a
 * test can check the fit without instantiating Compose.
 */
fun chooserContentWidth(
    columns: Int,
    buttonSize: Dp = MinTapTarget,
    spacing: Dp = 8.dp,
    scrimMargin: Dp = 16.dp,
    panelPadding: Dp = 16.dp,
): Dp {
    val rowWidth = buttonSize * columns + spacing * (columns - 1).coerceAtLeast(0)
    return rowWidth + (scrimMargin + panelPadding) * 2
}

/**
 * Total height the chooser's button grid plus its cancel control needs,
 * given it has already been laid out at [rows] rows of fixed-size buttons
 * (one row of level buttons per row, then one more for the cancel
 * control) — mirrors exactly what [LevelChooserOverlay] lays out via a
 * `Column` of fixed-size children at runtime, so a test can check the fit
 * without instantiating Compose. The floor is structural because every
 * term here is a fixed size, never a size divided by a measured space.
 */
fun chooserContentHeight(
    rows: Int,
    buttonSize: Dp = MinTapTarget,
    spacing: Dp = 8.dp,
    scrimMargin: Dp = 16.dp,
    panelPadding: Dp = 16.dp,
): Dp {
    val childCount = rows + 1 // level-button rows, plus the cancel row
    val childrenHeight = buttonSize * childCount
    val gaps = spacing * rows // gaps between all childCount children
    return childrenHeight + gaps + (scrimMargin + panelPadding) * 2
}

/**
 * Full-screen modal revealing every level up to [highestLevel] as a grid of
 * plain dot-count buttons, so a child who prefers an earlier level can drop
 * back down without an adult. Escapable two ways, both obvious: the drawn
 * cancel control, and a tap anywhere on the scrim outside the button grid.
 * Neither ever fires [onLevelChosen] — a child who triggers this by
 * accident and does nothing but let go is never forced into a level.
 */
@Composable
fun LevelChooserOverlay(
    game: GameModule,
    highestLevel: Int,
    onLevelChosen: (GameModule, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Real measured constraints, not an assumed screen size: the overlay is
    // laid out inside whatever box KidsApp handed the picker (already net
    // of `safeDrawing`), and the panel below derives its column count from
    // THIS box rather than a hand-picked width — the mistake that parked
    // musicpad and countanimals.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .testTag("level_chooser_scrim")
            .pointerInput(game.id) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        val scrimMargin = 16.dp
        val panelPadding = 16.dp
        val panelAvailableWidth = maxWidth - scrimMargin * 2 - panelPadding * 2

        Column(
            modifier = Modifier
                .padding(scrimMargin)
                .background(KidPalette.Surface, RoundedCornerShape(24.dp))
                .padding(panelPadding)
                // Consume taps on the panel itself so they don't fall
                // through to the scrim's dismiss-on-tap behind it.
                .pointerInput(game.id) { detectTapGestures(onTap = {}) }
                .testTag("level_chooser_panel"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val columns = chooserColumnsForWidth(highestLevel, availableWidth = panelAvailableWidth)
            val rows = chooserRowsForColumns(highestLevel, columns)
            for (row in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until columns) {
                        val levelNumber = row * columns + col + 1
                        if (levelNumber <= highestLevel) {
                            KidButton(
                                onClick = { onLevelChosen(game, levelNumber) },
                                testTag = "level_choice_${game.id}_$levelNumber",
                            ) {
                                LevelDots(highestLevel = levelNumber, modifier = Modifier)
                            }
                        }
                    }
                }
            }

            KidButton(
                onClick = onDismiss,
                testTag = "level_chooser_cancel",
            ) {
                CancelGlyph()
            }
        }
    }
}

/** A plain drawn "X", picture only, no text — the obvious way back out of
 * the chooser for a child who opened it by accident. */
@Composable
private fun CancelGlyph() {
    Canvas(modifier = Modifier.size(MinTapTarget.times(0.5f))) {
        val stroke = Stroke(width = size.minDimension * 0.16f, cap = StrokeCap.Round)
        val color = KidPalette.OnSurface
        drawLine(
            color = color,
            start = Offset(size.width * 0.15f, size.height * 0.15f),
            end = Offset(size.width * 0.85f, size.height * 0.85f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.85f, size.height * 0.15f),
            end = Offset(size.width * 0.15f, size.height * 0.85f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}
