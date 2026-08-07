package com.kidsgames.carwash

/**
 * The tools the child can drag over the car. This is a SANDBOX: levels unlock
 * more tools, never a harder task, and no tool is ever the "wrong" one to use
 * anywhere on the car -- every stroke with any unlocked tool cleans dirt.
 */
enum class Tool { SPONGE, HOSE, DRYER, WAX, POLISH }

/**
 * Plain-Kotlin, Android-free state machine for `:games:carwash`. No Android
 * imports, so it is unit-tested without an emulator (see [CarWashState]'s
 * companion object and the accompanying test file).
 *
 * The car's dirt is a fixed-size grid (row-major, [GRID_ROWS] x [GRID_COLS])
 * of dirt AMOUNTS in `0f..~1.6f`, not booleans -- it needs to be able to
 * represent "a little grimier" for level 5 without any extra state shape.
 * [scrub] is the only thing that ever changes [dirt], and it only ever
 * lowers a cell's value, coerced at a floor of `0f`. There is no code path,
 * anywhere in this class, that raises a dirt value once it has been
 * scrubbed -- dirt removal is monotonic and irreversible, which is exactly
 * what "nothing can go wrong, nothing needs to be undone" requires from a
 * sandbox.
 *
 * [dirt] is copied (not mutated in place) on every [scrub] call that
 * actually changes something, and returned wrapped in a brand new
 * [CarWashState] via [copy] -- callers hold this in a single
 * `mutableStateOf` and write the result back, exactly like every other
 * module in the suite. A 48-float list copy on every call that touches the
 * car is cheap enough to do on every pointer-move event.
 */
data class CarWashState(
    val level: Int,
    val dirt: List<Float>,
    val selectedTool: Tool,
    val strokeCount: Int = 0,
) {

    /** True once every cell has been scrubbed down to (or started at) zero dirt. */
    val isClean: Boolean get() = dirt.all { it <= 0f }

    /** Equips a different tool. A tool not yet unlocked at [level] is silently ignored -- never a fail state. */
    fun selectTool(tool: Tool): CarWashState =
        if (tool in toolsFor(level)) copy(selectedTool = tool) else this

    /**
     * Scrubs the currently selected tool over grid cell ([row], [col]) and a
     * small neighbourhood around it (radius depends on the tool -- the hose
     * covers more ground per stroke than the sponge). Every affected cell's
     * dirt can only go DOWN, coerced at `0f`; a cell already at zero is left
     * alone. Returns `this` unchanged (same instance) if the touch point is
     * off the grid or nothing was actually reduced, so recomposition is
     * driven by genuine state changes rather than a version counter.
     */
    fun scrub(row: Int, col: Int): CarWashState {
        if (row !in 0 until GRID_ROWS || col !in 0 until GRID_COLS) return this

        val radius = radiusFor(selectedTool)
        val amount = amountFor(selectedTool)
        val radius2 = radius * radius
        var changed = false
        val newDirt = dirt.toMutableList()

        for (r in maxOf(0, row - radius)..minOf(GRID_ROWS - 1, row + radius)) {
            for (c in maxOf(0, col - radius)..minOf(GRID_COLS - 1, col + radius)) {
                val dr = r - row
                val dc = c - col
                val dist2 = dr * dr + dc * dc
                if (dist2 > radius2) continue
                val idx = r * GRID_COLS + c
                val current = newDirt[idx]
                if (current <= 0f) continue
                // Falloff so the stroke centre cleans faster than its edge,
                // without ever being able to push a value below zero.
                val falloff = 1f - (dist2.toFloat() / (radius2 + 1))
                val reduced = (current - amount * falloff).coerceAtLeast(0f)
                if (reduced < current) {
                    newDirt[idx] = reduced
                    changed = true
                }
            }
        }

        return if (changed) copy(dirt = newDirt, strokeCount = strokeCount + 1) else this
    }

    companion object {
        const val GRID_ROWS = 6
        const val GRID_COLS = 8

        /**
         * Which tools are on the tray at [level]. Strictly non-decreasing in
         * COUNT across levels 1-5 (1, 2, 3, 5, 5) -- see
         * `CarWashStateTest."tool count is non-decreasing across levels one through five"`
         * for the literal invariant. L5 repeats L4's five tools; its only
         * difference is a dirtier starting car (see [initial]), never a
         * stricter or harder task.
         */
        fun toolsFor(level: Int): List<Tool> = when {
            level <= 1 -> listOf(Tool.SPONGE)
            level == 2 -> listOf(Tool.SPONGE, Tool.HOSE)
            level == 3 -> listOf(Tool.SPONGE, Tool.HOSE, Tool.DRYER)
            else -> listOf(Tool.SPONGE, Tool.HOSE, Tool.DRYER, Tool.WAX, Tool.POLISH)
        }

        /**
         * Starting dirt amount per cell. Level 5's car starts grimier
         * (a higher amount, needing more strokes to fully clean) rather than
         * anything about the TASK becoming stricter -- there is still no
         * tolerance, no time limit, and no wrong tool.
         */
        private fun initialDirtValue(level: Int): Float = if (level >= 5) 1.6f else 1f

        private fun radiusFor(tool: Tool): Int = if (tool == Tool.HOSE) 2 else 1

        private fun amountFor(tool: Tool): Float = if (tool == Tool.HOSE) 0.30f else 0.22f

        /**
         * Pure accumulate-until-target decision for the sandbox completion
         * timer: given how much FOREGROUND play has already accumulated and
         * a single tick's duration, returns the new accumulated total after
         * one more tick, coerced so it never overshoots [targetMillis]. The
         * caller (the Composable's `LaunchedEffect`) is responsible for the
         * actual suspension between ticks and for stopping once
         * `accumulatedMillis >= targetMillis` -- this function only has to
         * prove the arithmetic terminates at the target rather than looping
         * forever, which is exactly what a unit test can assert without any
         * Android dependency.
         */
        fun accumulatePlayMillis(accumulatedMillis: Long, tickMillis: Long, targetMillis: Long): Long =
            (accumulatedMillis + tickMillis).coerceAtMost(targetMillis)

        /** Builds the starting state for [level]: every cell dirty, sponge equipped (always unlocked first). */
        fun initial(level: Int): CarWashState {
            val value = initialDirtValue(level)
            return CarWashState(
                level = level,
                dirt = List(GRID_ROWS * GRID_COLS) { value },
                selectedTool = toolsFor(level).first(),
            )
        }
    }
}
