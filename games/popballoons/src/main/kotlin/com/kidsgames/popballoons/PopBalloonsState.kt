package com.kidsgames.popballoons

/**
 * Colours a balloon can take. Ordered, because level 5 pops them in this
 * fixed rainbow order.
 */
enum class BalloonColor { RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK }

/**
 * One balloon on screen. Immutable -- popping a balloon produces a new
 * [Balloon] (via [copy]), never mutates this one in place.
 */
data class Balloon(
    val id: Int,
    val color: BalloonColor,
    val size: Float,
    val drifting: Boolean,
    val popped: Boolean = false,
)

/**
 * The pop-balloons state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. `PopBalloonsGame` is the only thing that
 * touches this class from Compose.
 *
 * This state is fully immutable: [pop] never mutates `this`, it returns a
 * NEW [PopBalloonsState]. Callers hold it in `mutableStateOf` and write the
 * result back (`state = state.pop(id)`), so Compose recomposition is driven
 * entirely by real state identity -- there is no separate "version" counter
 * to remember to bump.
 *
 * There is no score, no timer, and no life count. [penalties] exists purely
 * to document that fact for tests and reviewers: it is always zero, and
 * nothing in this class ever changes it.
 *
 * - L1: 5 still balloons, any tap pops.
 * - L2: 8 drifting balloons, any tap pops.
 * - L3: 12 varied-size balloons, any tap pops.
 * - L4: 15 balloons, only the (single, fixed) target colour pops; that
 *   target colour is the majority colour (13 of 15) so L4 requires MORE
 *   work than L3, not less. Tapping any other colour is a no-op on state --
 *   the caller plays a gentle-retry cue and a visible wobble. Once the
 *   target group is fully cleared, the handful of decoy balloons are
 *   auto-cleared too, so the board reaches a legible, fully-empty end state
 *   instead of finishing with untouched balloons still on screen.
 * - L5: 15 balloons, colours must be cleared one colour-group at a time in a
 *   fixed rainbow order. Tapping a balloon whose colour isn't the current
 *   target is a no-op on state, same as L4.
 */
data class PopBalloonsState(
    val level: Int,
    val balloons: List<Balloon> = buildBalloons(level),
) {

    /** Always zero. There is no such thing as a penalty in this game. */
    val penalties: Int = 0

    val isComplete: Boolean
        get() = when (level) {
            // L4 asks for one named colour; the rest are decoys the child is
            // never required to clear by hand (they auto-clear, see [pop]).
            4 -> balloons.filter { it.color == fixedTargetColor }.all { it.popped }
            else -> balloons.all { it.popped }
        }

    /**
     * The colour the child should pop next. Meaningful at L4 (fixed for the
     * whole level) and L5 (advances as each colour group clears). At L1-L3
     * every colour is a valid tap, so this is simply the first remaining
     * colour and callers should ignore it. Render this visibly at L4/L5 --
     * it is the ONLY on-screen indication of what to do next.
     */
    val targetColor: BalloonColor
        get() = when (level) {
            5 -> colorOrder.firstOrNull { color -> balloons.any { it.color == color && !it.popped } }
                ?: fixedTargetColor
            else -> fixedTargetColor
        }

    /** Fixed target colour for L4; also used as the L1-L3 placeholder and the
     *  L5 fallback once every colour is cleared. */
    private val fixedTargetColor: BalloonColor = balloons.first().color

    private val colorOrder: List<BalloonColor> = BalloonColor.entries

    /**
     * Returns a NEW state with balloon [id] popped, or `this` unchanged if
     * the tap doesn't count (already popped, or wrong colour at L4/L5).
     */
    fun pop(id: Int): PopBalloonsState {
        val balloon = balloons.find { it.id == id } ?: return this
        if (balloon.popped) return this
        if (level >= 4 && balloon.color != targetColor) return this

        val popped = balloons.map { if (it.id == id) it.copy(popped = true) else it }
        val next = copy(balloons = popped)

        // L4 only requires the target-colour group to be cleared, but
        // leaving a handful of untouched decoy balloons on screen after
        // completion reads as "broken" to a 4-6 year old. Auto-clear them
        // the moment the target group empties so the board itself shows
        // "you're done".
        return if (level == 4 && next.isComplete) {
            next.copy(balloons = next.balloons.map { it.copy(popped = true) })
        } else {
            next
        }
    }

    companion object {
        private fun buildBalloons(level: Int): List<Balloon> {
            val count = when (level) {
                1 -> 5
                2 -> 8
                3 -> 12
                4, 5 -> 15
                else -> 5
            }
            val drifting = level >= 2
            val variedSizes = level >= 3
            val palette = BalloonColor.entries

            return (0 until count).map { index ->
                val color = when (level) {
                    // Target colour is the majority (13 of 15) so L4 requires
                    // at least as much work as L3's 12 pops, not less. A
                    // couple of decoys in a second colour are enough to make
                    // "wrong colour" a real, discoverable concept.
                    4 -> if (index < count - DECOY_COUNT) palette[0] else palette[1]
                    3, 5 -> palette[index % palette.size]
                    else -> palette[index % minOf(3, palette.size)]
                }
                // Sizes stay at or above 1.0 -- the Composable coerces to a
                // minimum tap-target size anyway, so generating sub-1.0
                // scales here only produced two indistinguishable size
                // classes on screen.
                val size = if (variedSizes) 1f + (index % 4) * 0.15f else 1f
                Balloon(id = index, color = color, size = size, drifting = drifting)
            }
        }

        private const val DECOY_COUNT = 2
    }
}
