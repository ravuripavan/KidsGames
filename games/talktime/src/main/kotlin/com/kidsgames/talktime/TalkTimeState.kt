package com.kidsgames.talktime

import com.kidsgames.vocab.DailyPicker
import com.kidsgames.vocab.Sector
import com.kidsgames.vocab.Sentence
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
 * [word] and [sentence] are drawn from two INDEPENDENT `:core:vocab` pools
 * (a 144-item catalogue and a much larger sentence pool) with no shared key,
 * so nothing in this module can assert the sentence is literally "about" the
 * word of the day. L1-L3 stay internally coherent by only ever pairing the
 * word with ITS OWN picture ([word.image]). L4 and L5 instead key their
 * picture(s) off [Sentence.image] -- the field `:core:vocab` ships
 * specifically so a sentence can be paired with the one picture it is about,
 * independent of the word pool -- so the picture the child sees always
 * matches what they just heard, on every single day.
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
 *  - L4 grows it again to the day's full sentence, paired with the picture
 *    the sentence is about ([Sentence.image]), not the unrelated word.
 *  - L5 keeps the full sentence but adds discrimination: the child must tell
 *    the sentence's own picture apart from two distractors whose sector
 *    visibly differs from it ([optionCountFor] jumps from 1 to 3).
 */
data class TalkTimeState(
    val level: Int,
    val dayOfYear: Int,
    val word: VocabItem = DailyPicker.wordOfDay(dayOfYear),
    val sentence: Sentence = DailyPicker.sentenceOfDay(dayOfYear),
    val phraseText: String = phraseTextFor(DailyPicker.wordOfDay(dayOfYear)),
    val pictureOptions: List<Int> = buildPictureOptions(level, dayOfYear),
    val isComplete: Boolean = false,
) {

    /** Whether this level's round shows a picture alongside the word/phrase/sentence. */
    val showsPicture: Boolean get() = showsPictureFor(level)

    /** The single L4 target picture: the sentence's OWN image, never the unrelated word's. */
    val sentenceImage: Int get() = sentence.image

    /**
     * The L1-4 "I said it" acknowledgement -- the turn-taking cue that closes
     * a round once the child has had their turn to repeat, never a check of
     * what they said. A no-op at L5 (use [tapPicture] there instead) and a
     * no-op once already complete.
     */
    fun acknowledge(): TalkTimeState = if (level >= 5 || isComplete) this else copy(isComplete = true)

    /**
     * L5 only: tapping one of [pictureOptions] by its image id. Matching the
     * sentence's own [Sentence.image] completes the level; anything else
     * returns `this` UNCHANGED -- there is no fail state, the round simply
     * continues until the child finds the match, the same shape
     * `:games:whatisit`'s reverse rounds use. A no-op before L5 or once
     * already complete.
     */
    fun tapPicture(image: Int): TalkTimeState {
        if (level < 5 || isComplete) return this
        return if (image == sentence.image) copy(isComplete = true) else this
    }

    companion object {

        /**
         * The L3 two-word phrase, built so it is grammatical for EVERY entry
         * in the catalogue, not just the common case. `"my $word"` reads
         * fine for ordinary nouns ("my dog", "my hat") but breaks for the
         * handful of catalogue entries that are not simple singular nouns:
         *  - entries that are already a two-word noun phrase on their own
         *    ("police officer", "bike race") stay exactly as written --
         *    prepending anything would push them to three words and "my
         *    police officer" reads like the child owns one;
         *  - activities take the verb English actually uses with them --
         *    "go swimming", "play soccer", "do gymnastics" -- because "my
         *    running" and "my hockey" are not phrases anyone says;
         *  - JOBS entries take an article rather than a possessive, since a
         *    child does not own a person: "a doctor", not "my doctor".
         * Every other catalogue word falls through to the possessive frame,
         * which is correct for ordinary singular nouns including "ball".
         * The verb sets are named individually rather than derived from the
         * SPORTS sector, because that sector mixes activities with an
         * ordinary object ("ball") that the possessive frame handles fine.
         * Checked against every day of the year in TalkTimeStateTest, not
         * just a sample day, since the broken cases only show up on
         * specific catalogue entries.
         */
        /** Activities you "go": "go swimming", never "my swimming". */
        private val GO_ACTIVITIES = setOf("swimming", "running", "skating")

        /** Games you "play": "play soccer", never "my soccer". */
        private val PLAY_GAMES = setOf("soccer", "basketball", "tennis", "baseball", "hockey")

        /** Activities you "do". Only one in the catalogue, but named rather than
         *  lumped in with the games, because "play gymnastics" is wrong too. */
        private val DO_ACTIVITIES = setOf("gymnastics")

        fun phraseTextFor(item: VocabItem): String {
            val word = item.word
            return when {
                // Already two words; a frame would make it three.
                word.contains(' ') -> word
                word in GO_ACTIVITIES -> "go $word"
                word in PLAY_GAMES -> "play $word"
                word in DO_ACTIVITIES -> "do $word"
                // A child does not own a person. "a doctor", not "my doctor".
                item.sector == Sector.JOBS -> "a $word"
                else -> "my $word"
            }
        }

        /** L1 is the word alone; every other level shows a picture too. */
        fun showsPictureFor(level: Int): Boolean = level >= 2

        /** L5 alone asks the child to discriminate among candidates; every earlier level has a single target. */
        fun optionCountFor(level: Int): Int = if (level >= 5) 3 else 1

        /**
         * Deterministic sector for a placeholder image id -- there is no
         * real artwork yet (see `:core:vocab`'s PlaceholderAssets), so a
         * stable, evenly-spread mapping to one of the twelve [Sector]s is
         * what lets two different images be guaranteed to render as two
         * visibly different glyphs. Never random: the same image id always
         * maps to the same sector.
         */
        fun sectorFor(image: Int): Sector {
            val n = Sector.entries.size
            return Sector.entries[((image % n) + n) % n]
        }

        /**
         * L1-4 have a single target (the word, or -- at L4 -- the sentence's
         * own picture, folded in by [sentenceImage] rather than here). L5
         * builds a target plus two distractors, all keyed off [Sentence]s
         * (never the independent word pool), each guaranteed a DIFFERENT
         * [sectorFor] than the target so the child always has three visibly
         * distinct pictures to choose from. Deterministic and unrelated to
         * time or difficulty tolerance -- a fixed day+level always yields
         * the same three images in the same rotated order.
         */
        private fun buildPictureOptions(level: Int, dayOfYear: Int): List<Int> {
            val target = DailyPicker.sentenceOfDay(dayOfYear).image
            if (level < 5) return listOf(target)

            val targetSector = sectorFor(target)
            val chosenImages = mutableListOf(target)
            val chosenSectors = mutableListOf(targetSector)

            var step = 1
            while (chosenImages.size < 3 && step <= 4000) {
                val candidate = DailyPicker.sentenceOfDay(dayOfYear + step * 137).image
                val candidateSector = sectorFor(candidate)
                if (candidate !in chosenImages && candidateSector !in chosenSectors) {
                    chosenImages.add(candidate)
                    chosenSectors.add(candidateSector)
                }
                step++
            }

            // Defensive-only fallback: with twelve sectors spread across a
            // sentence pool of well over a thousand entries this never
            // triggers, but it keeps buildPictureOptions total rather than
            // ever returning fewer than three options.
            var extra = 1
            while (chosenImages.size < 3) {
                val candidate = DailyPicker.sentenceOfDay(dayOfYear + extra).image
                if (candidate !in chosenImages) chosenImages.add(candidate)
                extra++
            }

            val rotation = dayOfYear % 3
            return chosenImages.drop(rotation) + chosenImages.take(rotation)
        }
    }
}
