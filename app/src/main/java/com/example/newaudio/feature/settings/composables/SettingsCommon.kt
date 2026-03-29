package com.example.newaudio.feature.settings.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.example.newaudio.ui.theme.Dimens

data class SettingsCardStyle(
    val transparent: Boolean = false,
    val borderWidthDp: Float = 0f,
    val borderColor: String = "#9E9E9E"
)

val LocalSettingsCardStyle = compositionLocalOf { SettingsCardStyle() }

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val style = LocalSettingsCardStyle.current
    val parsedBorderColor = remember(style.borderColor) {
        try { Color(style.borderColor.toColorInt()) } catch (e: Exception) { Color.Gray }
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (style.transparent) Color.Transparent else containerColor
        ),
        border = if (style.borderWidthDp > 0f) BorderStroke(style.borderWidthDp.dp, parsedBorderColor) else null,
        content = content
    )
}

@Composable
fun SettingsFilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val style = LocalSettingsCardStyle.current
    val parsedBorderColor = remember(style.borderColor) {
        try { Color(style.borderColor.toColorInt()) } catch (e: Exception) { Color.Gray }
    }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (style.transparent) ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.Transparent
        ) else ButtonDefaults.filledTonalButtonColors(),
        border = if (style.borderWidthDp > 0f) BorderStroke(style.borderWidthDp.dp, parsedBorderColor) else null,
        content = content
    )
}

@Composable
fun ColorCircle(hexColor: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.SettingsScreen_ColorCircleSize)
            .clip(CircleShape)
            .background(Color(hexColor.toColorInt()))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(Dimens.SettingsScreen_ColorCircleCheckSize)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun ThemeOption(selected: Boolean, onClick: () -> Unit, label: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        label()
        RadioButton(selected = selected, onClick = onClick)
    }
}
