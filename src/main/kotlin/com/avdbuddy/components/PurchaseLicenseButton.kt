package com.avdbuddy.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.avdbuddy.iconography.Gift
import com.avdbuddy.iconography.Icons
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonMetrics
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.defaultButtonStyle

@Composable
internal fun PurchaseLicenseButton(onClick: () -> Unit) {
    val base = JewelTheme.defaultButtonStyle
    val style = remember(base) {
        val colors = listOf(Color(0xFFDC2626), Color(0xFFE11D48), Color(0xFFF97316))
        val gradient = Brush.horizontalGradient(colors)
        val hovered = Brush.horizontalGradient(colors.map { lerp(it, Color.White, .08f) })
        val pressed = Brush.horizontalGradient(colors.map { lerp(it, Color.Black, .12f) })
        ButtonStyle(
            colors = ButtonColors(
                background = gradient,
                backgroundDisabled = base.colors.backgroundDisabled,
                backgroundFocused = gradient,
                backgroundPressed = pressed,
                backgroundHovered = hovered,
                content = Color.White,
                contentDisabled = base.colors.contentDisabled,
                contentFocused = Color.White,
                contentPressed = Color.White,
                contentHovered = Color.White,
                border = gradient,
                borderDisabled = base.colors.borderDisabled,
                borderFocused = base.colors.borderFocused,
                borderPressed = pressed,
                borderHovered = hovered,
            ),
            metrics = ButtonMetrics(
                cornerSize = CornerSize(8.dp),
                padding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                minSize = DpSize(0.dp, 28.dp),
                borderWidth = base.metrics.borderWidth,
                focusOutlineExpand = base.metrics.focusOutlineExpand,
            ),
            focusOutlineAlignment = base.focusOutlineAlignment,
        )
    }
    DefaultButton(onClick, Modifier.testTag("BuyLicense"), style = style) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Gift, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Buy a license", fontWeight = FontWeight.Medium)
        }
    }
}
