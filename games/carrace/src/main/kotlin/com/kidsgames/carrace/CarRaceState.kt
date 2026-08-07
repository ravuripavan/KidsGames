package com.kidsgames.carrace

/**
 * What occupies one lane at one row of the endless road.
 *
 * [Obstacle] never ends the run -- driving through one only triggers a soft
 * slow-and-bounce (see [CarRaceState.tick] and [CarRaceState.bumpTrigger]).
 * [Ramp] and [Star] are purely positive: a ramp is a brief visual hop, a
 * star is a collectible flourish. Nothing here can ever ends the drive.
 */
enum class RoadItem { NONE, STAR, OBSTACLE, RAMP }

/** One row of the road, one entry per lane, scrolling toward the car. */
data class RoadRow(val lanes: List<RoadItem>)

/**
 * The endless-drive state machine. Plain Kotlin, no Android imports, so it
 * is unit-tested without an emulator. `CarRaceGame` is the only thing that
 * touches this class from Compose.
 *
 * Fully immutable: [tick], [switchLane], [moveLeft], and [moveRight] never
 * mutate `this`, they each return a NEW [CarRaceState]. Callers hold it in
 * `mutableStateOf` and write the result back (`state = state.tick()`), so
 * recomposition is driven entirely by real state identity -- there is no
 * separate "version" or "trigger" counter anywhere in this class.
 *
 * There is NO crash, NO lap timer, NO opponent, and NO score. [starsCollected]
 * exists only to drive a visual flourish (a few extra sparkles), never a
 * numeric readout, and nothing in this class ever reads it as a pass/fail
 * condition. [bumpTrigger] increments on every obstacle contact purely so
 * the Composable can play a one-shot flash-and-bounce animation; it is never
 * checked by anything that could end the run. [rampTrigger] increments on
 * every ramp contact purely so the Composable can play a one-shot hop
 * animation; like [bumpTrigger], it is never checked by anything that could
 * end the run.
 *
 * The car's lane is always clamped to `0 until laneCount(level)` -- there is
 * no code path, on any input, that can move it outside the road.
 *
 * - L1: 2 lanes, no obstacles, no ramps, no branches.
 * - L2: 3 lanes, no obstacles, no ramps, no branches.
 * - L3: 3 lanes, PLUS obstacles that appear in the scrolling rows.
 * - L4: 3 lanes, obstacles PLUS ramps.
 * - L5: 3 lanes, obstacles, ramps, PLUS branching rows (a row where more
 *   than one lane offers a star, so the child chooses a path).
 *
 * Difficulty grows only by adding road features, never by speed: [speed] is
 * fixed per tick regardless of level and stays comfortably within a 4-6
 * year old's reaction time even at L5.
 */
