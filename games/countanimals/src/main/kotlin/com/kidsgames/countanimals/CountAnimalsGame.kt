package com.kidsgames.countanimals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.designkit.rememberSoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay

/**
 * Tap animals one at a time; each tap speaks the next number (via
 * [SoundBank], when audio assets eventually exist). Audio does not exist
 * yet and never carries required information on its own -- the running
 * count is always drawn on screen as [NumberGlyph] plus filled pips, and
 * each tapped animal is visibly marked in place, so the whole game is fully
 * understandable at zero volume.
 *
 * Shape copied from `:games:popballoons`, the reference implementation: an
 * `object : GameModule` plus a `Play` composable that owns nothing but a
 * plain-Kotlin, pure state machine ([CountAnimalsState]) held in a single
 * `mutableStateOf`. `tap()`/`pickNumeral()` return brand-new state objects
 * rather than mutating anything in place; there is no separate
 * "version"/"trigger" counter anywhere in this module.
 *
 * INVARIANT this Play composable must hold: an animal never changes screen
 * position or size once rendered. [items] below is called on the FULL
 * (unfiltered) animal list for exactly this reason -- tapped animals stay
 * in the grid, visibly marked and non-clickable, instead of being removed,
 * so a still-untapped animal never moves under the child's finger.
 *
 * Numerals ARE allowed in this game (L3 requires picking one), but every
 * digit is drawn via [NumeralGlyph]/[NumberGlyph] on a `Canvas`, never via
 * `Text(...)` -- the repo-wide no-text build gate fails on any literal
 * passed to `Text` and this module is not on its allowlist.
 */
object CountAnimalsGame : GameModule {

    override val id: String = "countanimals"
    override val icon: Int = R.drawable.ic_countanimals
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        // rememberSoundBank() owns the remember(context)+DisposableEffect
        // release dance internally -- no need to hand-roll it here.
        val soundBank = rememberSoundBank()

        // A fresh, unpredictable per-question seed, picked once when this
        // Play enters a level (remember(level) means a NEW random value is
        // drawn every time the child re-enters this level, e.g. by exiting
        // and coming back) and then held FIXED for the lifetime of this
        // composition -- see CountAnimalsState.attempt: this is what makes
        // numeralOptions' answer position vary between questions at L3/L5
        // while staying stable across recompositions of the SAME question.
        val attempt = remember(level) { kotlin.random.Random.nextInt() }

