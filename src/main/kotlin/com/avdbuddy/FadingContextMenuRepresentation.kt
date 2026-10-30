@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.avdbuddy

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition
import com.avdbuddy.components.FadingMenu
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.menuStyle

internal object FadingContextMenuRepresentation : ContextMenuRepresentation {
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val open = state.status as? ContextMenuState.Status.Open
        val currentItems = if (open != null) items() else null
        var lastItems by remember { mutableStateOf(emptyList<ContextMenuItem>()) }
        var lastPosition by remember { mutableStateOf(Offset.Zero) }
        val position = open?.rect?.center ?: lastPosition
        val displayedItems = currentItems ?: lastItems

        LaunchedEffect(open != null) {
            if (open != null && currentItems != null) {
                lastItems = currentItems
                lastPosition = position
            }
        }
        if (open != null && currentItems.isNullOrEmpty()) {
            SideEffect { state.status = ContextMenuState.Status.Closed }
        }

        val style = JewelTheme.menuStyle
        val positionProvider = rememberPopupPositionProviderAtPosition(position, style.metrics.offset)
        FadingMenu(open != null && displayedItems.isNotEmpty()) { fade ->
            PopupMenu(
                onDismissRequest = { state.status = ContextMenuState.Status.Closed; true },
                popupPositionProvider = positionProvider,
                modifier = fade,
            ) {
                displayedItems.forEach { item ->
                    selectableItem(selected = false, enabled = item.enabled, onClick = {
                        state.status = ContextMenuState.Status.Closed
                        item.onClick()
                    }) { Text(item.label) }
                }
            }
        }
    }
}
