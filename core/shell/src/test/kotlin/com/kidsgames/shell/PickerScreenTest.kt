package com.kidsgames.shell

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import androidx.compose.runtime.Composable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the picker budget at fourteen games on the smallest supported
 * portrait viewport (360x640dp): every tile must meet the 64dp tap-target
 * floor, and the grid must fit without needing scroll discovery. See the dp
 * arithmetic documented on [PickerScreen] — this test is the check that the
 * arithmetic actually holds at runtime, not just on paper.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp")
class PickerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeGame(override val id: String) : GameModule {
        override val icon: Int = android.R.drawable.ic_menu_add
        override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
        override val estimatedMinutes: Int = 5
        override val levelCount: Int = 5

        @Composable
        override fun Play(level: Int, onFinished: (Outcome) -> Unit) {}
    }

    private val fourteenGames = (1..14).map { FakeGame(id = "game$it") }

    @Test
    fun everyTileMeetsTheMinimumTapTargetAtFourteenGames() {
        val registry = GameRegistry(fourteenGames)

        composeTestRule.setContent {
            PickerScreen(registry = registry, onGameSelected = {})
        }

        fourteenGames.forEach { game ->
            composeTestRule.onNodeWithTag("game_tile_${game.id}")
                .assertWidthIsAtLeast(64.dp)
                .assertHeightIsAtLeast(64.dp)
        }
    }

    @Test
    fun allFourteenTilesLayOutWithoutRequiringScrollToBecomeVisible() {
        val registry = GameRegistry(fourteenGames)

        composeTestRule.setContent {
            PickerScreen(registry = registry, onGameSelected = {})
        }
        composeTestRule.waitForIdle()

        // LazyVerticalGrid only composes nodes it currently lays out
        // on/near screen. If the budget in the PickerScreen doc is correct,
        // all fourteen tiles fit in the 360x640dp viewport at once and are
        // all composed without the child ever having to scroll to reach
        // them.
        val composedTiles = composeTestRule.onAllNodesWithTag("game_tile_", useUnmergedTree = true)
        assertEquals(
            "expected all 14 tiles to be laid out without scrolling",
            14,
            fourteenGames.count { game ->
                composeTestRule.onAllNodesWithTag("game_tile_${game.id}")
                    .fetchSemanticsNodes().isNotEmpty()
            },
        )
    }
}
