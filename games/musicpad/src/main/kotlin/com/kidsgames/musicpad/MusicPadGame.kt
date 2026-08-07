package com.kidsgames.musicpad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import android.os.SystemClock
import com.kidsgames.designkit.Celebration
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
 * Layout budget: [PadGrid] owns its own [BoxWithConstraints] and reads BOTH
 * `maxWidth` and `maxHeight` there -- not just width -- because on a short
 * or narrow-and-tall screen, or at a raised system Display (font/density)
 * size, the vertical budget runs out before the horizontal one does. The
 * worst case is L4/L5: 8 pads PLUS one control row (see [InstrumentRow] /
 * [RecordReplayRow], each exactly [MinTapTarget] tall -- no slack for
 * `weight` to eat into). [PadGrid] never assumes 4 columns fit; it walks
 * columns down from the natural cap until every pad, measured in BOTH
 * dimensions, would be at least [MinTapTarget], which happens on narrower or
 * shorter phones and at raised Display sizes well before the layout runs out
 * of room to try.
 */
object MusicPadGame : GameModule {

    override val id: String = "musicpad"
    override val icon: Int = R.drawable.ic_musicpad
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    /** How long a session runs before this sandbox reports completion, unconditionally. */
    private const val SANDBOX_MILLIS = 180_000L

    /**
     * The longest a single gap between two replayed taps is allowed to run.
     * Real gaps between taps are preserved up to this cap; anything longer
     * (a child who wandered off for a minute mid-recording) is clamped so
     * replay never leaves the grid locked and silent for longer than a young
     * child will wait.
     */
    private const val MAX_REPLAY_GAP_MILLIS = 2_500L

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val lifecycleOwner = LocalLifecycleOwner.current
        // Hoisted once per lifecycleOwner rather than read as a property
        // inside the loops below: `currentStateFlow` is a GETTER that
        // allocates a fresh observing StateFlow on every call, so reading it
        // per-iteration (about 180 times over a sandbox session, plus once
        // per replayed tap) leaked one observer per read. Reading it once
        // here and reusing the same flow collects on the same observer for
        // the whole composition.
        val lifecycleStates = remember(lifecycleOwner) { lifecycleOwner.lifecycle.currentStateFlow }

        var state by remember(level) { mutableStateOf(MusicPadState.initial(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        LaunchedEffect(level) {
            var elapsed = 0L
            while (elapsed < SANDBOX_MILLIS) {
                lifecycleStates.first { it.isAtLeast(Lifecycle.State.STARTED) }
                delay(1000L)
                elapsed += 1000L
            }
            celebrating = true
            delay(900L)
            // Re-await STARTED: the delay above can itself span a
            // backgrounding, and without re-checking here `onFinished` could
            // fire while nothing is on screen to show it happened.
            lifecycleStates.first { it.isAtLeast(Lifecycle.State.STARTED) }
            onFinished(Outcome.Completed)
        }

        // Replays the current recording tap by tap, honouring the original
        // spacing between taps. An empty recording finishes this loop
        // immediately -- silence is a perfectly valid replay. The FIRST
        // event's own offset is subtracted out below so replay starts the
        // instant it is pressed, rather than opening with a frozen pause as
        // long as the gap between pressing record and the child's first tap.
        // A cap on any single gap keeps one long real-world pause between
        // taps from locking the grid for the same length of time on replay.
        LaunchedEffect(state.isReplaying) {
            if (state.isReplaying) {
                val firstOffset = state.recordedEvents.firstOrNull()?.elapsedMillis ?: 0L
                var previousOffset = firstOffset
                for (event in state.recordedEvents) {
                    lifecycleStates.first { it.isAtLeast(Lifecycle.State.STARTED) }
                    val gap = (event.elapsedMillis - previousOffset).coerceIn(0L, MAX_REPLAY_GAP_MILLIS)
                    delay(gap)
                    previousOffset = event.elapsedMillis
                    state = state.replayTap(event)
                    soundBank.playRaw(padResId(event.instrumentIndex, event.padId))
                }
                state = state.stopReplay()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(KidPalette.Background)) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
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
                                state.startRecording(atMillis = SystemClock.elapsedRealtime())
                            }
                        },
                        onPlay = {
                            soundBank.play(SoundBank.Cue.TAP)
                            state = if (state.isReplaying) {
                                // A second press of replay stops it -- an
                                // in-progress playback must never be able to
                                // lock the grid for its full, unbounded
                                // length with no way back.
                                state.stopReplay()
                            } else {
                                // Reaching for replay while recording is
                                // armed must still do something visible --
                                // finish the recording, then play it back,
                                // rather than silently ignoring the tap.
                                val ready = if (state.isRecording) state.stopRecording() else state
                                ready.startReplay()
                            }
                        },
                    )
                }

