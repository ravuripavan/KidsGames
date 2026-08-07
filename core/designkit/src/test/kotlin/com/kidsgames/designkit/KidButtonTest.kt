package com.kidsgames.designkit

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KidButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun TaggedKidButton(modifier: Modifier = Modifier) {
        KidButton(onClick = {}, modifier = modifier, testTag = "kidbutton") {
            Text("tap")
        }
    }

    @Test
    fun kidButtonMeetsMinimumTapTarget() {
        composeTestRule.setContent { TaggedKidButton() }

        composeTestRule.onNodeWithTag("kidbutton").assertHeightIsAtLeast(64.dp)
    }

    /**
     * Regression test for the min-tap-target-defeat defect: a caller
     * modifier that fixes this node's size (`.size(40.dp)`) followed by
     * `.padding(...)` used to make `defaultMinSize` a no-op because it was
     * appended to the *same* modifier chain the caller controlled. The fix
     * moves the caller's modifier to an inner visual layer the caller
     * cannot use to reach the outer, min-size-owning node, so the touch
     * node must stay >= 64dp no matter what the caller passes.
     */
    @Test
    fun kidButtonCannotBeShrunkBelowMinimumTapTargetByCallerModifier() {
        composeTestRule.setContent {
            TaggedKidButton(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp),
            )
        }

        composeTestRule.onNodeWithTag("kidbutton").assertHeightIsAtLeast(64.dp)
    }

    @Test
    fun kidButtonTapStillRegistersClick() {
        val clicked = mutableStateOf(false)
        composeTestRule.setContent {
            KidButton(onClick = { clicked.value = true }, testTag = "kidbutton") {
                Text("tap")
            }
        }

        composeTestRule.onNodeWithTag("kidbutton").performClick()

        assertTrue(clicked.value)
    }
}
