package com.example.newaudio.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.domain.model.UserPreferences.VideoDisplayMode
import com.example.newaudio.ui.NewAudioTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val directionExpectations = linkedMapOf(
        UserPreferences.GradientDirection.TOP_TO_BOTTOM to
            (NewAudioTestTags.GRADIENT_DIRECTION_TOP_TO_BOTTOM to "Top to Bottom"),
        UserPreferences.GradientDirection.BOTTOM_TO_TOP to
            (NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_TO_TOP to "Bottom to Top"),
        UserPreferences.GradientDirection.LEFT_TO_RIGHT to
            (NewAudioTestTags.GRADIENT_DIRECTION_LEFT_TO_RIGHT to "Left to Right"),
        UserPreferences.GradientDirection.RIGHT_TO_LEFT to
            (NewAudioTestTags.GRADIENT_DIRECTION_RIGHT_TO_LEFT to "Right to Left"),
        UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT to
            (NewAudioTestTags.GRADIENT_DIRECTION_TOP_LEFT_TO_BOTTOM_RIGHT to
                "Top Left to Bottom Right"),
        UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT to
            (NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_RIGHT_TO_TOP_LEFT to
                "Bottom Right to Top Left"),
        UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT to
            (NewAudioTestTags.GRADIENT_DIRECTION_TOP_RIGHT_TO_BOTTOM_LEFT to
                "Top Right to Bottom Left"),
        UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT to
            (NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_LEFT_TO_TOP_RIGHT to
                "Bottom Left to Top Right")
    )

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
    fun gradientDirectionPickerIsConditionalAndRoutesEveryDirection() {
        var settings by mutableStateOf(UserPreferences.default())
        val selectedDirections = mutableListOf<UserPreferences.GradientDirection>()
        setSettingsContent(
            settings = { settings },
            designActions = DesignSettingsActions(
                onBackgroundGradientDirectionChange = { direction ->
                    selectedDirections += direction
                    settings = settings.copy(backgroundGradientDirection = direction)
                }
            )
        )

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_DESIGN).performClick()
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER).assertDoesNotExist()

        composeRule.runOnIdle {
            settings = settings.copy(backgroundGradientEnabled = true)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER)
            .performScrollTo()
            .assertExists()

        composeRule.onAllNodesWithTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER)
            .assertCountEquals(1)
        directionExpectations.values.forEach { (tag, label) ->
            composeRule.onNodeWithTag(tag)
                .assertExists()
                .assertWidthIsEqualTo(64.dp)
                .assertHeightIsEqualTo(64.dp)
                .assertContentDescriptionEquals(label)
                .assert(
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        Role.RadioButton
                    )
                )
        }
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_CENTER)
            .assertExists()
            .assert(
                SemanticsMatcher("has no click or selection semantics") { node ->
                    !node.config.contains(SemanticsActions.OnClick) &&
                        !node.config.contains(SemanticsProperties.Selected)
                }
            )
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_TOP_TO_BOTTOM)
            .assertIsSelected()

        directionExpectations.forEach { (direction, expectation) ->
            val (tag, label) = expectation
            composeRule.onNodeWithTag(tag).performClick()
            composeRule.runOnIdle { assertEquals(direction, selectedDirections.last()) }
            directionExpectations.forEach { (candidate, candidateExpectation) ->
                val candidateNode = composeRule.onNodeWithTag(candidateExpectation.first)
                if (candidate == direction) {
                    candidateNode.assertIsSelected()
                } else {
                    candidateNode.assertIsNotSelected()
                }
            }
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }

        composeRule.runOnIdle {
            settings = settings.copy(backgroundGradientEnabled = false)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER).assertDoesNotExist()
        assertEquals(
            UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT,
            settings.backgroundGradientDirection
        )
    }

    @Test
    fun gradientDirectionPickerFitsAt320Dp() {
        assertGradientDirectionPickerFits(320.dp)
    }

    @Test
    fun gradientDirectionPickerFitsAt360Dp() {
        assertGradientDirectionPickerFits(360.dp)
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
        designActions: DesignSettingsActions = DesignSettingsActions(),
        systemActions: SystemSettingsActions = SystemSettingsActions(),
        viewportWidth: Dp? = null
    ) {
        composeRule.setContent {
            MaterialTheme {
                val rootModifier = viewportWidth?.let { width ->
                    Modifier.width(width).fillMaxHeight()
                } ?: Modifier.fillMaxSize()
                Box(modifier = rootModifier) {
                    SettingsTabs(
                        settings = settings(),
                        generalActions = GeneralSettingsActions(),
                        mediaActions = mediaActions,
                        designActions = designActions,
                        systemActions = systemActions,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun assertGradientDirectionPickerFits(viewportWidth: Dp) {
        setSettingsContent(
            settings = {
                UserPreferences.default().copy(backgroundGradientEnabled = true)
            },
            viewportWidth = viewportWidth
        )

        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_TAB_DESIGN).performClick()
        composeRule.onNodeWithTag(NewAudioTestTags.SETTINGS_SCROLL).performScrollToIndex(4)
        composeRule.onNodeWithTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER)
            .performScrollTo()
            .assertIsDisplayed()

        directionExpectations.values.forEach { (tag, _) ->
            val node = composeRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = node.getUnclippedBoundsInRoot()
            assertTrue("$tag starts outside the viewport", bounds.left >= 0.dp)
            assertTrue("$tag exceeds $viewportWidth", bounds.right <= viewportWidth)
        }
    }
}
