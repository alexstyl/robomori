package com.avdbuddy.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.ui.component.styling.PopupContainerColors
import org.jetbrains.jewel.ui.component.styling.PopupContainerStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.theme.simpleListItemStyle
import org.jetbrains.jewel.ui.theme.defaultButtonStyle
import org.jetbrains.jewel.ui.theme.dropdownStyle

/** Native component style APIs stay in Kotlin, independently of token generation. */
object AvdBuddyStyles {
    val defaultButton @Composable get() = JewelTheme.defaultButtonStyle
    val dropdown @Composable get() = JewelTheme.dropdownStyle
    val popupContainer: PopupContainerStyle by lazy {
        PopupContainerStyle.light(
            colors = PopupContainerColors.light(
                background = AvdBuddyColors.popupBackground,
                border = AvdBuddyColors.popupBorder,
                shadow = AvdBuddyColors.popupShadow,
            ),
        )
    }

    val deviceRow: SimpleListItemStyle
        @Composable get() {
            val base = JewelTheme.simpleListItemStyle
            return remember(base) {
                SimpleListItemStyle(base.colors, SimpleListItemMetrics(
                    innerPadding = base.metrics.innerPadding,
                    outerPadding = PaddingValues(0.dp),
                    selectionBackgroundCornerSize = CornerSize(8.dp),
                    iconTextGap = base.metrics.iconTextGap,
                ))
            }
        }
}
