package com.kidsgames.whatisit

import com.kidsgames.vocab.VocabCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemVariantTest {

    @Test
    fun `forItem is deterministic for the same id`() {
        val item = VocabCatalogue.items.first()
        val a = ItemVariant.forItem(item)
        val b = ItemVariant.forItem(item)
        assertEquals(a, b)
    }

    @Test
    fun `visualDescriptor is deterministic for the same item`() {
        val item = VocabCatalogue.items.first()
        assertEquals(visualDescriptor(item), visualDescriptor(item))
    }

    @Test
    fun `no two items within the same sector share a visual descriptor`() {
        // This is the direct regression test for B1: within any one
        // sector, every catalogue item must render as a different picture.
        val bySector = VocabCatalogue.items.groupBy { it.sector }
        for ((sector, items) in bySector) {
            val byDescriptor = items.groupBy { visualDescriptor(it) }
            for ((descriptor, group) in byDescriptor) {
                val distinctIds = group.map { it.id }.distinct()
                assertTrue(
                    "sector $sector: items $distinctIds share visual descriptor $descriptor",
                    distinctIds.size <= 1,
                )
            }
        }
    }
}
