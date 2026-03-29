package com.example.newaudio.ui.theme

import androidx.compose.foundation.background
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

private fun buildColorSchemeAndBrush(
    baseColorScheme: ColorScheme,
    primaryColor: String,
    fraction: Float,
    gradientEnabled: Boolean
): Pair<ColorScheme, Brush?> {
    val userPrimaryColor = Color(android.graphics.Color.parseColor(primaryColor))
    val scheme = baseColorScheme.copy(primary = userPrimaryColor)

    if (fraction <= 0f) return scheme to null

    val tintedBackground = lerp(baseColorScheme.background, userPrimaryColor, fraction)
    val tintedSurface = lerp(baseColorScheme.surface, userPrimaryColor, fraction)

    return if (gradientEnabled) {
        scheme.copy(
            background = Color.Transparent,
            surface = tintedSurface
        ) to Brush.verticalGradient(listOf(baseColorScheme.background, tintedBackground))
    } else {
        scheme.copy(
            background = tintedBackground,
            surface = tintedSurface
        ) to null
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

    val (colorScheme, backgroundBrush) = try {
        buildColorSchemeAndBrush(
            baseColorScheme = baseColorScheme,
            primaryColor = userPreferences.primaryColor,
            fraction = userPreferences.backgroundTintFraction,
            gradientEnabled = userPreferences.backgroundGradientEnabled
        )
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
        baseColorScheme to null
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (backgroundBrush != null) Modifier.background(backgroundBrush)
                    else Modifier
                )
        ) {
            content()
        }
    }
}
