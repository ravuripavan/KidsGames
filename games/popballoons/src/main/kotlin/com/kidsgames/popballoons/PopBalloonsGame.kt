package com.kidsgames.popballoons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import com.kidsgames.designkit.SoundBank
import com.kidsgames.designkit.kidTapFeedback
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
        val state = remember(level) { PopBalloonsState(level) }

        // PopBalloonsState mutates balloon.popped in place and exposes no
        // Compose state of its own -- this counter is what forces
        // recomposition after every pop() call.
        var version by remember(level) { mutableIntStateOf(0) }
        var celebrating by remember(level) { mutableStateOf(false) }

        LaunchedEffect(state, version) {
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
                items(state.balloons, key = { it.id }) { balloon ->
                    if (!balloon.popped) {
                        BalloonView(
                            balloon = balloon,
                            onTap = {
                                val wasPopped = balloon.popped
                                state.pop(balloon.id)
                                version++
                                if (balloon.popped && !wasPopped) {
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                } else {
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                }
                            },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

@Composable
private fun BalloonView(balloon: Balloon, onTap: () -> Unit) {
    val visualSize = MinTapTarget * balloon.size.coerceAtLeast(1f)

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
            .padding(4.dp)
            .kidTapFeedback(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(balloon.color.toComposeColor(), CircleShape),
        )
    }
}

private fun BalloonColor.toComposeColor(): Color = KidPalette.Swatch[this.ordinal]
