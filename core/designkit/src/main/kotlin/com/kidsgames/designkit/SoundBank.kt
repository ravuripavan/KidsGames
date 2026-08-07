package com.kidsgames.designkit

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Wraps [SoundPool] with a fixed set of cues. Games reference cues by name,
 * never by file, so no game module bundles or loads its own sound resources.
 *
 * Cue audio is bundled inside :core:designkit's own res/raw and loaded lazily
 * on first [play]. Missing or unloaded assets are swallowed silently — sound
 * is always optional and a game must never behave differently because a cue
 * failed to load.
 *
 * [Cue] is deliberately small and fixed: it is only for the four generic
 * feedback sounds every game needs (tap, success, retry, celebration). It is
 * NOT how a game plays spoken catalogue content — for that, use [playRaw]
 * with a [com.kidsgames.vocab.VocabItem.audio] or
 * [com.kidsgames.vocab.Sentence.audio] resource id. Both paths share the
 * same [SoundPool], the same lifecycle, and the same silent-no-op-on-failure
 * behaviour; they differ only in whether the resource id is one of the four
 * fixed cues or an arbitrary bundled id from :core:vocab.
 */
class SoundBank(private val context: Context) {

    enum class Cue { TAP, SUCCESS, GENTLE_RETRY, CELEBRATION }

    private val soundPool: SoundPool by lazy {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()
    }

    private val loadedSoundIds = mutableMapOf<Cue, Int>()
    private val loadedRawSoundIds = mutableMapOf<Int, Int>()

    /** Resolves a cue to its bundled raw resource id. Overridable for tests. */
    var resourceFor: (Cue) -> Int? = { null }

    fun play(cue: Cue) {
        val soundId = loadedSoundIds.getOrPut(cue) {
            val resId = resourceFor(cue) ?: return
            loadSafely(resId) ?: return
        }
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    /**
     * Plays an arbitrary bundled audio resource by id — the escape hatch for
     * spoken catalogue content that has no place in the fixed [Cue] set.
     * Callers pass a [com.kidsgames.vocab.VocabItem.audio] or
     * [com.kidsgames.vocab.Sentence.audio] id directly.
     *
     * Today those ids come from PlaceholderAssets in :core:vocab and are NOT
     * real Android resource ids, because no recordings have been produced
     * yet. Any id this cannot load — placeholder, missing, or otherwise
     * unloadable — is a silent no-op, exactly as if the volume were off.
     * Once real recordings replace the placeholders this starts working with
     * no change at the call site.
     */
    fun playRaw(resId: Int) {
        val soundId = loadedRawSoundIds.getOrPut(resId) {
            loadSafely(resId) ?: return
        }
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    private fun loadSafely(resId: Int): Int? = try {
        val soundId = soundPool.load(context, resId, 1)
        if (soundId <= 0) null else soundId
    } catch (_: Exception) {
        null
    }

    fun release() {
        soundPool.release()
        loadedSoundIds.clear()
        loadedRawSoundIds.clear()
    }
}

/**
 * Creates a [SoundBank] scoped to the current composition and releases it
 * automatically via [DisposableEffect] when the composable leaves
 * composition, so games never have to remember to call [SoundBank.release]
 * themselves. Prefer this over `remember { SoundBank(context) }`.
 */
@Composable
fun rememberSoundBank(): SoundBank {
    val context = LocalContext.current
    val soundBank = remember(context) { SoundBank(context) }
    DisposableEffect(soundBank) {
        onDispose { soundBank.release() }
    }
    return soundBank
}
