package com.kidsgames.tracelines

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.rememberSoundBank
import com.kidsgames.designkit.SoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay

/**
 * Trace a dotted path with a finger; the filled-in line grows behind the
 * finger as it travels the path. State machine: [TraceLineState], plain
 * Kotlin, no Android imports, pure/immutable -- every mutation returns a NEW
 * state, and this composable holds it in a single `mutableStateOf` per the
 * house rule (no separate version/trigger counter).
 *
 * LAYOUT BUDGET (write it down before building -- after safeDrawing insets
 * plus the shell's reserved 96dp bottom exit strip, GameHost hands this
 * composable roughly 360 x 496dp on a typical device; the exact figure
 * varies with insets, which is why the code below measures its real
 * BoxWithConstraints size rather than trusting a hardcoded number):
 *   - Outer padding: 24dp on every side.
 *   - Available content box: roughly (360 - 48) x (496 - 48) = 312 x 448dp.
 *   - The trace square is sized to the SMALLER of those two via
 *     `minOf(maxWidth, maxHeight)` from the real BoxWithConstraints below,
 *     and centred in the remaining box -- verified to leave well over 100dp
 *     of unused vertical space split above/below (deliberate breathing
 *     room, not a packed fit, per "leave real margin").
 *   - 312dp comfortably clears the 64dp minimum touch target many times
 *     over; nothing here is smaller than 64dp because the trace itself, not
 *     a small button, is the touch surface.
 * Nothing scrolls: the whole path is always laid out inside this single
 * fixed square, at every level, so there is no "content below the fold".
 *
 * ZERO-VOLUME PLAYABILITY: every path dot is always drawn, at full opacity,
 * whether filled or not, so a child with the volume off can see exactly
 * where they've been and exactly where to go next. The current frontier dot
 * additionally pulses (an [Animatable] independent of the fill state) so the
 * "go here next" cue is visible motion, not just a static difference in
 * color. Straying off the path produces a visible amber flash across the
 * whole trace square (`strayFlash`) -- never merely `Cue.GENTLE_RETRY`,
 * which would be silent with the volume off.
 *
 * TRACING TOLERANCE: [TraceLineState.TOLERANCE] is a single fixed constant
 * used at every level, deliberately generous, and it NEVER shrinks as levels
 * rise -- see [TraceLineState]'s own KDoc. Difficulty grows only through
 * [TraceLineState.dotCountFor] (more, and more complex, path geometry), never
 * through tightened tolerance, time pressure, or any kind of penalty.
 */
object TraceLinesGame : GameModule {

    override val id: String = "tracelines"
    override val icon: Int = R.drawable.ic_tracelines
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 3
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()

