package com.kidsgames.talktime

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidsgames.designkit.Celebration
import com.kidsgames.designkit.KidButton
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.SoundBank
import com.kidsgames.designkit.rememberSoundBank
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import com.kidsgames.vocab.VocabItem
import java.time.LocalDate
import kotlinx.coroutines.delay

/**
 * The suite's daily word/sentence lesson: a picture and its spoken name
 * (or phrase, or sentence, per [level]), paused so the child can repeat it,
 * celebrated unconditionally. See [TalkTimeState]'s KDoc for the level
 * shape and for why nothing here is ever scored.
 *
 * LAYOUT BUDGET (comment-first, measured against the ~360x496dp
 * [BoxWithConstraints] actually reports after the shell's `safeDrawing`
 * insets and 96dp exit strip):
 *
 * Levels 1-4 (single target, "I said it" flow):
 *   top padding                 16dp
 *   word/phrase/sentence text  ~64dp  (up to 2 lines, only rendered content
 *                                      in the whole suite -- see the text
 *                                      exemption note below)
 *   gap                         16dp
 *   picture card               180dp  (level 1 only: SKIPPED, see
 *                                      [TalkTimeState.showsPictureFor])
 *   gap                         16dp
 *   speaker/replay button       88dp
 *   gap                         16dp
 *   "I said it" button          80dp
 *   ------------------------------
 *   L1  (no picture): 16+64+16+88+16+80          = 280dp of 496dp
 *   L2-4 (with picture): 16+64+16+180+16+88+16+80 = 476dp of 496dp
 *
 * Level 5 (sentence, then pick-the-picture):
 *   top padding                 16dp
 *   sentence text               64dp
 *   gap                         16dp
 *   speaker/replay button       88dp
 *   gap                         16dp
 *   row of 3 option pictures   112dp  (100dp buttons + 2*12dp gaps = 324dp
 *                                      of ~328dp usable width, 360dp screen
 *                                      minus 16dp margins each side)
 *   ------------------------------
 *   used                       312dp of 496dp -> 184dp spare
 *
 * TEXT EXEMPTION: this is the ONLY module in the suite allowed to render
 * text, and even here text is used for exactly one thing -- the word,
 * phrase, or sentence being taught -- never an instruction, a label, or a
 * piece of feedback. Every [Text] call below renders [TalkTimeState.word],
 * [TalkTimeState.phraseText], or [TalkTimeState.sentence] and nothing else.
 *
 * AUDIO EXEMPTION: like `:games:whatisit`, this game is not fully playable
 * muted, because the spoken word/phrase/sentence IS its content. It shows
 * the same visible [MutedSpeakerIndicator]-shaped slash across the speaker
 * glyph whenever the device volume is at zero.
 *
 * NO MICROPHONE anywhere in this module. The child's speech is never
 * recorded, listened for, or evaluated -- there is no audio *input* code
 * path anywhere in this file, only playback triggers via
 * [SoundBank.playRaw]. No recordings exist yet -- [VocabItem.audio] and
 * `Sentence.audio` are still placeholder integers -- so playback is a
 * silent no-op today and starts working the moment real audio lands, with
 * no change here.
 */
object TalkTimeGame : GameModule {

    override val id: String = "talktime"
    override val icon: Int = R.drawable.ic_talktime
    override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
    override val estimatedMinutes: Int = 4
    override val levelCount: Int = 5

    @Composable
    override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
        val soundBank = rememberSoundBank()
        val muted = rememberIsDeviceMuted()
        val dayOfYear = remember { LocalDate.now().dayOfYear }

        // Immutable state machine: acknowledge()/tapPicture() each return a
        // NEW TalkTimeState, never mutating the previous one. Writing the
        // result back to this `var` is what drives recomposition -- there is
        // no separate version/trigger counter anywhere in this module.
        var state by remember(level) { mutableStateOf(TalkTimeState(level = level, dayOfYear = dayOfYear)) }
        var celebrating by remember(level) { mutableStateOf(false) }

