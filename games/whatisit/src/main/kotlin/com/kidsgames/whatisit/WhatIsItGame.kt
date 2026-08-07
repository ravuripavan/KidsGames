package com.kidsgames.whatisit

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import com.kidsgames.vocab.VocabItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows a picture and invites the child to say what it is. Tapping the
 * speaker plays the name; a next arrow brings the following item. The
 * child's answer is NEVER captured or checked -- see [WhatIsItState]'s KDoc.
 * The pause before tapping the speaker IS the activity.
 *
 * LAYOUT BUDGET (comment-first, per the lessons learned elsewhere in this
 * suite) against the ~360x544dp the shell hands this composable, with no
 * manual bottom-left exit-zone reservation needed (the shell now does that
 * structurally):
 *
 * Naming round (forward mode):
 *   top padding            20dp
 *   picture card           240dp  (a 160dp glyph centred inside it)
 *   gap                     24dp
 *   speaker button          96dp
 *   gap                     24dp
 *   next-arrow button       88dp
 *   ------------------------------
 *   used                   492dp  of 544dp -> 52dp spare at the bottom
 *
 * Reverse round (L5 only):
 *   top padding             20dp
 *   "what's this called?" speaker button   96dp
 *   gap                     20dp
 *   row of 3 option buttons 100dp  (100*3 + 12*2 = 324dp of 328dp usable
 *                                   width, 360dp screen minus 16dp margins
 *                                   on each side)
 *   gap                     20dp
 *   ------------------------------
 *   used                   256dp  of 544dp -> 288dp spare
 *
 * AUDIO EXEMPTION: unlike every other game in the suite, this one is not
 * fully playable muted, because the spoken name IS its content (per the
 * design doc). [MutedSpeakerIndicator] draws a visible slash across every
 * speaker glyph whenever the device volume is at zero, so a muted child
 * still gets a truthful signal that sound would have played.
 *
 * NO MICROPHONE anywhere in this module. The child's speech is never
 * recorded, listened for, or evaluated -- there is no audio *input* code
 * path in this file at all, only playback triggers.
 *
 * MISSING FROM :core:designkit: there is no [SoundBank.Cue] for "speak this
 * catalogue word" (only TAP/SUCCESS/GENTLE_RETRY/CELEBRATION exist), and
 * [VocabItem.audio] is a [PlaceholderAssets] integer with no real recording
 * behind it yet. Tapping the speaker therefore plays [SoundBank.Cue.TAP] as
 * a generic acknowledgement and drives a visible "speaking" animation on the
 * glyph -- the closest honest stand-in with what exists today, not a local
 * reimplementation of real word-audio playback.
 */
object WhatIsItGame : GameModule {

    override val id: String = "whatisit"
    override val icon: Int = R.drawable.ic_whatisit
    override val ageBand: AgeBand = AgeBand.FIVE_TO_SIX
    override val estimatedMinutes: Int = 5
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val muted = rememberIsDeviceMuted()

        // Immutable state machine: advance()/tapReverseOption() each return
        // a NEW WhatIsItState, never mutating the previous one. Writing the
        // result back to this `var` is what drives recomposition -- there
        // is no separate version/trigger counter anywhere in this module.
        var state by remember(level) { mutableStateOf(WhatIsItState(level)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        LaunchedEffect(state.isComplete) {
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
            // key(state.index) gives every round a fresh identity for its
            // own remembered animation state (speak triggers, wrong-tap
            // triggers) -- a shape this module borrows from matchshapes'
            // remember(level) pattern, scoped one level finer since rounds
            // change far more often than levels do.
            key(state.index) {
                when (val round = state.currentRound) {
                    is Round.Name -> NamingRound(
                        item = round.item,
                        muted = muted,
                        onSpeak = { soundBank.play(SoundBank.Cue.TAP) },
                        onNext = { if (!celebrating) state = state.advance() },
                    )
                    is Round.Reverse -> ReverseRound(
                        round = round,
                        muted = muted,
                        onSpeak = { soundBank.play(SoundBank.Cue.TAP) },
                        onTapOption = { itemId ->
                            if (!celebrating) {
                                val next = state.tapReverseOption(itemId)
                                if (next != state) {
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                    state = next
                                } else {
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                }
                            }
                        },
                    )
                    null -> Unit
                }
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Celebration(visible = celebrating, big = level == 5)
            }
        }
    }
}

/** One naming round: a big picture, a speaker to hear it, an arrow to move on. */
@Composable
private fun NamingRound(
    item: VocabItem,
    muted: Boolean,
    onSpeak: () -> Unit,
    onNext: () -> Unit,
) {
    var speakTrigger by remember(item.id) { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PictureCard(item = item, speakTrigger = speakTrigger, size = 240.dp, glyphSize = 160.dp)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        SpeakerButton(
            size = 96.dp,
            muted = muted,
            onTap = {
                speakTrigger++
                onSpeak()
            },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(24.dp))

        KidButton(onClick = onNext, testTag = "next-item") {
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                NextArrowGlyph(modifier = Modifier.size(48.dp))
            }
        }
    }
}

