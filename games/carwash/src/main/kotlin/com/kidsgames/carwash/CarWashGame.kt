package com.kidsgames.carwash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.lifecycle.Lifecycle

/**
 * Sandbox: drag a sponge, hose, and (from level 3) a dryer, and (from level
 * 4) wax and polish, over a dirty car until it shines. Copies the reference
 * module's shape -- an `object : GameModule` plus a `Play` composable
 * holding one pure, immutable [CarWashState] in a single `mutableStateOf`,
 * rewritten wholesale (`state = state.scrub(row, col)`), never mutated.
 *
 * THE INTERACTION IS DIRECT, NOT DRAG-AND-DROP. The tool tray at the top is
 * a row of [KidButton]s that simply EQUIP a tool on tap (like a paint
 * palette) -- nothing is ever picked up and carried across the screen. The
 * car itself, in the space below, is the one and only drag surface: the
 * child's finger scrubs directly on the car, and every grid cell it passes
 * over gets cleaner in real time. This sidesteps the class of defect that
 * parked two other modules (`:games:puzzleboard`, `:games:colorsort`) --
 * a dragged glyph clipped by a scrolling/wrapping parent -- because nothing
 * is ever drawn in an overlay outside its own layout: the touch point and
 * the drawn dirt removal are the SAME canvas, so drawn and judged positions
 * cannot diverge.
 *
 * SANDBOX RULES: no tool is ever wrong, no order is required, and there is
 * no completion condition tied to getting the car fully clean -- reaching
 * [CarWashState.isClean] only plays a small celebratory sparkle and the
 * child keeps playing. [onFinished] fires unconditionally after a few
 * minutes of open play (see [SANDBOX_MILLIS]), exactly like every other
 * sandbox module, so open play is rewarded on the same schedule as
 * goal-directed games without ever gating on how clean the car got.
 *
 * LAYOUT BUDGET (derived from a real [BoxWithConstraints], not assumed):
 * worst case is level 4/5's five-tool tray on a 360dp-wide phone. Five
 * 64dp [KidButton]s cannot fit one row (5*64 + 4*8 + 2*12 = 400dp > 360dp),
 * so the tray WRAPS at three tools per row rather than scrolling (see
 * [TOOLS_PER_TRAY_ROW] and the width/height arithmetic below it). The car
 * canvas below gets whatever height remains from the real measured
 * `maxHeight`, exactly like [games.puzzleboard]'s board -- never a
 * hardcoded number.
 */
object CarWashGame : GameModule {

    override val id: String = "carwash"
    override val icon: Int = R.drawable.wash_car
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val lifecycleOwner = LocalLifecycleOwner.current

        var state by remember(level) { mutableStateOf(CarWashState.initial(level)) }
        var sparkling by remember(level) { mutableStateOf(false) }
        var wasClean by remember(level) { mutableStateOf(false) }

        // Where the tool is touching right now, in the car canvas's own
        // local coordinate space, and a per-move counter so the visual
        // effect can key off a REAL change (not "if (trigger > 0)"). Purely
        // cosmetic -- never read by the state machine.
        var touchPoint by remember(level) { mutableStateOf<Offset?>(null) }
        var touchTrigger by remember(level) { mutableStateOf(0) }

        // SOUND THROTTLE: scrubbing is continuous input -- a finger dragged
        // across the car fires many pointer-move events per second. Playing
        // a cue on every one of them turns "cleaning feels good" into a
        // buzzer. This gate only allows a cue at most once per
        // [SOUND_THROTTLE_MILLIS], regardless of how many scrub() calls
        // happen in between. The VISUAL still updates on every event above.
        var lastCueAtMillis by remember(level) { mutableStateOf(0L) }

        LaunchedEffect(state.isClean) {
            if (state.isClean && !wasClean) {
                wasClean = true
                sparkling = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(900L)
                sparkling = false
            }
        }

