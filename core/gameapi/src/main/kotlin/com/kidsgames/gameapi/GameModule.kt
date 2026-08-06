package com.kidsgames.gameapi

import androidx.compose.runtime.Composable

/**
 * The contract every mini-game implements. Frozen — every game module and the
 * shell compile against this exact interface. A game module signals completion
 * by invoking [onFinished]; it never navigates, never knows the shell exists,
 * and never persists its own level. The shell owns progression and passes
 * [Play]'s `level` parameter in.
 */
interface GameModule {
    /** Stable identifier, used as the DataStore key. */
    val id: String

    /** Drawable resource shown on the picker. */
    val icon: Int

    /** Which half of the 4-6 range this game targets. */
    val ageBand: AgeBand

    /** Used by the orchestrator to pace rotation. */
    val estimatedMinutes: Int

    /** Always 5 in the first wave. */
    val levelCount: Int

    @Composable
    fun Play(level: Int, onFinished: (Outcome) -> Unit)
}

enum class AgeBand { FOUR_TO_FIVE, FIVE_TO_SIX }

sealed interface Outcome {
    /** The child reached the end of this level. */
    data object Completed : Outcome

    /** The child backed out or went idle. */
    data object Abandoned : Outcome
}
