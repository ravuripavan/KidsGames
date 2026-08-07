package com.kidsgames.musicpad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.designkit.rememberSoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * A pad sandbox: tapping a pad sounds a note (once real recordings land --
 * see [padResId]) and plays a distinct animation. This is a SANDBOX: there
 * is no correct note, no wrong tap, and nothing to fail. [Play] calls
 * [onFinished] with [Outcome.Completed] unconditionally after a few minutes
 * of open play, never gated on the child having done anything in particular.
 *
 * Levels unlock TOOLS, never difficulty:
 * - L1: 4 pads.
 * - L2: 6 pads.
 * - L3: 8 pads.
 * - L4: 8 pads plus a second instrument (a switcher appears).
 * - L5: 8 pads, 2 instruments, plus record-and-replay.
 *
 * Layout budget (measured via [BoxWithConstraints], not hardcoded): the
 * shell hands this module roughly 360x496dp after `safeDrawing` insets and
 * the exit strip. The worst case is L4/L5: 8 pads in a 4-column x 2-row
 * grid, PLUS an instrument-switcher row, PLUS a record/replay row.
 * Reserving 72dp for each of those two control rows plus 8dp of spacing
 * between them and the grid leaves roughly 496 - 72 - 8 - 72 - 8 = 336dp of
 * height and the full ~360dp of width for the pad grid itself. Splitting
 * that into 4 columns and 2 rows with 8dp gutters on all sides gives each
 * pad roughly (360 - 5*8)/4 = 80dp wide by (336 - 3*8)/2 = 156dp tall --
 * both comfortably above the 64dp [MinTapTarget] floor, with real dead
 * space between pads so a clumsy hand cannot land on two at once.
 */
object MusicPadGame : GameModule {

    override val id: String = "musicpad"
    override val icon: Int = R.drawable.ic_musicpad
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    /** How long a session runs before this sandbox reports completion, unconditionally. */
    private const val SANDBOX_MILLIS = 180_000L

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val lifecycleOwner = LocalLifecycleOwner.current

        var state by remember(level) { mutableStateOf(MusicPadState.initial(level)) }

        // Fake wall clock for record/replay timestamps -- plain millis
        // counted up by this composable, never touching a real system
        // clock inside the pure state machine.
        var clockMillis by remember(level) { mutableStateOf(0L) }

        LaunchedEffect(level) {
            var elapsed = 0L
            while (elapsed < SANDBOX_MILLIS) {
                lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                delay(1000L)
                elapsed += 1000L
                clockMillis += 1000L
            }
            onFinished(Outcome.Completed)
        }

