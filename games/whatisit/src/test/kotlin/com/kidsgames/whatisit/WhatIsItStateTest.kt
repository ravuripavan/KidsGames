package com.kidsgames.whatisit

import com.kidsgames.vocab.Sector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatIsItStateTest {

    @Test
    fun `round counts are literal and non-decreasing across levels one through five`() {
        // "More elements" axis: literal expected round counts per level,
        // asserted directly rather than derived from buildRounds -- a test
        // that recomputes its own expectation from the implementation
        // proves nothing.
        val expected = mapOf(1 to 5, 2 to 6, 3 to 7, 4 to 8, 5 to 9)
        for ((level, count) in expected) {
            assertEquals("level $level round count", count, WhatIsItState(level).rounds.size)
        }
        val counts = (1..5).map { expected.getValue(it) }
        for (i in 1 until counts.size) {
            assertTrue(counts[i] >= counts[i - 1])
        }
    }

    @Test
    fun `level one draws only tier one items from the familiar sectors`() {
        val state = WhatIsItState(1)
        val familiar = setOf(Sector.ANIMALS, Sector.FRUITS, Sector.VEGETABLES, Sector.BODY, Sector.FOOD)
        for (round in state.rounds) {
            val item = (round as Round.Name).item
            assertTrue("item ${item.id} should be tier 1", item.tier <= 1)
            assertTrue("item ${item.id} sector ${item.sector} should be familiar", item.sector in familiar)
        }
    }

    @Test
    fun `level two adds household and vehicles`() {
        val sectors = WhatIsItState.sectorsFor(2)
        assertTrue(sectors.contains(Sector.HOUSEHOLD))
        assertTrue(sectors.contains(Sector.VEHICLES))
        assertFalse(sectors.contains(Sector.NATURE))
        assertFalse(sectors.contains(Sector.JOBS))
    }

    @Test
    fun `level three adds nature and clothes`() {
        val sectors = WhatIsItState.sectorsFor(3)
        assertTrue(sectors.contains(Sector.NATURE))
        assertTrue(sectors.contains(Sector.CLOTHES))
        assertFalse(sectors.contains(Sector.JOBS))
        assertFalse(sectors.contains(Sector.SPORTS))
    }

    @Test
    fun `level four adds jobs and instruments`() {
        val sectors = WhatIsItState.sectorsFor(4)
        assertTrue(sectors.contains(Sector.JOBS))
        assertTrue(sectors.contains(Sector.INSTRUMENTS))
        assertFalse(sectors.contains(Sector.SPORTS))
    }

    @Test
    fun `level five includes all twelve sectors`() {
        val sectors = WhatIsItState.sectorsFor(5)
        assertEquals(Sector.entries.toSet(), sectors)
    }

    @Test
    fun `level five ends with three reverse rounds after six naming rounds`() {
        val state = WhatIsItState(5)
        val kinds = state.rounds.map { it::class }
        assertEquals(9, kinds.size)
        assertTrue(state.rounds.take(6).all { it is Round.Name })
        assertTrue(state.rounds.drop(6).all { it is Round.Reverse })
    }

    @Test
    fun `levels one through four have no reverse rounds`() {
        for (level in 1..4) {
            assertTrue(WhatIsItState(level).rounds.none { it is Round.Reverse })
        }
    }

    @Test
    fun `every reverse round offers three distinct options including the target`() {
        val state = WhatIsItState(5)
        for (round in state.rounds.filterIsInstance<Round.Reverse>()) {
            assertEquals(3, round.options.size)
            assertEquals(3, round.options.map { it.id }.distinct().size)
            assertTrue(round.options.any { it.id == round.targetId })
        }
    }

    @Test
    fun `advance moves to the next round without mutating the original state`() {
        val state = WhatIsItState(1)
        val next = state.advance()
        assertEquals(0, state.index)
        assertEquals(1, next.index)
    }

    @Test
    fun `advancing past the final round marks the state complete`() {
        var state = WhatIsItState(1)
        assertFalse(state.isComplete)
        repeat(state.rounds.size) { state = state.advance() }
        assertTrue(state.isComplete)
    }

    @Test
    fun `advance beyond completion is a no-op`() {
        var state = WhatIsItState(1)
        repeat(state.rounds.size + 5) { state = state.advance() }
        assertTrue(state.isComplete)
        assertEquals(state.rounds.size, state.index)
    }

    @Test
    fun `tapping the correct reverse option advances the round`() {
        val state = WhatIsItState(5)
        val reverseIndex = state.rounds.indexOfFirst { it is Round.Reverse }
        var current = state
        repeat(reverseIndex) { current = current.advance() }
        val round = current.currentRound as Round.Reverse
        val next = current.tapReverseOption(round.targetId)
        assertEquals(reverseIndex + 1, next.index)
    }

    @Test
    fun `tapping a wrong reverse option never advances and never mutates the original state`() {
        val state = WhatIsItState(5)
        val reverseIndex = state.rounds.indexOfFirst { it is Round.Reverse }
        var current = state
        repeat(reverseIndex) { current = current.advance() }
        val round = current.currentRound as Round.Reverse
        val wrongId = round.options.first { it.id != round.targetId }.id
        val next = current.tapReverseOption(wrongId)
        assertEquals(current.index, next.index)
        assertEquals(current, next)
    }

    @Test
    fun `a wrong reverse tap never runs out -- the round can always still be won afterward`() {
        val state = WhatIsItState(5)
        val reverseIndex = state.rounds.indexOfFirst { it is Round.Reverse }
        var current = state
        repeat(reverseIndex) { current = current.advance() }
        val round = current.currentRound as Round.Reverse
        val wrongId = round.options.first { it.id != round.targetId }.id
        // Many wrong taps in a row -- no attempt limit, no fail state.
        repeat(10) { current = current.tapReverseOption(wrongId) }
        assertEquals(reverseIndex, current.index)
        val advanced = current.tapReverseOption(round.targetId)
        assertEquals(reverseIndex + 1, advanced.index)
    }

    @Test
    fun `tapReverseOption on a naming round is a no-op`() {
        val state = WhatIsItState(1)
        val next = state.tapReverseOption("anything")
        assertEquals(state, next)
    }

    @Test
    fun `completing every round of every level reaches isComplete`() {
        for (level in 1..5) {
            var state = WhatIsItState(level)
            while (!state.isComplete) {
                val round = state.currentRound
                state = when (round) {
                    is Round.Name -> state.advance()
                    is Round.Reverse -> state.tapReverseOption(round.targetId)
                    null -> state
                }
            }
            assertTrue(state.isComplete)
        }
    }
}
