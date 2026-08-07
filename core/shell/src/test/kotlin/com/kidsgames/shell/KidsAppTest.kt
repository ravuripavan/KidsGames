package com.kidsgames.shell

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the fix for the dead-end defect: a child who abandons a game (via
 * the on-screen exit control, or system back — both funnel through
 * [Outcome.Abandoned] in [KidsApp]) must return to the picker at no cost: no
 * level advance, no completion recorded. Only [Outcome.Completed] may ever
 * touch [ProgressStore].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KidsAppTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** A fake game that can only be finished by tapping its own "complete"
     * button, so tests fully control whether it ever reports Completed. */
    private class FakeGame(override val id: String = "fakegame") : GameModule {
        // A real, always-present system drawable so PickerScreen's
        // painterResource(id = game.icon) resolves under Robolectric.
        override val icon: Int = android.R.drawable.ic_menu_add
        override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
        override val estimatedMinutes: Int = 5
        override val levelCount: Int = 5

        @Composable
        override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
            Button(onClick = { onFinished(Outcome.Completed) }) {
                Text("complete")
            }
        }
    }

    /**
     * [ProgressStore] reads run on DataStore's own dispatcher, outside the
     * composition's test clock, so a plain `waitForIdle()` after a click can
     * return before the level lookup that gates entering a game has
     * resolved. Poll for the expected tag to appear/disappear instead of
     * assuming a single idle pass covers it.
     */
    private fun waitUntilTagGone(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun waitUntilTagPresent(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun newProgressStore(): ProgressStore {
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { tempFolder.newFile("kidsapp-${System.nanoTime()}.preferences_pb") },
        )
        return ProgressStore(dataStore)
    }

    @Test
    fun abandoningAGameViaTheExitControlRecordsNoCompletionAndDoesNotAdvanceTheLevel() {
        val game = FakeGame()
        val registry = GameRegistry(listOf(game))
        val progressStore = newProgressStore()

        composeTestRule.setContent {
            KidsApp(registry = registry, progressStore = progressStore)
        }

        // Enter the game from the picker.
        composeTestRule.onNodeWithTag("game_tile_${game.id}").performClick()
        waitUntilTagPresent("exit_game_button")

        // Leave via the visible exit control, without ever tapping "complete".
        composeTestRule.onNodeWithTag("exit_game_button").performClick()
        waitUntilTagPresent("game_tile_${game.id}")

        // Abandoning returned the child to the picker.
        composeTestRule.onNodeWithTag("game_tile_${game.id}").assertExists()

        val level = runBlocking { progressStore.levelFor(game.id) }
        assertEquals("abandoning must not advance the stored level", 1, level)
    }

    @Test
    fun abandoningAGameLeavesProgressStoreUntouched() {
        val game = FakeGame()
        val registry = GameRegistry(listOf(game))
        val progressStore = newProgressStore()

        // A prior completion of level 1 unlocks level 2 legitimately.
        runBlocking { progressStore.recordCompletion(game.id, 1) }

        composeTestRule.setContent {
            KidsApp(registry = registry, progressStore = progressStore)
        }

        composeTestRule.onNodeWithTag("game_tile_${game.id}").performClick()
        waitUntilTagPresent("exit_game_button")

        // Abandon level 2 without completing it.
        composeTestRule.onNodeWithTag("exit_game_button").performClick()
        waitUntilTagPresent("game_tile_${game.id}")

        val level = runBlocking { progressStore.levelFor(game.id) }
        assertEquals(
            "abandoning a level already in progress must not touch the stored level either",
            2,
            level,
        )
    }
}