        // Sandbox completion: fires once, unconditionally, after a fixed
        // amount of FOREGROUND play -- never gated on how clean the car is.
        // `repeatOnLifecycle` does NOT return when its block completes: by
        // contract it re-launches the block on every entry to STARTED and
        // suspends the caller until the lifecycle is DESTROYED, which made
        // `onFinished` below it permanently unreachable. Instead, follow
        // `games:carrace`'s shape: await
        // `lifecycle.currentStateFlow.first { it.isAtLeast(STARTED) }`
        // before each tick. That suspends while backgrounded and RESUMES
        // (returns) the instant the app is foreground again, so the
        // surrounding while-loop -- and the `onFinished` call after it --
        // can actually complete. `playedMillis` still lives in this outer
        // coroutine, so a suspend/resume resumes accounting from the
        // accumulated value, never from zero: a child who plays 20s, has
        // the phone taken for three minutes, and gets it back experiences
        // 20s counted, not three minutes plus 20s.
        LaunchedEffect(level) {
            var playedMillis = 0L
            while (playedMillis < SANDBOX_MILLIS) {
                lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
                delay(FOREGROUND_TICK_MILLIS)
                playedMillis = CarWashState.accumulatePlayMillis(playedMillis, FOREGROUND_TICK_MILLIS, SANDBOX_MILLIS)
            }
            lifecycleOwner.lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            onFinished(Outcome.Completed)
        }

        fun handleScrubAt(localX: Float, localY: Float, gridWidthPx: Float, gridHeightPx: Float) {
            // Off the car entirely -- do nothing at all. Not a fail state:
            // no cue, no penalty, no state change, exactly as if the screen
            // had not been touched. The previous version clamped a
            // background touch into the nearest cell, which let a child
            // scrub the empty cream surround and finish the wash without
            // ever touching the vehicle.
            val cell = CarWashState.cellAt(localX, localY, gridWidthPx, gridHeightPx) ?: return
            val (row, col) = cell

            touchPoint = Offset(localX, localY)
            touchTrigger += 1

            val next = state.scrub(row, col)
            if (next !== state) {
                state = next
                // Monotonic clock: System.currentTimeMillis() can jump
                // backwards on a wall-clock correction (NTP sync crossing
                // networks, a parent changing the time) and silence the
                // scrub cue until wall time catches back up.
                val now = SystemClock.elapsedRealtime()
                if (now - lastCueAtMillis >= SOUND_THROTTLE_MILLIS) {
                    lastCueAtMillis = now
                    // TAP, not SUCCESS: SUCCESS is the suite's achievement
                    // register (shared with Celebration) and firing it ~6
                    // times a second during a drag both misuses that
                    // register and overlaps SoundPool's 4-stream cap at
                    // this cadence. TAP is a short, repeatable texture.
                    soundBank.play(SoundBank.Cue.TAP)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            ToolTray(
                unlocked = CarWashState.toolsFor(level),
                selected = state.selectedTool,
                onSelect = { tool ->
                    state = state.selectTool(tool)
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
                        // A four year old's first touch on a screen full of
                        // dirt is far more likely to be a POKE than a drag,
                        // and `detectDragGestures` by construction does not
                        // fire until touch slop is exceeded -- a bare tap
                        // would otherwise be completely inert. This detector
                        // is on its OWN chained `.pointerInput`, not folded
                        // into the drag one behind a shared `launch`: two
                        // gesture detectors racing inside one `pointerInput`
                        // block is exactly what silently ate the first
                        // stroke in `games:tracelines`, because neither
                        // detector is guaranteed to register before the
                        // first DOWN. Two separately chained modifiers each
                        // get their own pointer event stream from the start.
                        .pointerInput(level) {
                            val widthPx = size.width.toFloat()
                            val heightPx = size.height.toFloat()
                            detectTapGestures(
                                onTap = { offset ->
                                    handleScrubAt(offset.x, offset.y, widthPx, heightPx)
                                },
                            )
                        }
                        .pointerInput(level) {
                            val widthPx = size.width.toFloat()
                            val heightPx = size.height.toFloat()
                            detectDragGestures(
                                onDragStart = { offset ->
                                    handleScrubAt(offset.x, offset.y, widthPx, heightPx)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    handleScrubAt(change.position.x, change.position.y, widthPx, heightPx)
                                },
                                onDragEnd = { touchPoint = null },
                                onDragCancel = { touchPoint = null },
                            )
                        },
                ) {
                    CarCanvas(dirt = state.dirt)
                    ToolEffect(tool = state.selectedTool, point = touchPoint, trigger = touchTrigger)
                }

                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Celebration(visible = sparkling)
                }
            }
        }
    }

    private const val SANDBOX_MILLIS = 180_000L
    private const val SOUND_THROTTLE_MILLIS = 160L
    private const val FOREGROUND_TICK_MILLIS = 500L
}

