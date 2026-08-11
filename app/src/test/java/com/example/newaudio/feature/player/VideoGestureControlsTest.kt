package com.example.newaudio.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoGestureControlsTest {
    @Test
    fun verticalDragStartingLeftOfCenterControlsBrightnessRegardlessOfY() {
        listOf(0f, 499.99f).forEach { startX ->
            assertEquals(
                VideoDragMode.BRIGHTNESS,
                resolveVideoDragMode(
                    currentMode = VideoDragMode.UNDECIDED,
                    startX = startX,
                    surfaceWidth = 1_000f,
                    totalDragX = 1f,
                    totalDragY = -30f
                )
            )
        }
    }

    @Test
    fun verticalDragStartingAtOrRightOfCenterControlsVolume() {
        listOf(500f, 750f, 1_000f).forEach { startX ->
            assertEquals(
                VideoDragMode.VOLUME,
                resolveVideoDragMode(
                    currentMode = VideoDragMode.UNDECIDED,
                    startX = startX,
                    surfaceWidth = 1_000f,
                    totalDragX = 1f,
                    totalDragY = 30f
                )
            )
        }
    }

    @Test
    fun movementBelowDirectionThresholdRemainsUndecided() {
        assertEquals(
            VideoDragMode.UNDECIDED,
            resolveVideoDragMode(
                currentMode = VideoDragMode.UNDECIDED,
                startX = 100f,
                surfaceWidth = 1_000f,
                totalDragX = 23.99f,
                totalDragY = 0f
            )
        )
    }

    @Test
    fun dominantHorizontalMovementWinsOnEitherHalf() {
        listOf(100f, 900f).forEach { startX ->
            assertEquals(
                VideoDragMode.HORIZONTAL,
                resolveVideoDragMode(
                    currentMode = VideoDragMode.UNDECIDED,
                    startX = startX,
                    surfaceWidth = 1_000f,
                    totalDragX = 40f,
                    totalDragY = 30f
                )
            )
        }
    }

    @Test
    fun equalAxisMovementUsesVerticalSideControl() {
        assertEquals(
            VideoDragMode.BRIGHTNESS,
            resolveVideoDragMode(
                currentMode = VideoDragMode.UNDECIDED,
                startX = 100f,
                surfaceWidth = 1_000f,
                totalDragX = 30f,
                totalDragY = 30f
            )
        )
    }

    @Test
    fun lockedModeDoesNotChangeWhenFingerCrossesCenterOrDirectionChanges() {
        assertEquals(
            VideoDragMode.BRIGHTNESS,
            resolveVideoDragMode(
                currentMode = VideoDragMode.BRIGHTNESS,
                startX = 900f,
                surfaceWidth = 1_000f,
                totalDragX = 500f,
                totalDragY = 1f
            )
        )
    }

    @Test
    fun invalidSurfaceWidthCannotLockMode() {
        assertEquals(
            VideoDragMode.UNDECIDED,
            resolveVideoDragMode(
                currentMode = VideoDragMode.UNDECIDED,
                startX = 0f,
                surfaceWidth = 0f,
                totalDragX = 100f,
                totalDragY = 100f
            )
        )
    }

    @Test
    fun brightnessMovesUpAndDownAndClampsToSafeRange() {
        assertEquals(0.75f, calculateVideoBrightness(0.5f, -200f, 1_000f), 0.0001f)
        assertEquals(0.25f, calculateVideoBrightness(0.5f, 200f, 1_000f), 0.0001f)
        assertEquals(MIN_WINDOW_BRIGHTNESS, calculateVideoBrightness(0.1f, 10_000f, 1_000f))
        assertEquals(MAX_WINDOW_BRIGHTNESS, calculateVideoBrightness(0.9f, -10_000f, 1_000f))
    }

    @Test
    fun brightnessWithInvalidHeightKeepsClampedStart() {
        assertEquals(0.5f, calculateVideoBrightness(0.5f, -200f, 0f))
        assertEquals(MIN_WINDOW_BRIGHTNESS, calculateVideoBrightness(-1f, -200f, 0f))
    }

    @Test
    fun unknownWindowBrightnessUsesDefault() {
        assertEquals(DEFAULT_WINDOW_BRIGHTNESS, normalizeWindowBrightness(-1f))
        assertEquals(MIN_WINDOW_BRIGHTNESS, normalizeWindowBrightness(0f))
        assertEquals(MAX_WINDOW_BRIGHTNESS, normalizeWindowBrightness(2f))
    }

    @Test
    fun volumeMovesUpAndDownAndClampsToStreamRange() {
        assertEquals(8, calculateVideoVolume(5, -200f, 1_000f, 10))
        assertEquals(2, calculateVideoVolume(5, 200f, 1_000f, 10))
        assertEquals(0, calculateVideoVolume(1, 10_000f, 1_000f, 10))
        assertEquals(10, calculateVideoVolume(9, -10_000f, 1_000f, 10))
    }

    @Test
    fun invalidVolumeInputsAreSafe() {
        assertEquals(0, calculateVideoVolume(5, -200f, 1_000f, 0))
        assertEquals(1, calculateVideoVolume(0, -500f, 1_000f, 1))
        assertEquals(0, calculateVideoVolume(1, 500f, 1_000f, 1))
        assertEquals(5, calculateVideoVolume(5, -200f, 0f, 10))
    }

    @Test
    fun horizontalSwipeUsesInclusiveThresholds() {
        assertEquals(VideoSwipeAction.NEXT, resolveVideoSwipeAction(-96f))
        assertEquals(VideoSwipeAction.PREVIOUS, resolveVideoSwipeAction(96f))
        assertEquals(VideoSwipeAction.NONE, resolveVideoSwipeAction(-95.99f))
        assertEquals(VideoSwipeAction.NONE, resolveVideoSwipeAction(95.99f))
    }
}
