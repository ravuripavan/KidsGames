package com.kidsgames.cardesign

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Sandbox: drag paint, wheels, stickers and (from level 5) body shapes onto
 * a car. Follows `:games:carwash`'s shape -- an `object : GameModule` plus a
 * `Play` composable holding one pure, immutable [CarDesignState] in a single
 * `mutableStateOf`, rewritten wholesale, never mutated.
 *
 * THE INTERACTION IS TAP-SELECT, THEN TAP-APPLY -- NOT DRAG-AND-DROP. A tray
 * of [KidButton]s at the top simply EQUIPS an item on tap (paint colour,
 * wheel style, sticker shape, or body shape), exactly like `carwash`'s tool
 * tray and `puzzleboard`/`colorsort`'s PARKED lesson: nothing is ever picked
 * up and carried across the screen, and nothing is ever drawn in an overlay
 * outside its own layout. The car canvas below is a single tap surface: a
 * tap there applies whatever is currently equipped, at the tapped location
 * (only meaningful for stickers -- paint/wheel/body-shape apply globally
 * regardless of where the tap landed on the car). Touch point and affected
 * surface are the SAME node, so drawn and judged positions cannot diverge.
 *
 * SANDBOX RULES: nothing is ever wrong. There is no bad colour, no
 * misplaced sticker, no wrong combination -- every choice is valid and
 * stays. [onFinished] fires unconditionally after a fixed span of open play
 * (see [SANDBOX_MILLIS]), never gated on how the car looks.
 *
 * LAYOUT BUDGET (derived from a real [BoxWithConstraints], see the block
 * below the object).
 */
object CarDesignGame : GameModule {

    override val id: String = "cardesign"
    override val icon: Int = R.drawable.ic_cardesign
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val lifecycleOwner = LocalLifecycleOwner.current

        var state by remember(level) { mutableStateOf(CarDesignState.initial(level)) }

        // Where the child last tapped the car, in the canvas's own 0f..1f
        // fractional space -- purely cosmetic feedback, never read by the
        // state machine, keyed off a real per-tap counter (never
        // `if (trigger > 0)`).
        var tapTrigger by remember(level) { mutableStateOf(0) }
        var lastTapFraction by remember(level) { mutableStateOf<Offset?>(null) }
        var celebrating by remember(level) { mutableStateOf(false) }

        // Hoisted once per lifecycle owner so the completion effect below
        // never registers a second, un-removed observer on recomposition.
        val states = remember(lifecycleOwner) { lifecycleOwner.lifecycle.currentStateFlow }

