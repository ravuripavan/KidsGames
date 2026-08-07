package com.kidsgames.popballoons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.delay

/**
 * The reference `:games:*` implementation. Tap floating balloons; each pops
 * with a colour burst and (when a device has sound) a spoken colour name.
 *
 * Copy this object's shape for every other game module: an `object : GameModule`
 * exposing the frozen properties, and a `Play` composable that owns nothing
 * but a plain-Kotlin, pure state machine ([PopBalloonsState]) held in a
 * single `mutableStateOf`. `pop()` returns a brand-new state object rather
 * than mutating anything in place; recomposition is driven entirely by that
 * new object's identity being written back to the `var`. There is no
 * separate "version" or "trigger" counter anywhere in this module, and
 * there must never be one -- if you find yourself reaching for one, the
 * state shape is wrong, not missing a counter.
 *
 * INVARIANT this Play composable must hold: a balloon that has not been
 * popped never changes screen position. [items] below is called on the
 * FULL (unfiltered) balloon list for exactly this reason -- popped balloons
 * stay in the grid as invisible, non-clickable placeholders instead of
 * being removed, so every still-live balloon keeps the same grid cell for
 * its whole life. (An earlier version filtered popped balloons out of the
 * list fed to `items(...)`; that reflows every later cell back by one slot
 * on every pop, which is worse than useless at L4/L5 where the child is
 * aiming at one specific colour among decoys -- the board re-packs under
 * their finger between taps.)
 *
 * WARNING for anyone copying [BalloonView]: modifiers passed to `KidButton`'s
 * `modifier =` parameter (scale, offset, etc.) move the drawn VISUAL only --
 * they land on `KidButton`'s inner content Box, not on its outer touch
 * node, which is sized and positioned independently (see [MinTapTarget]).
 * At the ~6dp drift used here that mismatch is invisible, but any module
 * with real travel (a lane-switching or drag-based game) MUST apply
 * position-changing modifiers to the element that also owns the touch
 * target, or the hitbox will silently stop matching what's on screen.
 */
object PopBalloonsGame : GameModule {

    override val id: String = "popballoons"
    override val icon: Int = R.drawable.ic_popballoons
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val context = LocalContext.current
        val soundBank = remember(context) { SoundBank(context) }

