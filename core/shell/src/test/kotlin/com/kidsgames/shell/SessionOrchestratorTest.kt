package com.kidsgames.shell

import androidx.compose.runtime.Composable
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the weighting rules in plain JUnit, with no Android dependency —
 * [SessionOrchestrator] is a pure function of its inputs by design.
 */
class SessionOrchestratorTest {

    private class FakeGame(override val id: String) : GameModule {
        override val icon: Int = 0
        override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
        override val estimatedMinutes: Int = 5
        override val levelCount: Int = 5

        @Composable
        override fun Play(level: Int, onFinished: (Outcome) -> Unit) {}
    }

    private val popballoons = FakeGame("popballoons")
    private val patterns = FakeGame("patterns")
    private val carrace = FakeGame("carrace")
    private val carwash = FakeGame("carwash")
    private val games = listOf(popballoons, patterns, carrace, carwash)

    @Test
    fun `an unplayed game is suggested over a played one`() {
        val state = SessionState(playedIds = setOf("popballoons"), lastPlayedId = "popballoons")

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        assertEquals("patterns", suggestion?.id)
    }

    @Test
    fun `no games played yet suggests the first registry entry`() {
        val state = SessionState()

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        assertEquals("popballoons", suggestion?.id)
    }

    @Test
    fun `finishing a car cluster game suggests the unplayed cluster mate over an unrelated unplayed game`() {
        val state = SessionState(playedIds = setOf("carrace"), lastPlayedId = "carrace")

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        // patterns is unplayed and earlier in registry order, but the car
        // cluster affinity wins: a child absorbed in vehicles is offered the
        // neighbouring car activity rather than something unrelated.
        assertEquals("carwash", suggestion?.id)
    }

    @Test
    fun `car cluster affinity does not apply when the last played game is outside the cluster`() {
        val state = SessionState(playedIds = setOf("popballoons"), lastPlayedId = "popballoons")

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        assertEquals("patterns", suggestion?.id)
    }

    @Test
    fun `car cluster affinity is skipped once every cluster mate has also been played`() {
        val state = SessionState(
            playedIds = setOf("carrace", "carwash"),
            lastPlayedId = "carrace",
        )

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        assertEquals("popballoons", suggestion?.id)
    }

    @Test
    fun `once every game has been played this session the last played tile is not repeated`() {
        val state = SessionState(
            playedIds = setOf("popballoons", "patterns", "carrace", "carwash"),
            lastPlayedId = "patterns",
        )

        val suggestion = SessionOrchestrator.suggestNext(games, state)

        assertEquals("popballoons", suggestion?.id)
        assertTrue(suggestion?.id != "patterns")
    }

    @Test
    fun `an empty registry suggests nothing`() {
        val suggestion = SessionOrchestrator.suggestNext(emptyList(), SessionState())

        assertNull(suggestion)
    }

    @Test
    fun `idle nudge fires once the threshold is reached`() {
        val fired = SessionOrchestrator.shouldNudge(
            idleMillis = 30_000,
            idleThresholdMillis = 30_000,
            alreadyNudgedThisIdlePeriod = false,
        )

        assertTrue(fired)
    }

    @Test
    fun `idle nudge does not fire before the threshold`() {
        val fired = SessionOrchestrator.shouldNudge(
            idleMillis = 29_999,
            idleThresholdMillis = 30_000,
            alreadyNudgedThisIdlePeriod = false,
        )

        assertFalse(fired)
    }

    @Test
    fun `idle nudge never fires twice for the same idle period, no matter how long idle continues`() {
        val stillFalse = SessionOrchestrator.shouldNudge(
            idleMillis = 600_000,
            idleThresholdMillis = 30_000,
            alreadyNudgedThisIdlePeriod = true,
        )

        assertFalse(stillFalse)
    }
}