        // Sandbox completion: fires once, unconditionally, after a fixed
        // amount of open play -- never gated on how the car looks. Elapsed
        // time is accumulated in a lifecycle-gated loop (one 1s tick at a
        // time, each re-checking STARTED) rather than a single flat `delay`,
        // so time spent backgrounded never counts toward the span -- a child
        // who leaves for a call and returns still gets a full session.
        LaunchedEffect(level) {
            var elapsed = 0L
            while (elapsed < SANDBOX_MILLIS) {
                states.first { it.isAtLeast(Lifecycle.State.STARTED) }
                delay(1000L)
                elapsed += 1000L
            }
            celebrating = true
            delay(900L)
            // Re-await STARTED: the delay above can itself span a
            // backgrounding, and without re-checking here `onFinished` could
            // fire while nothing is on screen to show it happened.
            states.first { it.isAtLeast(Lifecycle.State.STARTED) }
            onFinished(Outcome.Completed)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            // Both axes of the REAL available space are read here, once,
            // and used to size the tray -- never assumed at a single
            // evaluated height the way the old fixed-360dp L5 tray budget
            // was (see chooseTrayColumns/MIN_CAR_CANVAS_HEIGHT KDoc below).
            // The tray is capped to leave MIN_CAR_CANVAS_HEIGHT for the car
            // and made scrollable, so a raised system Display size shrinks
            // how much of the tray is visible at once -- never how big a
            // button is, and never down to a 0dp car.
            val unlocked = CarDesignState.unlockedItemsFor(level)
            val maxColumns = min(MAX_TRAY_COLUMNS, unlocked.size).coerceAtLeast(1)
            val columns = chooseTrayColumns(maxColumns, maxWidth)
            val trayMaxHeight = (maxHeight - MIN_CAR_CANVAS_HEIGHT).coerceAtLeast(0.dp)

            Column(modifier = Modifier.fillMaxSize()) {
                ItemTray(
                    unlocked = unlocked,
                    columns = columns,
                    maxHeight = trayMaxHeight,
                    selected = state.selected,
                    onSelect = { item ->
                        state = state.selectItem(item)
                        soundBank.play(SoundBank.Cue.TAP)
                    },
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = CAR_PADDING_DP, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val carWidth = maxWidth
                    val carHeight = maxHeight

                    Box(
                        modifier = Modifier
                            .size(carWidth, carHeight)
                            .pointerInput(level) {
                            val widthPx = size.width.toFloat()
                            val heightPx = size.height.toFloat()
                            detectTapGestures { offset ->
                                val fx = (offset.x / widthPx).coerceIn(0f, 1f)
                                val fy = (offset.y / heightPx).coerceIn(0f, 1f)
                                lastTapFraction = Offset(fx, fy)
                                tapTrigger += 1

                                // The sticker's REAL on-screen half-size
                                // (see CarCanvas: `size = min(w,h) * 0.16f`),
                                // converted to a per-axis fractional radius
                                // so the hit zone matches the drawn shape's
                                // actual dp bounds rather than an isotropic
                                // circle that becomes an elongated ellipse
                                // on a non-square canvas.
                                val halfSizePx = min(widthPx, heightPx) * 0.08f
                                val radiusX = halfSizePx / widthPx
                                val radiusY = halfSizePx / heightPx

                                    val touchesSticker = state.selected !is DesignItem.StickerShape &&
                                        state.stickers.isNotEmpty() &&
                                        state.stickerNear(fx, fy, radiusX, radiusY)

                                    // A tap on a sticker with a non-sticker
                                    // tool equipped is never a pure loss: it
                                    // removes the sticker AND applies the
                                    // equipped tool, so the child's tap
                                    // always does what they asked (change
                                    // the colour/wheel/shape) even when it
                                    // also clears a sticker out of the way.
                                    var next = state
                                    if (touchesSticker) {
                                        next = next.removeStickerNear(fx, fy, radiusX, radiusY)
                                    }
                                    next = next.applyAt(fx, fy)

                                    if (next != state) {
                                        state = next
                                        soundBank.play(SoundBank.Cue.SUCCESS)
                                    }
                                }
                            },
                    ) {
                        CarCanvas(state = state)
                        TapEffect(point = lastTapFraction, trigger = tapTrigger)
                    }
                }
            }

            Celebration(visible = celebrating, big = level == 5)
        }
    }

    private const val SANDBOX_MILLIS = 180_000L
}