/** L5 reverse round: hear a name, tap the matching picture among three. */
@Composable
private fun ReverseRound(
    round: Round.Reverse,
    muted: Boolean,
    onSpeak: () -> Unit,
    onTapOption: (String) -> Unit,
) {
    var speakTrigger by remember(round) { mutableStateOf(0) }
    var wrongTapId by remember(round) { mutableStateOf<String?>(null) }
    var wrongTapTrigger by remember(round) { mutableStateOf(0) }

    // The name is spoken automatically as the round begins, and can be
    // replayed without limit by tapping the same speaker button again.
    LaunchedEffect(round) {
        speakTrigger++
        onSpeak()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 20.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpeakerButton(
            size = 96.dp,
            muted = muted,
            onTap = {
                speakTrigger++
                onSpeak()
            },
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (option in round.options) {
                ReverseOptionButton(
                    item = option,
                    wrongTapId = wrongTapId,
                    wrongTapTrigger = wrongTapTrigger,
                    onTap = {
                        if (option.id != round.targetId) {
                            wrongTapId = option.id
                            wrongTapTrigger++
                        }
                        onTapOption(option.id)
                    },
                )
            }
        }
    }
}

/**
 * The picture on screen. [speakTrigger] drives a gentle scale-pop each time
 * the speaker is tapped -- the visible carrier that "something was said",
 * required because this game's audio (unlike every other module's) may
 * actually be the ONLY channel a real recording would use, and the suite
 * still promises a zero-volume-understandable screen (see class KDoc).
 */
@Composable
private fun PictureCard(
    item: VocabItem,
    speakTrigger: Int,
    size: androidx.compose.ui.unit.Dp,
    glyphSize: androidx.compose.ui.unit.Dp,
) {
    val pop = remember(item.id) { Animatable(1f) }
    LaunchedEffect(speakTrigger) {
        if (speakTrigger > 0) {
            pop.snapTo(1f)
            pop.animateTo(1.12f, tween(140))
            pop.animateTo(1f, tween(180))
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .scale(pop.value)
            .background(KidPalette.Surface, RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
        ItemGlyph(item = item, modifier = Modifier.size(glyphSize))
    }
}

/** One tappable candidate in a reverse round. */
@Composable
private fun ReverseOptionButton(
    item: VocabItem,
    wrongTapId: String?,
    wrongTapTrigger: Int,
    onTap: () -> Unit,
) {
    val wobble = remember(item.id) { Animatable(0f) }
    LaunchedEffect(wrongTapTrigger) {
        if (wrongTapTrigger > 0 && wrongTapId == item.id) {
            wobble.snapTo(0f)
            wobble.animateTo(10f, tween(80))
            wobble.animateTo(-10f, tween(140))
            wobble.animateTo(0f, tween(80))
        }
    }
    KidButton(onClick = onTap, testTag = "reverse-option-${item.id}") {
        Box(
            modifier = Modifier
                .size(100.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ItemGlyph(
                item = item,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(wobble.value),
            )
        }
    }
}

/**
 * A speaker glyph, tappable. Draws [MutedSpeakerIndicator]'s slash across
 * itself whenever [muted] -- the required visible signal for this module's
 * audio exemption (see class KDoc).
 */
@Composable
private fun SpeakerButton(
    size: androidx.compose.ui.unit.Dp,
    muted: Boolean,
    onTap: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "speaker-pulse")
    val ringScale by pulse.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "speaker-ring",
    )
    KidButton(onClick = onTap, testTag = "speaker") {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(size * 0.7f)
                    .scale(if (muted) 1f else ringScale),
            ) {
                drawSpeakerGlyph(if (muted) KidPalette.OnSurface.copy(alpha = 0.45f) else KidPalette.Blue)
                if (muted) drawMuteSlash()
            }
        }
    }
}

private fun DrawScope.drawSpeakerGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val cone = Path().apply {
        moveTo(w * 0.1f, h * 0.38f)
        lineTo(w * 0.35f, h * 0.38f)
        lineTo(w * 0.6f, h * 0.15f)
        lineTo(w * 0.6f, h * 0.85f)
        lineTo(w * 0.35f, h * 0.62f)
        lineTo(w * 0.1f, h * 0.62f)
        close()
    }
    drawPath(cone, color = color)
    // Sound waves.
    drawArc(
        color = color,
        startAngle = -40f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(w * 0.62f, h * 0.22f),
        size = androidx.compose.ui.geometry.Size(w * 0.3f, h * 0.56f),
        style = Stroke(width = w * 0.06f),
    )
    drawArc(
        color = color,
        startAngle = -30f,
        sweepAngle = 60f,
        useCenter = false,
        topLeft = Offset(w * 0.78f, h * 0.3f),
        size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.4f),
        style = Stroke(width = w * 0.06f),
    )
}

/** The muted-indicator slash: this game's required visible signal when the device volume is zero. */
private fun DrawScope.drawMuteSlash() {
    drawLine(
        color = KidPalette.Red,
        start = Offset(size.width * 0.05f, size.height * 0.05f),
        end = Offset(size.width * 0.95f, size.height * 0.95f),
        strokeWidth = size.width * 0.09f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
}

@Composable
private fun NextArrowGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.5f)
            lineTo(w * 0.65f, h * 0.5f)
            moveTo(w * 0.45f, h * 0.2f)
            lineTo(w * 0.85f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.8f)
        }
        drawPath(path, color = KidPalette.Green, style = Stroke(width = w * 0.1f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/**
 * Polls the current media volume roughly once a second. There is no push
 * notification for volume changes without registering a broadcast receiver
 * (a capability :core:designkit does not expose to game modules), so a
 * lightweight poll is the closest honest option available here.
 */
@Composable
private fun rememberIsDeviceMuted(): Boolean {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(isMuted(context)) }
    LaunchedEffect(context) {
        while (true) {
            muted = isMuted(context)
            delay(1000L)
        }
    }
    return muted
}

private fun isMuted(context: Context): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
}
