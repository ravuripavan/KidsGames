package com.kidsgames.popballoons

/**
 * Colours a balloon can take. Ordered, because level 5 pops them in this
 * fixed rainbow order.
 */
enum class BalloonColor { RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK }

/**
 * One balloon on screen. [popped] is the only mutable field; everything else
 * is fixed for the lifetime of a level.
 */
class Balloon(
    val id: Int,
    val color: BalloonColor,
    val size: Float,
    val drifting: Boolean,
    var popped: Boolean = false,
)

/**
 * The pop-balloons state machine. Plain Kotlin, no Android imports, so it is
 * unit-tested without an emulator. `PopBalloonsGame` is the only thing that
 * touches this class from Compose.
 *
 * There is no score, no timer, and no life count. [penalties] exists purely
 * to document that fact for tests and reviewers: it is always zero, and
 * nothing in this class ever changes it.
 *
 * - L1: 5 still balloons, any tap pops.
 * - L2: 8 drifting balloons, any tap pops.
 * - L3: 12 varied-size balloons, any tap pops.
 * - L4: 15 balloons, only the (single, fixed) target colour pops. Tapping any
 *   other colour is a no-op on state -- the caller plays a gentle-retry cue.
 * - L5: 15 balloons, colours must be cleared one colour-group at a time in a
 *   fixed rainbow order. Tapping a balloon whose colour isn't the current
 *   target is a no-op on state, same as L4.
 */
class PopBalloonsState(val level: Int) {

    val balloons: List<Balloon> = buildBalloons(level)

    /** Always zero. There is no such thing as a penalty in this game. */
    val penalties: Int = 0

    val isComplete: Boolean
        get() = when (level) {
            // L4 asks for one named colour; the rest are decoys the child is
            // never required to clear.
            4 -> balloons.filter { it.color == fixedTargetColor }.all { it.popped }
            else -> balloons.all { it.popped }
        }

    /**
     * The colour the child should pop next. Meaningful at L4 (fixed for the
     * whole level) and L5 (advances as each colour group clears). At L1-L3
     * every colour is a valid tap, so this is simply the first remaining
     * colour and callers should ignore it.
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

    fun pop(id: Int) {
        val balloon = balloons.find { it.id == id } ?: return
        if (balloon.popped) return
        if (level >= 4 && balloon.color != targetColor) return
        balloon.popped = true
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
            val multiColor = level >= 3

            return (0 until count).map { index ->
                val color = when {
                    multiColor -> palette[index % palette.size]
                    else -> palette[index % minOf(3, palette.size)]
                }
                val size = if (variedSizes) 0.7f + (index % 4) * 0.2f else 1f
                Balloon(id = index, color = color, size = size, drifting = drifting)
            }
        }
    }
}