                PadGrid(
                    padCount = state.padCount,
                    instrument = state.selectedInstrument,
                    tapTriggers = state.tapTriggers,
                    onTap = { padId ->
                        if (!state.isReplaying) {
                            state = state.tapPad(padId, atMillis = SystemClock.elapsedRealtime())
                            soundBank.playRaw(padResId(state.selectedInstrument, padId))
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level >= 5)
            }
        }
    }

    /**
     * Placeholder per-pad, per-instrument audio resource id. These are NOT
     * real Android resource ids -- real resource ids are generated as
     * `0x7f######`, while this returns small literal ints (1000-1107) that
     * can never resolve to any resource, now or later. [SoundBank.playRaw]
     * silently no-ops on any id it cannot load, so note audio is silent
     * today and stays silent regardless of what lands in `res/raw`. Wiring
     * real note audio requires replacing this function's body with a lookup
     * into actual `R.raw.*` ids once bundled note recordings are added to
     * this module -- nothing here does that automatically.
     */
    private fun padResId(instrument: Int, padId: Int): Int = 1000 + instrument * 100 + padId
}

/**
 * Every control row is exactly [MinTapTarget] tall, with no padding on the
 * buttons themselves -- a control row taller than the floor (the old 72dp)
 * only existed to leave room for an 4dp inset on each button so its OWN
 * `defaultMinSize` could still hold 64dp after that inset. `weight` hands a
 * button an EXACT slice of the row, and `defaultMinSize` cannot grow past an
 * exact constraint -- so any padding eaten from that exact slice comes
 * straight out of the touch target. Removing the inset and using
 * [ControlButtonGap] as an explicit spacer instead keeps every control
 * button at the full floor while freeing height for [PadGrid].
 */
private val ControlRowHeight = MinTapTarget

/** Fixed gap between adjacent control buttons -- wide enough that a clumsy tap for one cannot clip its neighbour. */
private val ControlButtonGap = 40.dp

