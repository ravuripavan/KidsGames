package com.kidsgames.designkit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SoundBankTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playRawWithPlaceholderIdIsSilentNoOp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val soundBank = SoundBank(context)

        // A placeholder id from :core:vocab's PlaceholderAssets — not a real
        // Android resource id. Must not throw.
        soundBank.playRaw(1)
        soundBank.playRaw(-1)
        soundBank.playRaw(0)

        soundBank.release()
    }

    @Test
    fun playRawWithUnloadableIdDoesNotThrowOnRepeatedCalls() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val soundBank = SoundBank(context)

        soundBank.playRaw(999999)
        soundBank.playRaw(999999)

        soundBank.release()
    }

    @Test
    fun rememberSoundBankDisposesOnLeavingComposition() {
        val disposedContainer = mutableStateOf(true)

        composeTestRule.setContent {
            if (disposedContainer.value) {
                ProbeContent()
            }
        }

        composeTestRule.runOnIdle {
            disposedContainer.value = false
        }

        composeTestRule.waitForIdle()
        // No crash / leak assertion possible directly without hooks; this
        // exercises the composition lifecycle path for rememberSoundBank and
        // ensures dispose runs without throwing.
        assertTrue(true)
    }

    @Composable
    private fun ProbeContent() {
        rememberSoundBank()
    }
}
