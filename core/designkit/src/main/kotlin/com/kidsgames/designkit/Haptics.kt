package com.kidsgames.designkit

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Light tick on tap, applied uniformly across the suite so every game's touch
 * feedback feels identical.
 *
 * This reacts to [interactionSource] rather than installing its own
 * `pointerInput`/`detectTapGestures` handler. A node can only have one
 * gesture detector consuming the pointer-down/up stream; [KidButton] already
 * owns that via `clickable`. A second, independent `detectTapGestures` on
 * the same node races it for the up event and can swallow taps. Watching
 * [PressInteraction] instead means this never competes for pointer input —
 * it only observes state that `clickable` already publishes.
 */
@Composable
fun KidTapHaptics(interactionSource: InteractionSource) {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }
}

/**
 * Deprecated no-op kept for source compatibility with existing call sites.
 * [KidButton] now applies [KidTapHaptics] internally against its own
 * `interactionSource`, so callers no longer need to (and must not) install
 * their own tap feedback on top of it.
 */
@Deprecated(
    message = "KidButton applies tap haptics internally. This modifier is now a no-op " +
        "and will be removed; delete call sites.",
    replaceWith = ReplaceWith("this"),
)
fun Modifier.kidTapFeedback(): Modifier = this
