package com.kidsgames.designkit

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Light tick on tap, applied uniformly across the suite so every game's touch
 * feedback feels identical. Combine with a scale-pop in the caller's own
 * animation state where richer feedback is wanted; this modifier owns only
 * the haptic.
 */
fun Modifier.kidTapFeedback(): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    this.pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            },
        )
    }
}