@Composable
private fun InstrumentRow(instrumentCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().height(ControlRowHeight)) {
        for (index in 0 until instrumentCount) {
            if (index > 0) {
                Box(modifier = Modifier.width(ControlButtonGap))
            }
            KidButton(
                onClick = { onSelect(index) },
                testTag = "instrument-$index",
                layoutModifier = Modifier.weight(1f).fillMaxSize(),
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
    val shape = if (index == 0) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(badgeScale)
            .clip(shape)
            .background(color)
            .let {
                // Unselected badges also lose their border, not just size --
                // scale alone is too subtle a signal at 40dp.
                if (selected) {
                    it.border(width = 3.dp, color = KidPalette.OnSurface.copy(alpha = 0.6f), shape = shape)
                } else {
                    it
                }
            },
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
    // A wide, dead-space gap sits between record and replay -- deliberately,
    // since these are opposite actions (one can erase the tune, the other
    // only plays it) and a clumsy hand aiming for one must not be able to
    // clip the other.
    Row(modifier = Modifier.fillMaxWidth().height(ControlRowHeight)) {
        KidButton(
            onClick = onToggleRecord,
            testTag = "record-toggle",
            layoutModifier = Modifier.weight(1f).fillMaxSize(),
        ) {
            RecordBadge(active = isRecording)
        }
        Box(modifier = Modifier.width(ControlButtonGap))
        KidButton(
            onClick = onPlay,
            testTag = "replay",
            layoutModifier = Modifier.weight(1f).fillMaxSize(),
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

/** Padding a pad cell loses to its own `.padding(6.dp)` on both sides, on EITHER axis. */
private val PadCellPadding = 12.dp

/**
 * Picks the largest column count (capped at [maxColumns], walked down to 1)
 * for which every resulting pad -- given the row count that column count
 * implies -- is at least [MinTapTarget] in BOTH width and height. If no
 * column count satisfies both simultaneously, this returns the count that
 * comes closest by width (matching the width-only search this replaced),
 * which only happens on a configuration this module genuinely cannot serve
 * at the floor with everything on screen -- see the KDoc on [MusicPadGame].
 */
internal fun chooseColumns(padCount: Int, maxColumns: Int, availableWidth: Dp, availableHeight: Dp): Int {
    var bestByWidthOnly = 1
    for (candidate in maxColumns downTo 1) {
        val rows = ceil(padCount / candidate.toFloat()).toInt()
        val width = availableWidth / candidate - PadCellPadding
        val height = availableHeight / rows - PadCellPadding
        val widthOk = width >= MinTapTarget
        val heightOk = height >= MinTapTarget
        if (widthOk && bestByWidthOnly == 1) bestByWidthOnly = candidate
        if (widthOk && heightOk) return candidate
    }
    return bestByWidthOnly
}

/**
 * Lays out [padCount] pads in a grid that never scrolls: rows are derived
 * from [padCount] and the chosen column count, and every pad gets a share of
 * whatever space this composable's OWN [BoxWithConstraints] actually
 * measured -- both `maxWidth` and `maxHeight` are read and used by
 * [chooseColumns], not assumed. The column count is capped by [padCount] but
 * then walked DOWN from that cap until every pad would be at least
 * [MinTapTarget] in BOTH dimensions; on a narrow or short phone, or at a
 * raised system Display size, this drops 8 pads from 4 columns to 3 (or
 * fewer) rather than silently letting [KidButton]'s floor be defeated by
 * `Modifier.weight`, which supplies an exact size [KidButton]'s
 * `defaultMinSize` cannot override on either axis.
 */
@Composable
private fun PadGrid(
    padCount: Int,
    instrument: Int,
    tapTriggers: Map<Pair<Int, Int>, Int>,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxColumns = when {
        padCount <= 4 -> 2
        padCount <= 6 -> 3
        else -> 4
    }

    BoxWithConstraints(modifier = modifier) {
        val columns = chooseColumns(padCount, maxColumns, maxWidth, maxHeight)
        val rows = ceil(padCount / columns.toFloat()).toInt()

        Column(modifier = Modifier.fillMaxSize()) {
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

/** Gap reserved around the shape so the instrument-2 ring's stroke fits fully inside the canvas. */
private val PadRingPadding = 8.dp

@Composable
private fun PadShapeView(shape: PadShape, color: Color, ringed: Boolean, scale: Float) {
    val shapeSize = MinTapTarget * 0.6f
    Canvas(
        modifier = Modifier
            .size(shapeSize + PadRingPadding * 2)
            .scale(scale),
    ) {
        // Draw the shape itself in an inset region matching the original
        // shapeSize, so enlarging the canvas to fit the ring never changes
        // the shape's own visual size.
        inset(PadRingPadding.toPx()) {
            drawPadShape(shape, color)
        }
        if (ringed) {
            drawCircle(
                color = KidPalette.OnSurface.copy(alpha = 0.55f),
                radius = shapeSize.toPx() / 2f + 5.dp.toPx(),
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawPadShape(shape: PadShape, color: Color) {
    when (shape) {
        PadShape.CIRCLE -> drawCircle(color = color, radius = min(size.width, size.height) / 2f)
        PadShape.SQUARE -> drawPath(rectPath(size.width, size.height), color)
        PadShape.TRIANGLE -> drawPath(polygonPath(size.width, size.height, sides = 3), color)
        // No rotationOffset here: the base angle already starts pointing
        // straight up, so a 4-sided polygon with zero offset IS a diamond
        // (top/right/bottom/left points). Adding a 45-degree offset -- as
        // this used to -- rotates it back into an axis-aligned square,
        // making it indistinguishable from PadShape.SQUARE.
        PadShape.DIAMOND -> drawPath(polygonPath(size.width, size.height, sides = 4), color)
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
