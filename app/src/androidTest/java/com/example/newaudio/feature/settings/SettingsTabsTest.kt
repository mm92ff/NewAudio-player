package com.example.newaudio.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode
import com.example.newaudio.ui.NewAudioTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun generalIsSelectedInitiallyAndAllTabsSwitchContent() {
        setSettingsContent()

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_GENERAL)
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_CONTENT_GENERAL).assertExists()

        listOf(
            NewAudioTestTags.SETTINGS_TAB_MEDIA to NewAudioTestTags.SETTINGS_CONTENT_MEDIA,
            NewAudioTestTags.SETTINGS_TAB_DESIGN to NewAudioTestTags.SETTINGS_CONTENT_DESIGN,
            NewAudioTestTags.SETTINGS_TAB_SYSTEM to NewAudioTestTags.SETTINGS_CONTENT_SYSTEM
        ).forEach { (tabTag, contentTag) ->
            composeRule.onNodeWithTag(tabTag).performClick().assertIsSelected()
            composeRule.onNodeWithTag(contentTag).assertExists()
        }
    }

    @Test
    fun galleryOptionsFollowTheSelectedVideoMode() {
        var settings by mutableStateOf(UserPreferences.default())
        setSettingsContent(
            settings = { settings },
            mediaActions = MediaSettingsActions(
                onVideoDisplayModeChange = { settings = settings.copy(videoDisplayMode = it) }
            )
        )

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_MEDIA).performClick()
        composeRule.onNodeWithText("Gallery columns").assertDoesNotExist()
        composeRule.onNodeWithText("Gallery square").performScrollTo().performClick()
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_SCROLL).performScrollToIndex(5)
        composeRule.onNodeWithText("Gallery columns").assertExists()
        composeRule.onNodeWithText("Show video names in gallery").assertExists()

        composeRule.runOnIdle {
            settings = settings.copy(videoDisplayMode = VideoDisplayMode.LIST)
        }
        composeRule.onNodeWithText("Gallery columns").assertDoesNotExist()
    }

    @Test
    fun designScrollPositionSurvivesTabChanges() {
        setSettingsContent()

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_DESIGN).performClick()
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_SCROLL).performScrollToIndex(7)
        composeRule.onNodeWithText("Progress bar height (Full Screen Player)")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_GENERAL).performClick()
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_DESIGN).performClick()
        composeRule.onNodeWithText("Progress bar height (Full Screen Player)").assertIsDisplayed()
    }

    @Test
    fun systemActionsRouteToInjectedCallbacks() {
        val calls = mutableListOf<String>()
        setSettingsContent(
            systemActions = SystemSettingsActions(
                onExportBackup = { calls += "export" },
                onImportBackup = { calls += "import" },
                onShowConsole = { calls += "console" },
                onKillApp = { calls += "kill" },
                onResetDatabase = { calls += "reset" }
            )
        )

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_SYSTEM).performClick()
        listOf(
            "Export" to "export",
            "Import" to "import",
            "Open log console" to "console",
            "Force close app" to "kill",
            "Reset database" to "reset"
        ).forEach { (label, expectedCall) ->
            composeRule.onNodeWithText(label).performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(expectedCall, calls.last()) }
        }
    }

    private fun setSettingsContent(
        settings: () -> UserPreferences = { UserPreferences.default() },
        mediaActions: MediaSettingsActions = MediaSettingsActions(),
        systemActions: SystemSettingsActions = SystemSettingsActions()
    ) {
        composeRule.setContent {
            MaterialTheme {
                SettingsTabs(
                    settings = settings(),
                    generalActions = GeneralSettingsActions(),
                    mediaActions = mediaActions,
                    designActions = DesignSettingsActions(),
                    systemActions = systemActions
                )
            }
        }
    }
}
