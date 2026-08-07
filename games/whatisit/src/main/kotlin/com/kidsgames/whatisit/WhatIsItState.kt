package com.kidsgames.whatisit

import com.kidsgames.vocab.Sector
import com.kidsgames.vocab.VocabCatalogue
import com.kidsgames.vocab.VocabItem

/**
 * One turn of the game. [Name] is the productive-recall task (see a picture,
 * name it privately, optionally hear it). [Reverse] is the L5-only receptive
 * task: a name is spoken and the child taps the matching picture among
 * [Reverse.options].
 */
sealed interface Round {
    data class Name(val item: VocabItem) : Round
    data class Reverse(val targetId: String, val options: List<VocabItem>) : Round
}

/**
 * Plain-Kotlin state machine for `:games:whatisit`, no Android imports so it
 * is unit-tested without an emulator. [advance] and [tapReverseOption] each
 * return a NEW [WhatIsItState] -- neither ever mutates `this` -- so callers
 * hold this in a single `mutableStateOf` and write the result back, the same
 * shape every other module in the suite uses.
 *
 * There is no score, no timer, and no attempt limit. The child's answer is
 * NEVER captured or checked here -- naming rounds have no "correct" input at
 * all, [advance] simply moves to the next picture whenever the child is
 * ready. A wrong tap in a reverse round is not wrong: [tapReverseOption]
 * leaves the round in place (unchanged state) until the matching picture is
 * tapped, and the round can be retried without limit.
 *
 * Levels widen the catalogue rather than tightening the task, exactly as the
 * design doc specifies: level 1 draws only tier-1 items from the most
 * familiar sectors, and each level admits one or two more sectors and one
 * more tier on top (see [sectorsFor]). Level 5 additionally appends three
 * reverse-mode rounds after its naming rounds -- receptive recall across all
 * twelve sectors, the hardest form of this task.
 */
data class WhatIsItState(
    val level: Int,
    val rounds: List<Round> = buildRounds(level),
    val index: Int = 0,
) {

    /** True once the child has advanced past the final round. */
    val isComplete: Boolean get() = index >= rounds.size

    /** The round currently on screen, or null once [isComplete]. */
    val currentRound: Round? get() = rounds.getOrNull(index)

    /**
     * Moves to the next round. This is the ONLY way a naming round ends --
     * there is nothing to get right or wrong, so a caller invokes this
     * whenever the child (or, functionally, the "next" control) is ready to
     * move on. A no-op once [isComplete].
     */
    fun advance(): WhatIsItState = if (isComplete) this else copy(index = index + 1)

    /**
     * Tapping a picture during a [Round.Reverse]. Returns a NEW state
     * advanced to the next round if [itemId] matches the round's spoken
     * target; otherwise returns `this` UNCHANGED -- there is no fail state,
     * the tapped picture is simply named aloud by the caller (using the
     * returned-unchanged round's own data) and the same round continues
     * until the child finds the match. A no-op when the current round is a
     * [Round.Name] (nothing to tap there).
     */
    fun tapReverseOption(itemId: String): WhatIsItState {
        val round = currentRound as? Round.Reverse ?: return this
        return if (itemId == round.targetId) advance() else this
    }

    companion object {

        private val FAMILIAR_SECTORS = setOf(
            Sector.ANIMALS, Sector.FRUITS, Sector.VEGETABLES, Sector.BODY, Sector.FOOD,
        )

        /**
         * The sectors admitted at [level], per the design doc's table:
         * L1 familiar items; L2 +household/vehicles; L3 +nature/clothes;
         * L4 +jobs/instruments; L5 all twelve sectors.
         */
        internal fun sectorsFor(level: Int): Set<Sector> = buildSet {
            addAll(FAMILIAR_SECTORS)
            if (level >= 2) addAll(listOf(Sector.HOUSEHOLD, Sector.VEHICLES))
            if (level >= 3) addAll(listOf(Sector.NATURE, Sector.CLOTHES))
            if (level >= 4) addAll(listOf(Sector.JOBS, Sector.INSTRUMENTS))
            if (level >= 5) add(Sector.SPORTS)
        }

        /** Every catalogue item whose sector and tier are admitted at [level]. */
        internal fun poolFor(level: Int): List<VocabItem> {
            val sectors = sectorsFor(level)
            return VocabCatalogue.items.filter { it.sector in sectors && it.tier <= level }
        }

        /**
         * "More elements" axis: the naming-round count literally grows with
         * level, 5/6/7/8/6 -- L5 trades two naming rounds for three reverse
         * rounds (see [reverseCountFor]) so its TOTAL round count (9) still
         * exceeds L4's (8), and the added rounds are the strictly harder
         * receptive task.
         */
        private fun forwardCountFor(level: Int): Int = when (level.coerceIn(1, 5)) {
            1 -> 5
            2 -> 6
            3 -> 7
            4 -> 8
            else -> 6
        }

        /** Reverse-mode rounds only exist at L5, per the design doc. */
        private fun reverseCountFor(level: Int): Int = if (level >= 5) 3 else 0

        /** Deterministic, evenly-spread pick of [count] items from [pool]. */
        private fun pickEvenlySpaced(pool: List<VocabItem>, count: Int): List<VocabItem> {
            if (pool.isEmpty() || count <= 0) return emptyList()
            return (0 until count).map { i -> pool[(i * pool.size) / count % pool.size] }
        }

        /**
         * Deterministic reverse rounds: each picks a target and two other
         * DISTINCT items spread across the pool (never random, so the same
         * level always produces the same rounds), then rotates their display
         * order by round index so the target isn't always shown in the same
         * position.
         */
        private fun buildReverseRounds(pool: List<VocabItem>, count: Int): List<Round.Reverse> {
            if (pool.size < 3 || count <= 0) return emptyList()
            return (0 until count).map { i ->
                val base = (i * 7) % pool.size
                val target = pool[base]
                val offset1 = (pool.size / 3).coerceAtLeast(1)
                val offset2 = (2 * pool.size / 3).coerceAtLeast(2)

                var other1 = pool[(base + offset1) % pool.size]
                if (other1.id == target.id) other1 = pool[(base + 1) % pool.size]

                var other2 = pool[(base + offset2) % pool.size]
                if (other2.id == target.id || other2.id == other1.id) other2 = pool[(base + 2) % pool.size]
                if (other2.id == target.id || other2.id == other1.id) other2 = pool[(base + 3) % pool.size]

                val options = listOf(target, other1, other2)
                val rotation = i % 3
                val rotated = options.drop(rotation) + options.take(rotation)
                Round.Reverse(targetId = target.id, options = rotated)
            }
        }

        internal fun buildRounds(level: Int): List<Round> {
            val pool = poolFor(level)
            val forwardRounds = pickEvenlySpaced(pool, forwardCountFor(level)).map { Round.Name(it) }
            val reverseRounds = buildReverseRounds(pool, reverseCountFor(level))
            return forwardRounds + reverseRounds
        }
    }
}