        LaunchedEffect(state.isComplete) {
            if (state.isComplete && !celebrating) {
                celebrating = true
                soundBank.play(SoundBank.Cue.CELEBRATION)
                delay(if (level == 5) 1600L else 1100L)
                onFinished(Outcome.Completed)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(KidPalette.Background),
        ) {
            key(level, dayOfYear) {
                if (level < 5) {
                    LessonRound(
                        state = state,
                        muted = muted,
                        onSpeak = { playAudio(soundBank, state) },
                        onAcknowledge = { if (!celebrating) state = state.acknowledge() },
                    )
                } else {
                    PickPictureRound(
                        state = state,
                        muted = muted,
                        onSpeak = { playAudio(soundBank, state) },
                        onTapOption = { itemId ->
                            if (!celebrating) {
                                val next = state.tapPicture(itemId)
                                if (next != state) {
                                    soundBank.play(SoundBank.Cue.SUCCESS)
                                    state = next
                                } else {
                                    soundBank.play(SoundBank.Cue.GENTLE_RETRY)
                                }
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

/** Plays the level-appropriate audio: the word (L1-2), or the day's sentence (L3-5, per the design table's phrase/sentence shape). */
private fun playAudio(soundBank: SoundBank, state: TalkTimeState) {
    if (state.level <= 2) {
        soundBank.playRaw(state.word.audio)
    } else {
        soundBank.playRaw(state.sentence.audio)
    }
}

/** Levels 1-4: word, phrase, or sentence text plus (L2+) a picture, a speaker to hear it, and an "I said it" button to close the round. */
@Composable
private fun LessonRound(
    state: TalkTimeState,
    muted: Boolean,
    onSpeak: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    var speakTrigger by remember(state.level) { mutableStateOf(0) }

    LaunchedEffect(state.level) {
        speakTrigger++
        onSpeak()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LessonText(state = state)

        Spacer(modifier = Modifier.size(16.dp))

        if (state.showsPicture) {
            PictureCard(item = state.word, speakTrigger = speakTrigger, size = 180.dp, glyphSize = 120.dp)
            Spacer(modifier = Modifier.size(16.dp))
        }

        SpeakerButton(
            size = 88.dp,
            muted = muted,
            onTap = {
                speakTrigger++
                onSpeak()
            },
        )

        Spacer(modifier = Modifier.size(16.dp))

        KidButton(onClick = onAcknowledge, testTag = "said-it") {
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                CheckGlyph(modifier = Modifier.size(44.dp))
            }
        }
    }
}

/** Level 5: the sentence is spoken, then the child taps the picture it describes among three candidates. */
@Composable
private fun PickPictureRound(
    state: TalkTimeState,
    muted: Boolean,
    onSpeak: () -> Unit,
    onTapOption: (String) -> Unit,
) {
    var speakTrigger by remember(state.level) { mutableStateOf(0) }
    var wrongTapId by remember(state.level) { mutableStateOf<String?>(null) }
    var wrongTapTrigger by remember(state.level) { mutableStateOf(0) }

    LaunchedEffect(state.level) {
        speakTrigger++
        onSpeak()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LessonText(state = state)

        Spacer(modifier = Modifier.size(16.dp))

        SpeakerButton(
            size = 88.dp,
            muted = muted,
            onTap = {
                speakTrigger++
                onSpeak()
            },
        )

        Spacer(modifier = Modifier.size(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (option in state.options) {
                OptionButton(
                    item = option,
                    wrongTapId = wrongTapId,
                    wrongTapTrigger = wrongTapTrigger,
                    onTap = {
                        if (option.id != state.word.id) {
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

/** The word/phrase/sentence text -- the ONLY place this module (or the suite) ever renders text, and only ever the content being taught. */
@Composable
private fun LessonText(state: TalkTimeState) {
    val text = when (state.level) {
        1, 2 -> state.word.word
        3 -> state.phraseText
        else -> state.sentence.text
    }
    Text(
        text = text,
        color = KidPalette.OnSurface,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

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

@Composable
private fun OptionButton(
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
    KidButton(onClick = onTap, testTag = "option-${item.id}") {
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
private fun CheckGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.4f, h * 0.8f)
            lineTo(w * 0.85f, h * 0.25f)
        }
        drawPath(
            path,
            color = KidPalette.Green,
            style = Stroke(width = w * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}

/**
 * Polls the current media volume roughly once a second. There is no push
 * notification for volume changes without registering a broadcast receiver
 * (a capability :core:designkit does not expose to game modules), so a
 * lightweight poll is the closest honest option available here -- the same
 * approach `:games:whatisit` uses for the same reason.
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
