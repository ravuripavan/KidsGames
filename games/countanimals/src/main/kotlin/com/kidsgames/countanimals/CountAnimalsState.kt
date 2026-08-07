package com.kidsgames.countanimals

/**
 * One animal on screen. Immutable -- tapping an animal produces a new
 * [Animal] (via [copy]), never mutates this one in place.
 *
 * [groupIndex] is 0 for every level except L4/L5, where two groups are
 * counted (and, at L5, added together).
 */
data class Animal(
    val id: Int,
    val groupIndex: Int,
    val tapped: Boolean = false,
)

/**
 * The count-animals state machine. Plain Kotlin, no Android imports, so it
 * is unit-tested without an emulator. `CountAnimalsGame` is the only thing
 * that touches this class from Compose.
 *
 * This state is fully immutable: [tap] and [pickNumeral] never mutate
 * `this`, they return a NEW [CountAnimalsState]. Callers hold it in
 * `mutableStateOf` and write the result back (`state = state.tap(id)`), so
 * Compose recomposition is driven entirely by real state identity -- there
 * is no separate "version" counter to remember to bump.
 *
 * Counting is spoken by the caller (via `SoundBank`), but audio assets do
 * not exist yet and the game must work at zero volume regardless. This
 * state therefore always exposes [countSoFar] and [total] so the running
 * count can be rendered as drawn numerals or filled pips -- the single
 * worst defect found in the reference game (`popballoons`) was information
 * that existed only as an audio cue.
 *
 * - L1: 5 animals, one group. Tap every animal to finish.
 * - L2: 8 animals, one group. Tap every animal to finish.
 * - L3: 9 animals, one group. After every animal is tapped, pick the
 *   numeral matching the count from [numeralOptions]. A wrong pick is a
 *   no-op on state -- the count is never reduced or reset.
 * - L4: two groups (6 + 4 = 10 animals). Tap every animal across both
 *   groups; no numeral pick. More total animals and a second group to
 *   discriminate makes this at least as much work as L3.
 * - L5: two groups (5 + 5 = 10 animals). Tap every animal across both
 *   groups, then pick the numeral equal to their SUM from
 *   [numeralOptions] -- simple addition to 10.
 *
 * There is no score, no timer, and no life count. [penalties] exists purely
 * to document that fact for tests and reviewers: it is always zero, and
 * nothing in this class ever changes it.
 */
data class CountAnimalsState(
    val level: Int,
    val animals: List<Animal> = buildAnimals(level),
    val selectedNumeral: Int? = null,
    /**
     * Identifies "which question this is" at this level. `total` (and
     * therefore the distractor set) is fixed per level, so without this the
     * shuffle seed used by [numeralOptions] would be `level*1000+total` --
     * a compile-time constant -- and the correct answer would sit at the
     * SAME index every single time a child reaches this level (first the
     * rightmost slot, then, after a naive re-seed, the centre slot: same
     * defect, different fixed position). Folding [attempt] into the seed
     * makes the position genuinely vary between attempts, while a single
     * [CountAnimalsState] instance still reports the SAME order on every
     * recomposition (the seed is a pure function of immutable fields, so
     * nothing reshuffles the buttons under the child's finger mid-question).
     * Callers should pick a fresh, unpredictable [attempt] each time a new
     * question begins at a numeral-pick level (see `CountAnimalsGame`).
     */
    val attempt: Int = 0,
) {

    /** Always zero. There is no such thing as a penalty in this game. */
    val penalties: Int = 0

    /** Total animals across all groups. Also the correct numeral for L3/L5. */
    val total: Int = animals.size

    /** How many animals have been tapped so far -- the number to display. */
    val countSoFar: Int
        get() = animals.count { it.tapped }

    /** True once every animal has been tapped, regardless of level. */
    val allTapped: Boolean
        get() = countSoFar == total

    /** L3 and L5 require picking a numeral after counting; L1/L2/L4 do not. */
    val needsNumeralPick: Boolean
        get() = level == 3 || level == 5

    /** Numeral-pick UI should only appear once counting is finished. */
    val awaitingNumeralPick: Boolean
        get() = needsNumeralPick && allTapped && selectedNumeral == null

    val isComplete: Boolean
        get() = if (needsNumeralPick) selectedNumeral == total else allTapped

    /**
     * Plausible numeral choices for the L3/L5 picker: the correct total plus
     * two distractors drawn from BOTH sides of the total where possible (one
     * below, one above), so the answer is not systematically the largest
     * value on screen. Order is deterministically shuffled (seeded from
     * [level] and [total], not `kotlin.random.Random()`'s default source) so
     * the correct answer's position varies by level instead of always
     * landing at a fixed index -- and stays STABLE across recompositions,
     * since the seed never changes for a given level/total.
     */
    val numeralOptions: List<Int>
        get() = if (!needsNumeralPick) {
            emptyList()
        } else {
            val below = (total - 1 downTo 1).toList()
            val above = (total + 1..10).toList()

            // Prefer one distractor below and one above so the correct
            // answer isn't always the maximum candidate (which is what
            // made it always sit last in an ascending-order Row).
            val distractors = mutableListOf<Int>()
            below.firstOrNull()?.let { distractors += it }
            above.firstOrNull()?.let { distractors += it }

            // If one side ran out (e.g. total == 10, nothing above), fill
            // the second distractor slot from whichever side has more room.
            if (distractors.size < 2) {
                val leftovers = below.drop(1) + above.drop(1)
                leftovers.firstOrNull()?.let { distractors += it }
            }

            val options = (distractors + total).distinct()
            // level/total alone would make this a compile-time constant
            // (total is fixed per level) -- attempt is folded in with a
            // large odd multiplier so consecutive attempt values don't
            // produce visibly-correlated seeds.
            val seed = (level.toLong() * 1_000L + total) * 1_000_003L + attempt
            options.shuffled(kotlin.random.Random(seed))
        }

    /**
     * Returns a NEW state with animal [id] tapped, or `this` unchanged if
     * the tap doesn't count (already tapped, or counting is already done
     * and a numeral pick is pending/complete).
     */
    fun tap(id: Int): CountAnimalsState {
        if (allTapped) return this
        val animal = animals.find { it.id == id } ?: return this
        if (animal.tapped) return this

        val next = animals.map { if (it.id == id) it.copy(tapped = true) else it }
        return copy(animals = next)
    }

    /**
     * Returns a NEW state with [numeral] recorded as the pick, or `this`
     * unchanged if the pick doesn't count: counting isn't finished yet, no
     * pick is expected at this level, a pick was already made, or [numeral]
     * is wrong. A wrong pick NEVER reduces or resets [countSoFar] -- the
     * child simply tries again.
     */
    fun pickNumeral(numeral: Int): CountAnimalsState {
        if (!awaitingNumeralPick) return this
        if (numeral != total) return this
        return copy(selectedNumeral = numeral)
    }

    companion object {
        private fun buildAnimals(level: Int): List<Animal> {
            val groupSizes = when (level) {
                1 -> listOf(5)
                2 -> listOf(8)
                3 -> listOf(9)
                4 -> listOf(6, 4)
                5 -> listOf(5, 5)
                else -> listOf(5)
            }

            var nextId = 0
            return groupSizes.flatMapIndexed { groupIndex, size ->
                (0 until size).map {
                    Animal(id = nextId++, groupIndex = groupIndex)
                }
            }
        }
    }
}
