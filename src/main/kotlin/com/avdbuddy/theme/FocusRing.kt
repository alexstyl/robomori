package com.avdbuddy.theme

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.focusRing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.sliderStyle

@Composable
fun Modifier.appFocusRing(interaction: InteractionSource, shape: Shape, offset: Dp = 0.dp): Modifier =
    focusRing(interaction, 2.dp, JewelTheme.sliderStyle.colors.thumbBorderFocused, shape, offset = offset)
