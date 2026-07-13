package com.example.newaudio.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.newaudio.R
import com.example.newaudio.domain.model.VideoMarker
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoMarkerAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun markerHas48DpTargetAndAccessibleMoveActions() {
        val marker = VideoMarker(
            id = 7L,
            videoPath = "/video/clip.mp4",
            filename = "clip.mp4",
            fileSize = 100L,
            durationMs = 20_000L,
            positionMs = 5_000L,
            createdAt = 1L,
            updatedAt = 1L
        )
        val moves = mutableListOf<Pair<Long, Long>>()
        composeRule.setContent {
            MaterialTheme {
                VideoMarkerTicks(
                    markers = persistentListOf(marker),
                    durationMs = 20_000L,
                    onMoveMarker = { id, position -> moves += id to position },
                    modifier = Modifier
                )
            }
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markerDescription = context.getString(R.string.video_marker_at, "0:05")
        val earlierLabel = context.getString(R.string.video_marker_move_earlier)
        val node = composeRule.onNodeWithContentDescription(markerDescription)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        val actions = node.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        val action = actions.single { it.label == earlierLabel }
        composeRule.runOnIdle { assertTrue(action.action()) }

        assertEquals(listOf(7L to 0L), moves)
    }
}
