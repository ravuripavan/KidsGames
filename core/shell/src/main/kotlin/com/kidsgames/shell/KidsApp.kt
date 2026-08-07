package com.kidsgames.shell

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.kidsgames.gameapi.GameModule
import com.kidsgames.gameapi.Outcome
import kotlinx.coroutines.launch

/**
 * Root composable for the whole suite: shows the [PickerScreen] by default,
 * and swaps in a game's own [GameModule.Play] when one is selected. A game
 * that throws is caught here at the shell boundary and treated as an
 * abandonment, so a bug in one game never takes down the shell.
 *
 * Every path out of a game — the on-screen exit control, system back, and an
 * uncaught exception — funnels through [leaveGame] and therefore always
 * produces [Outcome.Abandoned] unless the game itself reported
 * [Outcome.Completed]. A child who backs out never has progress recorded and
 * never advances a level: abandoning is free.
 *
 * System back while the picker is showing is NOT intercepted here, so it
 * falls through to the platform default and exits the app, as it should —
 * the picker is the top of the shell's own navigation stack.
 */
@Composable
fun KidsApp(registry: GameRegistry, progressStore: ProgressStore) {
    var activeGame by remember { mutableStateOf<GameModule?>(null) }
    var activeLevel by remember { mutableStateOf(1) }
    val scope = rememberCoroutineScope()

    val game = activeGame
    if (game == null) {
        PickerScreen(
            registry = registry,
            onGameSelected = { selected ->
                scope.launch {
                    activeLevel = progressStore.levelFor(selected.id)
                    activeGame = selected
                }
            },
        )
    } else {
        // Intercepts system back only while a game is showing. Returns the
        // child to the picker instead of exiting the app — the dead end the
        // spec calls out as unrecoverable for a pre-reader.
        BackHandler(enabled = true) {
            activeGame = null
        }

        fun leaveGame(outcome: Outcome) {
            if (outcome == Outcome.Completed) {
                scope.launch {
                    progressStore.recordCompletion(game.id, activeLevel)
                }
            }
            activeGame = null
        }

        GameHost(
            game = game,
            level = activeLevel,
            onFinished = { outcome -> leaveGame(outcome) },
            onExitRequested = { leaveGame(Outcome.Abandoned) },
        )
    }
}