        // Immutable state machine: tap()/pickNumeral() return a NEW
        // CountAnimalsState, never mutating animals in place. Writing the
        // result back to this `var` is what drives recomposition -- there
        // is no separate version/trigger counter to remember to bump, and
        // there must never be one.
        var state by remember(level) { mutableStateOf(CountAnimalsState(level, attempt = attempt)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped per-animal on a repeat tap so AnimalView can play a visible
        // wobble even with sound off.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }
        // Bumped on a wrong numeral pick so the picker can show a visible,
        // non-punitive shake even with sound off.
        var numeralWobble by remember(level) { mutableStateOf(0) }

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
            // Shared by both the single-grid layout (L1/L2/L3) and the
            // two-group layout (L4/L5): tap() already no-ops safely for an
            // already-tapped animal or a finished level, so this can always
            // be called -- the wobble/gentle-retry branch below is what
            // makes a blocked tap visibly reachable instead of dead code.
            val onAnimalTap: (Animal) -> Unit = { animal ->
                val next = state.tap(animal.id)
                if (next != state) {
                    state = next
                    soundBank.play(SoundBank.Cue.TAP)
                } else {
                    wobbleTriggers = wobbleTriggers +
                        (animal.id to (wobbleTriggers[animal.id] ?: 0) + 1)
                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // L4/L5 show a per-group count beside each group's own
                // container instead -- showing the combined total here
                // would draw L5's answer on screen before the child picks
                // it (BLOCKING 2).
                if (level != 4 && level != 5) {
                    // The ONLY on-screen indication of the running count --
                    // this is what keeps the game legible at zero volume.
                    // Every tap must move this, never just an audio cue.
                    RunningCount(countSoFar = state.countSoFar, total = state.total)
                }

                val onPick: (Int) -> Unit = { numeral ->
                    val next = state.pickNumeral(numeral)
                    if (next.isComplete) {
                        state = next
                        soundBank.play(SoundBank.Cue.SUCCESS)
                    } else {
                        numeralWobble += 1
                        soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                    }
                }

                if (level == 4 || level == 5) {
                    // Groups (and, at L5, the picker) are laid out IN THE
                    // FLOW here, never as Box/BottomCenter overlay siblings
                    // -- see TwoGroupAnimalsView's kdoc for the explicit
                    // height budget this depends on.
                    TwoGroupAnimalsView(
                        animals = state.animals,
                        wobbleTriggers = wobbleTriggers,
                        enabled = !state.allTapped,
                        showPlusSign = level == 5,
                        awaitingNumeralPick = state.awaitingNumeralPick && !celebrating,
                        numeralOptions = state.numeralOptions,
                        numeralWobbleTrigger = numeralWobble,
                        onTap = onAnimalTap,
                        onPickNumeral = onPick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = MinTapTarget + 16.dp),
                            contentPadding = PaddingValues(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Emit EVERY animal, tapped or not -- never
                            // filter the list. See the class-level doc:
                            // filtering reflows every later cell on each
                            // tap, moving still-untapped animals under the
                            // child's finger.
                            items(state.animals, key = { it.id }) { animal ->
                                AnimalView(
                                    animal = animal,
                                    wobbleTrigger = wobbleTriggers[animal.id] ?: 0,
                                    enabled = !state.allTapped,
                                    onTap = { onAnimalTap(animal) },
                                )
                            }
                        }

                        if (state.awaitingNumeralPick && !celebrating) {
                            NumeralPicker(
                                options = state.numeralOptions,
                                wobbleTrigger = numeralWobble,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp),
                                onPick = onPick,
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

/**
 * The running count, drawn as a large digit plus a row of filled/unfilled
 * pips -- one pip per animal in the level. This is the sole visible record
 * of progress; a wrong tap never changes it, and it never goes down.
 */
@Composable
private fun RunningCount(countSoFar: Int, total: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NumberGlyph(value = countSoFar, color = KidPalette.OnSurface)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(total) { index ->
                Pip(filled = index < countSoFar)
            }
        }
    }
}

@Composable
private fun Pip(filled: Boolean, size: Dp = 14.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                if (filled) KidPalette.Green else KidPalette.Surface,
                CircleShape,
            ),
    )
}

/**
 * L4 ("count two groups") and L5 ("simple addition to 10") render as two
 * VISUALLY DISTINCT, bordered containers STACKED VERTICALLY -- never side by
 * side. Side by side gives each group only ~152dp of width, which forces
 * `GridCells.Adaptive` down to a single column and overflows into a hidden,
 * scrollbar-less `LazyVerticalGrid` scroll (the defect that got
 * `colorsort` parked). Stacked, each group gets the FULL content width.
 *
 * Group identity rests on more than colour: each container also carries a
 * drawn corner marker (circle vs. square, see [GroupMarker]) so the boundary
 * survives red-green colour-vision deficiency, which is undiagnosed at this
 * age. Animal fill colours are Red vs. Blue (a safe pair on both the
 * protan/deutan axes), not the former Red/Orange -- a classic confusion
 * pair -- from `KidPalette.Swatch[0]`/`[1]`.
 *
 * EXPLICIT HEIGHT BUDGET (do not fit exactly -- this leaves real margin):
 * `GameHost` hands `Play` roughly 360 x 464dp of usable content after
 * `safeDrawing` insets and its own structural 96dp bottom exit reserve.
 * This composable's own vertical padding is 4dp top + 4dp bottom = 8dp.
 *
 * Per GroupContainer (padding 6dp, grid spacing 4dp, top content pad 2dp,
 * animal cell = AnimalView's fixed 64dp + 4dp padding each side = 72dp):
 *   inner width  = 360 - 2*12(outer horiz pad) - 2*6(container pad) = 324dp
 *   columns      = floor: 4*72 + 3*4 = 300 <= 324   (4 columns fit; a 5th
 *                  would need 376dp, so this can't silently regain a column)
 *   1-row group (L4's 4-animal group): 72 + 2(toppad) + 12(container pad) = 86dp
 *   2-row group (L4's 6-animal / L5's 5-animal groups):
 *                  72*2 + 4(rowgap) + 2(toppad) + 12(container pad) = 162dp
 *
 * L4 total : 162 (group0, 6 animals, 2 rows)
 *          +  12 (gap between groups)
 *          +  86 (group1, 4 animals, 1 row)
 *          +   8 (outer vertical padding)
 *          = 268dp  -- 196dp of margin under the 464dp budget, no picker.
 *
 * L5 total : 162 (group0, 5 animals, 2 rows)
 *          + 162 (group1, 5 animals, 2 rows)
 *          +  28 (gap between groups, sized to fit the 24dp PlusSign)
 *          +   8 (outer vertical padding)
 *          = 360dp, leaving 104dp for the picker laid out BELOW both
 *            groups, in the flow (never as a Box/BottomCenter overlay --
 *            that was H2, occluding both groups' bottoms at once).
 *            NumeralPicker's KidButtons are `defaultMinSize` 64dp tall;
 *            budgeting 76dp for the button row + 12dp top gap = 88dp
 *            still leaves 16dp of real margin at 464dp total.
 */
@Composable
private fun TwoGroupAnimalsView(
    animals: List<Animal>,
    wobbleTriggers: Map<Int, Int>,
    enabled: Boolean,
    showPlusSign: Boolean,
    awaitingNumeralPick: Boolean,
    numeralOptions: List<Int>,
    numeralWobbleTrigger: Int,
    onTap: (Animal) -> Unit,
    onPickNumeral: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val group0 = remember(animals) { animals.filter { it.groupIndex == 0 } }
    val group1 = remember(animals) { animals.filter { it.groupIndex == 1 } }

    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GroupContainer(
            animals = group0,
            marker = GroupMarker.Circle,
            wobbleTriggers = wobbleTriggers,
            enabled = enabled,
            onTap = onTap,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showPlusSign) {
            PlusSign(
                color = KidPalette.OnSurface,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(24.dp),
            )
        }
        GroupContainer(
            animals = group1,
            marker = GroupMarker.Square,
            wobbleTriggers = wobbleTriggers,
            enabled = enabled,
            onTap = onTap,
            modifier = Modifier.fillMaxWidth(),
        )

        // In the flow, below BOTH groups -- never an overlay, which used to
        // cover ~102dp across the full width of both groups' bottoms at
        // once (H2).
        if (awaitingNumeralPick) {
            NumeralPicker(
                options = numeralOptions,
                wobbleTrigger = numeralWobbleTrigger,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp),
                onPick = onPickNumeral,
            )
        }
    }
}

/** A second, non-colour channel for group identity -- see the class kdoc. */
private enum class GroupMarker { Circle, Square }

@Composable
private fun GroupContainer(
    animals: List<Animal>,
    marker: GroupMarker,
    wobbleTriggers: Map<Int, Int>,
    enabled: Boolean,
    onTap: (Animal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .border(width = 3.dp, color = KidPalette.OnSurface.copy(alpha = 0.3f), shape = RoundedCornerShape(20.dp))
            .background(KidPalette.Surface, RoundedCornerShape(20.dp))
            .padding(6.dp),
    ) {
        // Per-animal checkmarks (drawn on every tapped AnimalView) are
        // already this group's silent, always-on progress indicator -- no
        // extra digit/pip header is budgeted here, see the height budget
        // above; the numeral pick answer is never leaked, and the running
        // count is never carried only by an audio cue either way.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = MinTapTarget + 8.dp),
            contentPadding = PaddingValues(top = 2.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(animals, key = { it.id }) { animal ->
                AnimalView(
                    animal = animal,
                    wobbleTrigger = wobbleTriggers[animal.id] ?: 0,
                    enabled = enabled,
                    onTap = { onTap(animal) },
                )
            }
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(14.dp),
        ) {
            when (marker) {
                GroupMarker.Circle -> drawCircle(color = KidPalette.OnSurface.copy(alpha = 0.5f))
                GroupMarker.Square -> drawRect(color = KidPalette.OnSurface.copy(alpha = 0.5f))
            }
        }
    }
}

/** A drawn "+" -- never `Text("+")` -- linking the two addend groups at L5. */
@Composable
private fun PlusSign(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.18f
        drawLine(
            color = color,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun AnimalView(animal: Animal, wobbleTrigger: Int, enabled: Boolean, onTap: () -> Unit) {
    val visualSize = MinTapTarget
    // L4/L5's two groups use Red vs. Blue, not the former Red/Orange
    // (KidPalette.Swatch[0]/[1]) -- red and orange sit on the same
    // protan/deutan confusion axis, so the border was carrying the whole
    // group distinction alone. Red/Blue is safe on that axis, and the
    // GroupMarker corner glyph is a second, colour-independent channel on
    // top of it. L1-L3 only ever use groupIndex 0, so this is unchanged
    // for them.
    val color = if (animal.groupIndex == 0) KidPalette.Red else KidPalette.Blue

    // A soft scale-bounce on a blocked tap (already tapped, or counting is
    // already finished and a numeral pick is pending) -- visible,
    // non-punitive feedback that works even with sound off. This has to be
    // declared BEFORE the tapped/untapped branch below and applied to BOTH,
    // otherwise a tapped animal is a plain non-clickable Box and every tap
    // on it after counting finishes produces nothing at all.
    val wobble = remember(animal.id) { Animatable(1f) }
    LaunchedEffect(wobbleTrigger) {
        if (wobbleTrigger > 0) {
            wobble.animateTo(0.85f, animationSpec = tween(90))
            wobble.animateTo(1f, animationSpec = tween(150))
        }
    }

    // A tapped animal stays in its grid cell -- same size, same position --
    // and reads as dimmed and checked, matching the reference game's
    // pattern for already-resolved items. It is never removed from
    // `items(...)`, so nothing else in the grid shifts. It STILL responds
    // to taps (with a wobble, never a second count) so the blocked-tap path
    // is reachable instead of dead code.
    if (animal.tapped) {
        KidButton(
            onClick = onTap,
            modifier = Modifier
                .size(visualSize)
                .scale(wobble.value)
                .padding(4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawAnimal(color = color, dimmed = true)
                }
                // The visible, in-place mark for a counted animal --
                // required so the count is never carried only by an audio
                // cue.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCheck()
                }
            }
        }
        return
    }

    KidButton(
        onClick = { if (enabled) onTap() },
        modifier = Modifier
            .size(visualSize)
            .scale(wobble.value)
            .padding(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAnimal(color = color, dimmed = false)
        }
    }
}

private fun DrawScope.drawAnimal(color: Color, dimmed: Boolean) {
    val drawColor = if (dimmed) color.copy(alpha = 0.35f) else color
    val w = size.width
    val h = size.height
    val bodyRadius = minOf(w, h) * 0.32f
    val center = Offset(w / 2f, h / 2f + bodyRadius * 0.15f)

    drawCircle(color = drawColor, radius = bodyRadius, center = center)

    // Two simple ears so the shape reads as an animal rather than a plain
    // dot, without needing any bundled artwork.
    val earRadius = bodyRadius * 0.4f
    drawCircle(
        color = drawColor,
        radius = earRadius,
        center = Offset(center.x - bodyRadius * 0.75f, center.y - bodyRadius * 0.95f),
    )
    drawCircle(
        color = drawColor,
        radius = earRadius,
        center = Offset(center.x + bodyRadius * 0.75f, center.y - bodyRadius * 0.95f),
    )
}

private fun DrawScope.drawCheck() {
    val w = size.width
    val h = size.height
    val strokeWidth = minOf(w, h) * 0.1f
    val start = Offset(w * 0.28f, h * 0.52f)
    val mid = Offset(w * 0.44f, h * 0.68f)
    val end = Offset(w * 0.74f, h * 0.32f)
    drawLine(color = KidPalette.Green, start = start, end = mid, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    drawLine(color = KidPalette.Green, start = mid, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
}

@Composable
private fun NumeralPicker(options: List<Int>, wobbleTrigger: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val wobble = remember { Animatable(1f) }
    LaunchedEffect(wobbleTrigger) {
        if (wobbleTrigger > 0) {
            wobble.animateTo(0.9f, animationSpec = tween(90))
            wobble.animateTo(1f, animationSpec = tween(150))
        }
    }

    // fillMaxWidth + real horizontal padding (24dp/side) instead of letting
    // the Row size to its unpadded content -- unpadded, three options (one
    // of them up to two digits wide) can reach ~344dp against a 360dp
    // screen, 8dp/side, which a larger display-size setting or anything
    // narrower than 360dp clips. Centering inside the padded width keeps
    // real margin regardless of content width.
    Row(
        modifier = modifier
            .scale(wobble.value)
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { numeral ->
            KidButton(onClick = { onPick(numeral) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NumberGlyph(value = numeral, color = KidPalette.OnSurface)
                    // A non-numeric channel for this option -- pips exist
                    // only in RunningCount otherwise, so a child who can't
                    // yet read digits has no way to play the picker at all.
                    // This shows the OPTION's value, exactly as much
                    // information as the numeral above it already gives --
                    // never more, so it doesn't leak which option is correct.
                    //
                    // 12dp pips (not the old 8dp) grouped 5+5 with an extra
                    // gap between the groups -- ten 8dp pips at 4dp spacing
                    // read as one dashed bar, not ten countable things.
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val firstGroup = numeral.coerceAtMost(5)
                        val secondGroup = (numeral - 5).coerceAtLeast(0)
                        repeat(firstGroup) { Pip(filled = true, size = 12.dp) }
                        if (secondGroup > 0) {
                            Box(modifier = Modifier.size(width = 6.dp, height = 12.dp))
                            repeat(secondGroup) { Pip(filled = true, size = 12.dp) }
                        }
                    }
                }
            }
        }
    }
}
