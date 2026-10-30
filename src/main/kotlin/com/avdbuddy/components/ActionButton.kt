package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import androidx.compose.ui.graphics.SolidColor
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.styling.ButtonColors
import org.jetbrains.jewel.ui.component.styling.ButtonStyle

@Composable fun ActionButton(text: String, primary: Boolean = false, enabled: Boolean = true, tag: String = text, dangerous: Boolean = false, onClick: () -> Unit) {
    if (primary || dangerous) DefaultButton(onClick, Modifier.testTag(tag), enabled = enabled,
        style = if (dangerous) dangerousButtonStyle() else AvdBuddyTheme.styles.defaultButton) { Text(text) }
    else OutlinedButton(onClick, Modifier.testTag(tag), enabled = enabled) { Text(text) }
}

@Composable private fun dangerousButtonStyle(): ButtonStyle {
    val base = AvdBuddyTheme.styles.defaultButton
    val colors = base.colors
    val dangerous = SolidColor(AvdBuddyTheme.colors.danger)
    return ButtonStyle(
        colors = ButtonColors(
            background = dangerous,
            backgroundDisabled = colors.backgroundDisabled,
            backgroundFocused = dangerous,
            backgroundPressed = dangerous,
            backgroundHovered = dangerous,
            content = colors.content,
            contentDisabled = colors.contentDisabled,
            contentFocused = colors.contentFocused,
            contentPressed = colors.contentPressed,
            contentHovered = colors.contentHovered,
            border = dangerous,
            borderDisabled = colors.borderDisabled,
            borderFocused = dangerous,
            borderPressed = dangerous,
            borderHovered = dangerous,
        ),
        metrics = base.metrics,
        focusOutlineAlignment = base.focusOutlineAlignment,
    )
}
