package com.kidsgames.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kidsgames.designkit.KidPalette
import com.kidsgames.designkit.MinTapTarget
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Friction, not security: leaving the app entirely — as opposed to leaving a
 * game back to the picker, which is always one tap — requires a deliberate
 * three-second press-and-hold on a corner target. It exists so a child does
 * not accidentally exit the whole app mid-journey, not to keep a determined
 * adult out.
 */
const val ParentalGateHoldMillis = 3_000L

/**
 * The pure timing rule behind the gate, kept Android-free so it is testable
 * in plain JUnit: a hold only counts if it has been continuously down for
 * [ParentalGateHoldMillis]. Any release restarts the clock on the next press
 * from zero — this is what makes rapid tapping unable to accumulate toward
 * the threshold no matter how many taps land, since every tap's held
 * duration individually falls far short of it.
 */
fun isParentalGateHeldLongEnough(heldMillis: Long): Boolean =
    heldMillis >= ParentalGateHoldMillis

/**
 * A corner target that fires [onGateOpen] only after being held down
 * continuously for [ParentalGateHoldMillis]. Releasing early — including
 * releasing and re-pressing rapidly, as fast random tapping does — cancels
 * the hold and starts over on the next press; there is no accumulation
 * across separate presses.
 *
 * Uses `awaitFirstDown` / `waitForUpOrCancellation` directly, rather than
 * `clickable`'s press-interaction stream, so the hold is measured against
 * one continuous pointer-down gesture rather than reconstructed from tap
 * events afterward.
 */
@Composable
fun ParentalGateTarget(onGateOpen: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(MinTapTarget)
            .background(KidPalette.Surface, CircleShape)
            .testTag("parental_gate_target")
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val heldFullDuration = withTimeoutOrNull(ParentalGateHoldMillis) {
                        waitForUpOrCancellation()
                    } == null
                    if (heldFullDuration) {
                        onGateOpen()
                    }
                }
            },
    )
}
