package com.kidsgames.app

import com.kidsgames.carrace.CarRaceGame
import com.kidsgames.carwash.CarWashGame
import com.kidsgames.gameapi.GameModule
import com.kidsgames.matchshapes.MatchShapesGame
import com.kidsgames.memorypairs.MemoryPairsGame
import com.kidsgames.patterns.PatternsGame
import com.kidsgames.popballoons.PopBalloonsGame
import com.kidsgames.tracelines.TraceLinesGame

/**
 * The assembled list of every game the shell knows about. This is the only file
 * that knows every game exists; the games themselves know nothing of each other.
 *
 * Seven modules cleared review and are registered here: popballoons, patterns,
 * matchshapes, carrace, memorypairs, carwash, tracelines. The remaining six are
 * parked pending human content decisions (see docs/superpowers/PARKED.md) and
 * talktime is built but not yet review-confirmed, so neither group is registered
 * yet.
 */
object AppGames {
    val all: List<GameModule> = listOf(
        PopBalloonsGame,
        PatternsGame,
        MatchShapesGame,
        CarRaceGame,
        MemoryPairsGame,
        CarWashGame,
        TraceLinesGame,
    )
}