// --- Layout budget -------------------------------------------------------
// Worked against the worst case: a 360dp-wide phone, level 4/5's five-tool
// tray (the largest tray any level shows).
//
// Tray, per row: 3 tools * 64dp KidButtons + 2 gaps * 16dp (TRAY_BUTTON_GAP_DP)
// + 2 * 8dp column padding (TRAY_COLUMN_PADDING_DP) = 192 + 32 + 16 = 240dp
// <= 360dp, with slack to spare (this is deliberately narrower than
// puzzleboard's 4-per-row tray, which measured 318dp against the same
// 360dp floor -- fewer, chunkier icons here).
// Five tools wrap onto two rows of 3 and 2. Tray block height:
// 2 rows * 64dp + 1 row gap * 16dp (TRAY_ROW_SPACING_DP) + 2 * 8dp column
// padding = 128 + 16 + 16 = 160dp.
// Levels 1-3 (1-3 tools) fit on a single row, so their tray is only
// 64 + 16 = 80dp tall -- strictly less, never more, than the level 4/5 case.
//
// The car canvas below gets whatever `maxHeight` BoxWithConstraints reports
// once the tray and its own 16dp vertical padding are laid out -- it is
// never assumed. On a 360x640dp device with the shell's own safeDrawing
// inset and 96dp exit strip already reserved structurally (per the shell's
// own accounting, not this module's), the worst case (level 4/5) leaves
// roughly 496 - 160 - 16 = 320dp of height for the car, comfortably clear
// of anything scrollable, and the full 360dp - 2*[CAR_PADDING_DP] = 312dp of
// width. Nothing here scrolls at any level.
private const val TOOLS_PER_TRAY_ROW = 3
// Gap between adjacent 64dp tool buttons, applied identically on BOTH axes:
// 8dp of dead space between two live targets is about 1.3mm, thin enough
// that a clumsy hand can clip both, whether those two targets sit side by
// side in the same row (WAX/POLISH) or stacked between rows (the centred
// [WAX, POLISH] row sitting directly under [SPONGE, HOSE, DRYER] at L4/L5).
// There is plenty of budget on both axes (see the arithmetic above) to give
// every adjacent pair of targets this much room.
private val TRAY_ROW_SPACING_DP = 16.dp
private val TRAY_BUTTON_GAP_DP = 16.dp
private val TRAY_COLUMN_PADDING_DP = 8.dp
private val CAR_PADDING_DP = 16.dp

@Composable
private fun ToolTray(unlocked: List<Tool>, selected: Tool, onSelect: (Tool) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(TRAY_COLUMN_PADDING_DP),
        verticalArrangement = Arrangement.spacedBy(TRAY_ROW_SPACING_DP),
    ) {
        unlocked.chunked(TOOLS_PER_TRAY_ROW).forEach { rowTools ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                rowTools.forEachIndexed { index, tool ->
                    if (index > 0) Box(modifier = Modifier.size(TRAY_BUTTON_GAP_DP))
                    ToolButton(tool = tool, isSelected = tool == selected, onClick = { onSelect(tool) })
                }
            }
        }
    }
}

