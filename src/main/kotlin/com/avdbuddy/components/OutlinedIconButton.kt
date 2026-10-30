@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.avdbuddy.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.theme.outlinedButtonStyle

@Composable
fun OutlinedIconButton(icon: ImageVector, description: String, tag: String = description,
    enabled: Boolean = true, onClick: () -> Unit) {
    val base = JewelTheme.outlinedButtonStyle
    val metrics = base.metrics
    val style = ButtonStyle(base.colors, ButtonMetrics(
        cornerSize = CornerSize(50),
        padding = PaddingValues(8.dp),
        minSize = DpSize(34.dp, 34.dp),
        borderWidth = metrics.borderWidth,
        focusOutlineExpand = metrics.focusOutlineExpand,
    ), base.focusOutlineAlignment)
    Tooltip(tooltip = { Text(description) }) {
        OutlinedButton(onClick, enabled = enabled, style = style,
            modifier = Modifier.testTag(tag).semantics { contentDescription = description }) {
            Icon(icon, tint = org.jetbrains.jewel.foundation.theme.LocalContentColor.current)
        }
    }
}
