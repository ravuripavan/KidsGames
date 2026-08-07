package com.kidsgames.tracelines

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A single point on the dotted path, in a fixed VIRTUAL_SIZE x VIRTUAL_SIZE
 * coordinate space (not pixels or dp -- the Composable maps real touch
 * coordinates into this space using the actual canvas size it was given).
 * Plain Kotlin, no Android imports, so this whole file is unit-testable
 * without an emulator.
 */
data class TracePoint(val x: Float, val y: Float)

/**
 * The trace-a-dotted-path state machine.
 *
 * The path is pre-sampled into a dense, ordered list of [TracePoint]s at
 * construction time (see [pathFor]). Tracing fills the path from its start:
 * [filledUpTo] is the index of the last point the child has successfully
 * reached, and the segment from index 0 to [filledUpTo] is what renders as
 * "filled in" on screen.
 *
 * [trace] is called continuously as the child's finger moves. It only ever
 * makes forward progress -- there is no path back to a smaller [filledUpTo].
 * A touch that lands far from the current frontier does NOT reset or shrink
 * progress; it only bumps [wobbleTrigger] so the Composable can play a
 * gentle, purely visual nudge. The child can always resume: moving the
 * finger back near the frontier point continues filling exactly where they
 * left off. There is no fail state, no attempt limit, and nothing here can
 * ever go backwards.
 *
 * TOLERANCE is fixed and GENEROUS regardless of level -- difficulty comes
 * only from path complexity (see [pathFor]/[DOT_COUNT_FOR_LEVEL]), never from
 * shrinking tolerance. A 4 year old's finger wanders just as much at level 5
 * as at level 1, so tightening tolerance to raise difficulty would be a fail
 * state wearing a disguise.
 */
