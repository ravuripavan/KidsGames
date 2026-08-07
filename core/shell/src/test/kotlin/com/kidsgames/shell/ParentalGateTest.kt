package com.kidsgames.shell

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The gate is friction, not security, but it must genuinely resist rapid
 * tapping: covers both the pure timing rule and the actual gesture.
 */
class ParentalGateTimingTest {
    @Test
    fun `a three second hold is long enough`() {
        assertTrue(isParentalGateHeldLongEnough(3_000L))
        assertTrue(isParentalGateHeldLongEnough(3_500L))
    }

    @Test
    fun `anything short of three seconds is not long enough`() {
        assertFalse(isParentalGateHeldLongEnough(2_999L))
        assertFalse(isParentalGateHeldLongEnough(0L))
    }

    @Test
    fun `many short taps never individually accumulate toward the threshold`() {
        // Rapid tapping is a sequence of separate, short presses. Each one
        // restarts the clock on release, so no matter how many of them land,
        // none of them individually clears the threshold.
        val rapidTapDurations = List(50) { 40L } // 50 taps at ~40ms each
        assertTrue(rapidTapDurations.none { isParentalGateHeldLongEnough(it) })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParentalGateTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun holdingForThreeSecondsOpensTheGate() {
        var opened = 0
        composeTestRule.setContent {
            ParentalGateTarget(onGateOpen = { opened++ })
        }

        composeTestRule.onNodeWithTag("parental_gate_target").performTouchInput {
            longClick(durationMillis = ParentalGateHoldMillis + 200)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun rapidTappingDoesNotOpenTheGate() {
        var opened = 0
        composeTestRule.setContent {
            ParentalGateTarget(onGateOpen = { opened++ })
        }

        repeat(20) {
            composeTestRule.onNodeWithTag("parental_gate_target").performTouchInput {
                down(center)
                advanceEventTime(40)
                up()
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(0, opened)
    }

    @Test
    fun releasingBeforeTheThresholdDoesNotOpenTheGate() {
        var opened = 0
        composeTestRule.setContent {
            ParentalGateTarget(onGateOpen = { opened++ })
        }

        composeTestRule.onNodeWithTag("parental_gate_target").performTouchInput {
            down(center)
            advanceEventTime(2_500)
            up()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, opened)
    }
}
