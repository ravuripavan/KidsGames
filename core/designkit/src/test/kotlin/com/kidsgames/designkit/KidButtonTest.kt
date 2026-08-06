package com.kidsgames.designkit

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
    private fun TaggedKidButton() {
        KidButton(onClick = {}, modifier = Modifier.testTag("kidbutton")) {
            Text("tap")
        }
    }

    @Test
    fun kidButtonMeetsMinimumTapTarget() {
        composeTestRule.setContent { TaggedKidButton() }

        composeTestRule.onNodeWithTag("kidbutton").assertHeightIsAtLeast(64.dp)
    }
}
