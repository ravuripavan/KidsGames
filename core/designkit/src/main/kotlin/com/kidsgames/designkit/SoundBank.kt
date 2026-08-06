package com.kidsgames.designkit

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Wraps [SoundPool] with a fixed set of cues. Games reference cues by name,
 * never by file, so no game module bundles or loads its own sound resources.
 *
 * Cue audio is bundled inside :core:designkit's own res/raw and loaded lazily
 * on first [play]. Missing or unloaded assets are swallowed silently — sound
 * is always optional and a game must never behave differently because a cue
 * failed to load.
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

    /** Resolves a cue to its bundled raw resource id. Overridable for tests. */
    var resourceFor: (Cue) -> Int? = { null }

    fun play(cue: Cue) {
        val soundId = loadedSoundIds.getOrPut(cue) {
            val resId = resourceFor(cue) ?: return
            soundPool.load(context, resId, 1)
        }
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
        loadedSoundIds.clear()
    }
}
