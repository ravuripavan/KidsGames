package com.kidsgames.app

import com.kidsgames.gameapi.GameModule

/**
 * The assembled list of every game the shell knows about. Populated in Phase 3
 * as each `:games:*` module lands; kept empty here rather than referencing a
 * module that isn't built yet.
 *
 * TODO(popballoons): register com.kidsgames.popballoons.PopBalloonsGame once
 * `:games:popballoons` compiles (built concurrently by another worker).
 */
object AppGames {
    val all: List<GameModule> = emptyList()
}
