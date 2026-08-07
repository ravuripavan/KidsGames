package com.kidsgames.popballoons

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * but a plain-Kotlin state machine plus a recomposition trigger.
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
        // version counter to remember to bump.
        var state by remember(level) { mutableStateOf(PopBalloonsState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }
        // Bumped per-balloon on a wrong-colour tap so BalloonView can play a
        // visible wobble even with sound off -- the only feedback used to be
        // an audio cue, which is silent at the spec's zero-volume baseline.
        var wobbleTriggers by remember(level) { mutableStateOf(emptyMap<Int, Int>()) }

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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = MinTapTarget + 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Filter popped balloons out of the list fed to items(...)
                // rather than branching inside the item lambda -- emitting a
                // popped balloon as a zero-size item reflows the whole grid
                // and moves every other balloon out from under the child's
                // next tap.
                items(state.balloons.filter { !it.popped }, key = { it.id }) { balloon ->
                    BalloonView(
                        balloon = balloon,
                        wobbleTrigger = wobbleTriggers[balloon.id] ?: 0,
                        onTap = {
                            val next = state.pop(balloon.id)
                            val tappedNowPopped = next.balloons.first { it.id == balloon.id }.popped
                            if (tappedNowPopped) {
                                state = next
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
                    color = state.targetColor.toComposeColor(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

@Composable
private fun TargetColorSwatch(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(color, CircleShape)
            .border(4.dp, KidPalette.Background, CircleShape)
            .border(6.dp, Color.White.copy(alpha = 0.9f), CircleShape),
    )
}

@Composable
private fun BalloonView(balloon: Balloon, wobbleTrigger: Int, onTap: () -> Unit) {
    val visualSize = MinTapTarget * balloon.size.coerceAtLeast(1f)

    // A soft scale-bounce on a wrong tap -- visible, non-punitive feedback
    // that works even with sound off.
    val wobble = remember(balloon.id) { Animatable(1f) }
    LaunchedEffect(wobbleTrigger) {
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

    KidButton(
        onClick = onTap,
        modifier = Modifier
            .size(visualSize)
            .offset(y = drift.dp)
            .scale(wobble.value)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(balloon.color.toComposeColor(), CircleShape),
        )
    }
}

private fun BalloonColor.toComposeColor(): Color = KidPalette.Swatch[this.ordinal]
