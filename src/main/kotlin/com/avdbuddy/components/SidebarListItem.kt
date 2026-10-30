package com.avdbuddy.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.theme.simpleListItemStyle

@Composable
fun SidebarListItem(
    selected: Boolean,
    height: Dp = Dp.Unspecified,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
    content: @Composable () -> Unit,
) {
    val base = JewelTheme.simpleListItemStyle
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val style = remember(base, hovered, contentPadding) {
        val colors = base.colors
        val hoverColors = if (hovered) SimpleListItemColors(
            background = colors.backgroundSelected,
            backgroundActive = colors.backgroundSelected,
            backgroundSelected = colors.backgroundSelected,
            backgroundSelectedActive = colors.backgroundSelectedActive,
            content = colors.content,
            contentActive = colors.contentActive,
            contentSelected = colors.contentSelected,
            contentSelectedActive = colors.contentSelectedActive,
        ) else colors
        SimpleListItemStyle(hoverColors, SimpleListItemMetrics(
            innerPadding = contentPadding,
            outerPadding = PaddingValues(0.dp),
            selectionBackgroundCornerSize = CornerSize(8.dp),
            iconTextGap = base.metrics.iconTextGap,
        ))
    }
    SimpleListItem(selected = selected, height = height, modifier = modifier.hoverable(interactionSource), style = style, content = content)
}
