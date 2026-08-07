package com.kidsgames.memorypairs

import org.junit.Assert.assertTrue
import org.junit.Test

class CardSymbolsTest {

    /**
     * Guards the round-3 colour-blind-confusion fix: no two card faces that
     * can appear together at any level may share a [CardShape] while
     * differing only in colour. If they did, hue would be the sole
     * discriminator between them -- exactly the failure mode a colour-blind
     * child hits hardest on a red/green pair. Checking the whole
     * [CardSymbols] list (not just the pairs live at each level) is the
     * strict superset check: if no two entries anywhere in the master list
     * share a shape, no level -- which only ever uses a prefix of that list
     * -- can produce the collision either.
     */
    @Test
    fun `no two card symbols share a shape while differing only in colour`() {
        val shapes = CardSymbols.map { it.first }
        assertTrue(
            "duplicate shape(s) found in CardSymbols: $shapes",
            shapes.size == shapes.distinct().size,
        )
    }

    @Test
    fun `every level's symbol set has one shape per symbol`() {
        for (level in 1..5) {
            val pairs = MemoryPairsState.pairCount(level)
            val shapesInPlay = CardSymbols.take(pairs).map { it.first }
            assertTrue(
                "level $level has a duplicate shape among its symbols in play: $shapesInPlay",
                shapesInPlay.size == shapesInPlay.distinct().size,
            )
        }
    }
}