        // Replays the current recording tap by tap, honouring the original
        // spacing between taps. An empty recording finishes this loop
        // immediately -- silence is a perfectly valid replay.
        LaunchedEffect(state.isReplaying) {
            if (state.isReplaying) {
                var previousOffset = 0L
                for (event in state.recordedEvents) {
                    lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                    val gap = (event.elapsedMillis - previousOffset).coerceAtLeast(0L)
                    delay(gap)
                    previousOffset = event.elapsedMillis
                    state = state.replayTap(event)
                    soundBank.playRaw(padResId(event.instrumentIndex, event.padId))
                }
                state = state.stopReplay()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(KidPalette.Background)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.instrumentCount > 1) {
                        InstrumentRow(
                            instrumentCount = state.instrumentCount,
                            selected = state.selectedInstrument,
                            onSelect = { index ->
                                if (!state.isReplaying) {
                                    state = state.selectInstrument(index)
                                    soundBank.play(SoundBank.Cue.TAP)
                                }
                            },
                        )
                        Box(modifier = Modifier.height(8.dp))
                    }

                    if (state.recordingSupported) {
                        RecordReplayRow(
                            isRecording = state.isRecording,
                            isReplaying = state.isReplaying,
                            hasRecording = state.recordedEvents.isNotEmpty(),
                            onToggleRecord = {
                                soundBank.play(SoundBank.Cue.TAP)
                                state = if (state.isRecording) {
                                    state.stopRecording()
                                } else {
                                    clockMillis = 0L
                                    state.startRecording(atMillis = 0L)
                                }
                            },
                            onPlay = {
                                if (!state.isRecording && !state.isReplaying) {
                                    soundBank.play(SoundBank.Cue.TAP)
                                    state = state.startReplay()
                                }
                            },
                        )
                        Box(modifier = Modifier.height(8.dp))
                    }

                    PadGrid(
                        padCount = state.padCount,
                        instrument = state.selectedInstrument,
                        tapTriggers = state.tapTriggers,
                        onTap = { padId ->
                            if (!state.isReplaying) {
                                state = state.tapPad(padId, atMillis = clockMillis)
                                soundBank.playRaw(padResId(state.selectedInstrument, padId))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    /**
     * Placeholder per-pad, per-instrument audio resource id. No note or
     * instrument recordings exist yet -- [SoundBank.playRaw] silently no-ops
     * on any id it cannot load, so this is silent today and starts working
     * unchanged the moment real bundled recordings land at these ids.
     */
    private fun padResId(instrument: Int, padId: Int): Int = 1000 + instrument * 100 + padId
}

@Composable
private fun InstrumentRow(instrumentCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        for (index in 0 until instrumentCount) {
            KidButton(
                onClick = { onSelect(index) },
                testTag = "instrument-$index",
                layoutModifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
            ) {
                InstrumentBadge(index = index, selected = selected == index)
            }
        }
    }
}

@Composable
private fun InstrumentBadge(index: Int, selected: Boolean) {
    val color = if (index == 0) KidPalette.Blue else KidPalette.Purple
    val badgeScale = if (selected) 1f else 0.72f
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(badgeScale)
            .clip(if (index == 0) CircleShape else RoundedCornerShape(8.dp))
            .background(color),
    )
}

@Composable
private fun RecordReplayRow(
    isRecording: Boolean,
    isReplaying: Boolean,
    hasRecording: Boolean,
    onToggleRecord: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
        KidButton(
            onClick = onToggleRecord,
            testTag = "record-toggle",
            layoutModifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
        ) {
            RecordBadge(active = isRecording)
        }
        KidButton(
            onClick = onPlay,
            testTag = "replay",
            layoutModifier = Modifier.weight(1f).fillMaxSize().padding(4.dp),
        ) {
            ReplayBadge(active = isReplaying, hasRecording = hasRecording)
        }
    }
}

@Composable
private fun RecordBadge(active: Boolean) {
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            while (true) {
                pulse.animateTo(1.25f, animationSpec = tween(420))
                pulse.animateTo(1f, animationSpec = tween(420))
            }
        } else {
            pulse.snapTo(1f)
        }
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(pulse.value)
            .clip(CircleShape)
            .background(KidPalette.Red.copy(alpha = if (active) 1f else 0.55f)),
    )
}

@Composable
private fun ReplayBadge(active: Boolean, hasRecording: Boolean) {
    val alpha = when {
        active -> 1f
        hasRecording -> 0.85f
        else -> 0.4f
    }
    Canvas(modifier = Modifier.size(36.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.12f)
            lineTo(size.width * 0.22f, size.height * 0.88f)
            lineTo(size.width * 0.88f, size.height * 0.5f)
            close()
        }
        drawPath(path, color = KidPalette.Green.copy(alpha = alpha))
    }
}

/**
 * Lays out [padCount] pads in a grid that never scrolls: columns and rows
 * are picked from the pad count itself, and every pad gets a share of
 * whatever space [BoxWithConstraints] measured, never a hardcoded size.
 */
@Composable
private fun PadGrid(
    padCount: Int,
    instrument: Int,
    tapTriggers: Map<Pair<Int, Int>, Int>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = when {
        padCount <= 4 -> 2
        padCount <= 6 -> 3
        else -> 4
    }
    val rows = ceil(padCount / columns.toFloat()).toInt()

    Column(modifier = modifier) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until columns) {
                    val padId = row * columns + col
                    if (padId < padCount) {
                        Pad(
                            padId = padId,
                            instrument = instrument,
                            trigger = tapTriggers[instrument to padId] ?: 0,
                            onTap = { onTap(padId) },
                            layoutModifier = Modifier.weight(1f).fillMaxSize().padding(6.dp),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
    }
}

/** The eight visually-distinct shape identities, cycled by pad index. */
private enum class PadShape { CIRCLE, SQUARE, TRIANGLE, DIAMOND, HEXAGON, STAR, CROSS, CHEVRON }

private fun shapeFor(padId: Int): PadShape = PadShape.entries[padId % PadShape.entries.size]

private fun colorFor(padId: Int): Color = KidPalette.Swatch[padId % KidPalette.Swatch.size]

@Composable
private fun Pad(
    padId: Int,
    instrument: Int,
    trigger: Int,
    onTap: () -> Unit,
    layoutModifier: Modifier = Modifier,
) {
    // Keyed per (instrument, pad): a second instrument gets its own,
    // independent animation state even when it reuses the same pad index.
    val bounce = remember(instrument, padId) { Animatable(1f) }
    val rise = remember(instrument, padId) { Animatable(0f) }

    // Driven by a CHANGE in trigger (its new value as the key), not by
    // `if (trigger > 0)` inside a key on something else -- this fires
    // exactly once per tap, including the very first one.
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            bounce.snapTo(0.8f)
            bounce.animateTo(1f, animationSpec = tween(220))
            rise.snapTo(0f)
            rise.animateTo(1f, animationSpec = tween(500))
        }
    }

    KidButton(
        onClick = onTap,
        testTag = "pad-$instrument-$padId",
        layoutModifier = layoutModifier,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Rising, fading trace of the note just played -- the primary,
            // always-present carrier of "something happened here" even at
            // zero volume.
            if (rise.value in 0f..0.999f && rise.value > 0f) {
                Box(
                    modifier = Modifier
                        .padding(bottom = (rise.value * 46).dp)
                        .size(14.dp)
                        .scale(1f + rise.value * 0.6f)
                        .clip(CircleShape)
                        .background(colorFor(padId).copy(alpha = 1f - rise.value)),
                )
            }

            PadShapeView(
                shape = shapeFor(padId),
                color = colorFor(padId),
                ringed = instrument == 1,
                scale = bounce.value,
            )
        }
    }
}