data class CarRaceState(
    val level: Int,
    val laneCount: Int = laneCountFor(level),
    val carLane: Int = laneCountFor(level) / 2,
    val rows: List<RoadRow> = emptyList(),
    val ticksElapsed: Int = 0,
    val starsCollected: Int = 0,
    val bumpTrigger: Int = 0,
    val rampTrigger: Int = 0,
    val finished: Boolean = false,
    private val rng: Long = level.toLong() * 104729L + 1,
) {

    /**
     * Advances the drive by one tick: rows scroll one step closer, a new
     * row spawns at the far end, and if a row reaches the car's row its
     * lane content is resolved (star collected, obstacle bumped, ramp
     * hopped, or nothing). Always returns a NEW state.
     *
     * The drive completes on elapsed time alone -- never on a star or
     * distance threshold, which would be a score by another name. See
     * [TICKS_TO_COMPLETE].
     */
    fun tick(): CarRaceState {
        if (finished) return this

        val advancedRows = rows.map { it }.toMutableList()
        var newStars = starsCollected
        var newBumpTrigger = bumpTrigger
        var newRampTrigger = rampTrigger

        // The row about to reach the car (index 0) is resolved this tick.
        if (advancedRows.isNotEmpty()) {
            val arriving = advancedRows.removeAt(0)
            when (arriving.lanes.getOrNull(carLane)) {
                RoadItem.STAR -> newStars += 1
                RoadItem.OBSTACLE -> newBumpTrigger += 1
                RoadItem.RAMP -> newRampTrigger += 1
                else -> Unit
            }
        }

        var nextRng = rng
        val spawnRounds = ROWS_PER_TICK
        repeat(spawnRounds) {
            val (row, updatedRng) = spawnRow(level, laneCount, nextRng)
            advancedRows.add(row)
            nextRng = updatedRng
        }

        val newTicks = ticksElapsed + 1
        return copy(
            rows = advancedRows,
            ticksElapsed = newTicks,
            starsCollected = newStars,
            bumpTrigger = newBumpTrigger,
            rampTrigger = newRampTrigger,
            finished = newTicks >= TICKS_TO_COMPLETE,
            rng = nextRng,
        )
    }

    /**
     * Moves the car one lane left. Clamped so the car can never leave the
     * road -- calling this at lane 0 returns `this` unchanged in that
     * regard (a new state with the same [carLane]).
     */
    fun moveLeft(): CarRaceState = copy(carLane = (carLane - 1).coerceIn(0, laneCount - 1))

    /** Moves the car one lane right, clamped the same way as [moveLeft]. */
    fun moveRight(): CarRaceState = copy(carLane = (carLane + 1).coerceIn(0, laneCount - 1))

    /** Jumps directly to a lane, clamped to the road -- used by tap-to-lane input. */
    fun switchLane(lane: Int): CarRaceState = copy(carLane = lane.coerceIn(0, laneCount - 1))

    companion object {
        /** Fixed tick cadence; the child controls when the drive ends only by quitting. */
        const val TICKS_TO_COMPLETE = 600
        private const val ROWS_PER_TICK = 1
        private const val VISIBLE_ROWS = 8

        fun laneCountFor(level: Int): Int = if (level <= 1) 2 else 3

        /** Builds the initial state with a pre-filled buffer of upcoming rows. */
        fun initial(level: Int): CarRaceState {
            val lanes = laneCountFor(level)
            var rng = level.toLong() * 104729L + 1
            val rows = mutableListOf<RoadRow>()
            repeat(VISIBLE_ROWS) {
                val (row, updatedRng) = spawnRow(level, lanes, rng)
                rows.add(row)
                rng = updatedRng
            }
            return CarRaceState(level = level, laneCount = lanes, carLane = lanes / 2, rows = rows, rng = rng)
        }

        /** Simple deterministic LCG so tests are reproducible without an Android Random dependency. */
        private fun nextRandom(seed: Long): Pair<Long, Double> {
            val next = (seed * 6364136223846793005L + 1442695040888963407L)
            val bounded = ((next ushr 33).toDouble()) / (1L shl 31).toDouble()
            return next to bounded
        }

        private fun spawnRow(level: Int, laneCount: Int, rng: Long): Pair<RoadRow, Long> {
            var seed = rng
            val hasObstacles = level >= 3
            val hasRamps = level >= 4
            val hasBranches = level >= 5

            val lanes = MutableList(laneCount) { RoadItem.NONE }

            // Star lane(s). L5 may place a star in more than one lane at
            // once (a branch: several viable paths), all other levels
            // place at most one star per row.
            val (r1, d1) = nextRandom(seed); seed = r1
            if (d1 < STAR_CHANCE) {
                val (r2, d2) = nextRandom(seed); seed = r2
                lanes[(d2 * laneCount).toInt().coerceIn(0, laneCount - 1)] = RoadItem.STAR

                if (hasBranches) {
                    val (r3, d3) = nextRandom(seed); seed = r3
                    if (d3 < BRANCH_CHANCE) {
                        val (r4, d4) = nextRandom(seed); seed = r4
                        val second = (d4 * laneCount).toInt().coerceIn(0, laneCount - 1)
                        if (lanes[second] == RoadItem.NONE) lanes[second] = RoadItem.STAR
                    }
                }
            }

            if (hasObstacles) {
                val (r5, d5) = nextRandom(seed); seed = r5
                if (d5 < OBSTACLE_CHANCE) {
                    val (r6, d6) = nextRandom(seed); seed = r6
                    val lane = (d6 * laneCount).toInt().coerceIn(0, laneCount - 1)
                    // Never block every lane, and never overwrite a star --
                    // an obstacle must always leave at least one clear or
                    // star-bearing lane reachable.
                    val blockedLanes = lanes.count { it != RoadItem.NONE }
                    if (lanes[lane] == RoadItem.NONE && blockedLanes < laneCount - 1) {
                        lanes[lane] = RoadItem.OBSTACLE
                    }
                }
            }

            if (hasRamps) {
                val (r7, d7) = nextRandom(seed); seed = r7
                if (d7 < RAMP_CHANCE) {
                    val (r8, d8) = nextRandom(seed); seed = r8
                    val lane = (d8 * laneCount).toInt().coerceIn(0, laneCount - 1)
                    if (lanes[lane] == RoadItem.NONE) lanes[lane] = RoadItem.RAMP
                }
            }

            return RoadRow(lanes) to seed
        }

        private const val STAR_CHANCE = 0.5
        private const val OBSTACLE_CHANCE = 0.35
        private const val RAMP_CHANCE = 0.25
        private const val BRANCH_CHANCE = 0.4
    }
}