// --- Layout budget -------------------------------------------------------
// Fixed-height budgets evaluated at a single screen size cannot survive a
// raised system Display (font/density) size, which shrinks a phone's own dp
// dimensions without shrinking any dp-fixed constant a game applies on top
// (the shell's 96dp exit zone, this module's own tray rows). The old
// "5 rows * 64dp = 360dp, so the car gets ~496-360=136dp" comment was true
// at exactly one height; one Display-size step out it left a 0dp car.
//
// So neither axis is assumed here. [chooseTrayColumns] is a pure function
// (see [CarDesignGameLayoutTest], which calls this SAME function against
// real screen configurations) that picks the widest column count that still
// holds every button at the [MinTapTarget] floor for the REAL available
// width. [MIN_CAR_CANVAS_HEIGHT] is reserved for the car FIRST -- the tray
// gets whatever is left, capped to that budget and scrollable -- so the car
// canvas can shrink but can never be squeezed to 0dp by a tall tray.
//
// MIN_CAR_CANVAS_HEIGHT = 100dp: derived from [CarCanvas]'s own proportions
// once wheels scale off `min(w, h)` (see H1's fix below) rather than width
// alone -- at 100dp the body band is ~36dp tall and each wheel ~18dp across,
// a legible two-tone car with visible wheels and a windshield, not a
// distortion. Below that the body band drops under ~30dp and the roof/cab
// details drawn as fixed fractions of height stop reading as anything.
//
// Required row width for n columns = 2*TRAY_COLUMN_PADDING_DP +
// 2*TRAY_ROW_PADDING_DP + n*64dp + (n-1)*TRAY_ROW_SPACING_DP. At n=5, 5*64dp
// already equals the smallest supported screen width (320dp) with nothing
// left for padding or gaps, so 5-per-row can never hold the floor at 320dp;
// [chooseTrayColumns] therefore never returns more than 4 on a phone-width
// screen, and drops to 3 or fewer as width shrinks further (see the layout
// test for the exact 1.3x-Display case, where width alone forces 3).
//
// Every button that IS shown is always exactly [MinTapTarget] (KidButton's
// `defaultMinSize` floor; nothing in this tray ever asks a button to be
// smaller). What changes with configuration is only how many rows are
// visible without scrolling and how tall the car canvas is -- never the
// button size and never the item count (level 5 always offers all 19 tools;
// see [CarDesignState.totalItemCountFor]'s literal invariant test).
private val TRAY_ROW_SPACING_DP = 8.dp
private val TRAY_ROW_GAP_DP = 6.dp
private val TRAY_ROW_PADDING_DP = 8.dp
private val TRAY_COLUMN_PADDING_DP = 8.dp
private val CAR_PADDING_DP = 16.dp
private val MIN_CAR_CANVAS_HEIGHT = 100.dp
private const val MAX_TRAY_COLUMNS = 5

/** The dp width a row of [columns] 64dp buttons needs, including all padding on both sides. */
internal fun requiredTrayRowWidth(columns: Int): Dp =
    TRAY_COLUMN_PADDING_DP * 2 + TRAY_ROW_PADDING_DP * 2 +
        MinTapTarget * columns + TRAY_ROW_SPACING_DP * (columns - 1).coerceAtLeast(0)

/**
 * Picks the largest column count (capped at [maxColumns], walked down to 1)
 * whose row still fits [availableWidth] without any button dropping under
 * [MinTapTarget]. Never assumes a fixed screen width -- see the layout
 * budget comment above and [CarDesignGameLayoutTest].
 */
internal fun chooseTrayColumns(maxColumns: Int, availableWidth: Dp): Int {
    for (candidate in maxColumns downTo 1) {
        if (requiredTrayRowWidth(candidate) <= availableWidth) return candidate
    }
    return 1
}

/** The tray's own content height at [columns] columns showing [itemCount] items, before any scroll clipping. */
internal fun trayContentHeight(itemCount: Int, columns: Int): Dp {
    val rows = ceil(itemCount / columns.toFloat()).toInt().coerceAtLeast(1)
    return MinTapTarget * rows + TRAY_ROW_GAP_DP * (rows - 1).coerceAtLeast(0) + TRAY_COLUMN_PADDING_DP * 2
}

