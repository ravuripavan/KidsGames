package com.kidsgames.shell

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
        game.Play(level = activeLevel) { outcome ->
            if (outcome == Outcome.Completed) {
                scope.launch {
                    progressStore.recordCompletion(game.id, activeLevel)
                }
            }
            activeGame = null
        }
    }
}