@Composable
private fun ToolButton(tool: Tool, isSelected: Boolean, onClick: () -> Unit) {
    KidButton(
        onClick = onClick,
        testTag = "tool-${tool.name}",
    ) {
        Box(
            modifier = Modifier
                .size(MinTapTarget * 0.72f)
                .clip(CircleShape)
                .background(if (isSelected) toolColor(tool) else toolColor(tool).copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            ToolGlyph(tool = tool)
        }
    }
}

private fun toolColor(tool: Tool): Color = when (tool) {
    Tool.SPONGE -> KidPalette.Yellow
    Tool.HOSE -> KidPalette.Blue
    Tool.DRYER -> KidPalette.Purple
    Tool.WAX -> KidPalette.Orange
    Tool.POLISH -> KidPalette.Pink
}

/**
 * Every tool is drawn as a distinct SHAPE, not just a distinct hue -- colour
 * is never the sole carrier of meaning. Sponge is a rounded square with pore
 * dots, hose is a coiled line with a nozzle dot, dryer is a fan-blade
 * triangle burst, wax is a diamond, polish is a five-point star (echoing the
 * shared [Celebration] star so "polish" reads as the shine-making tool).
 */
@Composable
private fun ToolGlyph(tool: Tool) {
    Canvas(modifier = Modifier.size(MinTapTarget * 0.44f)) {
        val w = size.width
        val h = size.height
        when (tool) {
            Tool.SPONGE -> {
                drawRoundRect(color = KidPalette.OnSurface.copy(alpha = 0.85f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.25f))
                val dot = w * 0.12f
                listOf(0.3f to 0.3f, 0.7f to 0.3f, 0.3f to 0.7f, 0.7f to 0.7f).forEach { (fx, fy) ->
                    drawCircle(color = KidPalette.Background, radius = dot, center = Offset(w * fx, h * fy))
                }
            }
            Tool.HOSE -> {
                val path = Path().apply {
                    moveTo(w * 0.15f, h * 0.85f)
                    quadraticBezierTo(w * 0.1f, h * 0.4f, w * 0.5f, h * 0.5f)
                    quadraticBezierTo(w * 0.9f, h * 0.6f, w * 0.85f, h * 0.15f)
                }
                drawPath(path, color = KidPalette.OnSurface.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.14f))
                drawCircle(color = KidPalette.OnSurface.copy(alpha = 0.85f), radius = w * 0.09f, center = Offset(w * 0.85f, h * 0.15f))
            }
            Tool.DRYER -> {
                val center = Offset(w / 2f, h / 2f)
                for (i in 0 until 3) {
                    val angle = -PI / 2 + i * (2 * PI / 3)
                    val path = fanBladePath(center, min(w, h) * 0.42f, angle)
                    drawPath(path, color = KidPalette.OnSurface.copy(alpha = 0.85f))
                }
                drawCircle(color = KidPalette.OnSurface.copy(alpha = 0.85f), radius = w * 0.08f, center = center)
            }
            Tool.WAX -> {
                val path = Path().apply {
                    moveTo(w / 2f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(w / 2f, h)
                    lineTo(0f, h / 2f)
                    close()
                }
                drawPath(path, color = KidPalette.OnSurface.copy(alpha = 0.85f))
            }
            Tool.POLISH -> drawPath(starPath(w, h, points = 5), color = KidPalette.OnSurface.copy(alpha = 0.85f))
        }
    }
}

private fun fanBladePath(center: Offset, radius: Float, angle: Double): Path {
    val tip = Offset(center.x + (radius * cos(angle)).toFloat(), center.y + (radius * sin(angle)).toFloat())
    val perp = angle + PI / 2
    val spread = radius * 0.28f
    val base1 = Offset(center.x + (spread * cos(perp)).toFloat(), center.y + (spread * sin(perp)).toFloat())
    val base2 = Offset(center.x - (spread * cos(perp)).toFloat(), center.y - (spread * sin(perp)).toFloat())
    return Path().apply {
        moveTo(base1.x, base1.y)
        lineTo(tip.x, tip.y)
        lineTo(base2.x, base2.y)
        close()
    }
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

/**
 * The car body plus its dirt overlay, all on one [Canvas] so the drawn
 * surface and the surface the drag gesture reads coordinates from are
 * IDENTICAL -- there is no separate overlay layer that could drift out of
 * sync with what [CarWashState.scrub] is judging.
 */
/**
 * The car and its dirt, back on ONE Canvas so the drawn surface and the
 * surface the drag gesture reads coordinates from are identical.
 *
 * This car is DRAWN, deliberately, after a bundled sprite was tried and
 * reverted. The only top-down car in the staged art pack is 71x131px, which
 * is ample at carrace's ~45dp but has to be magnified across this module's
 * entire play area, where it rendered as a large, visibly blurry blob --
 * worse than the crisp vector it replaced. Resolution, not style, is the
 * reason. The drawing below keeps the vector's sharpness at any size and
 * takes the depth treatment used elsewhere: gradient body, darker outline,
 * glass highlight, wheel rims.
 */
@Composable
private fun CarCanvas(dirt: List<Float>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.42f
        val bodyBottom = h * 0.78f
        val paint = KidPalette.Green
        val shell = Brush.verticalGradient(
            colors = listOf(
                lerp(paint, Color.White, 0.30f),
                paint,
                lerp(paint, Color.Black, 0.22f),
            ),
            startY = h * 0.18f,
            endY = bodyBottom,
        )
        val edge = lerp(paint, Color.Black, 0.42f)
        val stroke = (w * 0.014f).coerceAtLeast(2f)

        val roofPath = Path().apply {
            moveTo(w * 0.22f, bodyTop)
            lineTo(w * 0.32f, h * 0.2f)
            lineTo(w * 0.68f, h * 0.2f)
            lineTo(w * 0.78f, bodyTop)
            close()
        }
        drawPath(roofPath, brush = shell)
        drawPath(roofPath, color = edge, style = Stroke(stroke))

        val bodyTopLeft = Offset(w * 0.06f, bodyTop)
        val bodySize = androidx.compose.ui.geometry.Size(w * 0.88f, bodyBottom - bodyTop)
        val bodyCorner = androidx.compose.ui.geometry.CornerRadius(w * 0.06f)
        drawRoundRect(brush = shell, topLeft = bodyTopLeft, size = bodySize, cornerRadius = bodyCorner)
        drawRoundRect(
            color = edge,
            topLeft = bodyTopLeft,
            size = bodySize,
            cornerRadius = bodyCorner,
            style = Stroke(stroke),
        )

        // Windscreen, with a pale streak across it so the glass reads as
        // glass rather than as a flat blue panel.
        val glassTop = h * 0.24f
        drawRoundRect(
            color = KidPalette.Blue.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.36f, glassTop),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, bodyTop - glassTop - 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.35f),
            topLeft = Offset(w * 0.37f, glassTop + (bodyTop - glassTop) * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.26f, (bodyTop - glassTop) * 0.22f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
        )

        val wheelRadius = w * 0.09f
        for (cx in listOf(w * 0.26f, w * 0.74f)) {
            drawCircle(color = KidPalette.OnSurface, radius = wheelRadius, center = Offset(cx, bodyBottom))
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = wheelRadius * 0.42f,
                center = Offset(cx, bodyBottom),
            )
        }

        // Dirt overlay: one translucent brown blob per grid cell whose
        // dirt is still above zero, sized and centred on that cell -- the
        // SAME grid CarWashState.scrub() reads coordinates into.
        // Cell centres come from CarWashState.cellCenter, the exact inverse
        // of the cellAt() the touch handler uses, so a blob is always drawn
        // on the cell that scrubbing that blob will clean.
        val cellW = (CarWashState.CAR_RIGHT - CarWashState.CAR_LEFT) * w / CarWashState.GRID_COLS
        val cellH = (CarWashState.CAR_BOTTOM - CarWashState.CAR_TOP) * h / CarWashState.GRID_ROWS
        for (row in 0 until CarWashState.GRID_ROWS) {
            for (col in 0 until CarWashState.GRID_COLS) {
                val amount = dirt.getOrNull(row * CarWashState.GRID_COLS + col) ?: 0f
                if (amount <= 0f) continue
                val (cx, cy) = CarWashState.cellCenter(row, col, w, h)
                drawCircle(
                    color = DIRT_COLOR.copy(alpha = 0.55f * amount.coerceAtMost(1f)),
                    radius = min(cellW, cellH) * 0.55f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

private val DIRT_COLOR = Color(0xFF6D4C2A)

/**
 * A small, tool-flavoured flourish drawn right where the finger last
 * touched -- bubbles for the sponge, a spray fan for the hose, air puffs
 * for the dryer, and sparkle bursts for wax/polish. Fades on its own via a
 * keyed [Animatable] driven by a real CHANGE in [trigger], never
 * `if (trigger > 0)`. Purely cosmetic feedback; this is what makes cleaning
 * feel immediate and responsive even with the sound off.
 */
@Composable
private fun ToolEffect(tool: Tool, point: Offset?, trigger: Int) {
    val alpha = remember(tool) { Animatable(0f) }
    // The flourish's own draw position is captured HERE, inside the effect
    // that starts the fade, rather than read live from [point] on every
    // recomposition. [point] is nulled the instant a drag ends (or was
    // never set at all, for a tap that fires once and never moves again),
    // which -- if read live below -- would make the Canvas bail out via
    // `?: return` before the 260ms fade this comment describes ever had a
    // chance to render a single frame.
    var effectPoint by remember(tool) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(trigger) {
        if (point != null) {
            effectPoint = point
            alpha.snapTo(1f)
            alpha.animateTo(0f, animationSpec = tween(260))
        }
    }
    val p = effectPoint ?: return
    if (alpha.value <= 0f) return

    Canvas(modifier = Modifier.fillMaxSize()) {
        val flourishColor = toolColor(tool)
        val r = 14.dp.toPx() * (0.6f + alpha.value)
        when (tool) {
            Tool.SPONGE, Tool.WAX, Tool.POLISH -> {
                drawCircle(color = flourishColor.copy(alpha = alpha.value * 0.6f), radius = r, center = p)
            }
            Tool.HOSE -> {
                drawCircle(color = flourishColor.copy(alpha = alpha.value * 0.5f), radius = r * 1.4f, center = p)
            }
            Tool.DRYER -> {
                drawCircle(
                    color = flourishColor.copy(alpha = alpha.value * 0.35f),
                    radius = r * 1.1f,
                    center = p,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()),
                )
            }
        }
    }
}
