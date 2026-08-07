package com.kidsgames.shell

import com.kidsgames.gameapi.GameModule

/**
 * The set of games available to the shell. Assembled once in `:app` and passed
 * down to the picker and session orchestrator. The shell never hardcodes which
 * games exist — that list lives entirely in [games].
 */
class GameRegistry(val games: List<GameModule>)
