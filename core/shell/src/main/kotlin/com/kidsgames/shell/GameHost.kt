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
 * Wraps a running [GameModule.Play] with the one thing every game in the
 * suite must offer and none of them are trusted to build themselves: a
 * visible, always-available way back to the picker.
 *
 * The exit control is a plain drawn arrow — no drawable resource, no text —
 * placed in the bottom-start corner, where a small hand holding the device
 * one-handed in portrait can reach it with a thumb, and away from the centre
 * of the screen where play happens so it is not brushed by accident.
 */
@Composable
fun GameHost(
    game: GameModule,
    level: Int,
    onFinished: (Outcome) -> Unit,
    onExitRequested: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        game.Play(level = level, onFinished = onFinished)

        // KidButton deliberately does not let a caller's modifier reach its
        // outer, real-touch-target node (see its own doc), so `.align(...)`
        // cannot be passed to KidButton directly — it would land on an inner
        // layer and be ignored by this Box. Wrapping KidButton in a plain
        // Box that IS a direct child of this Box is what makes the
        // alignment apply.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            KidButton(
                onClick = onExitRequested,
                testTag = "exit_game_button",
            ) {
                ExitArrowIcon()
            }
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
