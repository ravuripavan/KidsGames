package com.kidsgames.memorypairs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPairsStateTest {

    @Test
    fun `level one starts with three pairs, six cards`() {
        assertEquals(6, MemoryPairsState(level = 1).cards.size)
    }

    @Test
    fun `level two starts with four pairs, eight cards`() {
        assertEquals(8, MemoryPairsState(level = 2).cards.size)
    }

    @Test
    fun `level three starts with six pairs, twelve cards`() {
        assertEquals(12, MemoryPairsState(level = 3).cards.size)
    }

    @Test
    fun `level four starts with eight pairs, sixteen cards`() {
        assertEquals(16, MemoryPairsState(level = 4).cards.size)
    }

    @Test
    fun `level five starts with ten pairs, twenty cards`() {
        assertEquals(20, MemoryPairsState(level = 5).cards.size)
    }

    @Test
    fun `no card starts face up or matched`() {
        val s = MemoryPairsState(level = 3)
        assertTrue(s.cards.none { it.faceUp || it.matched })
    }

    @Test
    fun `flipping a card turns it face up`() {
        val s = MemoryPairsState(level = 1)
        val id = s.cards.first().id
        val next = s.flip(id)
        assertTrue(next.cards.first { it.id == id }.faceUp)
    }

    @Test
    fun `flip does not mutate the state it was called on`() {
        val s = MemoryPairsState(level = 1)
        val id = s.cards.first().id
        s.flip(id)
        assertFalse(s.cards.first { it.id == id }.faceUp)
    }

    @Test
    fun `flipping two cards makes the state pending resolution`() {
        var s = MemoryPairsState(level = 1)
        val (first, second) = s.cards.take(2)
        s = s.flip(first.id).flip(second.id)
        assertTrue(s.isPendingResolution)
    }

    @Test
    fun `a third flip is ignored while pending resolution`() {
        var s = MemoryPairsState(level = 1)
        val (first, second, third) = s.cards.take(3)
        s = s.flip(first.id).flip(second.id)
        val afterThird = s.flip(third.id)
        assertFalse(afterThird.cards.first { it.id == third.id }.faceUp)
        assertEquals(s, afterThird)
    }

    @Test
    fun `resolving a matching pair keeps both face up and marks them matched`() {
        var s = MemoryPairsState(level = 1)
        val a = s.cards.first()
        val b = s.cards.first { it.id != a.id && it.symbolIndex == a.symbolIndex }
        s = s.flip(a.id).flip(b.id).resolve()
        assertTrue(s.cards.first { it.id == a.id }.matched)
        assertTrue(s.cards.first { it.id == b.id }.matched)
        assertTrue(s.cards.first { it.id == a.id }.faceUp)
        assertTrue(s.cards.first { it.id == b.id }.faceUp)
        assertFalse(s.isPendingResolution)
    }

    @Test
    fun `resolving a non-matching pair flips both back down`() {
        var s = MemoryPairsState(level = 1)
        val a = s.cards.first()
        val b = s.cards.first { it.symbolIndex != a.symbolIndex }
        s = s.flip(a.id).flip(b.id).resolve()
        assertFalse(s.cards.first { it.id == a.id }.faceUp)
        assertFalse(s.cards.first { it.id == b.id }.faceUp)
        assertFalse(s.cards.first { it.id == a.id }.matched)
        assertFalse(s.cards.first { it.id == b.id }.matched)
        assertFalse(s.isPendingResolution)
    }

    @Test
    fun `a wrong flip costs nothing - state is otherwise identical except face-down cards`() {
        // No score, no counter, no attempts field exists on this class at all --
        // this test pins that a mismatch, once resolved, leaves the state
        // indistinguishable from the state before the pair was flipped, other
        // than card identity/equality of the (still unmatched, still face-down) cards.
        var s = MemoryPairsState(level = 1)
        val before = s
        val a = s.cards.first()
        val b = s.cards.first { it.symbolIndex != a.symbolIndex }
        s = s.flip(a.id).flip(b.id).resolve()
        assertEquals(before.cards.map { it.matched }, s.cards.map { it.matched })
        assertEquals(before.cards.map { it.faceUp }, s.cards.map { it.faceUp })
        assertFalse(s.isComplete)
    }

    @Test
    fun `matched cards cannot be flipped again and stay matched forever`() {
        var s = MemoryPairsState(level = 1)
        val a = s.cards.first()
        val b = s.cards.first { it.id != a.id && it.symbolIndex == a.symbolIndex }
        s = s.flip(a.id).flip(b.id).resolve()
        val reflipped = s.flip(a.id)
        assertEquals(s, reflipped)
        assertTrue(reflipped.cards.first { it.id == a.id }.matched)
    }

    @Test
    fun `matching every pair completes the level`() {
        var s = MemoryPairsState(level = 1)
        while (!s.isComplete) {
            val a = s.cards.first { !it.matched && !it.faceUp }
            val b = s.cards.first { it.id != a.id && it.symbolIndex == a.symbolIndex && !it.matched }
            s = s.flip(a.id).flip(b.id).resolve()
        }
        assertTrue(s.isComplete)
        assertTrue(s.cards.all { it.matched && it.faceUp })
    }

    @Test
    fun `flip never removes a card or reorders the list`() {
        var s = MemoryPairsState(level = 2)
        val originalIds = s.cards.map { it.id }
        val originalSize = s.cards.size
        // Deliberately flip a NON-matching partner first, so the mismatch path
        // is exercised too, then flip the true partner. Picking the partner by
        // id alone would never match anything and this loop would never end.
        while (!s.isComplete) {
            val a = s.cards.first { !it.matched && !it.faceUp }
            val mismatch = s.cards.firstOrNull {
                it.id != a.id && !it.matched && it.symbolIndex != a.symbolIndex
            }
            if (mismatch != null) {
                s = s.flip(a.id).flip(mismatch.id)
                if (s.isPendingResolution) s = s.resolve()
                assertEquals(originalSize, s.cards.size)
                assertEquals(originalIds, s.cards.map { it.id })
            }

            val partner = s.cards.first {
                it.id != a.id && it.symbolIndex == a.symbolIndex && !it.matched
            }
            s = s.flip(a.id).flip(partner.id)
            if (s.isPendingResolution) s = s.resolve()
            assertEquals(originalSize, s.cards.size)
            assertEquals(originalIds, s.cards.map { it.id })
        }
    }

    @Test
    fun `required pairs are non-decreasing across levels one through five`() {
        val requiredByLevel = (1..5).map { MemoryPairsState.pairCount(it) }
        for (i in 1 until requiredByLevel.size) {
            assertTrue(
                "required pairs dropped from level $i (${requiredByLevel[i - 1]}) " +
                    "to level ${i + 1} (${requiredByLevel[i]})",
                requiredByLevel[i] >= requiredByLevel[i - 1],
            )
        }
    }

    @Test
    fun `two different shuffle seeds produce different card orderings`() {
        val a = MemoryPairsState(level = 4, shuffleSeed = 1)
        val b = MemoryPairsState(level = 4, shuffleSeed = 2)
        assertTrue(
            "two different seeds produced the exact same symbol ordering",
            a.cards.map { it.symbolIndex } != b.cards.map { it.symbolIndex },
        )
    }

    @Test
    fun `pairing integrity holds under a non-default shuffle seed`() {
        for (seed in listOf(1, 42, -7, 999)) {
            for (level in 1..5) {
                val s = MemoryPairsState(level = level, shuffleSeed = seed)
                val pairs = MemoryPairsState.pairCount(level)
                assertEquals(pairs * 2, s.cards.size)
                assertEquals(pairs, s.cards.map { it.symbolIndex }.distinct().size)
                s.cards.groupBy { it.symbolIndex }.values.forEach { group ->
                    assertEquals(2, group.size)
                }
            }
        }
    }

    @Test
    fun `each level has enough distinct symbols to give every pair a unique picture`() {
        for (level in 1..5) {
            val s = MemoryPairsState(level = level)
            val pairs = MemoryPairsState.pairCount(level)
            assertEquals(pairs, s.cards.map { it.symbolIndex }.distinct().size)
            // every symbol appears exactly twice -- true pairs, no triples/singletons
            s.cards.groupBy { it.symbolIndex }.values.forEach { group ->
                assertEquals(2, group.size)
            }
        }
    }
}
