@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.Text
import com.avdbuddy.theme.AvdBuddyTheme

@Composable internal fun Modifier.iconButtonSurface(): Modifier = clip(CircleShape)
    .background(AvdBuddyTheme.colors.shelfBackground, CircleShape)
    .border(1.dp, AvdBuddyTheme.colors.shelfBorder, CircleShape)

@Composable fun IconButton(icon: ImageVector, description: String, tag: String = description, enabled: Boolean = true, dangerous: Boolean = false, iconModifier: Modifier = Modifier.size(18.dp), ghost: Boolean = false, onClick: () -> Unit) {
    Tooltip(tooltip = { Text(description) }) {
        org.jetbrains.jewel.ui.component.IconButton(onClick, enabled = enabled,
            modifier = (if (ghost) Modifier else Modifier.iconButtonSurface()).testTag(tag).semantics { contentDescription = description }) { state ->
            val tint = when {
                !enabled -> AvdBuddyTheme.colors.textDisabled
                !dangerous -> AvdBuddyTheme.contentColor
                else -> AvdBuddyTheme.colors.danger.copy(alpha = if (state.isHovered || state.isFocused) 1f else 0.55f)
            }
            Icon(icon, tint = tint, modifier = Modifier.padding(8.dp).then(iconModifier))
        }
    }
}
