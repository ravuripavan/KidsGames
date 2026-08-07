package com.kidsgames.matchshapes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
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
 * resolved never changes screen position. Both rows below are plain, always
 * fully-populated `Row`s (never a lazy list filtered down to "remaining"
 * items) -- a matched shape stays in its row slot as an invisible,
 * non-clickable placeholder, and its hole stays in its row slot too, now
 * drawn "filled". Removing either from the row it is rendered from would
 * shift every later sibling into a new position mid-drag, which is exactly
 * the "board re-packs under your finger" defect the reference game's KDoc
 * warns about.
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

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val context = LocalContext.current
        val soundBank = remember(context) { SoundBank(context) }
        val density = androidx.compose.ui.platform.LocalDensity.current

        // Immutable state machine: drop()/rotate() each return a NEW
        // MatchShapesState. Writing the result back to this `var` is what
        // drives recomposition -- there is no separate version/trigger
        // counter to remember to bump, and there must never be one.
        var state by remember(level) { mutableStateOf(MatchShapesState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        // Root-coordinate bounds of every hole, filled in via
        // onGloballyPositioned as the holes row lays out. Used only to hit
        // test a drag end -- never mutates game state on its own.
        val holeBounds = remember(level) { mutableStateMapOf<Int, Rect>() }
        // Root-coordinate bounds of every shape's resting tray slot, used
        // as the base position a shape's drag offset is added to.
        val shapeOrigin = remember(level) { mutableStateMapOf<Int, Offset>() }

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
                .background(KidPalette.Background),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // EVERY hole is emitted, matched or not -- see the class KDoc.
                for (hole in state.holes) {
                    val filled = state.shapes.any { it.kind == hole.kind && it.matched }
                    HoleView(
                        hole = hole,
                        filled = filled,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            holeBounds[hole.id] = Rect(coords.positionInRoot(), coords.size.toSize())
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // EVERY shape is emitted, matched or not -- see the class KDoc.
                for (shape in state.shapes) {
                    ShapeTrayItem(
                        shape = shape,
                        needsRotation = level >= 4,
                        onOriginPositioned = { origin -> shapeOrigin[shape.id] = origin },
                        onRotate = { state = state.rotate(shape.id) },
                        onDragEnd = { dropPointRoot ->
                            val tolerancePx = with(density) { DROP_TOLERANCE.toPx() }
                            val target = holeBounds.entries.firstOrNull { (_, rect) ->
                                rect.inflate(tolerancePx).contains(dropPointRoot)
                            }
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

@Composable
private fun HoleView(hole: HoleItem, filled: Boolean, modifier: Modifier = Modifier) {
    val color = shapeColor(hole.kind)
    Box(
        modifier = modifier
            .size(MinTapTarget)
            .border(4.dp, color.copy(alpha = 0.7f), holeBorderShape(hole.kind))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            ShapeIcon(kind = hole.kind, color = color, modifier = Modifier.fillMaxSize())
        }
    }
}

private fun holeBorderShape(kind: ShapeKind): androidx.compose.ui.graphics.Shape = when (kind) {
    ShapeKind.CIRCLE, ShapeKind.OVAL -> CircleShape
    else -> RoundedCornerShape(12.dp)
}

/**
 * One shape sitting in its tray slot. Reports its resting (undragged) root
 * position once via [onOriginPositioned] so the caller can compute absolute
 * drag positions; renders invisible and non-interactive once [ShapeItem.matched]
 * is true, but keeps occupying the exact same slot so no sibling shifts.
 */
@Composable
private fun ShapeTrayItem(
    shape: ShapeItem,
    needsRotation: Boolean,
    onOriginPositioned: (Offset) -> Unit,
    onRotate: () -> Unit,
    onDragEnd: (Offset) -> Boolean,
) {
    if (shape.matched) {
        Box(modifier = Modifier.size(MinTapTarget).padding(4.dp))
        return
    }

    var offsetX by remember(shape.id) { mutableStateOf(0f) }
    var offsetY by remember(shape.id) { mutableStateOf(0f) }
    var origin by remember(shape.id) { mutableStateOf(Offset.Zero) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val color = shapeColor(shape.kind)

    Box(
        modifier = Modifier
            .size(MinTapTarget)
            .padding(4.dp)
            .onGloballyPositioned { coords ->
                val topLeft = coords.positionInRoot() - Offset(offsetX, offsetY)
                origin = topLeft
                onOriginPositioned(topLeft)
            }
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
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
            modifier = Modifier.fillMaxSize().rotate(shape.currentRotation.toFloat()),
        )
    }

    if (needsRotation) {
        KidButton(onClick = onRotate, modifier = Modifier.size(28.dp)) {
            Box(modifier = Modifier.fillMaxSize().background(KidPalette.Surface, CircleShape))
        }
    }
}

@Composable
private fun ShapeIcon(kind: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawShape(kind, color)
    }
}

private fun DrawScope.drawShape(kind: ShapeKind, color: Color) {
    when (kind) {
        ShapeKind.CIRCLE -> drawCircle(color = color, radius = min(size.width, size.height) / 2f)
        ShapeKind.OVAL -> drawOval(color = color)
        ShapeKind.SQUARE -> drawRect(color = color)
        ShapeKind.RECTANGLE -> {
            val inset = size.width * 0.18f
            drawRect(
                color = color,
                topLeft = Offset(0f, inset),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - inset * 2f),
            )
        }
        ShapeKind.TRIANGLE -> drawPolygon(color, sides = 3, pointUp = true)
        ShapeKind.DIAMOND -> drawPolygon(color, sides = 4, pointUp = true, rotationDeg = 0f)
        ShapeKind.PENTAGON -> drawPolygon(color, sides = 5, pointUp = true)
        ShapeKind.HEXAGON -> drawPolygon(color, sides = 6, pointUp = true)
        ShapeKind.STAR -> drawStarShape(color)
        ShapeKind.CROSS -> drawCross(color)
    }
}

private fun DrawScope.drawPolygon(color: Color, sides: Int, pointUp: Boolean, rotationDeg: Float = 0f) {
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
    drawPath(path, color = color)
}

private fun DrawScope.drawStarShape(color: Color) {
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
    drawPath(path, color = color)
}

private fun DrawScope.drawCross(color: Color) {
    val third = size.width / 3f
    drawRect(color = color, topLeft = Offset(third, 0f), size = androidx.compose.ui.geometry.Size(third, size.height))
    drawRect(color = color, topLeft = Offset(0f, third), size = androidx.compose.ui.geometry.Size(size.width, third))
}

private fun shapeColor(kind: ShapeKind): Color = KidPalette.Swatch[kind.ordinal % KidPalette.Swatch.size]
