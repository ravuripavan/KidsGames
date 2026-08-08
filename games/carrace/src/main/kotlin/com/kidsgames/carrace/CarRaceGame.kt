package com.kidsgames.carrace

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Endless lane-based drive. The car sits near the bottom of the screen and
 * road rows scroll toward it; tapping a lane column moves the car into it.
 * Copy [PopBalloonsGame]'s shape: an `object : GameModule` plus a `Play`
 * composable that owns nothing but a plain-Kotlin, pure state machine
 * ([CarRaceState]) held in a single `mutableStateOf`.
 *
 * There is NO crash, NO lap timer, NO opponent, and NO score anywhere in
 * this module. Driving through an [RoadItem.OBSTACLE] only ever produces a
 * soft flash-and-bounce ([CarRaceState.bumpTrigger] driving a one-shot
 * animation here) -- it never ends the run, and the run continues exactly
 * as it would have otherwise. The car cannot leave the road: lane input is
 * always clamped in [CarRaceState], and this Composable never offers a tap
 * target outside `0 until state.laneCount`. The drive itself ends only
 * after a fixed elapsed-time budget ([CarRaceState.TICKS_TO_COMPLETE]),
 * never on a star count or distance, so progression is rewarded on the
 * same schedule as goal-directed games without ever reading as a score.
 *
 * TAP is the primary and only control: tapping anywhere in a lane column
 * moves the car to that lane. There is no tilt control -- the vehicle the
 * child is riding in is already tilting, so tilt would be a confusing,
 * unreliable input here, not merely a poor one.
 */
object CarRaceGame : GameModule {

    override val id: String = "carrace"
    override val icon: Int = R.drawable.car_red
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 4
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val lifecycleOwner = LocalLifecycleOwner.current

