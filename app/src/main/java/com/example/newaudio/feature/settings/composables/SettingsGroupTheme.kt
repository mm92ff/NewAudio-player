package com.example.newaudio.feature.settings.composables

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.example.newaudio.R
import com.example.newaudio.domain.model.UserPreferences
import com.example.newaudio.ui.NewAudioTestTags
import com.example.newaudio.ui.theme.Dimens
import com.example.newaudio.util.Constants

@Composable
fun ThemeSetting(
    selectedTheme: UserPreferences.Theme,
    onThemeSelected: (UserPreferences.Theme) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.theme),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        SettingsCard {
            Column {
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.LIGHT,
                    onClick = { onThemeSelected(UserPreferences.Theme.LIGHT) }
                ) {
                    Text(stringResource(R.string.theme_light), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                    thickness = Dimens.SettingsScreen_DividerThickness
                )
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.DARK,
                    onClick = { onThemeSelected(UserPreferences.Theme.DARK) }
                ) {
                    Text(stringResource(R.string.theme_dark), modifier = Modifier.weight(1f))
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Dimens.PaddingMedium),
                    thickness = Dimens.SettingsScreen_DividerThickness
                )
                ThemeOption(
                    selected = selectedTheme == UserPreferences.Theme.SYSTEM,
                    onClick = { onThemeSelected(UserPreferences.Theme.SYSTEM) }
                ) {
                    Text(stringResource(R.string.theme_system), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSetting(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.primary_color),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.SettingsScreen_RowSpacing)
        ) {
            Constants.ThemeColors.extendedColorOptions.forEach { hex ->
                ColorCircle(
                    hexColor = hex,
                    isSelected = selectedColor == hex,
                    onClick = { onColorSelected(hex) }
                )
            }
        }
    }
}

@Composable
fun BackgroundTintSetting(
    tintFraction: Float,
    onTintFractionChange: (Float) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.background_tint_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.SettingsScreen_RowSpacing))
        SettingsCard {
            Column(modifier = Modifier.padding(Dimens.PaddingMedium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.background_tint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(tintFraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Slider(
                    value = tintFraction,
                    onValueChange = onTintFractionChange,
                    valueRange = 0f..0.30f,
                    steps = 29
                )
            }
        }
    }
}

@Composable
fun TransparentListItemsSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.transparent_list_items_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.transparent_list_items_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
fun BackgroundGradientSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.PaddingMedium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.background_gradient_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(R.string.background_gradient_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}

