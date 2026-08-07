package com.kidsgames.memorypairs

import kotlin.random.Random

/**
 * One card on the board. Immutable -- flipping or matching a card produces a
 * NEW [MemoryCard] (via [MemoryPairsState.copy]/`map`), never mutates this one
 * in place. [symbolIndex] identifies which of the level's pictures this card
 * shows face-up; two cards with the same [symbolIndex] are a pair.
 */
data class MemoryCard(
    val id: Int,
    val symbolIndex: Int,
    val faceUp: Boolean = false,
    val matched: Boolean = false,
)

/**
 * The memory-match state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. `MemoryPairsGame` is the only thing that
 * touches this class from Compose.
 *
 * This state is fully immutable: [flip] and [resolve] never mutate `this`,
 * they return a NEW [MemoryPairsState]. Callers hold it in `mutableStateOf`
 * and write the result back (`state = state.flip(id)`), so Compose
 * recomposition is driven entirely by real state identity -- there is no
 * separate "version" or "trigger" counter anywhere in this class, and there
 * must never be one.
 *
 * There is deliberately NO attempt counter, move counter, score, or timer
 * anywhere on this class. A wrong pair costs literally nothing: [resolve]
 * simply flips both cards back face-down and play continues exactly as
 * before. The child may flip as many times as they like, forever.
 *
 * [flippedIds] holds the 0, 1, or 2 cards currently face-up and NOT YET
 * matched -- i.e. the pair the child is actively resolving. While it holds
 * two ids ([isPendingResolution]), further taps are ignored: the caller
 * (`MemoryPairsGame`) is expected to hold the board in that state for a
 * generous pause (long enough for a four year old to actually look at both
 * cards) before calling [resolve], which is the only thing that clears it.
 *
 * - L1: 3 pairs (6 cards)
 * - L2: 4 pairs (8 cards)
 * - L3: 6 pairs (12 cards)
 * - L4: 8 pairs (16 cards)
 * - L5: 10 pairs (20 cards)
 *
 * Required work -- the number of pairs that must be matched to finish -- is
 * exactly [pairCount] and is non-decreasing across levels 1-5, since
 * difficulty here grows only by adding more pairs, never by time pressure,
 * a shorter look-time, or a penalty for a wrong flip.
 */
data class MemoryPairsState(
    val level: Int,
    val cards: List<MemoryCard> = buildCards(level),
    val flippedIds: List<Int> = emptyList(),
) {

    val isComplete: Boolean
        get() = cards.all { it.matched }

    /** True while exactly two unmatched cards are face-up, awaiting [resolve]. */
    val isPendingResolution: Boolean
        get() = flippedIds.size == 2

    /**
     * Returns a NEW state with card [id] flipped face-up, or `this` unchanged
     * if the tap doesn't count: the board is mid-resolution, the card is
     * already matched, or the card is already face-up.
     */
    fun flip(id: Int): MemoryPairsState {
        if (isPendingResolution) return this
        val card = cards.find { it.id == id } ?: return this
        if (card.matched || card.faceUp) return this

        val flipped = cards.map { if (it.id == id) it.copy(faceUp = true) else it }
        return copy(cards = flipped, flippedIds = flippedIds + id)
    }

    /**
     * Resolves the two pending face-up cards: if they share a [MemoryCard.symbolIndex]
     * they stay face-up and become [MemoryCard.matched]; otherwise both flip back
     * face-down. Either way [flippedIds] clears, unblocking further taps. A no-op
     * (returns `this`) unless [isPendingResolution] is true.
     *
     * Callers should invoke this only after a generous pause -- long enough for
     * a four year old to look at both cards -- never immediately on the second
     * tap, since the pause is the whole point of a memory game.
     */
    fun resolve(): MemoryPairsState {
        if (!isPendingResolution) return this
        val (firstId, secondId) = flippedIds
        val first = cards.first { it.id == firstId }
        val second = cards.first { it.id == secondId }
        val matched = first.symbolIndex == second.symbolIndex

        val resolved = cards.map {
            when (it.id) {
                firstId, secondId -> it.copy(faceUp = matched, matched = matched)
                else -> it
            }
        }
        return copy(cards = resolved, flippedIds = emptyList())
    }

    companion object {
        /** Number of pairs (and cards = pairs * 2) for each of the five levels. */
        fun pairCount(level: Int): Int = when (level) {
            1 -> 3
            2 -> 4
            3 -> 6
            4 -> 8
            5 -> 10
            else -> 3
        }

        private fun buildCards(level: Int): List<MemoryCard> {
            val pairs = pairCount(level)
            val symbolIndices = (0 until pairs).flatMap { listOf(it, it) }
                .shuffled(Random(level * 7919 + 13))
            return symbolIndices.mapIndexed { index, symbolIndex ->
                MemoryCard(id = index, symbolIndex = symbolIndex)
            }
        }
    }
}
