package com.example.newaudio.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.example.newaudio.domain.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class GradientDirectionOffsetsTest {

    @Test
    fun `all directions map to the expected non-square root edges`() {
        val size = Size(width = 1080f, height = 2400f)
        val expected = mapOf(
            UserPreferences.GradientDirection.TOP_TO_BOTTOM to
                (Offset(540f, 0f) to Offset(540f, 2400f)),
            UserPreferences.GradientDirection.BOTTOM_TO_TOP to
                (Offset(540f, 2400f) to Offset(540f, 0f)),
            UserPreferences.GradientDirection.LEFT_TO_RIGHT to
                (Offset(0f, 1200f) to Offset(1080f, 1200f)),
            UserPreferences.GradientDirection.RIGHT_TO_LEFT to
                (Offset(1080f, 1200f) to Offset(0f, 1200f)),
            UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT to
                (Offset(0f, 0f) to Offset(1080f, 2400f)),
            UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT to
                (Offset(1080f, 2400f) to Offset(0f, 0f)),
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT to
                (Offset(1080f, 0f) to Offset(0f, 2400f)),
            UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT to
                (Offset(0f, 2400f) to Offset(1080f, 0f))
        )

        expected.forEach { (direction, offsets) ->
            assertEquals(direction.name, offsets, gradientDirectionOffsets(direction, size))
        }
    }

    @Test
    fun `opposite directions swap start and end exactly`() {
        val size = Size(width = 333f, height = 777f)
        val oppositePairs = listOf(
            UserPreferences.GradientDirection.TOP_TO_BOTTOM to
                UserPreferences.GradientDirection.BOTTOM_TO_TOP,
            UserPreferences.GradientDirection.LEFT_TO_RIGHT to
                UserPreferences.GradientDirection.RIGHT_TO_LEFT,
            UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT to
                UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT,
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT to
                UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        )

        oppositePairs.forEach { (forward, reverse) ->
            val forwardOffsets = gradientDirectionOffsets(forward, size)
            val reverseOffsets = gradientDirectionOffsets(reverse, size)
            assertEquals(forwardOffsets.first, reverseOffsets.second)
            assertEquals(forwardOffsets.second, reverseOffsets.first)
        }
    }

    @Test
    fun `zero size is clamped to safe finite offsets`() {
        assertEquals(
            Offset(0.5f, 0f) to Offset(0.5f, 1f),
            gradientDirectionOffsets(
                UserPreferences.GradientDirection.TOP_TO_BOTTOM,
                Size.Zero
            )
        )
    }
}
