package com.kidsgames.shell

import com.kidsgames.gameapi.GameModule

/**
 * Games treated as a loose affinity cluster: a child absorbed in vehicles is
 * offered the neighbouring car activity rather than something unrelated.
 * `cardesign` is parked and not registered yet, but it is listed here so the
 * cluster needs no change when it is.
 */
val CarAffinityCluster: Set<String> = setOf("carrace", "carwash", "cardesign")

/**
 * What the orchestrator knows about the current session. Deliberately tiny
 * and Android-free: no timestamps from the system clock live here, so the
 * decision logic below is a pure function of its inputs and is testable
 * without an emulator.
 */
data class SessionState(
    val playedIds: Set<String> = emptySet(),
    val lastPlayedId: String? = null,
)

/**
 * Pure decision logic for what the shell suggests next. It never picks for
 * the child — the picker's free-choice grid is always available regardless
 * of what this returns — it only decides which single tile, if any, gets a
 * gentle suggestion highlight.
 *
 * Kept free of Android and Compose so the weighting rules can be enumerated
 * and asserted in plain JUnit, which is the shape this project's hardest
 * defects turned out to need.
 */
object SessionOrchestrator {

    /**
     * Chooses the next suggestion.
     *
     * Ordering of rules, each breaking ties by registry order so the result
     * is deterministic given the same [games] and [state]:
     *
     * 1. If the last game played belongs to [CarAffinityCluster], prefer an
     *    unplayed cluster-mate over anything else — a child absorbed in
     *    vehicles is offered the neighbouring car activity.
     * 2. Otherwise prefer any unplayed game — breadth over depth, since
     *    breadth is what produces two hours of engagement at this age.
     * 3. If every game has been played this session, breadth is exhausted,
     *    and any game may be suggested again; the first one not equal to the
     *    last played game is chosen so the same tile isn't suggested twice
     *    in a row.
     */
    fun suggestNext(games: List<GameModule>, state: SessionState): GameModule? {
        if (games.isEmpty()) return null

        val lastId = state.lastPlayedId
        if (lastId != null && lastId in CarAffinityCluster) {
            val unplayedClusterMate = games.firstOrNull { candidate ->
                candidate.id in CarAffinityCluster &&
                    candidate.id != lastId &&
                    candidate.id !in state.playedIds
            }
            if (unplayedClusterMate != null) return unplayedClusterMate
        }

        val unplayed = games.firstOrNull { it.id !in state.playedIds }
        if (unplayed != null) return unplayed

        // Breadth is exhausted: every game has been played this session.
        // Avoid repeating the tile the child is standing on.
        return games.firstOrNull { it.id != lastId } ?: games.first()
    }

    /**
     * Whether an idle nudge should fire right now. The caller supplies
     * elapsed idle time and the threshold, and is responsible for resetting
     * [alreadyNudgedThisIdlePeriod] to false the moment real activity
     * resumes — that reset is what makes this "nudge once" rather than
     * "nudge on a loop": once true, this keeps returning false for the rest
     * of the same idle stretch no matter how long it continues.
     */
    fun shouldNudge(
        idleMillis: Long,
        idleThresholdMillis: Long,
        alreadyNudgedThisIdlePeriod: Boolean,
    ): Boolean {
        if (alreadyNudgedThisIdlePeriod) return false
        return idleMillis >= idleThresholdMillis
    }
}
