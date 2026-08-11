package com.example.newaudio.feature.player

import androidx.compose.ui.unit.dp
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFullscreenOverlayLayoutTest {
    @Test
    fun landscapeFullscreenTimelineIsFlushWithBottomEdge() {
        assertEquals(0.dp, fullscreenTimelineVerticalPadding(VideoSize(1920, 1080)))
    }

    @Test
    fun portraitFullscreenTimelineKeepsExistingOuterPadding() {
        assertEquals(28.dp, fullscreenTimelineVerticalPadding(VideoSize(1080, 1920)))
    }

    @Test
    fun squareFullscreenTimelineKeepsExistingOuterPadding() {
        assertEquals(28.dp, fullscreenTimelineVerticalPadding(VideoSize(1080, 1080)))
    }

    @Test
    fun unknownVideoSizeKeepsExistingOuterPadding() {
        assertEquals(28.dp, fullscreenTimelineVerticalPadding(VideoSize.UNKNOWN))
    }
}
