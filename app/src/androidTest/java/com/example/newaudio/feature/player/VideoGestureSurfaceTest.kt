package com.example.newaudio.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.cancel
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.newaudio.ui.NewAudioTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class VideoGestureSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun leftVerticalDragChangesBrightnessAndShowsBrightnessFeedback() {
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }

        composeRule.runOnIdle {
            assertTrue(control.windowBrightness > 0.5f)
            assertEquals(0, control.volumeWriteCount)
            assertEquals(0, nextCount)
            assertEquals(0, previousCount)
        }
    }

    @Test
    fun brightnessFeedbackUsesStableBrightnessTag() {
        composeRule.setContent {
            MaterialTheme {
                VideoGestureFeedbackView(
                    feedback = VideoGestureFeedback(
                        type = VideoGestureFeedbackType.BRIGHTNESS,
                        percent = 75
                    )
                )
            }
        }

        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_BRIGHTNESS)
            .fetchSemanticsNode()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_BRIGHTNESS)
            .fetchSemanticsNode()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
            .assertTextEquals("75%")
    }

    @Test
    fun volumeFeedbackUsesStableVolumeTag() {
        composeRule.setContent {
            MaterialTheme {
                VideoGestureFeedbackView(
                    feedback = VideoGestureFeedback(
                        type = VideoGestureFeedbackType.VOLUME,
                        percent = 60
                    )
                )
            }
        }

        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_VOLUME)
            .fetchSemanticsNode()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_VOLUME)
            .fetchSemanticsNode()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
            .assertTextEquals("60%")
    }

    @Test
    fun zeroVolumeFeedbackUsesMutedIconAndZeroPercent() {
        composeRule.setContent {
            MaterialTheme {
                VideoGestureFeedbackView(
                    feedback = VideoGestureFeedback(
                        type = VideoGestureFeedbackType.VOLUME,
                        percent = 0
                    )
                )
            }
        }

        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_ICON_MUTED)
            .fetchSemanticsNode()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
            .assertTextEquals("0%")
    }

    @Test
    fun rightVerticalDragChangesVolumeWithoutChangingBrightness() {
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.75f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }

        composeRule.runOnIdle {
            assertTrue(control.mediaVolume > 5)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, nextCount)
            assertEquals(0, previousCount)
        }
    }

    @Test
    fun centerLineBelongsToVolumeSide() {
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.50f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }

        composeRule.runOnIdle {
            assertTrue(control.mediaVolume > 5)
            assertEquals(0, control.brightnessWriteCount)
        }
    }

    @Test
    fun dragStartingLeftStaysBrightnessAfterCrossingCenter() {
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            down(Offset(width * 0.25f, height * 0.80f))
            moveTo(Offset(width * 0.75f, height * 0.20f), delayMillis = 250L)
            up()
        }

        composeRule.runOnIdle {
            assertTrue(control.windowBrightness > 0.5f)
            assertEquals(0, control.volumeWriteCount)
        }
    }

    @Test
    fun dragStartingRightStaysVolumeAfterCrossingCenter() {
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            down(Offset(width * 0.75f, height * 0.80f))
            moveTo(Offset(width * 0.25f, height * 0.20f), delayMillis = 250L)
            up()
        }

        composeRule.runOnIdle {
            assertTrue(control.mediaVolume > 5)
            assertEquals(0, control.brightnessWriteCount)
        }
    }

    @Test
    fun landscapeSurfaceStillUsesLeftAndRightHalves() {
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control, widthDp = 400, heightDp = 200)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        composeRule.runOnIdle {
            assertTrue(control.windowBrightness > 0.5f)
            assertEquals(0, control.volumeWriteCount)
        }

        val brightnessWriteCount = control.brightnessWriteCount
        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.75f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        composeRule.runOnIdle {
            assertTrue(control.mediaVolume > 5)
            assertEquals(brightnessWriteCount, control.brightnessWriteCount)
        }
    }

    @Test
    fun horizontalDragNavigatesWithoutChangingBrightnessOrVolume() {
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.80f, height * 0.50f)
            down(start)
            moveTo(Offset(width * 0.10f, start.y), delayMillis = 200L)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, nextCount)
            assertEquals(0, previousCount)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, control.volumeWriteCount)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .assertDoesNotExist()
    }

    @Test
    fun rightHorizontalDragNavigatesToPreviousWithoutVerticalSideEffects() {
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.20f, height * 0.50f)
            down(start)
            moveTo(Offset(width * 0.90f, start.y), delayMillis = 200L)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, nextCount)
            assertEquals(1, previousCount)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, control.volumeWriteCount)
        }
    }

    @Test
    fun shortVerticalMovementDoesNothing() {
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.50f)
            down(start)
            moveTo(Offset(start.x, start.y - 10f), delayMillis = 100L)
            up()
        }

        composeRule.runOnIdle {
            assertEquals(0, nextCount)
            assertEquals(0, previousCount)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, control.volumeWriteCount)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .assertDoesNotExist()
    }

    @Test
    fun canceledVerticalDragClearsFeedbackWithoutNavigating() {
        composeRule.mainClock.autoAdvance = false
        val control = FakeVideoGestureControl()
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            cancel()
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle {
            assertTrue(control.brightnessWriteCount > 0)
            assertEquals(0, nextCount)
            assertEquals(0, previousCount)
            assertEquals(0, control.volumeWriteCount)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .assertDoesNotExist()
    }

    @Test
    fun feedbackRemainsForConfiguredDurationThenDisappears() {
        composeRule.mainClock.autoAdvance = false
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_BRIGHTNESS)
            .fetchSemanticsNode()
        composeRule.mainClock.advanceTimeBy(VIDEO_GESTURE_FEEDBACK_DURATION_MS - 1L)
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .fetchSemanticsNode()
        composeRule.mainClock.advanceTimeBy(2L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .assertDoesNotExist()
    }

    @Test
    fun feedbackPercentUpdatesDuringSameDrag() {
        composeRule.mainClock.autoAdvance = false
        val control = FakeVideoGestureControl()
        setGestureSurface(control = control)

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.80f)
            down(start)
            moveTo(Offset(start.x, height * 0.60f), delayMillis = 100L)
        }
        composeRule.mainClock.advanceTimeByFrame()
        val firstPercent = (control.windowBrightness * 100).roundToInt()
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
            .assertTextEquals("$firstPercent%")

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            moveTo(Offset(width * 0.25f, height * 0.20f), delayMillis = 100L)
        }
        composeRule.mainClock.advanceTimeByFrame()
        val secondPercent = (control.windowBrightness * 100).roundToInt()
        assertTrue(secondPercent > firstPercent)
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK_PERCENT)
            .assertTextEquals("$secondPercent%")

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput { up() }
    }

    @Test
    fun doubleTapInvokesFullscreenCallbackExactlyOnce() {
        val control = FakeVideoGestureControl()
        var doubleTapCount = 0
        setGestureSurface(
            control = control,
            onDoubleTap = { doubleTapCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            doubleClick()
        }

        composeRule.runOnIdle {
            assertEquals(1, doubleTapCount)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, control.volumeWriteCount)
        }
        composeRule.onNodeWithTag(NewAudioTestTags.VIDEO_GESTURE_FEEDBACK)
            .assertDoesNotExist()
    }

    @Test
    fun verticalDragFollowedByDoubleTapDoesNotNavigateTwice() {
        val control = FakeVideoGestureControl()
        var doubleTapCount = 0
        var nextCount = 0
        var previousCount = 0
        setGestureSurface(
            control = control,
            onDoubleTap = { doubleTapCount++ },
            onSwipeNext = { nextCount++ },
            onSwipePrevious = { previousCount++ }
        )

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
            doubleClick()
        }

        composeRule.runOnIdle {
            assertEquals(1, doubleTapCount)
            assertEquals(0, nextCount)
            assertEquals(0, previousCount)
            assertTrue(control.brightnessWriteCount > 0)
            assertEquals(0, control.volumeWriteCount)
        }
    }

    @Test
    fun fullscreenPinchDoesNotChangeBrightnessOrVolume() {
        val control = FakeVideoGestureControl()
        var zoomInCount = 0
        var zoomOutCount = 0
        composeRule.setContent {
            MaterialTheme {
                VideoGestureInputSurface(
                    onDoubleTap = {},
                    onSwipeNext = {},
                    onSwipePrevious = {},
                    testTag = TEST_SURFACE_TAG,
                    controlOverride = control,
                    modifier = Modifier
                        .size(width = 400.dp, height = 200.dp)
                        .pointerInput(Unit) {
                            detectFullscreenPinchResize(
                                onZoomIn = { zoomInCount++ },
                                onZoomOut = { zoomOutCount++ }
                            )
                        }
                )
            }
        }

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            pinch(
                start0 = Offset(width * 0.45f, height * 0.50f),
                end0 = Offset(width * 0.20f, height * 0.50f),
                start1 = Offset(width * 0.55f, height * 0.50f),
                end1 = Offset(width * 0.80f, height * 0.50f),
                durationMillis = 300L
            )
        }

        composeRule.runOnIdle {
            assertEquals(1, zoomInCount)
            assertEquals(0, zoomOutCount)
            assertEquals(0, control.brightnessWriteCount)
            assertEquals(0, control.volumeWriteCount)
        }
    }

    @Test
    fun disposingSurfaceRestoresPreviousWindowBrightness() {
        val control = FakeVideoGestureControl(windowBrightness = -1f)
        val visible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                if (visible.value) {
                    VideoGestureInputSurface(
                        onDoubleTap = {},
                        onSwipeNext = {},
                        onSwipePrevious = {},
                        testTag = TEST_SURFACE_TAG,
                        controlOverride = control,
                        modifier = Modifier.size(width = 200.dp, height = 400.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        composeRule.runOnIdle { visible.value = false }

        composeRule.runOnIdle {
            assertEquals(-1f, control.windowBrightness, 0f)
            assertTrue(control.brightnessWriteCount >= 2)
        }
    }

    @Test
    fun disposingSurfaceDoesNotRestoreUserSelectedMediaVolume() {
        val control = FakeVideoGestureControl(mediaVolume = 5)
        val visible = mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                if (visible.value) {
                    VideoGestureInputSurface(
                        onDoubleTap = {},
                        onSwipeNext = {},
                        onSwipePrevious = {},
                        testTag = TEST_SURFACE_TAG,
                        controlOverride = control,
                        modifier = Modifier.size(width = 200.dp, height = 400.dp)
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TEST_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.75f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        val selectedVolume = control.mediaVolume
        assertTrue(selectedVolume > 5)

        composeRule.runOnIdle { visible.value = false }
        composeRule.runOnIdle {
            assertEquals(selectedVolume, control.mediaVolume)
            assertEquals(1, control.volumeWriteCount)
        }
    }

    @Test
    fun nestedInlineAndFullscreenSurfacesRestoreBrightnessInOrder() {
        val control = FakeVideoGestureControl(windowBrightness = -1f)
        val inlineVisible = mutableStateOf(true)
        val fullscreenVisible = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 200.dp, height = 400.dp)) {
                    if (inlineVisible.value) {
                        VideoGestureInputSurface(
                            onDoubleTap = {},
                            onSwipeNext = {},
                            onSwipePrevious = {},
                            testTag = INLINE_SURFACE_TAG,
                            controlOverride = control
                        )
                    }
                    if (fullscreenVisible.value) {
                        VideoGestureInputSurface(
                            onDoubleTap = {},
                            onSwipeNext = {},
                            onSwipePrevious = {},
                            testTag = FULLSCREEN_SURFACE_TAG,
                            controlOverride = control
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(INLINE_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.75f)
            down(start)
            moveTo(Offset(start.x, height * 0.25f), delayMillis = 200L)
            up()
        }
        val inlineBrightness = control.windowBrightness
        assertTrue(inlineBrightness > DEFAULT_WINDOW_BRIGHTNESS)

        composeRule.runOnIdle { fullscreenVisible.value = true }
        composeRule.onNodeWithTag(FULLSCREEN_SURFACE_TAG).performTouchInput {
            val start = Offset(width * 0.25f, height * 0.25f)
            down(start)
            moveTo(Offset(start.x, height * 0.75f), delayMillis = 200L)
            up()
        }
        assertTrue(control.windowBrightness < inlineBrightness)

        composeRule.runOnIdle { fullscreenVisible.value = false }
        composeRule.runOnIdle {
            assertEquals(inlineBrightness, control.windowBrightness, 0f)
        }

        composeRule.runOnIdle { inlineVisible.value = false }
        composeRule.runOnIdle {
            assertEquals(-1f, control.windowBrightness, 0f)
        }
    }

    private fun setGestureSurface(
        control: FakeVideoGestureControl,
        onDoubleTap: () -> Unit = {},
        onSwipeNext: () -> Unit = {},
        onSwipePrevious: () -> Unit = {},
        widthDp: Int = 200,
        heightDp: Int = 400
    ) {
        composeRule.setContent {
            MaterialTheme {
                VideoGestureInputSurface(
                    onDoubleTap = onDoubleTap,
                    onSwipeNext = onSwipeNext,
                    onSwipePrevious = onSwipePrevious,
                    testTag = TEST_SURFACE_TAG,
                    controlOverride = control,
                    modifier = Modifier.size(width = widthDp.dp, height = heightDp.dp)
                )
            }
        }
    }

    private class FakeVideoGestureControl(
        var windowBrightness: Float = 0.5f,
        var mediaVolume: Int = 5,
        private val maxMediaVolume: Int = 10
    ) : VideoGestureControl {
        var brightnessWriteCount = 0
            private set
        var volumeWriteCount = 0
            private set

        override fun readWindowBrightness(): Float = windowBrightness

        override fun writeWindowBrightness(brightness: Float) {
            windowBrightness = brightness
            brightnessWriteCount++
        }

        override fun readMediaVolume(): Int = mediaVolume

        override fun readMaxMediaVolume(): Int = maxMediaVolume

        override fun writeMediaVolume(volume: Int) {
            mediaVolume = volume
            volumeWriteCount++
        }
    }

    private companion object {
        const val TEST_SURFACE_TAG = "video_gesture_test_surface"
        const val INLINE_SURFACE_TAG = "video_gesture_inline_surface"
        const val FULLSCREEN_SURFACE_TAG = "video_gesture_fullscreen_surface"
    }
}
