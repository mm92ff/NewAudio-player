package com.example.newaudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.lerp
import com.example.newaudio.domain.model.UserPreferences

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private data class ThemeBackground(
    val colorScheme: ColorScheme,
    val gradientColors: List<Color>?
)

private fun buildColorSchemeAndBackground(
    baseColorScheme: ColorScheme,
    primaryColor: String,
    fraction: Float,
    gradientEnabled: Boolean
): ThemeBackground {
    val userPrimaryColor = Color(android.graphics.Color.parseColor(primaryColor))
    val scheme = baseColorScheme.copy(primary = userPrimaryColor)

    if (fraction <= 0f) return ThemeBackground(scheme, gradientColors = null)

    val tintedBackground = lerp(baseColorScheme.background, userPrimaryColor, fraction)
    val tintedSurface = lerp(baseColorScheme.surface, userPrimaryColor, fraction)

    return if (gradientEnabled) {
        ThemeBackground(
            colorScheme = scheme.copy(
                background = Color.Transparent,
                surface = tintedSurface
            ),
            gradientColors = listOf(baseColorScheme.background, tintedBackground)
        )
    } else {
        ThemeBackground(
            colorScheme = scheme.copy(
                background = tintedBackground,
                surface = tintedSurface
            ),
            gradientColors = null
        )
    }
}

internal fun gradientDirectionOffsets(
    direction: UserPreferences.GradientDirection,
    size: Size
): Pair<Offset, Offset> {
    val right = size.width.coerceAtLeast(1f)
    val bottom = size.height.coerceAtLeast(1f)
    val left = 0f
    val top = 0f
    val centerX = right / 2f
    val centerY = bottom / 2f

    return when (direction) {
        UserPreferences.GradientDirection.TOP_TO_BOTTOM ->
            Offset(centerX, top) to Offset(centerX, bottom)
        UserPreferences.GradientDirection.BOTTOM_TO_TOP ->
            Offset(centerX, bottom) to Offset(centerX, top)
        UserPreferences.GradientDirection.LEFT_TO_RIGHT ->
            Offset(left, centerY) to Offset(right, centerY)
        UserPreferences.GradientDirection.RIGHT_TO_LEFT ->
            Offset(right, centerY) to Offset(left, centerY)
        UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT ->
            Offset(left, top) to Offset(right, bottom)
        UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT ->
            Offset(right, bottom) to Offset(left, top)
        UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT ->
            Offset(right, top) to Offset(left, bottom)
        UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT ->
            Offset(left, bottom) to Offset(right, top)
    }
}

@Composable
fun NewAudioTheme(
    userPreferences: UserPreferences,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (userPreferences.theme) {
        UserPreferences.Theme.SYSTEM -> isSystemInDarkTheme()
        UserPreferences.Theme.LIGHT -> false
        UserPreferences.Theme.DARK -> true
    }

    val baseColorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    val themeBackground = try {
        buildColorSchemeAndBackground(
            baseColorScheme = baseColorScheme,
            primaryColor = userPreferences.primaryColor,
            fraction = userPreferences.backgroundTintFraction,
            gradientEnabled = userPreferences.backgroundGradientEnabled
        )
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        ThemeBackground(baseColorScheme, gradientColors = null)
    }

    MaterialTheme(
        colorScheme = themeBackground.colorScheme,
        typography = Typography
    ) {
        val gradientColors = themeBackground.gradientColors
        val direction = userPreferences.backgroundGradientDirection
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (gradientColors != null) {
                        Modifier.drawWithCache {
                            val (start, end) = gradientDirectionOffsets(direction, size)
                            val brush = Brush.linearGradient(
                                colors = gradientColors,
                                start = start,
                                end = end
                            )
                            onDrawBehind { drawRect(brush) }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            content()
        }
    }
}
