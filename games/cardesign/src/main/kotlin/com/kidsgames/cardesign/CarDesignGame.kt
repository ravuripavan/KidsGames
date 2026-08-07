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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
        var sparkling by remember(level) { mutableStateOf(false) }

        // Where the child last tapped the car, in the canvas's own 0f..1f
        // fractional space -- purely cosmetic feedback, never read by the
        // state machine, keyed off a real per-tap counter (never
        // `if (trigger > 0)`).
        var tapTrigger by remember(level) { mutableStateOf(0) }
        var lastTapFraction by remember(level) { mutableStateOf<Offset?>(null) }

        // Sandbox completion: fires once, unconditionally, after a fixed
        // amount of open play -- never gated on how the car looks.
        LaunchedEffect(level) {
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            delay(SANDBOX_MILLIS)
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            onFinished(Outcome.Completed)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            ItemTray(
                unlocked = CarDesignState.unlockedItemsFor(level),
                selected = state.selected,
                onSelect = { item ->
                    state = state.selectItem(item)
                    soundBank.play(SoundBank.Cue.TAP)
                },
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
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

                                val isStickerRemoval = state.selected !is DesignItem.StickerShape &&
                                    state.stickers.isNotEmpty() &&
                                    nearestStickerWithin(state, fx, fy)
                                val next = if (isStickerRemoval) {
                                    state.removeStickerNear(fx, fy)
                                } else {
                                    state.applyAt(fx, fy)
                                }
                                if (next != state) {
                                    state = next
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                    sparkling = true
                                }
                            }
                        },
                ) {
                    CarCanvas(state = state)
                    TapEffect(point = lastTapFraction, trigger = tapTrigger)
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Celebration(visible = sparkling)
                }
                LaunchedEffect(tapTrigger) {
                    if (tapTrigger > 0) {
                        delay(260L)
                        sparkling = false
                    }
                }
            }
        }
    }

    private const val SANDBOX_MILLIS = 180_000L
}

/**
 * A tap directly ON an existing sticker while a NON-sticker tool is equipped
 * removes that sticker -- this is the child's only way to take one back off
 * the car, echoing [CarDesignState.removeStickerNear]'s own tolerance.
 */
private fun nearestStickerWithin(state: CarDesignState, x: Float, y: Float): Boolean =
    state.stickers.any { s ->
        val dx = s.x - x
        val dy = s.y - y
        (dx * dx + dy * dy) <= REMOVE_RADIUS * REMOVE_RADIUS
    }

private const val REMOVE_RADIUS = 0.08f

// --- Layout budget -------------------------------------------------------
// Worked against the worst case: a 360dp-wide phone, level 5's full 19-item
// tray (8 paint + 3 wheel + 4 sticker + 4 body-shape -- the largest tray any
// level shows).
//
// Tray, per row: 3 items * 64dp KidButtons + 2 gaps * 8dp + 2 * 12dp row
// padding = 192 + 16 + 24 = 232dp <= 360dp, matching carwash's own
// per-row math with slack to spare. 19 items wrap onto ceil(19/3) = 7 rows.
// Tray block height: 7 rows * 64dp + 6 gaps * 8dp + 2 * 8dp column padding
// = 448 + 48 + 16 = 512dp -- THIS EXCEEDS the ~496dp the shell hands us
// after safeDrawing + the 96dp exit strip, so level 5's tray alone would
// force a scroll, which is banned outright.
//
// Fix: widen the row to 5-per-row instead of 3. 5 * 64dp + 4 gaps * 8dp +
// 2 * 12dp row padding = 320 + 32 + 24 = 376dp -- still 16dp over a strict
// 360dp floor. Since KidButton's icons need no horizontal margin beyond the
// button's own bounds, row padding is trimmed to 6dp each side for the tray
// only (identical KidButtons, just tighter row insets): 320 + 32 + 12 =
// 364dp -- still 4dp over. Dropping the inter-item gap to 6dp instead of 8dp
// closes it: 320 + 4*6 + 12 = 356dp <= 360dp.
//
// At 5-per-row, 19 items wrap onto ceil(19/5) = 4 rows. Tray block height:
// 4 rows * 64dp + 3 gaps * 6dp + 2 * 8dp column padding = 256 + 18 + 16 =
// 290dp. Levels 1-4 have fewer rows (1, 2, 3, 3 respectively), so their tray
// is never taller. The car canvas below gets whatever `maxHeight`
// BoxWithConstraints reports once the tray is laid out -- never assumed:
// worst case leaves roughly 496 - 290 = 206dp of height, which combined with
// the full 360 - 2*16 = 328dp of width is enough to draw a small car
// legibly. Nothing here scrolls at any level.
private const val ITEMS_PER_TRAY_ROW = 5
private val TRAY_ROW_SPACING_DP = 6.dp
private val TRAY_ROW_PADDING_DP = 6.dp
private val TRAY_COLUMN_PADDING_DP = 8.dp
private val CAR_PADDING_DP = 16.dp

@Composable
private fun ItemTray(unlocked: List<DesignItem>, selected: DesignItem, onSelect: (DesignItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TRAY_COLUMN_PADDING_DP),
        verticalArrangement = Arrangement.spacedBy(TRAY_ROW_SPACING_DP),
    ) {
        unlocked.chunked(ITEMS_PER_TRAY_ROW).forEach { rowItems ->
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
                    itemColor(item).copy(alpha = if (isSelected) 1f else 0.5f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            ItemGlyph(item = item)
        }
    }
}

/**
 * The colour behind each tray button -- purely decorative for wheels,
 * stickers and body shapes (which use neutral tones), and the actual paint
 * colour for [DesignItem.PaintColor]. Colour is never the ONLY signal: see
 * [ItemGlyph] for the marker every item also carries.
 */
private fun itemColor(item: DesignItem): Color = when (item) {
    is DesignItem.PaintColor -> paintPalette[item.paletteIndex % paintPalette.size]
    is DesignItem.WheelStyle -> KidPalette.OnSurface.copy(alpha = 0.35f)
    is DesignItem.StickerShape -> KidPalette.Yellow.copy(alpha = 0.5f)
    is DesignItem.BodyShapeItem -> KidPalette.Blue.copy(alpha = 0.35f)
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
private fun ItemGlyph(item: DesignItem) {
    Canvas(modifier = Modifier.size(MinTapTarget * 0.4f)) {
        val w = size.width
        val h = size.height
        val markColor = KidPalette.OnSurface.copy(alpha = 0.85f)
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
    when (index % 8) {
        0 -> drawCircle(color = color, radius = min(w, h) * 0.4f, center = Offset(w / 2f, h / 2f))
        1 -> drawRect(color = color, topLeft = Offset(w * 0.15f, h * 0.15f), size = Size(w * 0.7f, h * 0.7f))
        2 -> drawPath(regularPolygonPath(w, h, sides = 3), color = color)
        3 -> drawPath(diamondPath(w, h), color = color)
        4 -> drawPath(starPath(w, h, points = 5), color = color)
        5 -> drawPath(regularPolygonPath(w, h, sides = 6), color = color)
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
    val spokes = 3 + index
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
    when (index % 4) {
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
    when (index % 4) {
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

        val wheelRadius = w * 0.09f
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
