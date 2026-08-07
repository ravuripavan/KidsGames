package com.kidsgames.app

import com.kidsgames.gameapi.GameModule
import com.kidsgames.popballoons.PopBalloonsGame

/**
 * The assembled list of every game the shell knows about. This is the only file
 * that knows every game exists; the games themselves know nothing of each other.
 *
 * The remaining thirteen modules are registered in Phase 3, after they are built
 * concurrently — keeping this file off the concurrent write path is what lets
 * those builds run without conflicting.
 */
object AppGames {
    val all: List<GameModule> = listOf(
        PopBalloonsGame,
    )
}
