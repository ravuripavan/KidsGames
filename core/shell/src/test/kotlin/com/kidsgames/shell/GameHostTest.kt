package com.kidsgames.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.kidsgames.gameapi.AgeBand
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the fix for the exit-button collision defect (carrace, colorsort,
 * matchshapes all had findings from it): [GameModule.Play] must be composed
 * inside a content area that structurally excludes the exit control's
 * footprint, so a game filling the space it is handed cannot land under the
 * button — no game-side exclusion math required.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** A fake game that fills whatever space it's given, tagged so its
     * actual on-screen bounds can be asserted against. */
    private class FillingFakeGame(override val id: String = "fakegame") : GameModule {
        override val icon: Int = android.R.drawable.ic_menu_add
        override val ageBand: AgeBand = AgeBand.FOUR_TO_FIVE
        override val estimatedMinutes: Int = 5
        override val levelCount: Int = 5

        @Composable
        override fun Play(level: Int, onFinished: (Outcome) -> Unit) {
            Box(modifier = Modifier.fillMaxSize().testTag("game_content"))
        }
    }

    @Test
    fun gameContentBottomEdgeStopsAboveTheExitZone() {
        val game = FillingFakeGame()

        composeTestRule.setContent {
            GameHost(
                game = game,
                level = 1,
                onFinished = {},
                onExitRequested = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        val hostBottom = composeTestRule.onRoot().getBoundsInRoot().bottom
        val contentBottom = composeTestRule.onNodeWithTag("game_content").getBoundsInRoot().bottom

        // The game's own content, filling everything it was given, must end
        // at least ExitZoneHeight above the bottom of the host: the shell
        // reserved that clearance before the game ever laid out.
        assertTrue(
            "game content must stop at or above the reserved exit clearance; " +
                "content bottom=$contentBottom, host bottom=$hostBottom, " +
                "required clearance=$ExitZoneHeight",
            contentBottom <= hostBottom - ExitZoneHeight,
        )
    }

    @Test
    fun exitButtonRemainsReachableWithinTheReservedZone() {
        val game = FillingFakeGame()

        composeTestRule.setContent {
            GameHost(
                game = game,
                level = 1,
                onFinished = {},
                onExitRequested = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeTestRule.onNodeWithTag("exit_game_button").assertHeightIsAtLeast(64.dp)
    }
}