data class TraceLineState(
    val level: Int,
    val path: List<TracePoint>,
    val filledUpTo: Int = 0,
    val wobbleTrigger: Int = 0,
    val finished: Boolean = false,
    /**
     * True while the child's finger is currently off the path, i.e. mid
     * "stray episode". This is the latch: [wobbleTrigger] increments only on
     * the false->true EDGE of this flag, not once per off-path sample, so a
     * continuous stray drag (which can deliver 60-120 [trace] calls per
     * second) produces exactly ONE cue/flash for the whole episode instead of
     * one per frame. It clears the instant the child re-enters the path
     * (forward progress) or the path finishes, so straying again afterward
     * is a fresh episode and bumps the trigger again.
     */
    private val straying: Boolean = false,
) {

    /**
     * Advances progress if [x],[y] (in the same VIRTUAL_SIZE space as
     * [path]) is within [TOLERANCE] of the path's current frontier, looking
     * ahead up to [LOOKAHEAD] points so a fast finger drag that jumps
     * several dots in one move event still registers as continuous forward
     * progress rather than getting stuck. Always returns a NEW state.
     */
    fun trace(x: Float, y: Float): TraceLineState {
        if (finished || path.isEmpty()) return this

        val searchEnd = min(filledUpTo + LOOKAHEAD, path.lastIndex)
        var newFilled = filledUpTo
        for (index in (filledUpTo + 1)..searchEnd) {
            val point = path[index]
            if (distance(x, y, point.x, point.y) <= TOLERANCE) {
                newFilled = index
            }
        }

        // The stray predicate is an actual DISTANCE test against the whole
        // path, never a question of whether the sample advanced the
        // frontier. A finger sitting exactly on the ink but moving slower
        // than one dot-spacing per event advances nothing, yet it is not
        // straying -- it is simply close to a point it has already filled,
        // or close to a point ahead it hasn't reached yet. Only a touch
        // that is genuinely far from every point on the path counts as off
        // the line.
        val nearestPathDistance = path.minOf { distance(x, y, it.x, it.y) }
        val onPath = nearestPathDistance <= TOLERANCE

        return when {
            newFilled > filledUpTo -> {
                // Forward progress: clear the stray latch so a future stray
                // episode is treated as new.
                copy(filledUpTo = newFilled, finished = newFilled == path.lastIndex, straying = false)
            }
            onPath -> {
                // No new dot filled, but the touch is still within
                // tolerance of the path (e.g. retracing behind the
                // frontier). This clears the latch without any cue or
                // flash, so a later genuine excursion is a fresh episode.
                if (straying) copy(straying = false) else this
            }
            straying -> {
                // Still mid-stray-episode: no new cue, no new flash -- this
                // is the fix for the "error buzzer" bug. Progress is
                // untouched.
                this
            }
            else -> {
                // The false->true EDGE of straying: exactly one wobble bump
                // per episode, not one per off-path sample. The child can
                // move back toward the path and resume at any time -- never
                // a reset.
                copy(wobbleTrigger = wobbleTrigger + 1, straying = true)
            }
        }
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()

    companion object {
        /** The coordinate space every [TracePoint] and touch position is expressed in. */
        const val VIRTUAL_SIZE = 300f

        /**
         * Fixed, generous catch radius in the same units as [VIRTUAL_SIZE].
         * Never varies by level -- see class doc.
         */
        const val TOLERANCE = 34f

        /** How many upcoming points a single touch sample may fill at once. */
        private const val LOOKAHEAD = 6

        /**
         * Literal, non-decreasing dot count per level -- this IS the
         * per-level work invariant, asserted directly (not derived from the
         * implementation) in TraceLineStateTest.
         */
        private const val DOTS_L1 = 12
        private const val DOTS_L2 = 18
        private const val DOTS_L3 = 24
        private const val DOTS_L4 = 30
        private const val DOTS_L5 = 36

        fun dotCountFor(level: Int): Int = when (level.coerceIn(1, 5)) {
            1 -> DOTS_L1
            2 -> DOTS_L2
            3 -> DOTS_L3
            4 -> DOTS_L4
            else -> DOTS_L5
        }

        fun initial(level: Int): TraceLineState =
            TraceLineState(level = level, path = pathFor(level))

        /**
         * Builds the dotted path for a level: straight line (L1), curve
         * (L2), zigzag (L3), closed shape outline (L4), or single-stroke
         * letter outline (L5) -- see the class table in the design spec.
         * Each is defined as a small set of control points and then densely
         * resampled to exactly [dotCountFor] points, evenly distributed
         * across the control polyline by arc length.
         */
        fun pathFor(level: Int): List<TracePoint> {
            val controlPoints = controlPointsFor(level)
            return resample(controlPoints, dotCountFor(level))
        }

        private fun controlPointsFor(level: Int): List<TracePoint> = when (level.coerceIn(1, 5)) {
            // L1: a straight horizontal line.
            1 -> listOf(TracePoint(60f, 150f), TracePoint(240f, 150f))
            // L2: a single gentle curve, approximated by densely sampling a
            // quadratic bezier into straight micro-segments before the
            // uniform resample below.
            2 -> quadraticBezier(
                start = TracePoint(60f, 220f),
                control = TracePoint(150f, 40f),
                end = TracePoint(240f, 220f),
            )
            // L3: a zigzag -- three direction changes.
            3 -> listOf(
                TracePoint(50f, 240f),
                TracePoint(120f, 60f),
                TracePoint(190f, 240f),
                TracePoint(260f, 60f),
            )
            // L4: a closed shape outline (square), returning to its start.
            4 -> listOf(
                TracePoint(80f, 80f),
                TracePoint(220f, 80f),
                TracePoint(220f, 220f),
                TracePoint(80f, 220f),
                TracePoint(80f, 80f),
            )
            // L5: a single-stroke letter outline ("Z") -- no reading is
            // required, only tracing the shape.
            else -> listOf(
                TracePoint(80f, 80f),
                TracePoint(220f, 80f),
                TracePoint(80f, 220f),
                TracePoint(220f, 220f),
            )
        }

        private fun quadraticBezier(start: TracePoint, control: TracePoint, end: TracePoint): List<TracePoint> {
            val steps = 24
            return (0..steps).map { i ->
                val t = i / steps.toFloat()
                val oneMinusT = 1f - t
                val x = oneMinusT * oneMinusT * start.x + 2 * oneMinusT * t * control.x + t * t * end.x
                val y = oneMinusT * oneMinusT * start.y + 2 * oneMinusT * t * control.y + t * t * end.y
                TracePoint(x, y)
            }
        }

        /** Resamples a polyline into exactly [count] points, evenly spaced by arc length. */
        private fun resample(controlPoints: List<TracePoint>, count: Int): List<TracePoint> {
            if (controlPoints.size < 2 || count <= 1) return controlPoints

            val segmentLengths = controlPoints.zipWithNext { a, b ->
                hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
            }
            val totalLength = max(segmentLengths.sum(), 0.0001f)

            return (0 until count).map { i ->
                val targetDistance = totalLength * i / (count - 1)
                var travelled = 0f
                var segmentIndex = 0
                while (segmentIndex < segmentLengths.size - 1 && travelled + segmentLengths[segmentIndex] < targetDistance) {
                    travelled += segmentLengths[segmentIndex]
                    segmentIndex++
                }
                val segmentLength = segmentLengths[segmentIndex]
                val segmentT = if (segmentLength > 0f) ((targetDistance - travelled) / segmentLength).coerceIn(0f, 1f) else 0f
                val a = controlPoints[segmentIndex]
                val b = controlPoints[segmentIndex + 1]
                TracePoint(
                    x = a.x + (b.x - a.x) * segmentT,
                    y = a.y + (b.y - a.y) * segmentT,
                )
            }
        }
    }
}