        // Immutable state machine: pop() returns a NEW PopBalloonsState, it
        // never mutates balloons in place. Writing the result back to this
        // `var` is what drives recomposition -- there is no separate
        // version/trigger counter to remember to bump, and there must never
        // be one.
        var state by remember(level) { mutableStateOf(PopBalloonsState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped per-balloon on a wrong-colour tap so BalloonView can play a
        // visible wobble even with sound off -- the only feedback used to be
        // an audio cue, which is silent at the spec's zero-volume baseline.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }
        // Bumped per-balloon on a SUCCESSFUL pop so BalloonView can play the
        // fx_pop burst once, purely visual (no state meaning attached).
        var popTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }

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
                        colors = listOf(
                            Color(0xFFFFF3D6),
                            Color(0xFFFFE3EC),
                            Color(0xFFE3F1FF),
                        ),
                    ),
                ),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MinTapTarget + 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Emit EVERY balloon, popped or not, to items(...) -- never
                // filter the list. A LazyVerticalGrid fills cells in list
                // order, so removing an item reflows every later item back
                // one slot on every single pop. Instead, popped balloons
                // render as an invisible, non-clickable placeholder of the
                // same size (see [BalloonView]'s `popped` branch) so their
                // cell stays occupied and no still-live balloon ever moves.
                items(state.balloons, key = { it.id }) { balloon ->
                    BalloonView(
                        balloon = balloon,
                        wobbleTrigger = wobbleTriggers[balloon.id] ?: 0,
                        popTrigger = popTriggers[balloon.id] ?: 0,
                        onTap = {
                            val next = state.pop(balloon.id)
                            val tappedNowPopped = next.balloons.first { it.id == balloon.id }.popped
                            if (tappedNowPopped) {
                                state = next
                                popTriggers = popTriggers +
                                    (balloon.id to (popTriggers[balloon.id] ?: 0) + 1)
                                soundBank.play(SoundBank.Cue.SUCCESS)
                            } else {
                                wobbleTriggers = wobbleTriggers +
                                    (balloon.id to (wobbleTriggers[balloon.id] ?: 0) + 1)
                                soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                            }
                        },
                    )
                }
            }

            // The ONLY on-screen indication of what to pop next at L4/L5 --
            // without it a wrong tap at zero volume looks like nothing
            // happened and the child has no way to discover the goal.
            if (level >= 4 && !celebrating) {
                TargetColorSwatch(
                    color = state.targetColor,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

/**
 * L4/L5's "pop this one" cue, now drawn as the actual balloon artwork for
 * the target colour (body + face) instead of an abstract flat swatch, so the
 * child recognises the thing they're hunting on sight.
 */
@Composable
private fun TargetColorSwatch(color: BalloonColor, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(64.dp)
            .border(4.dp, KidPalette.Background, CircleShape)
            .border(6.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .padding(6.dp),
    ) {
        BalloonArtwork(color = color, modifier = Modifier.fillMaxSize())
    }
}

/**
 * The coloured body + face composite shared by [BalloonView] and
 * [TargetColorSwatch]. Exhaustive `when` over [BalloonColor] with no `else`
 * so a colour added later without matching art fails the build instead of
 * silently drawing nothing.
 *
 * NOTE: [BalloonColor.ORANGE] has no staged `balloon_orange.png` in
 * drawable-nodpi (only red/blue/green/yellow/purple/pink shipped). Rather
 * than silently drop orange balloons from the game -- which L3-L5 use as
 * real gameplay colours, including as the L4 decoy colour -- this tints the
 * yellow body art towards `KidPalette.Orange` as a stand-in. This is a
 * flagged compromise, not a silent one: see the final report for the
 * missing-asset callout.
 */
@Composable
private fun BalloonArtwork(color: BalloonColor, modifier: Modifier = Modifier) {
    val bodyRes = when (color) {
        BalloonColor.RED -> R.drawable.balloon_red
        BalloonColor.ORANGE -> R.drawable.balloon_yellow
        BalloonColor.YELLOW -> R.drawable.balloon_yellow
        BalloonColor.GREEN -> R.drawable.balloon_green
        BalloonColor.BLUE -> R.drawable.balloon_blue
        BalloonColor.PURPLE -> R.drawable.balloon_purple
        BalloonColor.PINK -> R.drawable.balloon_pink
    }
    val tint = if (color == BalloonColor.ORANGE) {
        ColorFilter.tint(KidPalette.Orange, BlendMode.Modulate)
    } else {
        null
    }
    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = bodyRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = tint,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(id = R.drawable.balloon_face),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BalloonView(
    balloon: Balloon,
    wobbleTrigger: Int,
    popTrigger: Int,
    onTap: () -> Unit,
) {
    val visualSize = MinTapTarget * balloon.size.coerceAtLeast(1f)

    // Popped balloons still occupy their grid cell (see the caller's
    // comment on why the balloon list is never filtered) but must neither
    // be visible nor tappable. They still play a brief burst animation the
    // instant they're popped (keyed on balloon id + the monotonically
    // increasing popTrigger, never on the whole balloon/state object, so it
    // can't restart or skip mid-flight), then settle into the same
    // same-size, invisible, non-clickable placeholder as before, keeping
    // the cell's dimensions in the grid layout without drawing or reacting
    // to touch.
    if (balloon.popped) {
        val fxAlpha = remember(balloon.id) { Animatable(0f) }
        val fxScale = remember(balloon.id) { Animatable(0.6f) }
        LaunchedEffect(balloon.id, popTrigger) {
            if (popTrigger > 0) {
                fxAlpha.snapTo(1f)
                fxScale.snapTo(0.6f)
                fxScale.animateTo(1.4f, animationSpec = tween(320))
            }
        }
        LaunchedEffect(balloon.id, popTrigger) {
            if (popTrigger > 0) {
                fxAlpha.animateTo(0f, animationSpec = tween(320))
            }
        }
        Box(modifier = Modifier.size(visualSize).padding(4.dp), contentAlignment = Alignment.Center) {
            if (fxAlpha.value > 0f) {
                Image(
                    painter = painterResource(id = R.drawable.fx_pop),
                    contentDescription = null,
                    modifier = Modifier
                        .size(visualSize)
                        .scale(fxScale.value)
                        .padding(4.dp),
                    alpha = fxAlpha.value,
                )
            }
        }
        return
    }

    // A soft scale-bounce on a wrong tap -- visible, non-punitive feedback
    // that works even with sound off.
    val wobble = remember(balloon.id) { Animatable(1f) }
    LaunchedEffect(balloon.id, wobbleTrigger) {
        if (wobbleTrigger > 0) {
            wobble.animateTo(0.85f, animationSpec = tween(90))
            wobble.animateTo(1f, animationSpec = tween(150))
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "balloon-drift")
    val drift = if (balloon.drifting) {
        val anim = infiniteTransition.animateFloat(
            initialValue = -6f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200 + (balloon.id % 5) * 180,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "balloon-drift-${balloon.id}",
        )
        anim.value
    } else {
        0f
    }

    // A gentle idle bob so the screen feels alive even on the still (L1)
    // and non-drifting balloons: low amplitude, slow, and staggered per
    // balloon via a per-id start delay so the board doesn't move in
    // lockstep. Distinct from `drift`, which is the existing lateral sway
    // used only at L2+; this bob applies to every balloon at every level.
    val bob = remember(balloon.id) { Animatable(0f) }
    LaunchedEffect(balloon.id) {
        delay((balloon.id % 7) * 140L)
        while (true) {
            bob.animateTo(1f, animationSpec = tween(1500 + (balloon.id % 4) * 130, easing = LinearEasing))
            bob.animateTo(0f, animationSpec = tween(1500 + (balloon.id % 4) * 130, easing = LinearEasing))
        }
    }
    val bobOffset = bob.value * 4f

    // A small per-balloon tilt gives balloons a distinct silhouette beyond
    // colour alone (the shared face/body art is otherwise identical across
    // colours), staggered by id so neighbours don't read as clones.
    val tilt = ((balloon.id % 5) - 2) * 3f

    KidButton(
        onClick = onTap,
        modifier = Modifier
            .size(visualSize)
            .offset(y = (drift + bobOffset).dp)
            .scale(wobble.value)
            .rotate(tilt)
            .padding(4.dp),
    ) {
        BalloonArtwork(color = balloon.color, modifier = Modifier.fillMaxSize())
    }
}
