package com.avdbuddy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme
import com.composeunstyled.UnstyledButton
import com.avdbuddy.theme.appFocusRing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.sliderStyle

@Composable
fun GhostButton(text: String, enabled: Boolean = true, tag: String = text, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val primary = JewelTheme.sliderStyle.colors.trackFilled
    val shape = RoundedCornerShape(4.dp)
    UnstyledButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.testTag(tag)
            .background(when {
                enabled && pressed -> primary.copy(alpha = 0.16f)
                enabled && hovered -> primary.copy(alpha = 0.08f)
                else -> Color.Transparent
            }, shape)
            .appFocusRing(interaction, shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Label(text, color = if (enabled) primary else AvdBuddyTheme.colors.textDisabled)
    }
}
