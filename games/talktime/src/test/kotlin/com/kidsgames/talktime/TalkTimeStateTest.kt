package com.kidsgames.talktime

import com.kidsgames.vocab.DailyPicker
import com.kidsgames.vocab.Sector
import com.kidsgames.vocab.VocabCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkTimeStateTest {

    @Test
    fun `showsPicture is literal and non-decreasing across levels one through five`() {
        // "More elements" axis: literal expected values asserted directly,
        // never recomputed from the implementation under test.
        val expected = mapOf(1 to false, 2 to true, 3 to true, 4 to true, 5 to true)
        for ((level, shows) in expected) {
            assertEquals("level $level showsPicture", shows, TalkTimeState.showsPictureFor(level))
        }
        val asInts = (1..5).map { if (expected.getValue(it)) 1 else 0 }
        for (i in 1 until asInts.size) {
            assertTrue(asInts[i] >= asInts[i - 1])
        }
    }

    @Test
    fun `optionCount is literal and non-decreasing across levels one through five`() {
        // "More discrimination" axis: L5 alone adds distractor pictures.
        val expected = mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 3)
        for ((level, count) in expected) {
            assertEquals("level $level optionCount", count, TalkTimeState.optionCountFor(level))
        }
        val counts = (1..5).map { expected.getValue(it) }
        for (i in 1 until counts.size) {
            assertTrue(counts[i] >= counts[i - 1])
        }
    }

    @Test
    fun `daily word and sentence are stable within a day across all five levels`() {
        for (level in 1..5) {
            val a = TalkTimeState(level = level, dayOfYear = 42)
            val b = TalkTimeState(level = level, dayOfYear = 42)
            assertEquals(a.word.id, b.word.id)
            assertEquals(a.sentence.text, b.sentence.text)
        }
    }

    @Test
    fun `daily word and sentence are pure functions of dayOfYear -- clock-independent by construction`() {
        // TalkTimeState never reads a clock: dayOfYear is the ONLY input
        // that can vary its word/sentence, so two states built from the
        // same day are equal regardless of when/how many times they are
        // constructed -- this is what "stable across restarts within a
        // day" actually requires, not merely data-class equality within one
        // process (which the two-instance test above already covers).
        for (day in listOf(1, 42, 100, 200, 365)) {
            val fromDailyPicker = DailyPicker.wordOfDay(day).id
            repeat(5) {
                assertEquals(fromDailyPicker, TalkTimeState(level = 1, dayOfYear = day).word.id)
            }
        }
    }

    @Test
    fun `consecutive days never share the same word or sentence`() {
        for (day in 1 until 365) {
            val today = TalkTimeState(level = 1, dayOfYear = day)
            val tomorrow = TalkTimeState(level = 1, dayOfYear = day + 1)
            assertNotEquals("day $day vs ${day + 1} word", today.word.id, tomorrow.word.id)
            assertNotEquals("day $day vs ${day + 1} sentence", today.sentence.text, tomorrow.sentence.text)
        }
    }

    @Test
    fun `a full year of sentences has no repeats`() {
        val sentences = (1..365).map { DailyPicker.sentenceOfDay(it).text }
        assertEquals(365, sentences.distinct().size)
    }

    @Test
    fun `word of day matches state's own word for the same day`() {
        val state = TalkTimeState(level = 1, dayOfYear = 100)
        assertEquals(DailyPicker.wordOfDay(100).id, state.word.id)
    }

    @Test
    fun `phrase text is a grammatical two-word phrase built from the day's word, for every day of the year`() {
        // Every day, not a single sample: the four bad days this regressed
        // on (135 "swimming", 137 "running", 140 "skating", 115 "police
        // officer") only show up on their own specific catalogue entries.
        for (day in 1..365) {
            val state = TalkTimeState(level = 3, dayOfYear = day)
            val words = state.phraseText.trim().split(" ")
            assertEquals("day $day phrase '${state.phraseText}' word count", 2, words.size)
            assertTrue("day $day phrase '${state.phraseText}'", state.phraseText.endsWith(state.word.word))
        }
    }

    @Test
    fun `no catalogue entry produces a possessive phrase that is not possessable`() {
        // Enumerate the WHOLE catalogue rather than sampling. The previous
        // version of this test checked a handful of known-bad words, so it
        // passed while ten more of the same class were still broken -- a fix
        // checked against the reported examples inherits the report's blind
        // spot. Every entry is reachable, since wordOfDay indexes
        // (dayOfYear - 1) % items.size.
        for (item in VocabCatalogue.items) {
            val phrase = TalkTimeState.phraseTextFor(item)
            assertEquals("'$phrase' should be two words", 2, phrase.trim().split(" ").size)
            if (phrase.startsWith("my ")) {
                assertTrue(
                    "'$phrase' uses a possessive for a job -- a child does not own a person",
                    item.sector != Sector.JOBS,
                )
                assertFalse(
                    "'$phrase' uses a possessive for an activity, which is not possessable",
                    item.word in setOf(
                        "swimming", "running", "skating",
                        "soccer", "basketball", "tennis", "baseball", "hockey",
                        "gymnastics",
                    ),
                )
            }
        }
    }

    @Test
    fun `phrase text uses the verb English actually takes with each activity`() {
        fun phraseOf(word: String) =
            TalkTimeState.phraseTextFor(VocabCatalogue.items.first { it.word == word })
        assertEquals("go swimming", phraseOf("swimming"))
        assertEquals("go running", phraseOf("running"))
        assertEquals("go skating", phraseOf("skating"))
        assertEquals("play soccer", phraseOf("soccer"))
        assertEquals("play hockey", phraseOf("hockey"))
        assertEquals("play tennis", phraseOf("tennis"))
        assertEquals("do gymnastics", phraseOf("gymnastics"))
        assertEquals("a doctor", phraseOf("doctor"))
        assertEquals("a firefighter", phraseOf("firefighter"))
        assertEquals("police officer", phraseOf("police officer"))
        assertEquals("bike race", phraseOf("bike race"))
        // An ordinary object in the SPORTS sector still takes the possessive.
        assertEquals("my ball", phraseOf("ball"))
        assertEquals("my dog", phraseOf("dog"))
    }

    @Test
    fun `level five target is always the sentence's own image, for every day of the year`() {
        // B1: the L5 correct answer must be derivable from what the child
        // just heard -- the sentence's own [Sentence.image] -- never from
        // the independent word pool, which has no relationship to the
        // sentence on any day.
        for (day in 1..365) {
            val state = TalkTimeState(level = 5, dayOfYear = day)
            val target = state.sentence.image
            assertTrue("day $day target missing from options", state.pictureOptions.contains(target))
            val won = state.tapPicture(target)
            assertTrue("day $day tapping the sentence's own image must complete", won.isComplete)
        }
    }

    @Test
    fun `level five distractors never render identically to the target, for every day of the year`() {
        for (day in 1..365) {
            val state = TalkTimeState(level = 5, dayOfYear = day)
            val target = state.sentence.image
            val targetSector = TalkTimeState.sectorFor(target)
            val distractors = state.pictureOptions.filter { it != target }
            assertEquals(2, distractors.size)
            for (distractor in distractors) {
                assertNotEquals(
                    "day $day distractor $distractor renders identically to target $target",
                    targetSector,
                    TalkTimeState.sectorFor(distractor),
                )
            }
        }
    }

    @Test
    fun `level four picture is the sentence's own image, not the unrelated word's`() {
        for (day in listOf(1, 50, 145, 200, 300, 365)) {
            val state = TalkTimeState(level = 4, dayOfYear = day)
            assertEquals(state.sentence.image, state.sentenceImage)
        }
    }

    @Test
    fun `level five offers three distinct options including the target`() {
        val state = TalkTimeState(level = 5, dayOfYear = 30)
        assertEquals(3, state.pictureOptions.size)
        assertEquals(3, state.pictureOptions.distinct().size)
        assertTrue(state.pictureOptions.contains(state.sentence.image))
    }

    @Test
    fun `levels one through four offer a single picture option -- the sentence's own image`() {
        for (level in 1..4) {
            val state = TalkTimeState(level = level, dayOfYear = 30)
            assertEquals(1, state.pictureOptions.size)
            assertEquals(state.sentence.image, state.pictureOptions.single())
        }
    }

    @Test
    fun `acknowledge completes levels one through four without mutating the original state`() {
        for (level in 1..4) {
            val state = TalkTimeState(level = level, dayOfYear = 5)
            val next = state.acknowledge()
            assertFalse(state.isComplete)
            assertTrue(next.isComplete)
        }
    }

    @Test
    fun `acknowledge is a no-op at level five`() {
        val state = TalkTimeState(level = 5, dayOfYear = 5)
        val next = state.acknowledge()
        assertEquals(state, next)
        assertFalse(next.isComplete)
    }

    @Test
    fun `acknowledge is a no-op once already complete`() {
        val state = TalkTimeState(level = 1, dayOfYear = 5).acknowledge()
        val next = state.acknowledge()
        assertEquals(state, next)
    }

    @Test
    fun `tapping the correct picture completes level five`() {
        val state = TalkTimeState(level = 5, dayOfYear = 20)
        val next = state.tapPicture(state.sentence.image)
        assertTrue(next.isComplete)
    }

    @Test
    fun `tapping a wrong picture never completes and never mutates the original state`() {
        val state = TalkTimeState(level = 5, dayOfYear = 20)
        val wrongImage = state.pictureOptions.first { it != state.sentence.image }
        val next = state.tapPicture(wrongImage)
        assertFalse(next.isComplete)
        assertEquals(state, next)
    }

    @Test
    fun `wrong picture taps never run out -- level five can always still be won afterward`() {
        val state = TalkTimeState(level = 5, dayOfYear = 20)
        val wrongImage = state.pictureOptions.first { it != state.sentence.image }
        var current = state
        repeat(15) { current = current.tapPicture(wrongImage) }
        assertFalse(current.isComplete)
        val won = current.tapPicture(state.sentence.image)
        assertTrue(won.isComplete)
    }

    @Test
    fun `tapPicture is a no-op before level five`() {
        for (level in 1..4) {
            val state = TalkTimeState(level = level, dayOfYear = 5)
            val next = state.tapPicture(state.sentence.image)
            assertEquals(state, next)
        }
    }

    @Test
    fun `every level can be completed by every child regardless of how many attempts it takes`() {
        for (level in 1..5) {
            var state = TalkTimeState(level = level, dayOfYear = 200)
            // Simulate an arbitrary number of wrong attempts first, where relevant.
            if (level == 5) {
                val wrongImage = state.pictureOptions.first { it != state.sentence.image }
                repeat(7) { state = state.tapPicture(wrongImage) }
                state = state.tapPicture(state.sentence.image)
            } else {
                state = state.acknowledge()
            }
            assertTrue(state.isComplete)
        }
    }

    @Test
    fun `lesson text mapping is word, word, phrase, sentence, sentence across levels one through five`() {
        // L2: no dedicated coverage of TalkTimeGame's private LessonText
        // when-branch exists elsewhere, so this pins the exact source each
        // level's text comes from without duplicating the Composable.
        val state1 = TalkTimeState(level = 1, dayOfYear = 77)
        val state2 = TalkTimeState(level = 2, dayOfYear = 77)
        val state3 = TalkTimeState(level = 3, dayOfYear = 77)
        val state4 = TalkTimeState(level = 4, dayOfYear = 77)
        val state5 = TalkTimeState(level = 5, dayOfYear = 77)

        assertEquals(state1.word.word, state2.word.word)
        assertTrue(state3.phraseText.endsWith(state3.word.word))
        assertEquals(state4.sentence.text, DailyPicker.sentenceOfDay(77).text)
        assertEquals(state5.sentence.text, DailyPicker.sentenceOfDay(77).text)
        // L1/L2 show the word alone; L3 the phrase; L4/L5 the full sentence
        // -- distinct strings on an ordinary day, confirming each level maps
        // to a different granularity of text rather than repeating one.
        assertNotEquals(state1.word.word, state3.phraseText)
        assertNotEquals(state3.phraseText, state4.sentence.text)
    }

    @Test
    fun `nothing about the state ever records what the child said or how they answered`() {
        // The state class carries only content (word, sentence, phrase,
        // picture options) and a single completion flag -- no score, no
        // attempt counter, no captured/recorded child input of any kind.
        val fields = TalkTimeState::class.java.declaredFields.map { it.name }
        val forbidden = listOf("score", "attempt", "recording", "recorded", "answer", "correctCount", "wrongCount")
        for (name in fields) {
            for (bad in forbidden) {
                assertFalse("field '$name' suggests recording/scoring child input", name.lowercase().contains(bad))
            }
        }
    }
}
