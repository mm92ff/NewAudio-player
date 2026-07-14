package com.example.newaudio

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.MainAppScreen
import com.example.newaudio.ui.theme.NewAudioTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityStarts() {
        assertFalse(composeRule.activity.isFinishing)
    }

    @Test
    fun coldStartDoesNotShowPlaybackInitializationError() {
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                NewAudioTheme(UserPreferences.default()) {
                    MainAppScreen()
                }
            }
        }
        composeRule.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unknownError = context.getString(R.string.unknown_error)
        val observationDeadline = SystemClock.uptimeMillis() + 6_000L
        var errorWasShown = false

        while (SystemClock.uptimeMillis() < observationDeadline && !errorWasShown) {
            composeRule.waitForIdle()
            errorWasShown = composeRule.onAllNodesWithText(unknownError)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (!errorWasShown) {
                SystemClock.sleep(50L)
            }
        }

        assertFalse("Playback initialization displayed an unknown error", errorWasShown)
    }
}
