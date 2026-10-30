@file:Suppress("DEPRECATION")
@file:OptIn(org.jetbrains.jewel.foundation.ExperimentalJewelApi::class)

package com.avdbuddy.components

import com.avdbuddy.theme.verticalScroll

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.avdbuddy.iconography.*
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledDropdownMenu
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.DropdownState
import org.jetbrains.jewel.ui.component.Text
import com.avdbuddy.theme.appFocusRing
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.textFieldStyle

@Composable
fun <T> Select(
    value: T,
    options: List<T>,
    onChange: (T) -> Unit,
    label: (T) -> String = { it.toString() },
    tag: String = "Select",
    modifier: Modifier = Modifier.widthIn(min = 130.dp, max = 300.dp),
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(130.dp) }
    val density = LocalDensity.current
    val style = AvdBuddyTheme.styles.dropdown
    val minHeight = maxOf(style.metrics.minSize.height, style.metrics.arrowMinSize.height, JewelTheme.textFieldStyle.metrics.minSize.height)
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val state = DropdownState.of(enabled = enabled, hovered = hovered, pressed = pressed)
    val contentColor by style.colors.contentFor(state)
    val menu = style.menuStyle
    val menuShape = RoundedCornerShape(menu.metrics.cornerSize)

    UnstyledDropdownMenu(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier,
        sideOffset = menu.metrics.offset.y,
        alignmentOffset = menu.metrics.offset.x,
        panel = {
            DropdownMenuPanel(
                Modifier.width(anchorWidth).heightIn(max = 320.dp)
                    .shadow(menu.metrics.shadowSize, menuShape, ambientColor = menu.colors.shadow, spotColor = menu.colors.shadow)
                    .clip(menuShape).background(menu.colors.background)
                    .border(menu.metrics.borderWidth, menu.colors.border, menuShape)
                    .verticalScroll(rememberScrollState()).padding(menu.metrics.contentPadding)
                    .testTag("$tag-Menu"),
                exit = fadeOut(tween(140)),
            ) {
                options.forEachIndexed { index, option ->
                    SelectOption(label(option), option == value, menu, "$tag-Option-$index") { onChange(option) }
                }
            }
        },
        anchor = {
            UnstyledButton(
                enabled = enabled,
                onClick = { expanded = !expanded },
                role = Role.DropdownList,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth()
                    .defaultMinSize(style.metrics.minSize.width, minHeight)
                    .onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }
                    .background(style.colors.backgroundFor(state).value, shape)
                    .border(style.metrics.borderWidth, style.colors.borderFor(state).value, shape)
                    .appFocusRing(interaction, shape).testTag(tag),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(label(value), color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(style.metrics.contentPadding).padding(end = style.metrics.arrowMinSize.width))
                    Box(Modifier.size(style.metrics.arrowMinSize).align(Alignment.CenterEnd), contentAlignment = Alignment.Center) {
                        Icon(Icons.ChevronDown, tint = contentColor, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
    )
}