        // Immutable state machine: tick()/switchLane() each return a NEW
        // CarRaceState, never mutating the previous one. Writing the result
        // back to this `var` is what drives recomposition -- there is no
        // separate version/trigger counter anywhere in this module.
        var state by remember(level) { mutableStateOf(CarRaceState.initial(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        var lastBumpSeen by remember(level) { mutableStateOf(0) }
        var lastRampSeen by remember(level) { mutableStateOf(0) }
        var lastStarsSeen by remember(level) { mutableStateOf(0) }

        // Continuous scroll progress within the current tick, 0f right after
        // a tick resolves, 1f the instant the next tick is about to resolve.
        // Interpolating this between ticks (instead of teleporting rows once
        // per tick) is what turns the road into a smooth scroll rather than
        // a strobe.
        val tickProgress = remember(level) { Animatable(0f) }

        // Fixed tick cadence regardless of level -- difficulty never comes
        // from speed. This stays comfortably within a young child's
        // reaction time even at level 5, where the only additions are more
        // road features, never faster scrolling.
        LaunchedEffect(level) {
            while (!state.finished) {
                // Suspends here (no busy loop, no wake-ups) whenever the app
                // is not at least STARTED, and resumes automatically when it
                // returns to the foreground.
                lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }

                tickProgress.snapTo(0f)
                tickProgress.animateTo(1f, animationSpec = tween(TICK_MILLIS.toInt(), easing = LinearEasing))

                state = state.tick()
                if (state.bumpTrigger != lastBumpSeen) {
                    lastBumpSeen = state.bumpTrigger
                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                }
                if (state.rampTrigger != lastRampSeen) {
                    lastRampSeen = state.rampTrigger
                }
                if (state.starsCollected != lastStarsSeen) {
                    lastStarsSeen = state.starsCollected
                    soundBank.play(SoundBank.Cue.SUCCESS)
                }
            }
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            if (!celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(1200L)
                onFinished(Outcome.Completed)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            RoadView(
                state = state,
                scrollProgress = tickProgress.value,
                bumpTrigger = state.bumpTrigger,
                rampTrigger = state.rampTrigger,
                starTrigger = state.starsCollected,
                onLaneTap = { lane ->
                    if (!celebrating) {
                        state = state.switchLane(lane)
                        soundBank.play(SoundBank.Cue.TAP)
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }

    private const val TICK_MILLIS = 220L
}

/** How far above the car's centre a road item finishes travelling. */
private val CAR_BOTTOM_PADDING = 16.dp

/**
 * The road surface: [CarRaceState.laneCount] equal-width columns, each a
 * [KidButton]-backed tap target spanning the FULL height of that column so
 * the child can tap anywhere in a lane, not just near the car. Road rows
 * scroll toward the bottom; the car itself sits fixed near the bottom edge
 * and only ever changes which lane it's drawn in.
 */
@Composable
private fun RoadView(
    state: CarRaceState,
    scrollProgress: Float,
    bumpTrigger: Int,
    rampTrigger: Int,
    starTrigger: Int,
    onLaneTap: (Int) -> Unit,
) {
    val bounce = remember(state.level) { Animatable(0f) }
    LaunchedEffect(bumpTrigger) {
        if (bumpTrigger > 0) {
            bounce.animateTo(1f, animationSpec = tween(90))
            bounce.animateTo(0f, animationSpec = tween(220))
        }
    }

    val bumpFlash = remember(state.level) { Animatable(0f) }
    LaunchedEffect(bumpTrigger) {
        if (bumpTrigger > 0) {
            bumpFlash.animateTo(1f, animationSpec = tween(60))
            bumpFlash.animateTo(0f, animationSpec = tween(280))
        }
    }

    // Ramp: a brief upward hop-and-scale arc on the car. Purely a delight --
    // it never changes lane, never changes state, never can fail.
    val hop = remember(state.level) { Animatable(0f) }
    LaunchedEffect(rampTrigger) {
        if (rampTrigger > 0) {
            hop.animateTo(1f, animationSpec = tween(180))
            hop.animateTo(0f, animationSpec = tween(220))
        }
    }

    // Star: a sparkle/scale celebration right where the star was collected.
    val sparkle = remember(state.level) { Animatable(0f) }
    LaunchedEffect(starTrigger) {
        if (starTrigger > 0) {
            sparkle.snapTo(0f)
            sparkle.animateTo(1f, animationSpec = tween(420))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val travelHeight = maxHeight - CAR_BOTTOM_PADDING - (MinTapTarget * 0.7f) / 2

        Row(modifier = Modifier.fillMaxSize()) {
            for (lane in 0 until state.laneCount) {
                LaneColumn(
                    lane = lane,
                    laneCount = state.laneCount,
                    items = state.rows.map { it.lanes.getOrNull(lane) ?: RoadItem.NONE },
                    isBranchRow = state.rows.map { row -> row.lanes.count { it == RoadItem.STAR } > 1 },
                    scrollProgress = scrollProgress,
                    travelHeight = travelHeight,
                    isCarLane = state.carLane == lane,
                    bounceOffset = bounce.value,
                    bumpFlash = bumpFlash.value,
                    hopOffset = hop.value,
                    sparkle = if (state.carLane == lane) sparkle.value else 0f,
                    onTap = { onLaneTap(lane) },
                    layoutModifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        LaneDividers(
            laneCount = state.laneCount,
            scrollProgress = scrollProgress,
            // How far a road row travels in one tick. The dividers must
            // advance by exactly this much per tick or the paint crawls
            // relative to the traffic moving over it.
            rowAdvance = if (state.rows.isEmpty()) travelHeight else travelHeight / state.rows.size,
        )
    }
}

/**
 * How many dash-and-gap cycles the paint shows per road row.
 *
 * The dash period is DERIVED from [LaneDividers]'s `rowAdvance` rather than
 * being a fixed dp value, and that is the whole trick. It buys two
 * properties at once that a fixed period cannot have together:
 *
 *  - **The paint moves at the traffic's speed.** The first version used a
 *    fixed 50dp period and advanced the pattern one period per tick, while a
 *    road row advances `travelHeight / rowCount` -- two unrelated speeds. On
 *    the device the markings visibly crawled while the stars and barrels
 *    swept past, which undercut the very motion the dashes were added to
 *    convey.
 *  - **The wrap stays invisible.** [scrollProgress] runs 0f..1f once per tick
 *    and then restarts. Because one tick advances the pattern by exactly
 *    this many whole periods, the offset at `scrollProgress == 1f` is
 *    congruent modulo the period to the offset at `0f`, so the restart lands
 *    the pattern exactly on itself. Any period that did not divide the row
 *    advance evenly would jump on every tick.
 */
private const val DASHES_PER_ROW = 2

/**
 * Road edges and lane dividers. The dividers are DASHED and slide downward
 * with [scrollProgress], which is what actually sells "we are driving" -- an
 * endless drive whose road never moves reads as a parked car.
 *
 * [rowAdvance] is how far a road row travels in one tick; the dash pattern
 * advances by exactly that, split into [DASHES_PER_ROW] cycles. It is driven
 * by the caller's existing scroll value rather than a second animation, so
 * the paint cannot drift out of step with the obstacles moving over it.
 */
@Composable
private fun LaneDividers(laneCount: Int, scrollProgress: Float, rowAdvance: Dp) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val laneWidth = size.width / laneCount
        val strokeWidth = 4.dp.toPx()
        // Road edges: solid, pale, the kerb.
        val edge = Color(0xFFF2F2F2)
        drawLine(
            color = edge,
            start = Offset(strokeWidth / 2, 0f),
            end = Offset(strokeWidth / 2, size.height),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = edge,
            start = Offset(size.width - strokeWidth / 2, 0f),
            end = Offset(size.width - strokeWidth / 2, size.height),
            strokeWidth = strokeWidth,
        )

        // Dividers between lanes: dashes moving at the traffic's own speed.
        val period = (rowAdvance.toPx() / DASHES_PER_ROW).coerceAtLeast(1f)
        val dashLength = period * 0.55f
        val shift = (scrollProgress % 1f) * rowAdvance.toPx()
        for (lane in 1 until laneCount) {
            val x = laneWidth * lane
            // Start one period above the top so a dash entering the screen is
            // never clipped into existence. `period` is >= 1px, so this loop
            // always advances and always terminates.
            var y = (shift % period) - period
            while (y < size.height) {
                val top = y.coerceAtLeast(0f)
                val bottom = (y + dashLength).coerceAtMost(size.height)
                if (bottom > top) {
                    drawLine(
                        color = Color(0xFFFFF3C4),
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = strokeWidth / 2,
                    )
                }
                y += period
            }
        }
    }
}

@Composable
private fun LaneColumn(
    lane: Int,
    laneCount: Int,
    items: List<RoadItem>,
    isBranchRow: List<Boolean>,
    scrollProgress: Float,
    travelHeight: Dp,
    isCarLane: Boolean,
    bounceOffset: Float,
    bumpFlash: Float,
    hopOffset: Float,
    sparkle: Float,
    onTap: () -> Unit,
    layoutModifier: Modifier = Modifier,
) {
    KidButton(
        onClick = onTap,
        testTag = "lane-$lane",
        layoutModifier = layoutModifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(laneBackground(lane, laneCount))
                .padding(horizontal = 4.dp),
        ) {
            // Scrolling road items, index 0 is the row about to reach the
            // car; higher indices are further away (nearer the horizon).
            items.forEachIndexed { rowIndex, item ->
                RoadItemView(
                    item = item,
                    isBranch = isBranchRow.getOrElse(rowIndex) { false },
                    rowIndex = rowIndex,
                    totalRows = items.size,
                    scrollProgress = scrollProgress,
                    travelHeight = travelHeight,
                )
            }

            if (isCarLane) {
                // Bump flash: a visible red pulse behind the car at the
                // instant of obstacle contact.
                if (bumpFlash > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = CAR_BOTTOM_PADDING)
                            .size(MinTapTarget * 0.7f * 1.6f)
                            .clip(CircleShape)
                            .background(KidPalette.Red.copy(alpha = bumpFlash * 0.55f)),
                    )
                }

                // Sparkle: a visible yellow burst behind the car at the
                // instant a star is collected.
                if (sparkle in 0f..0.999f && sparkle > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = CAR_BOTTOM_PADDING)
                            .size(MinTapTarget * 0.7f * (1.2f + sparkle))
                            .clip(CircleShape)
                            .background(KidPalette.Yellow.copy(alpha = (1f - sparkle) * 0.8f)),
                    )
                }

                CarView(
                    bounceOffset = bounceOffset,
                    hopOffset = hopOffset,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = CAR_BOTTOM_PADDING),
                )
            }
        }
    }
}

@Composable
private fun RoadItemView(
    item: RoadItem,
    isBranch: Boolean,
    rowIndex: Int,
    totalRows: Int,
    scrollProgress: Float,
    travelHeight: Dp,
) {
    if (item == RoadItem.NONE) return

    // fraction 0 == exactly at the car (row 0 at the instant a tick
    // resolves); fraction 1 == entering at the top edge. Rows move from
    // fraction 1 toward fraction 0 as scrollProgress advances, matching the
    // direction rows are actually consumed in CarRaceState.tick().
    val fraction = ((rowIndex + (1f - scrollProgress)) / totalRows).coerceIn(0f, 1f)
    val topPadding = travelHeight * (1f - fraction)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = topPadding)
                .size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                RoadItem.STAR -> if (isBranch) BranchMarkerShape() else StarShape()
                // A real road barrel. Still distinguished from a ramp by
                // SHAPE as well as colour -- barrel versus arrow -- so the
                // two never rely on hue alone to be told apart.
                RoadItem.OBSTACLE -> Image(
                    painter = painterResource(id = R.drawable.obstacle_barrel),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // An upward chevron reads as "go / boost" to a pre-reader far
                // better than the old orange disc did.
                RoadItem.RAMP -> Image(
                    painter = painterResource(id = R.drawable.road_arrow),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                RoadItem.NONE -> Unit
            }
        }
    }
}

