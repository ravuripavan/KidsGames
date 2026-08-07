package com.kidsgames.talktime

import com.kidsgames.vocab.DailyPicker
import com.kidsgames.vocab.Sentence
import com.kidsgames.vocab.VocabCatalogue
import com.kidsgames.vocab.VocabItem

/**
 * Plain-Kotlin state machine for `:games:talktime`, no Android imports so it
 * is unit-tested without an emulator. Every mutator returns a NEW
 * [TalkTimeState] -- nothing here ever mutates `this` -- so callers hold this
 * in a single `mutableStateOf` and write the result back, the same shape
 * every other module in the suite uses.
 *
 * [dayOfYear] is passed in rather than read from the clock, which is what
 * keeps this class Android-free and lets tests fix the day to assert
 * determinism. [word] and [sentence] both come from `:core:vocab`'s
 * [DailyPicker], which is itself deterministic by day-of-year -- the same
 * day always yields the same pair, a different pair tomorrow, never random,
 * never networked.
 *
 * Nothing here records or scores the child. There is no field anywhere that
 * captures what the child said or how many times they replayed audio --
 * [acknowledge] and [tapPicture] each only ever move the level toward
 * [isComplete], never away from it, and a wrong [tapPicture] leaves the
 * state byte-for-byte unchanged so the round can be retried without limit.
 *
 * Levels get harder purely on the "more elements" and "more discrimination"
 * axes the design doc requires, never on time or tolerance:
 *  - L1 shows the word alone -- no picture yet ([showsPictureFor] is false).
 *  - L2 adds the picture as a new element ([showsPictureFor] becomes true).
 *  - L3 grows the spoken/written unit from one word to a two-word phrase.
 *  - L4 grows it again to the day's full sentence.
 *  - L5 keeps the full sentence but adds discrimination: the child must tell
 *    the target picture apart from two distractors ([optionCountFor] jumps
 *    from 1 to 3).
 */
data class TalkTimeState(
    val level: Int,
    val dayOfYear: Int,
    val word: VocabItem = DailyPicker.wordOfDay(dayOfYear),
    val sentence: Sentence = DailyPicker.sentenceOfDay(dayOfYear),
    val phraseText: String = "my ${DailyPicker.wordOfDay(dayOfYear).word}",
    val options: List<VocabItem> = buildOptions(level, dayOfYear),
    val isComplete: Boolean = false,
) {

    /** Whether this level's round shows a picture alongside the word/sentence. */
    val showsPicture: Boolean get() = showsPictureFor(level)

    /**
     * The L1-4 "I said it" acknowledgement -- the turn-taking cue that closes
     * a round once the child has had their turn to repeat, never a check of
     * what they said. A no-op at L5 (use [tapPicture] there instead) and a
     * no-op once already complete.
     */
    fun acknowledge(): TalkTimeState = if (level >= 5 || isComplete) this else copy(isComplete = true)

    /**
     * L5 only: tapping one of [options]. Matching [word] completes the
     * level; anything else returns `this` UNCHANGED -- there is no fail
     * state, the round simply continues until the child finds the match, the
     * same shape `:games:whatisit`'s reverse rounds use. A no-op before L5
     * or once already complete.
     */
    fun tapPicture(itemId: String): TalkTimeState {
        if (level < 5 || isComplete) return this
        return if (itemId == word.id) copy(isComplete = true) else this
    }

    companion object {

        /** L1 is the word alone; every other level shows the picture too. */
        fun showsPictureFor(level: Int): Boolean = level >= 2

        /** L5 alone asks the child to discriminate among candidates; every earlier level has a single target. */
        fun optionCountFor(level: Int): Int = if (level >= 5) 3 else 1

        /** Deterministic, evenly-spread pick of two distractors plus the target -- never random, so a given day+level always yields the same three pictures in the same rotated order. */
        private fun buildOptions(level: Int, dayOfYear: Int): List<VocabItem> {
            val target = DailyPicker.wordOfDay(dayOfYear)
            if (level < 5) return listOf(target)
            val pool = VocabCatalogue.items
            if (pool.size < 3) return listOf(target)

            val base = dayOfYear % pool.size
            val offset1 = (pool.size / 3).coerceAtLeast(1)
            val offset2 = (2 * pool.size / 3).coerceAtLeast(2)

            var other1 = pool[(base + offset1) % pool.size]
            if (other1.id == target.id) other1 = pool[(base + 1) % pool.size]

            var other2 = pool[(base + offset2) % pool.size]
            if (other2.id == target.id || other2.id == other1.id) other2 = pool[(base + 2) % pool.size]
            if (other2.id == target.id || other2.id == other1.id) other2 = pool[(base + 3) % pool.size]

            val options = listOf(target, other1, other2)
            val rotation = dayOfYear % 3
            return options.drop(rotation) + options.take(rotation)
        }
    }
}
