package com.kidsgames.talktime

import com.kidsgames.vocab.DailyPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `daily word and sentence differ across a full day cycle`() {
        val words = (1..365).map { DailyPicker.wordOfDay(it).id }.distinct()
        val sentences = (1..365).map { DailyPicker.sentenceOfDay(it).text }.distinct()
        // Every day inside a year must be distinguishable from at least
        // some other day -- the pool is not a single repeated value.
        assertTrue(words.size > 1)
        assertTrue(sentences.size > 1)
    }

    @Test
    fun `word of day matches state's own word for the same day`() {
        val state = TalkTimeState(level = 1, dayOfYear = 100)
        assertEquals(DailyPicker.wordOfDay(100).id, state.word.id)
    }

    @Test
    fun `phrase text is a two-word phrase built from the day's word`() {
        val state = TalkTimeState(level = 3, dayOfYear = 10)
        val words = state.phraseText.trim().split(" ")
        assertEquals(2, words.size)
        assertTrue(state.phraseText.endsWith(state.word.word))
    }

    @Test
    fun `level five offers three distinct options including the target`() {
        val state = TalkTimeState(level = 5, dayOfYear = 30)
        assertEquals(3, state.options.size)
        assertEquals(3, state.options.map { it.id }.distinct().size)
        assertTrue(state.options.any { it.id == state.word.id })
    }

    @Test
    fun `levels one through four offer a single option -- the target itself`() {
        for (level in 1..4) {
            val state = TalkTimeState(level = level, dayOfYear = 30)
            assertEquals(1, state.options.size)
            assertEquals(state.word.id, state.options.single().id)
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
        val next = state.tapPicture(state.word.id)
        assertTrue(next.isComplete)
    }

    @Test
    fun `tapping a wrong picture never completes and never mutates the original state`() {
        val state = TalkTimeState(level = 5, dayOfYear = 20)
        val wrongId = state.options.first { it.id != state.word.id }.id
        val next = state.tapPicture(wrongId)
        assertFalse(next.isComplete)
        assertEquals(state, next)
    }

    @Test
    fun `wrong picture taps never run out -- level five can always still be won afterward`() {
        val state = TalkTimeState(level = 5, dayOfYear = 20)
        val wrongId = state.options.first { it.id != state.word.id }.id
        var current = state
        repeat(15) { current = current.tapPicture(wrongId) }
        assertFalse(current.isComplete)
        val won = current.tapPicture(state.word.id)
        assertTrue(won.isComplete)
    }

    @Test
    fun `tapPicture is a no-op before level five`() {
        for (level in 1..4) {
            val state = TalkTimeState(level = level, dayOfYear = 5)
            val next = state.tapPicture(state.word.id)
            assertEquals(state, next)
        }
    }

    @Test
    fun `every level can be completed by every child regardless of how many attempts it takes`() {
        for (level in 1..5) {
            var state = TalkTimeState(level = level, dayOfYear = 200)
            // Simulate an arbitrary number of wrong attempts first, where relevant.
            if (level == 5) {
                val wrongId = state.options.first { it.id != state.word.id }.id
                repeat(7) { state = state.tapPicture(wrongId) }
                state = state.tapPicture(state.word.id)
            } else {
                state = state.acknowledge()
            }
            assertTrue(state.isComplete)
        }
    }

    @Test
    fun `nothing about the state ever records what the child said or how they answered`() {
        // The state class carries only content (word, sentence, phrase,
        // options) and a single completion flag -- no score, no attempt
        // counter, no captured/recorded child input of any kind.
        val fields = TalkTimeState::class.java.declaredFields.map { it.name }
        val forbidden = listOf("score", "attempt", "recording", "recorded", "answer", "correctCount", "wrongCount")
        for (name in fields) {
            for (bad in forbidden) {
                assertFalse("field '$name' suggests recording/scoring child input", name.lowercase().contains(bad))
            }
        }
    }
}