@Composable
fun BackgroundGradientDirectionSetting(
    selectedDirection: UserPreferences.GradientDirection,
    onDirectionSelected: (UserPreferences.GradientDirection) -> Unit
) {
    SettingsCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NewAudioTestTags.GRADIENT_DIRECTION_PICKER)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(GradientDirectionCardPadding)
        ) {
            Text(
                text = stringResource(R.string.background_gradient_direction_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(GradientDirectionButtonGap))
            GradientDirectionPicker(
                selectedDirection = selectedDirection,
                onDirectionSelected = onDirectionSelected
            )
            Spacer(modifier = Modifier.height(GradientDirectionButtonGap))
            Text(
                text = stringResource(selectedDirection.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GradientDirectionPicker(
    selectedDirection: UserPreferences.GradientDirection,
    onDirectionSelected: (UserPreferences.GradientDirection) -> Unit
) {
    val grid = listOf(
        listOf(
            UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT,
            UserPreferences.GradientDirection.BOTTOM_TO_TOP,
            UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT
        ),
        listOf(
            UserPreferences.GradientDirection.RIGHT_TO_LEFT,
            null,
            UserPreferences.GradientDirection.LEFT_TO_RIGHT
        ),
        listOf(
            UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT,
            UserPreferences.GradientDirection.TOP_TO_BOTTOM,
            UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GradientDirectionButtonGap)
    ) {
        grid.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(GradientDirectionButtonGap)) {
                row.forEach { direction ->
                    if (direction == null) {
                        Box(
                            modifier = Modifier
                                .size(GradientDirectionButtonSize)
                                .testTag(NewAudioTestTags.GRADIENT_DIRECTION_CENTER)
                        )
                    } else {
                        GradientDirectionButton(
                            direction = direction,
                            selected = selectedDirection == direction,
                            onClick = { onDirectionSelected(direction) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradientDirectionButton(
    direction: UserPreferences.GradientDirection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(GradientDirectionButtonCornerRadius)
    val label = stringResource(direction.labelRes)
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        label = "gradient_direction_background"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
        },
        label = "gradient_direction_border"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        label = "gradient_direction_icon"
    )

    Box(
        modifier = Modifier
            .size(GradientDirectionButtonSize)
            .testTag(direction.testTag)
            .clip(shape)
            .background(backgroundColor)
            .border(GradientDirectionButtonBorderWidth, borderColor, shape)
            .semantics { contentDescription = label }
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        contentAlignment = Alignment.Center
    ) {
        DirectionArrowIcon(
            rotationDegrees = direction.rotationDegrees,
            color = iconColor
        )
    }
}

@Composable
private fun DirectionArrowIcon(
    rotationDegrees: Float,
    color: Color
) {
    Canvas(modifier = Modifier.size(GradientDirectionArrowSize)) {
        val strokeWidth = GradientDirectionArrowStrokeWidth.toPx()
        val centerX = size.width / 2f
        val start = Offset(centerX, size.height * 0.78f)
        val end = Offset(centerX, size.height * 0.22f)
        val headSize = size.width * 0.22f

        rotate(degrees = rotationDegrees) {
            drawLine(color, start, end, strokeWidth, StrokeCap.Round)
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x - headSize, end.y + headSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = end,
                end = Offset(end.x + headSize, end.y + headSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private val UserPreferences.GradientDirection.labelRes: Int
    @StringRes
    get() = when (this) {
        UserPreferences.GradientDirection.TOP_TO_BOTTOM -> R.string.background_gradient_direction_top_to_bottom
        UserPreferences.GradientDirection.BOTTOM_TO_TOP -> R.string.background_gradient_direction_bottom_to_top
        UserPreferences.GradientDirection.LEFT_TO_RIGHT -> R.string.background_gradient_direction_left_to_right
        UserPreferences.GradientDirection.RIGHT_TO_LEFT -> R.string.background_gradient_direction_right_to_left
        UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT ->
            R.string.background_gradient_direction_top_left_to_bottom_right
        UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT ->
            R.string.background_gradient_direction_bottom_right_to_top_left
        UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT ->
            R.string.background_gradient_direction_top_right_to_bottom_left
        UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT ->
            R.string.background_gradient_direction_bottom_left_to_top_right
    }

private val UserPreferences.GradientDirection.rotationDegrees: Float
    get() = when (this) {
        UserPreferences.GradientDirection.BOTTOM_TO_TOP -> 0f
        UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> 45f
        UserPreferences.GradientDirection.LEFT_TO_RIGHT -> 90f
        UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> 135f
        UserPreferences.GradientDirection.TOP_TO_BOTTOM -> 180f
        UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT -> -135f
        UserPreferences.GradientDirection.RIGHT_TO_LEFT -> -90f
        UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT -> -45f
    }

private val UserPreferences.GradientDirection.testTag: String
    get() = when (this) {
        UserPreferences.GradientDirection.TOP_TO_BOTTOM -> NewAudioTestTags.GRADIENT_DIRECTION_TOP_TO_BOTTOM
        UserPreferences.GradientDirection.BOTTOM_TO_TOP -> NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_TO_TOP
        UserPreferences.GradientDirection.LEFT_TO_RIGHT -> NewAudioTestTags.GRADIENT_DIRECTION_LEFT_TO_RIGHT
        UserPreferences.GradientDirection.RIGHT_TO_LEFT -> NewAudioTestTags.GRADIENT_DIRECTION_RIGHT_TO_LEFT
        UserPreferences.GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT ->
            NewAudioTestTags.GRADIENT_DIRECTION_TOP_LEFT_TO_BOTTOM_RIGHT
        UserPreferences.GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT ->
            NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_RIGHT_TO_TOP_LEFT
        UserPreferences.GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT ->
            NewAudioTestTags.GRADIENT_DIRECTION_TOP_RIGHT_TO_BOTTOM_LEFT
        UserPreferences.GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT ->
            NewAudioTestTags.GRADIENT_DIRECTION_BOTTOM_LEFT_TO_TOP_RIGHT
    }

private val GradientDirectionButtonSize = 64.dp
private val GradientDirectionButtonGap = 8.dp
private val GradientDirectionArrowSize = 30.dp
private val GradientDirectionArrowStrokeWidth = 2.4.dp
private val GradientDirectionButtonCornerRadius = 8.dp
private val GradientDirectionButtonBorderWidth = 2.dp
private val GradientDirectionCardPadding = 12.dp