@Composable
private fun StarShape() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawStarPath(starPath(size.width, size.height, points = 5))
    }
}

/**
 * A branch marker: same yellow "collectible" colour as an ordinary star, but
 * a distinct SHAPE (a 4-pointed sparkle/diamond instead of a 5-pointed
 * star) so a row offering a choice between two paths is visually different
 * from an ordinary single star, not merely a different colour -- colour
 * vision deficiency is undiagnosed at this age.
 */
@Composable
private fun BranchMarkerShape() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawStarPath(starPath(size.width, size.height, points = 4))
    }
}

private fun starPath(width: Float, height: Float, points: Int): Path {
    val outerRadius = min(width, height) / 2f
    val innerRadius = outerRadius * 0.4f
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

private fun DrawScope.drawStarPath(path: Path) {
    drawPath(path, color = KidPalette.Yellow)
}

@Composable
private fun CarView(bounceOffset: Float, hopOffset: Float, modifier: Modifier = Modifier) {
    // Ramp hop: the car lifts up and grows slightly, a brief visible arc,
    // never a lane change and never a state change.
    val hopLift = -(hopOffset * 28).dp
    // The drawable is a top-down car, taller than it is wide (71x131). It is
    // sized by WIDTH and left to take the height its aspect ratio demands via
    // ContentScale.Fit, so it can never render as a stretched blob -- the
    // failure a square .size() on a non-square asset would produce.
    Image(
        painter = painterResource(id = R.drawable.car_blue),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .offset(y = hopLift)
            .width(MinTapTarget * 0.7f)
            .scale((1f - bounceOffset * 0.3f) * (1f + hopOffset * 0.15f)),
    )
}

/**
 * Asphalt, not paper. The lanes alternate between two near-identical dark
 * greys purely so the lane boundary is legible; the road must read as one
 * continuous surface, which the old Surface/near-white pair did not.
 */
private fun laneBackground(lane: Int, laneCount: Int): Color =
    if (lane % 2 == 0) Color(0xFF51565D) else Color(0xFF3E4248)
