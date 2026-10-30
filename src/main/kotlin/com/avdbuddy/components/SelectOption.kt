@file:Suppress("DEPRECATION")

package com.avdbuddy.components

import androidx.compose.runtime.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.Check
import com.composeunstyled.UnstyledDropdownMenuItem
import org.jetbrains.jewel.ui.component.MenuItemState
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.styling.MenuStyle

@Composable
internal fun SelectOption(text: String, selected: Boolean, style: MenuStyle, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val state = MenuItemState.of(selected = selected, enabled = true, focused = focused, hovered = hovered, pressed = pressed)
    val colors = style.colors.itemColors
    val metrics = style.metrics.itemMetrics
    val shape = RoundedCornerShape(metrics.selectionCornerSize)
    UnstyledDropdownMenuItem(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.fillMaxWidth().padding(metrics.outerPadding)
            .clip(shape).background(colors.backgroundFor(state).value)
            .semantics { this.selected = selected }.testTag(tag),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = metrics.minHeight).padding(metrics.contentPadding),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(metrics.iconSize), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Check, tint = colors.iconTintFor(state).value, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(8.dp))
            Text(text, color = colors.contentFor(state).value, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