@Composable
private fun PadShapeView(shape: PadShape, color: Color, ringed: Boolean, scale: Float) {
    Canvas(
        modifier = Modifier
            .size(MinTapTarget * 0.6f)
            .scale(scale),
    ) {
        drawPadShape(shape, color)
        if (ringed) {
            drawCircle(
                color = KidPalette.OnSurface.copy(alpha = 0.55f),
                radius = min(size.width, size.height) / 2f + 5.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawPadShape(shape: PadShape, color: Color) {
    when (shape) {
        PadShape.CIRCLE -> drawCircle(color = color, radius = min(size.width, size.height) / 2f)
        PadShape.SQUARE -> drawPath(rectPath(size.width, size.height), color)
        PadShape.TRIANGLE -> drawPath(polygonPath(size.width, size.height, sides = 3), color)
        PadShape.DIAMOND -> drawPath(polygonPath(size.width, size.height, sides = 4, rotationOffset = PI.toFloat() / 4f), color)
        PadShape.HEXAGON -> drawPath(polygonPath(size.width, size.height, sides = 6), color)
        PadShape.STAR -> drawPath(starPath(size.width, size.height, points = 5), color)
        PadShape.CROSS -> drawCrossPath(this, color)
        PadShape.CHEVRON -> drawPath(polygonPath(size.width, size.height, sides = 3, rotationOffset = PI.toFloat()), color)
    }
}

private fun rectPath(width: Float, height: Float): Path = Path().apply {
    val inset = min(width, height) * 0.06f
    addRect(androidx.compose.ui.geometry.Rect(inset, inset, width - inset, height - inset))
}

private fun polygonPath(width: Float, height: Float, sides: Int, rotationOffset: Float = 0f): Path {
    val radius = min(width, height) / 2f
    val center = Offset(width / 2f, height / 2f)
    val path = Path()
    val angleStep = 2 * PI / sides
    for (i in 0 until sides) {
        val angle = -PI / 2 + rotationOffset + i * angleStep
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun starPath(width: Float, height: Float, points: Int): Path {
    val outerRadius = min(width, height) / 2f
    val innerRadius = outerRadius * 0.42f
    val center = Offset(width / 2f, height / 2f)
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
    return path
}

private fun drawCrossPath(scope: DrawScope, color: Color) {
    val width = scope.size.width
    val height = scope.size.height
    val thickness = min(width, height) * 0.32f
    val halfThickness = thickness / 2f
    val centerX = width / 2f
    val centerY = height / 2f
    val path = Path().apply {
        moveTo(centerX - halfThickness, 0f)
        lineTo(centerX + halfThickness, 0f)
        lineTo(centerX + halfThickness, centerY - halfThickness)
        lineTo(width, centerY - halfThickness)
        lineTo(width, centerY + halfThickness)
        lineTo(centerX + halfThickness, centerY + halfThickness)
        lineTo(centerX + halfThickness, height)
        lineTo(centerX - halfThickness, height)
        lineTo(centerX - halfThickness, centerY + halfThickness)
        lineTo(0f, centerY + halfThickness)
        lineTo(0f, centerY - halfThickness)
        lineTo(centerX - halfThickness, centerY - halfThickness)
        close()
    }
    scope.drawPath(path, color = color)
}