@Composable
private fun ItemTray(
    unlocked: List<DesignItem>,
    columns: Int,
    maxHeight: Dp,
    selected: DesignItem,
    onSelect: (DesignItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(TRAY_COLUMN_PADDING_DP),
        verticalArrangement = Arrangement.spacedBy(TRAY_ROW_GAP_DP),
    ) {
        unlocked.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TRAY_ROW_PADDING_DP),
                horizontalArrangement = Arrangement.spacedBy(TRAY_ROW_SPACING_DP, Alignment.CenterHorizontally),
            ) {
                rowItems.forEach { item ->
                    ItemButton(item = item, isSelected = item == selected, onClick = { onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun ItemButton(item: DesignItem, isSelected: Boolean, onClick: () -> Unit) {
    KidButton(
        onClick = onClick,
        testTag = "item-${item::class.simpleName}-${item.paletteIndex}",
    ) {
        Box(
            modifier = Modifier
                .size(MinTapTarget * 0.72f)
                .clip(CircleShape)
                .background(
                    itemColor(item, isSelected),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ItemGlyph(item = item, markerColor = markerColorFor(item, isSelected))
        }
    }
}

/**
 * The fully-opaque colour a tray button's swatch is based on -- purely
 * decorative for wheels, stickers and body shapes (which use neutral
 * tones), and the actual paint colour for [DesignItem.PaintColor]. Used
 * both to derive the displayed swatch (see [itemColor]) and to pick a
 * contrast-aware marker colour (see [markerColorFor]), so a marker is
 * always chosen against the colour it will actually sit on, never a
 * post-alpha-blend approximation.
 */
private fun itemBaseColor(item: DesignItem): Color = when (item) {
    is DesignItem.PaintColor -> paintPalette[item.paletteIndex % paintPalette.size]
    is DesignItem.WheelStyle -> KidPalette.OnSurface
    is DesignItem.StickerShape -> KidPalette.Yellow
    is DesignItem.BodyShapeItem -> KidPalette.Blue
}

/**
 * The colour actually painted behind a tray button. Selection is
 * communicated by MULTIPLYING the base alpha (never `Color.copy(alpha =)`
 * replacing it outright -- that was the bug that turned a selected wheel
 * button fully opaque black, erasing the same-colour marker drawn on top
 * of it). A selected item is always more opaque than an unselected one,
 * but never opaque enough to make its own marker unreadable.
 */
private fun itemColor(item: DesignItem, isSelected: Boolean): Color {
    val base = itemBaseColor(item)
    val baseAlpha = when (item) {
        is DesignItem.PaintColor -> 1f
        is DesignItem.WheelStyle -> 0.55f
        is DesignItem.StickerShape -> 0.6f
        is DesignItem.BodyShapeItem -> 0.55f
    }
    val selectionAlpha = if (isSelected) 1f else 0.6f
    return base.copy(alpha = (base.alpha * baseAlpha * selectionAlpha).coerceIn(0.2f, 1f))
}

/**
 * A marker colour with adequate contrast against what a tray button ACTUALLY
 * renders -- [itemColor]'s base swatch alpha-blended over [KidButton]'s
 * white surface, AT THE ALPHA [isSelected] ACTUALLY SHIPS (H2: deciding
 * against the opaque base colour picked a marker that often had no relation
 * to the pale, alpha-blended swatch a child actually sees -- level 5's blue
 * body-shape glyph rendered as a near-blank white circle because a light
 * marker was chosen against opaque blue while the screen only ever shows
 * blue at 33%-55% alpha over white).
 *
 * Deliberately decided PER SELECTION STATE rather than once for both: some
 * swatches (purple is the concrete case, pinned in the state test) have no
 * single marker colour that clears the 3:1 floor at both the selected
 * (near-opaque) and unselected (much paler) alpha -- a fully-opaque purple
 * needs a light marker, the same purple at 60% alpha over white is pale
 * enough that only a dark marker holds the floor. The item's marker SHAPE
 * never changes across selection (see [ItemGlyph]/[CarDesignState]'s
 * distinctness tests), only its colour, so identity is never lost.
 */
private fun markerColorFor(item: DesignItem, isSelected: Boolean): Color {
    val base = itemBaseColor(item)
    val alpha = itemColor(item, isSelected).alpha
    val light = CarDesignState.isLightMarkerNeededComposited(base.red, base.green, base.blue, listOf(alpha))
    return if (light) Color(0xFFF5F5F5) else Color(0xFF2A2A2A)
}

/**
 * Eight high-saturation, high-contrast colours -- [KidPalette.Swatch] only
 * defines seven, so an eighth (teal) is added locally here for this module's
 * 8-colour levels. Every entry ALSO gets a distinct marker shape (see
 * [ItemGlyph]) so a colour-blind child can tell any two apart without
 * relying on hue at all.
 */
private val paintPalette: List<Color> = KidPalette.Swatch + Color(0xFF00897B)

/**
 * Every tray item is drawn with a marker distinct from every other item in
 * its OWN category, so colour is never the sole carrier of meaning:
 * - paint: a different marker SHAPE per colour (circle, square, triangle,
 *   diamond, star, hexagon, cross, pentagon -- 8 shapes for 8 colours).
 * - wheels: a different spoke count per style.
 * - stickers: a different silhouette per shape (star, heart, flower, cloud).
 * - body shapes: a different outline per shape (sedan, van, round, pickup).
 */
@Composable
private fun ItemGlyph(item: DesignItem, markerColor: Color) {
    Canvas(modifier = Modifier.size(MinTapTarget * 0.4f)) {
        val w = size.width
        val h = size.height
        val markColor = markerColor
        when (item) {
            is DesignItem.PaintColor -> drawPaintMarker(item.paletteIndex, w, h, markColor)
            is DesignItem.WheelStyle -> drawWheelMarker(item.paletteIndex, w, h, markColor)
            is DesignItem.StickerShape -> drawStickerShape(item.paletteIndex, w, h, markColor)
            is DesignItem.BodyShapeItem -> drawBodyShapeMarker(item.paletteIndex, w, h, markColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPaintMarker(
    index: Int,
    w: Float,
    h: Float,
    color: Color,
) {
    when (CarDesignState.paintMarkerShapeId(index)) {
        0 -> drawCircle(color = color, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f))
        1 -> drawRect(color = color, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(w * 0.7f, h * 0.7f))
        2 -> drawPath(regularPolygonPath(w, h, sides = 3), color = color)
        3 -> drawPath(diamondPath(w, h), color = color)
        4 -> drawPath(starPath(w, h, points = 5), color = color)
        // A RING, not a hexagon: at the ~24dp glyph size a filled hexagon
        // (6 sides) and the pentagon below it (5 sides) read as the same
        // blob (M4) -- an open ring is unmistakably different from every
        // other filled shape in this set regardless of how small it draws.
        5 -> drawCircle(color = color, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f), style = Stroke(width = min(w, h) * 0.18f))
        6 -> drawCrossMarker(w, h, color)
        else -> drawPath(regularPolygonPath(w, h, sides = 5), color = color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWheelMarker(
    index: Int,
    w: Float,
    h: Float,
    color: Color,
) {
    val center = Offset(w / 2f, h / 2f)
    val radius = min(w, h) * 0.42f
    drawCircle(color = color, radius = radius, center = center, style = Stroke(width = w * 0.1f))
    val spokes = CarDesignState.wheelSpokeCount(index)
    for (i in 0 until spokes) {
        val angle = i * (2 * PI / spokes)
        val end = Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat())
        drawLine(color = color, start = center, end = end, strokeWidth = w * 0.08f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStickerShape(
    index: Int,
    w: Float,
    h: Float,
    color: Color,
) {
    when (CarDesignState.stickerMarkerShapeId(index)) {
        0 -> drawPath(starPath(w, h, points = 5), color = color)
        1 -> drawPath(heartPath(w, h), color = color)
        2 -> drawPath(flowerPath(w, h), color = color)
        else -> drawPath(cloudPath(w, h), color = color)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBodyShapeMarker(
    index: Int,
    w: Float,
    h: Float,
    color: Color,
) {
    when (CarDesignState.bodyMarkerShapeId(index)) {
        0 -> drawRoundRect(color = color, topLeft = Offset(w * 0.1f, h * 0.3f), size = Size(w * 0.8f, h * 0.4f), cornerRadius = CornerRadius(w * 0.08f))
        1 -> drawRoundRect(color = color, topLeft = Offset(w * 0.08f, h * 0.18f), size = Size(w * 0.84f, h * 0.55f), cornerRadius = CornerRadius(w * 0.15f))
        2 -> drawCircle(color = color, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f), style = Stroke(width = w * 0.1f))
        else -> drawPath(
            Path().apply {
                moveTo(w * 0.05f, h * 0.75f)
                lineTo(w * 0.05f, h * 0.35f)
                lineTo(w * 0.5f, h * 0.35f)
                lineTo(w * 0.95f, h * 0.55f)
                lineTo(w * 0.95f, h * 0.75f)
                close()
            },
            color = color,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrossMarker(w: Float, h: Float, color: Color) {
    val thickness = w * 0.22f
    drawRect(color = color, topLeft = Offset((w - thickness) / 2f, h * 0.1f), size = Size(thickness, h * 0.8f))
    drawRect(color = color, topLeft = Offset(w * 0.1f, (h - thickness) / 2f), size = Size(w * 0.8f, thickness))
}

private fun regularPolygonPath(width: Float, height: Float, sides: Int): Path {
    val radius = min(width, height) / 2.2f
    val center = Offset(width / 2f, height / 2f)
    val path = Path()
    for (i in 0 until sides) {
        val angle = -PI / 2 + i * (2 * PI / sides)
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun diamondPath(width: Float, height: Float): Path = Path().apply {
    moveTo(width / 2f, height * 0.05f)
    lineTo(width * 0.95f, height / 2f)
    lineTo(width / 2f, height * 0.95f)
    lineTo(width * 0.05f, height / 2f)
    close()
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

private fun heartPath(width: Float, height: Float): Path = Path().apply {
    moveTo(width / 2f, height * 0.85f)
    cubicTo(width * -0.1f, height * 0.45f, width * 0.15f, height * 0.05f, width / 2f, height * 0.3f)
    cubicTo(width * 0.85f, height * 0.05f, width * 1.1f, height * 0.45f, width / 2f, height * 0.85f)
    close()
}

private fun flowerPath(width: Float, height: Float): Path {
    val center = Offset(width / 2f, height / 2f)
    val petalRadius = min(width, height) * 0.22f
    val path = Path()
    for (i in 0 until 5) {
        val angle = i * (2 * PI / 5)
        val cx = center.x + (petalRadius * 1.1f * cos(angle)).toFloat()
        val cy = center.y + (petalRadius * 1.1f * sin(angle)).toFloat()
        path.addOval(androidx.compose.ui.geometry.Rect(center = Offset(cx, cy), radius = petalRadius))
    }
    return path
}

private fun cloudPath(width: Float, height: Float): Path {
    val path = Path()
    path.addOval(androidx.compose.ui.geometry.Rect(center = Offset(width * 0.32f, height * 0.55f), radius = width * 0.22f))
    path.addOval(androidx.compose.ui.geometry.Rect(center = Offset(width * 0.6f, height * 0.4f), radius = width * 0.28f))
    path.addOval(androidx.compose.ui.geometry.Rect(center = Offset(width * 0.78f, height * 0.6f), radius = width * 0.2f))
    return path
}

/**
 * The car body plus its equipped decorations, all on one [Canvas] so the
 * drawn surface and the surface the tap gesture reads coordinates from are
 * IDENTICAL -- there is no separate overlay layer that could drift out of
 * sync with what [CarDesignState] is tracking.
 */
@Composable
private fun CarCanvas(state: CarDesignState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bodyColor = state.bodyColorIndex?.let { paintPalette[it % paintPalette.size] }
            ?: KidPalette.Surface

        val bodyTop = h * 0.42f
        val bodyBottom = h * 0.78f

        when (state.bodyShapeIndex % 4) {
            0 -> { // sedan: low rounded box + roof trapezoid
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.06f, bodyTop),
                    size = Size(w * 0.88f, bodyBottom - bodyTop),
                    cornerRadius = CornerRadius(w * 0.06f),
                )
                val roof = Path().apply {
                    moveTo(w * 0.22f, bodyTop)
                    lineTo(w * 0.32f, h * 0.2f)
                    lineTo(w * 0.68f, h * 0.2f)
                    lineTo(w * 0.78f, bodyTop)
                    close()
                }
                drawPath(roof, color = bodyColor)
            }
            1 -> { // van: tall rounded box, roof flush with body
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.08f, h * 0.18f),
                    size = Size(w * 0.84f, bodyBottom - h * 0.18f),
                    cornerRadius = CornerRadius(w * 0.1f),
                )
            }
            2 -> { // round/bug: single rounded blob
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.1f, h * 0.22f),
                    size = Size(w * 0.8f, bodyBottom - h * 0.22f),
                    cornerRadius = CornerRadius(w * 0.28f),
                )
            }
            else -> { // pickup: cab + open flat bed
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.06f, bodyTop),
                    size = Size(w * 0.4f, bodyBottom - bodyTop),
                    cornerRadius = CornerRadius(w * 0.05f),
                )
                val cab = Path().apply {
                    moveTo(w * 0.1f, bodyTop)
                    lineTo(w * 0.16f, h * 0.24f)
                    lineTo(w * 0.4f, h * 0.24f)
                    lineTo(w * 0.44f, bodyTop)
                    close()
                }
                drawPath(cab, color = bodyColor)
                drawRoundRect(
                    color = bodyColor.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.5f, bodyTop + h * 0.05f),
                    size = Size(w * 0.44f, bodyBottom - bodyTop - h * 0.05f),
                    cornerRadius = CornerRadius(w * 0.03f),
                )
            }
        }

        drawRoundRect(
            color = KidPalette.Blue.copy(alpha = 0.5f),
            topLeft = Offset(w * 0.36f, h * 0.24f),
            size = Size(w * 0.28f, (bodyTop - h * 0.24f - 4f).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(w * 0.02f),
        )

        // Derived from the SMALLER of the two canvas dimensions (H1: a
        // width-only radius made the wheels 1.4x taller than the body and
        // clipped past the bottom edge on the short, wide canvas a tight
        // tray budget produces). Capped against the body band's own height
        // so a wheel can never dwarf the body it is attached to, at any
        // canvas shape this module can measure.
        val wheelRadius = (min(w, h) * 0.09f).coerceAtMost((bodyBottom - bodyTop) * 0.55f)
        val wheelStyle = state.wheelStyleIndex
        listOf(w * 0.26f, w * 0.74f).forEach { cx ->
            val center = Offset(cx, bodyBottom)
            drawCircle(color = KidPalette.OnSurface, radius = wheelRadius, center = center)
            if (wheelStyle != null) {
                val spokes = 3 + wheelStyle
                for (i in 0 until spokes) {
                    val angle = i * (2 * PI / spokes)
                    val end = Offset(
                        center.x + (wheelRadius * 0.8f * cos(angle)).toFloat(),
                        center.y + (wheelRadius * 0.8f * sin(angle)).toFloat(),
                    )
                    drawLine(color = KidPalette.Background, start = center, end = end, strokeWidth = wheelRadius * 0.18f)
                }
            } else {
                drawCircle(color = KidPalette.Background, radius = wheelRadius * 0.4f, center = center)
            }
        }

        state.stickers.forEach { sticker ->
            val cx = w * sticker.x
            val cy = h * sticker.y
            val size = min(w, h) * 0.16f
            translate(cx - size / 2f, cy - size / 2f) {
                val stickerColor = KidPalette.Swatch[sticker.id % KidPalette.Swatch.size]
                when (sticker.shapeIndex % 4) {
                    0 -> drawPath(starPath(size, size, points = 5), color = stickerColor)
                    1 -> drawPath(heartPath(size, size), color = stickerColor)
                    2 -> drawPath(flowerPath(size, size), color = stickerColor)
                    else -> drawPath(cloudPath(size, size), color = stickerColor)
                }
            }
        }
    }
}

/**
 * A small ripple drawn right where the finger last tapped, fading on its own
 * via a keyed [Animatable] driven by a real CHANGE in [trigger], never
 * `if (trigger > 0)`. Purely cosmetic; this is what makes every tap feel
 * responsive even with the sound off.
 */
@Composable
private fun TapEffect(point: Offset?, trigger: Int) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (point != null && trigger > 0) {
            alpha.snapTo(1f)
            alpha.animateTo(0f, animationSpec = tween(320))
        }
    }
    val p = point ?: return
    if (alpha.value <= 0f) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = 16.dp.toPx() * (0.6f + alpha.value)
            drawCircle(
                color = KidPalette.OnSurface.copy(alpha = alpha.value * 0.3f),
                radius = r,
                center = Offset(p.x * widthPx, p.y * heightPx),
            )
        }
    }
}
