package com.kidsgames.matchshapes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as rotateCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.kidsgames.designkit.Celebration
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drag a shape to the hole of the same kind. Copies the shape of
 * `:games:popballoons`, the reference implementation: a plain-Kotlin, pure
 * state machine ([MatchShapesState]) held in a single `mutableStateOf`, with
 * `drop()`/`rotate()` each returning a brand-new state rather than mutating
 * anything in place. There is no separate "version" or "trigger" counter
 * anywhere in this module -- recomposition is driven entirely by that new
 * state object's identity being written back to the `var`.
 *
 * INVARIANT this Play composable holds: a shape or hole the child has not
 * resolved never changes screen position. Both rows below are always
 * fully-populated `FlowRow`s (never a lazy list filtered down to "remaining"
 * items) -- a matched shape stays in its slot as an invisible, non-clickable
 * placeholder of the SAME size as a live item, and its hole stays in its
 * slot too, now drawn "filled". Removing either, or shrinking the
 * placeholder, would shift every later sibling into a new position
 * mid-drag, which is exactly the "board re-packs under your finger" defect
 * the reference game's KDoc warns about.
 *
 * `FlowRow` (not `Row`) because a plain `Row` cannot shrink fixed 64dp
 * children to fit a ~360dp portrait phone: at L3-L5 seven 64dp holes are
 * already 448dp wide, well past the screen, and a `Row` clips the overflow
 * instead of wrapping it -- the child literally cannot reach or see the
 * missing holes/shapes. `FlowRow` wraps to as many rows as needed instead.
 * Worst case is L5's tray: 7 items x 64dp + 6 x 8dp gaps = 496dp requested
 * width against ~344dp available (360dp screen - 8dp horizontal padding on
 * each side), so it wraps to 4 items then 3: 4x64 + 3x8 = 280dp, comfortably
 * under 344dp. The rotate affordance is drawn as a corner badge INSIDE each
 * shape's own 64dp box (see [ShapeTrayItem]), never as a second sibling, so
 * no item is ever wider than [MinTapTarget].
 *
 * Drag mechanics: each shape tracks its own drag offset in an [Animatable]
 * kept OUTSIDE [MatchShapesState] -- it is transient visual state, not part
 * of the logical match result, the same separation `PopBalloonsGame` uses
 * for its wrong-tap wobble. On drag end the shape's absolute position is
 * compared against every hole's on-screen bounds, inflated by a generous,
 * fixed margin that does not shrink at higher levels (`DROP_TOLERANCE`). A
 * successful drop calls [MatchShapesState.drop]; anything else -- wrong
 * kind, or (L4/L5) not yet rotated to fit -- leaves the state unchanged and
 * the shape springs back to its tray slot with a `Cue.GENTLE_RETRY`. Nothing
 * is ever wrong: the shape just returns home and play continues.
 *
 * WARNING for anyone copying this: [KidButton]'s `modifier =` parameter only
 * ever reaches its inner visual Box, never the outer touch node (see
 * `KidButton`'s own KDoc). A draggable shape therefore is NOT a `KidButton`
 * -- it is a plain `Box` at least [MinTapTarget] on a side with its own
 * `pointerInput(detectDragGestures)`, so the element that moves on screen is
 * the exact element that owns the touch handling. `KidButton` is used only
 * for the rotate control, which is a plain tap with no travel.
 */
object MatchShapesGame : GameModule {

    override val id: String = "matchshapes"
    override val icon: Int = R.drawable.ic_matchshapes
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    /** Fixed, generous hit margin around every hole. Never shrinks by level. */
    private val DROP_TOLERANCE = 48.dp

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val density = androidx.compose.ui.platform.LocalDensity.current

        // Immutable state machine: drop()/rotate() each return a NEW
        // MatchShapesState. Writing the result back to this `var` is what
        // drives recomposition -- there is no separate version/trigger
        // counter to remember to bump, and there must never be one.
        var state by remember(level) { mutableStateOf(MatchShapesState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        // One colour per kind, assigned by this level's kind order (never by
        // ShapeKind.ordinal % Swatch.size) so a level's active kinds never
        // collide with each other -- e.g. RECTANGLE (ordinal 7) would
        // otherwise wrap to the same Swatch entry as CIRCLE (ordinal 0).
        val kindColors = remember(level) {
            MatchShapesState(level).shapes
                .mapIndexed { index, shape -> shape.kind to KidPalette.Swatch[index % KidPalette.Swatch.size] }
                .toMap()
        }

        // Root-coordinate bounds of every hole, filled in via
        // onGloballyPositioned as the holes flow row lays out. Used only to
        // hit test a drag end -- never mutates game state on its own.
        val holeBounds = remember(level) { mutableStateMapOf<Int, Rect>() }

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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            KidPalette.Pink.copy(alpha = 0.18f),
                            KidPalette.Background,
                            KidPalette.Green.copy(alpha = 0.14f),
                        ),
                    ),
                ),
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // EVERY hole is emitted, matched or not -- see the class KDoc.
                for (hole in state.holes) {
                    val filled = state.shapes.any { it.kind == hole.kind && it.matched }
                    HoleView(
                        hole = hole,
                        color = kindColors.getValue(hole.kind),
                        filled = filled,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            holeBounds[hole.id] = Rect(coords.positionInRoot(), coords.size.toSize())
                        },
                    )
                }
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp)
                    // Bottom padding clears the shell's bottom-left exit
                    // button hit zone (x 16-80 / y 16-80 from the bottom,
                    // per GameHost) with margin -- the tray's bottom edge
                    // sits well above y=80 regardless of how FlowRow wraps
                    // rows, so no shape (leftmost included) can ever land
                    // under the exit button (see M1 in round 3 review).
                    .padding(bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // EVERY shape is emitted, matched or not -- see the class KDoc.
                for (shape in state.shapes) {
                    ShapeTrayItem(
                        shape = shape,
                        color = kindColors.getValue(shape.kind),
                        needsRotationControl = level >= 4 && MatchShapesState.hasVisibleRotation(shape.kind),
                        onRotate = { state = state.rotate(shape.id) },
                        onDragEnd = { dropPointRoot ->
                            val tolerancePx = with(density) { DROP_TOLERANCE.toPx() }
                            // Nearest hole CENTRE among candidates, not the first
                            // one found: at L3+ neighbouring holes' inflated hit
                            // boxes fully overlap, so "first" can resolve to a
                            // neighbour even on a dead-centre drop.
                            val target = holeBounds.entries
                                .filter { (_, rect) -> rect.inflate(tolerancePx).contains(dropPointRoot) }
                                .minByOrNull { (_, rect) -> (rect.center - dropPointRoot).getDistanceSquared() }
                            if (target != null) {
                                val next = state.drop(shape.id, target.key)
                                if (next != state) {
                                    state = next
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                    true
                                } else {
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                    false
                                }
                            } else {
                                false
                            }
                        },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

private fun androidx.compose.ui.unit.IntSize.toSize(): androidx.compose.ui.geometry.Size =
    androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())

/**
 * A hole always draws the SAME outline path [drawShape] uses for the live
 * shape (stroked when empty, filled once matched) -- never a generic
 * circle-or-rounded-square stand-in. That is what makes "does this shape fit
 * this hole" provably answerable by silhouette alone, with colour only ever
 * a secondary cue.
 *
 * Critically, the hole is drawn at [HoleItem.requiredRotation], NOT at 0 --
 * see BLOCKING 2 in the round-3 review: an unrotated hole showed the WRONG
 * target orientation at L4/L5, so the shape that visually matched the hole
 * was the WRONG answer and the correct answer visibly did not match. Drawing
 * the hole at its required rotation makes "turn the shape until it looks
 * like the hole" the correct and sufficient strategy again.
 */
@Composable
private fun HoleView(hole: HoleItem, color: Color, filled: Boolean, modifier: Modifier = Modifier) {
    // Correct-match confirmation: a small bounce the moment this hole flips
    // from empty to filled. `filled` is derived from `matched`, which -- like
    // every field this state machine ever produces -- only ever goes
    // false -> true and never regresses (see MatchShapesState.drop), so
    // keying this LaunchedEffect on `filled` cannot restart or replay
    // mid-flight.
    val bounce = remember(hole.id) { Animatable(1f) }
    LaunchedEffect(filled) {
        if (filled) {
            bounce.snapTo(0.6f)
            bounce.animateTo(1.2f, animationSpec = tween(160))
            bounce.animateTo(1f, animationSpec = tween(120))
        }
    }
    Box(
        modifier = modifier.size(MinTapTarget).padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(hole.requiredRotation.toFloat())
                .scale(bounce.value),
        ) {
            drawShapeOutline(hole.kind, color, filled = filled)
        }
    }
}

/**
 * One shape sitting in its tray slot. Renders invisible and non-interactive
 * once [ShapeItem.matched] is true, but keeps occupying a box of the exact
 * same [MinTapTarget] size -- rotate badge included -- so no sibling ever
 * shifts position when a match lands (see the class KDoc invariant).
 *
 * BLOCKING 1 fix (round 3): rotation used to live inside `onDragEnd` of the
 * SAME `detectDragGestures` pointerInput, which never fires at all for a
 * clean tap (`detectDragGestures` awaits touch slop before invoking any of
 * its callbacks, so a pointer that lifts before slop produces nothing). Tap
 * detection is therefore a SEPARATE `detectTapGestures` pointerInput on the
 * same node, independent of the drag detector: a tap always rotates (or
 * wobbles), a drag always moves, and neither is inferred from the other.
 *
 * The rotate control is not a second sibling node (that doubled every tray
 * item's width and is what overflowed the screen at L4/L5). Instead a tap
 * rotates the shape in place when rotation is meaningful at this level, and
 * a small curved-arrow badge is painted in the corner of the shape's own box
 * purely as a discoverability hint -- tapping the badge is tapping the
 * shape, so it needs no touch target of its own.
 */
@Composable
private fun ShapeTrayItem(
    shape: ShapeItem,
    color: Color,
    needsRotationControl: Boolean,
    onRotate: () -> Unit,
    onDragEnd: (Offset) -> Boolean,
) {
    // Outer wrapper reports the shape's resting root position. It never
    // carries the drag offset itself, so -- unlike a single node that both
    // moves and reports its own position -- `origin` can never be corrupted
    // by onGloballyPositioned re-firing mid-drag with the offset baked in.
    var origin by remember(shape.id) { mutableStateOf(Offset.Zero) }

    if (shape.matched) {
        Box(modifier = Modifier.size(MinTapTarget))
        return
    }

    Box(
        modifier = Modifier
            .size(MinTapTarget)
            .onGloballyPositioned { coords -> origin = coords.positionInRoot() },
    ) {
        var offsetX by remember(shape.id) { mutableStateOf(0f) }
        var offsetY by remember(shape.id) { mutableStateOf(0f) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        // MEDIUM 2: at L1-L3 (and for symmetric kinds at L4/L5, where
        // rotating produces no visible change) a tap must still give SOME
        // response -- a 4 year old's most likely first action can't be a
        // silent no-op. `wobbleAngle` drives a quick back-and-forth tilt
        // that reads as "drag me" without implying rotation happened.
        val wobbleAngle = remember(shape.id) { Animatable(0f) }

        Box(
            // The full 64dp box IS the touch/drag node -- padding is applied
            // only inside the icon's own modifier below, never here, so the
            // drag hit region is never smaller than MinTapTarget.
            modifier = Modifier
                .size(MinTapTarget)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                // Tap detector: fully independent of the drag detector below
                // so a clean tap -- one that never crosses touch slop and so
                // never fires ANY detectDragGestures callback -- still does
                // something every time.
                .pointerInput(shape.id, needsRotationControl) {
                    detectTapGestures {
                        if (needsRotationControl) {
                            onRotate()
                        } else {
                            scope.launch {
                                wobbleAngle.snapTo(0f)
                                wobbleAngle.animateTo(14f, tween(80))
                                wobbleAngle.animateTo(-14f, tween(140))
                                wobbleAngle.animateTo(0f, tween(80))
                            }
                        }
                    }
                }
                .pointerInput(shape.id) {
                    val halfTarget = MinTapTarget.toPx() / 2f
                    detectDragGestures(
                        onDragEnd = {
                            val dropPointRoot = origin + Offset(offsetX, offsetY) + Offset(halfTarget, halfTarget)
                            val matched = onDragEnd(dropPointRoot)
                            if (matched) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                // Spring back to the tray -- no fail state, just home.
                                val start = Offset(offsetX, offsetY)
                                scope.launch {
                                    val springAnim = Animatable(start, Offset.VectorConverter)
                                    springAnim.animateTo(
                                        targetValue = Offset.Zero,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                    ) {
                                        offsetX = value.x
                                        offsetY = value.y
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            offsetX = 0f
                            offsetY = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            ShapeIcon(
                kind = shape.kind,
                color = color,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .rotate(shape.currentRotation.toFloat() + wobbleAngle.value),
            )

            if (needsRotationControl) {
                RotateBadge(modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }
}

/**
 * BLOCKING 3 fix (round 3): the rotate badge used to be a static Canvas --
 * no motion, no touch handling, inert decoration a child has no reason to
 * notice. A gentle looping partial-rotation pulse makes it visually read as
 * "this turns" while remaining purely a hint: it stays inside the shape's
 * own [MinTapTarget] box (see the class KDoc), adds no layout width, and is
 * only ever composed when [ShapeTrayItem] decides rotation is meaningful at
 * this level -- i.e. only at L4/L5, for kinds where rotating is visible.
 */
@Composable
private fun RotateBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotateBadgePulse")
    val badgeAngle by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotateBadgeAngle",
    )
    Canvas(
        modifier = modifier
            .size(28.dp)
            .background(KidPalette.Surface, CircleShape),
    ) {
        rotateCanvas(badgeAngle) {
            drawRotateGlyph(KidPalette.OnSurface)
        }
    }
}

/** A live tray shape is always drawn filled, at its current rotation. */
@Composable
private fun ShapeIcon(kind: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawShapeOutline(kind, color, filled = true)
    }
}

private const val OUTLINE_STROKE_WIDTH_DP = 4f

/**
 * The ONE drawing path every hole and every shape goes through -- see
 * BLOCKING 2 in the round-2 review: a hole used to render a generic
 * circle-or-rounded-square regardless of [kind], so a triangle's hole, a
 * star's hole and a hexagon's hole were three identical rounded squares that
 * differed only by colour, which is unusable for a colour-blind child and
 * broke the game's entire premise. An empty hole strokes this exact
 * silhouette; a filled hole (or a live tray shape) fills it. Same geometry,
 * so "does this shape fit this hole" is always answerable by outline alone.
 */
private fun DrawScope.drawShapeOutline(kind: ShapeKind, color: Color, filled: Boolean) {
    val style = if (filled) {
        androidx.compose.ui.graphics.drawscope.Fill
    } else {
        Stroke(width = OUTLINE_STROKE_WIDTH_DP.dp.toPx())
    }
    when (kind) {
        ShapeKind.CIRCLE -> drawCircle(color = color, radius = min(size.width, size.height) / 2f, style = style)
        ShapeKind.OVAL -> {
            // A genuinely oval aspect ratio, not a circle in a square box
            // (MEDIUM 9): inset the bounding box so width and height differ.
            val insetY = size.height * 0.22f
            drawOval(
                color = color,
                topLeft = Offset(0f, insetY),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - insetY * 2f),
                style = style,
            )
        }
        ShapeKind.SQUARE -> drawRect(color = color, style = style)
        ShapeKind.RECTANGLE -> {
            val inset = size.width * 0.18f
            drawRect(
                color = color,
                topLeft = Offset(0f, inset),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - inset * 2f),
                style = style,
            )
        }
        ShapeKind.TRIANGLE -> drawPolygon(color, sides = 3, style = style)
        ShapeKind.DIAMOND -> drawPolygon(color, sides = 4, style = style)
        ShapeKind.PENTAGON -> drawPolygon(color, sides = 5, style = style)
        ShapeKind.HEXAGON -> drawPolygon(color, sides = 6, style = style)
        ShapeKind.STAR -> drawStarShape(color, style = style)
        ShapeKind.CROSS -> drawCross(color, style = style)
    }
}

private fun DrawScope.drawPolygon(
    color: Color,
    sides: Int,
    style: androidx.compose.ui.graphics.drawscope.DrawStyle,
    rotationDeg: Float = 0f,
) {
    val radius = min(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    val startAngle = -PI / 2 + Math.toRadians(rotationDeg.toDouble())
    for (i in 0 until sides) {
        val angle = startAngle + i * (2 * PI / sides)
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, style = style)
}

private fun DrawScope.drawStarShape(color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val points = 5
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.4f
    val center = Offset(size.width / 2f, size.height / 2f)
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
    drawPath(path, color = color, style = style)
}

private fun DrawScope.drawCross(color: Color, style: androidx.compose.ui.graphics.drawscope.DrawStyle) {
    val third = size.width / 3f
    if (style is Stroke) {
        val path = Path().apply {
            moveTo(third, 0f)
            lineTo(third * 2, 0f)
            lineTo(third * 2, third)
            lineTo(size.width, third)
            lineTo(size.width, third * 2)
            lineTo(third * 2, third * 2)
            lineTo(third * 2, size.height)
            lineTo(third, size.height)
            lineTo(third, third * 2)
            lineTo(0f, third * 2)
            lineTo(0f, third)
            lineTo(third, third)
            close()
        }
        drawPath(path, color = color, style = style)
    } else {
        drawRect(color = color, topLeft = Offset(third, 0f), size = androidx.compose.ui.geometry.Size(third, size.height))
        drawRect(color = color, topLeft = Offset(0f, third), size = androidx.compose.ui.geometry.Size(size.width, third))
    }
}

/** Small curved-arrow glyph: the discoverable "this can turn" hint. */
private fun DrawScope.drawRotateGlyph(color: Color) {
    val strokeWidth = size.minDimension * 0.14f
    val radius = size.minDimension * 0.28f
    val center = Offset(size.width / 2f, size.height / 2f)
    val arcRect = androidx.compose.ui.geometry.Rect(center - Offset(radius, radius), androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f))
    drawArc(
        color = color,
        startAngle = -30f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
    )
    // Arrowhead at the end of the arc, pointing along the direction of travel.
    val headAngleRad = Math.toRadians(-30.0 + 260.0)
    val tip = Offset(
        center.x + (radius * cos(headAngleRad)).toFloat(),
        center.y + (radius * sin(headAngleRad)).toFloat(),
    )
    val headSize = size.minDimension * 0.22f
    val headPath = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(tip.x - headSize, tip.y - headSize * 0.3f)
        lineTo(tip.x - headSize * 0.2f, tip.y + headSize * 0.7f)
        close()
    }
    drawPath(headPath, color = color)
}
