package com.kidsgames.vocab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabCatalogueTest {

    @Test
    fun `catalogue holds at least one hundred items`() {
        assertTrue(VocabCatalogue.items.size >= 100)
    }

    @Test
    fun `every sector has at least six items`() {
        Sector.entries.forEach { s ->
            assertTrue("$s has too few", VocabCatalogue.bySector(s).size >= 6)
        }
    }

    @Test
    fun `daily pick is stable within a day and changes across days`() {
        assertEquals(DailyPicker.wordOfDay(42), DailyPicker.wordOfDay(42))
        assertNotEquals(DailyPicker.wordOfDay(42), DailyPicker.wordOfDay(43))
    }

    @Test
    fun `sentence pool covers a full year without repeating`() {
        val year = (1..365).map { DailyPicker.sentenceOfDay(it).text }
        assertEquals(365, year.toSet().size)
    }

    @Test
    fun `level one draws only the most familiar items`() {
        assertTrue(VocabCatalogue.forLevel(1).all { it.tier == 1 })
    }
}