        // Immutable state machine: trace() returns a NEW TraceLineState.
        // Writing it back to this `var` is what drives recomposition; there
        // is no separate version/trigger counter anywhere in this module.
        var state by remember(level) { mutableStateOf(TraceLineState.initial(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        var lastWobbleSeen by remember(level) { mutableIntStateOf(0) }

        LaunchedEffect(state.finished) {
            if (state.finished && !celebrating) {
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val squareSide = minOf(maxWidth, maxHeight)

                TraceSquare(
                    state = state,
                    sideDp = squareSide,
                    enabled = !celebrating,
                    onTrace = { vx, vy ->
                        val before = state
                        state = state.trace(vx, vy)
                        if (state.wobbleTrigger != lastWobbleSeen) {
                            lastWobbleSeen = state.wobbleTrigger
                            soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                        } else if (state.filledUpTo != before.filledUpTo) {
                            soundBank.play(SoundBank.Cue.TAP)
                        }
                    },
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

/**
 * The square trace surface. Owns the mapping between real touch pixels and
 * [TraceLineState]'s virtual [TraceLineState.VIRTUAL_SIZE] coordinate space,
 * and drives two purely-visual [Animatable]s -- a pulsing frontier dot and a
 * stray-touch flash -- that never affect [TraceLineState] itself.
 */
@Composable
private fun TraceSquare(
    state: TraceLineState,
    sideDp: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onTrace: (Float, Float) -> Unit,
) {
    // Pulses continuously to draw the eye to "go here next" -- independent
    // of any trigger, so it's always animating rather than static.
    val pulse = remember(state.level) { Animatable(0f) }
    LaunchedEffect(state.level) {
        while (true) {
            pulse.animateTo(1f, animationSpec = tween(500))
            pulse.animateTo(0f, animationSpec = tween(500))
        }
    }

    // Keyed by level (not by an always-incrementing trigger alone) and
    // driven by a CHANGE in wobbleTrigger below, so this never replays on
    // an unrelated recomposition.
    val strayFlash = remember(state.level) { Animatable(0f) }
    var lastFlashedTrigger by remember(state.level) { mutableIntStateOf(state.wobbleTrigger) }
    LaunchedEffect(state.wobbleTrigger) {
        if (state.wobbleTrigger != lastFlashedTrigger) {
            lastFlashedTrigger = state.wobbleTrigger
            strayFlash.snapTo(1f)
            strayFlash.animateTo(0f, animationSpec = tween(350))
        }
    }

    Box(
        modifier = Modifier
            .size(sideDp)
            // TWO CHAINED pointerInput modifiers, not one block launching two
            // detectors. A single block would hit `coroutineScope { launch; launch }`,
            // and `launch` defaults to a DISPATCHED start on AndroidUiDispatcher --
            // so neither detector has registered when the first DOWN is dispatched,
            // and the entire first stroke after entering a level is silently
            // swallowed. Two modifiers give two pointer-input nodes, each
            // registering synchronously. Same shape as games/matchshapes.
            .pointerInput(state.level, enabled) {
                if (!enabled) return@pointerInput
                val scale = TraceLineState.VIRTUAL_SIZE / size.width.toFloat()
                detectDragGestures(
                    onDragStart = { start ->
                        onTrace(start.x * scale, start.y * scale)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        onTrace(change.position.x * scale, change.position.y * scale)
                    },
                )
            }
            .pointerInput(state.level, enabled) {
                if (!enabled) return@pointerInput
                val scale = TraceLineState.VIRTUAL_SIZE / size.width.toFloat()
                detectTapGestures(
                    onTap = { tap -> onTrace(tap.x * scale, tap.y * scale) },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = size.width / TraceLineState.VIRTUAL_SIZE
            fun toPx(p: TracePoint): Offset = Offset(p.x * scale, p.y * scale)

            // Stray flash: a soft amber wash over the whole square, visible
            // with the volume off, never a fail state -- just a nudge.
            if (strayFlash.value > 0f) {
                drawRect(color = KidPalette.Orange.copy(alpha = strayFlash.value * 0.22f))
            }

            // Every dot is always drawn, filled or not -- nothing is ever
            // removed from the layout or hidden. Unfilled dots are drawn
            // FIRST and filled dots SECOND so a closed shape whose start and
            // end share a coordinate (e.g. level 4's square) still shows the
            // filled "begin here" marker on top, rather than a later grey
            // unfilled dot painting over it.
            val dotRadius = 6.dp.toPx()
            state.path.forEachIndexed { index, point ->
                if (index > state.filledUpTo) {
                    drawCircle(
                        color = KidPalette.OnSurface.copy(alpha = 0.25f),
                        radius = dotRadius,
                        center = toPx(point),
                    )
                }
            }
            state.path.forEachIndexed { index, point ->
                if (index <= state.filledUpTo) {
                    drawCircle(
                        color = KidPalette.Blue,
                        radius = dotRadius,
                        center = toPx(point),
                    )
                }
            }

            // The filled-in line itself, drawn as a thick stroke through all
            // filled points -- this IS "the line filling in behind" the
            // finger.
            if (state.filledUpTo > 0) {
                val path = Path()
                val first = toPx(state.path.first())
                path.moveTo(first.x, first.y)
                for (i in 1..state.filledUpTo) {
                    val p = toPx(state.path[i])
                    path.lineTo(p.x, p.y)
                }
                drawPath(
                    path = path,
                    color = KidPalette.Blue,
                    style = Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
            }

            // Pulsing frontier marker: shows exactly where to go next, as
            // visible motion rather than a static color difference alone.
            if (!state.finished) {
                val frontierIndex = (state.filledUpTo + 1).coerceAtMost(state.path.lastIndex)
                val frontier = toPx(state.path[frontierIndex])
                val pulseRadius = dotRadius * (1.6f + pulse.value * 0.8f)
                drawCircle(
                    color = KidPalette.Yellow.copy(alpha = 0.9f - pulse.value * 0.3f),
                    radius = pulseRadius,
                    center = frontier,
                )
            }
        }
    }
}
