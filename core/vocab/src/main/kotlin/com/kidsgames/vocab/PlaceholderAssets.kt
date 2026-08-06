package com.kidsgames.vocab

/**
 * No recorded audio or drawn artwork exists yet for this catalogue. Real
 * Android resource ids (R.drawable.*, R.raw.*) cannot be minted without the
 * actual asset files, so every [VocabItem] and [Sentence] in
 * [VocabCatalogue] and [DailyPicker] points at a placeholder integer from
 * here instead.
 *
 * These are NOT valid Android resource ids and must be replaced, item by
 * item, once real artwork and one-voice recordings are produced and dropped
 * into core/vocab/src/main/res/drawable and res/raw. Nothing downstream
 * should assume these ids resolve to anything.
 */
internal object PlaceholderAssets {
    private var next = 1

    fun nextImage(): Int = next++
    fun nextAudio(): Int = next++
}
