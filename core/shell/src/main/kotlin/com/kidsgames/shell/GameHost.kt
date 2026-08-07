package com.kidsgames.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome

/**
 * Fixed clearance reserved along the bottom of the game's content area for
 * the exit control's footprint. [GameModule.Play] is composed inside a box
 * that stops this far above the bottom edge, so a game measuring and
 * placing its own content within the space it is handed cannot collide with
 * the exit button — it never sees the button, its bounds, or even that it
 * exists. This is the structural fix for findings across carrace,
 * colorsort, and matchshapes, where the exit button won hit-testing against
 * game content placed under it: each required a game author to remember a
 * hand-tuned exclusion zone, and each eventually forgot or miscalculated
 * it. See the cross-cutting note in docs/superpowers/PARKED.md recommending
 * exactly this: fixed bottom padding on the outer content, applied by the
 * shell rather than by each game.
 *
 * 96dp comfortably contains the button (a touch node no larger than
 * [MinTapTarget]) plus its 16dp padding on every side, with margin to
 * spare.
 */
val ExitZoneHeight = 96.dp

/**
 * Wraps a running [GameModule.Play] with the one thing every game in the
 * suite must offer and none of them are trusted to build themselves: a
 * visible, always-available way back to the picker.
 *
 * The exit control is a plain drawn arrow — no drawable resource, no text —
 * placed in the bottom-start corner, where a small hand holding the device
 * one-handed in portrait can reach it with a thumb, and away from the centre
 * of the screen where play happens so it is not brushed by accident.
 *
 * The game is composed inside a content box that excludes [ExitZoneHeight]
 * of space along the bottom edge; the exit button is composed after it, in
 * that reserved strip, so there is no rectangle in which both can draw and
 * ordering can no longer decide the winner of a hit test.
 */
@Composable
fun GameHost(
    game: GameModule,
    level: Int,
    onFinished: (Outcome) -> Unit,
    onExitRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = ExitZoneHeight)) {
            game.Play(level = level, onFinished = onFinished)
        }

        // KidButton's `modifier` param only reaches its inner visual layer,
        // never the outer touch-target node a Box needs for alignment to
        // take effect — `layoutModifier` is the one that lands on that outer
        // node, so it's the one that must carry `.align(...)`.
        KidButton(
            onClick = onExitRequested,
            layoutModifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            testTag = "exit_game_button",
        ) {
            ExitArrowIcon()
        }
    }
}

/** A plain drawn arrow-to-corner glyph. Picture only, no text, no resource. */
@Composable
private fun ExitArrowIcon() {
    Canvas(modifier = Modifier.size(MinTapTarget.times(0.5f))) {
        val stroke = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round)
        val color = KidPalette.OnSurface
        // Horizontal shaft.
        drawLine(
            color = color,
            start = Offset(size.width * 0.9f, size.height * 0.5f),
            end = Offset(size.width * 0.1f, size.height * 0.5f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        // Arrowhead pointing left/out, toward the picker.
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, size.height * 0.15f),
            end = Offset(size.width * 0.1f, size.height * 0.5f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, size.height * 0.85f),
            end = Offset(size.width * 0.1f, size.height * 0.5f),
            strokeWidth = stroke.width,
            cap = stroke.cap,
        )
    }
}
