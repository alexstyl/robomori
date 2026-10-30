package com.avdbuddy.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import com.avdbuddy.HostOs
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.ui.ComponentStyling

private val LocalTypography = staticCompositionLocalOf<AvdBuddyTypography> {
    error("Apply AvdBuddyTheme before reading typography")
}

/** App-facing API. Jewel supplies the existing controls and their platform styles. */
object AvdBuddyTheme {
    val colors = AvdBuddyColors
    val typography @Composable get() = LocalTypography.current
    val styles = AvdBuddyStyles

    // Inherit content color from surrounding controls, including selected and disabled states.
    val contentColor @Composable get() = JewelTheme.contentColor

    @Composable
    operator fun invoke(content: @Composable () -> Unit) {
        val typography = rememberAvdBuddyTypography()
        val definition = remember(typography) {
            JewelTheme.lightThemeDefinition(
                colors = GlobalColors.light(
                    panelBackground = colors.panelBackground,
                    text = TextColors.light(
                        normal = colors.text,
                        selected = colors.text,
                        disabled = colors.textDisabled,
                        info = colors.textSecondary,
                        error = colors.danger,
                    ),
                ),
                defaultTextStyle = typography.body,
            )
        }
        val styling = remember {
            ComponentStyling.light(
                popupContainerStyle = styles.popupContainer,
                iconButtonStyle = IconButtonStyle.light(metrics = IconButtonMetrics(
                    cornerSize = CornerSize(50),
                    borderWidth = 1.dp,
                    padding = PaddingValues(0.dp),
                    minSize = DpSize(34.dp, 34.dp),
                )),
            )
        }
        val density = LocalDensity.current.density
        val overscrollFactory = if (HostOs.detect() == HostOs.MAC) {
            remember(density) { MacOverscrollFactory(density) }
        } else {
            null
        }
        CompositionLocalProvider(
            LocalTypography provides typography,
            LocalMacOverscrollFactory provides overscrollFactory,
        ) {
            IntUiTheme(theme = definition, styling = styling, content = content)
        }
    }
}
