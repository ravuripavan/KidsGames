package com.kidsgames.patterns

/**
 * The patterns state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. `PatternsGame` is the only thing that
 * touches this class from Compose.
 *
 * This state is fully immutable: [choose] never mutates `this`, it returns a
 * NEW [PatternsState]. Callers hold it in `mutableStateOf` and write the
 * result back (`state = state.choose(category)`), so Compose recomposition
 * is driven entirely by real state identity -- there is no separate
 * "version" or "trigger" counter anywhere in this module, and there must
 * never be one.
 *
 * A "category" is an abstract index (0, 1, 2, ...) into the repeating unit.
 * This class deliberately has no notion of colour or shape -- it is pure
 * sequence logic. `PatternsGame` maps each category to a fixed (shape,
 * colour) pair so the pattern is visually inferable on two independent
 * channels; colour is never the sole carrier of meaning here, per the design
 * spec's colour-vision-deficiency guidance.
 *
 * The sequence itself is generated on demand from a small repeating [unit]
 * rather than materialised as a list, since it is conceptually infinite --
 * `categoryAt(index)` just wraps `index` into the unit via modulo.
 *
 * - L1: unit AB (2 categories), one demo repeat shown before the first
 *   blank, 3 required fills.
 * - L2: unit AB (2 categories), TWO demo repeats shown (literally "ABAB")
 *   before the first blank, 4 required fills -- same rule as L1, longer
 *   demonstrated cycle and more required work.
 * - L3: unit AABB (2 categories, period 4), 6 required fills.
 * - L4: unit ABC (3 categories), 6 required fills.
 * - L5: unit ABC (3 categories), 8 required fills. `PatternsGame` renders
 *   each of the three categories with both a distinct colour AND a distinct
 *   shape here, same as every other level -- L5's extra difficulty is pure
 *   sequence length/discrimination, never a hidden rule change.
 *
 * There is no score, no timer, and no life count. A wrong [choose] is a
 * total no-op on state: [filledCount] never decreases and the sequence is
 * never reset, so a wrong tap can never cost the child anything they've
 * already earned.
 */
data class PatternsState(
    val level: Int,
    val filledCount: Int = 0,
) {

    private val unit: List<Int> = unitFor(level)
    private val prefixLength: Int = unit.size * prefixRepsFor(level)

    /** Distinct categories used by this level: 2 for AB/AABB, 3 for ABC. */
    val categories: Int = unit.distinct().size

    /** How many blanks the child must fill correctly to finish this level. */
    val requiredFills: Int = requiredFillsFor(level)

    val isComplete: Boolean
        get() = filledCount >= requiredFills

    /**
     * How many sequence slots should currently be rendered: every resolved
     * slot, plus exactly one open blank slot while incomplete. Monotonic --
     * it only ever grows, it never shrinks, so a slot the child has already
     * seen never disappears or moves.
     */
    val visibleLength: Int
        get() = prefixLength + filledCount + if (isComplete) 0 else 1

    /** The index of the single open blank slot, or null once complete. */
    val blankIndex: Int?
        get() = if (isComplete) null else prefixLength + filledCount

    /** The category the child must tap next to advance. Only meaningful while incomplete. */
    val nextCategory: Int
        get() = categoryAt(prefixLength + filledCount)

    /** The category shown at a given sequence position; the pattern repeats forever via modulo. */
    fun categoryAt(index: Int): Int = unit[index % unit.size]

    /**
     * Returns a NEW state with one more blank filled if [category] matches
     * [nextCategory], or `this` unchanged otherwise (wrong tap, or already
     * complete). Never regresses [filledCount] and never resets the
     * sequence -- there is no fail state here, only "not yet".
     */
    fun choose(category: Int): PatternsState {
        if (isComplete) return this
        return if (category == nextCategory) copy(filledCount = filledCount + 1) else this
    }

    companion object {
        private fun unitFor(level: Int): List<Int> = when (level) {
            1, 2 -> listOf(0, 1)
            3 -> listOf(0, 0, 1, 1)
            4, 5 -> listOf(0, 1, 2)
            else -> listOf(0, 1)
        }

        private fun prefixRepsFor(level: Int): Int = when (level) {
            1 -> 1
            else -> 2
        }

        private fun requiredFillsFor(level: Int): Int = when (level) {
            1 -> 3
            2 -> 4
            3 -> 6
            4 -> 6
            5 -> 8
            else -> 3
        }
    }
}
