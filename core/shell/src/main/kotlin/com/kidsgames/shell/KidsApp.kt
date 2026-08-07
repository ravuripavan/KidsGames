package com.kidsgames.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 *
 * [MainActivity][com.kidsgames.app] calls `enableEdgeToEdge()`, which draws
 * the whole window behind the system bars. This composable is the shell/host
 * boundary — the one place both the picker and every game funnel through —
 * so it is where `safeDrawing` insets are applied. Neither the picker nor any
 * [GameModule] has to know edge-to-edge is enabled or remember to inset
 * itself: the space they are laid out in is already safe.
 */
@Composable
fun KidsApp(registry: GameRegistry, progressStore: ProgressStore, onExitApp: () -> Unit = {}) {
    var activeGame by remember { mutableStateOf<GameModule?>(null) }
    var activeLevel by remember { mutableStateOf(1) }
    var sessionState by remember { mutableStateOf(SessionState()) }
    val scope = rememberCoroutineScope()
    val safeAreaModifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)

    val game = activeGame
    if (game == null) {
        val suggestedGame = remember(registry.games, sessionState) {
            SessionOrchestrator.suggestNext(registry.games, sessionState)
        }
        // The picker needs every game's highest level to draw its dots and
        // to know which levels a press-and-hold may offer. Read once when
        // the picker appears, and again whenever a game finishes, since
        // that is the only thing that can raise a level.
        var highestLevels by remember { mutableStateOf(emptyMap<String, Int>()) }
        LaunchedEffect(registry.games, sessionState) {
            highestLevels = registry.games.associate { it.id to progressStore.levelFor(it.id) }
        }

        PickerScreen(
            registry = registry,
            suggestedGameId = suggestedGame?.id,
            onExitApp = onExitApp,
            highestLevels = highestLevels,
            onGameSelected = { selected ->
                scope.launch {
                    activeLevel = progressStore.levelFor(selected.id)
                    activeGame = selected
                    sessionState = sessionState.copy(lastPlayedId = selected.id)
                }
            },
            // A level chosen by press-and-hold is played as chosen. The
            // spec promises every unlocked level stays playable, and a tap
            // alone could only ever reach the highest one.
            onLevelSelected = { selected, level ->
                activeLevel = level
                activeGame = selected
                sessionState = sessionState.copy(lastPlayedId = selected.id)
            },
            modifier = safeAreaModifier,
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
                sessionState = sessionState.copy(playedIds = sessionState.playedIds + game.id)
            }
            activeGame = null
        }

        GameHost(
            game = game,
            level = activeLevel,
            onFinished = { outcome -> leaveGame(outcome) },
            onExitRequested = { leaveGame(Outcome.Abandoned) },
            modifier = safeAreaModifier,
        )
    }
}
