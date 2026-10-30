package com.avdbuddy.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import com.composeunstyled.DialogProperties
import com.composeunstyled.DialogScope
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import com.avdbuddy.theme.AvdBuddyTheme

internal val LocalAppDialogScope = staticCompositionLocalOf<DialogScope> {
    error("AppDialogPanel must be inside AppDialog")
}

@Composable
fun <T : Any> AppDialog(
    value: T?,
    onClose: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    var lastValue by remember { mutableStateOf(value) }
    SideEffect { if (value != null) lastValue = value }
    val displayedValue = value ?: lastValue
    val style = AvdBuddyTheme.styles.popupContainer
    UnstyledDialog(
        visible = value != null,
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = dismissOnClickOutside),
        overlay = {
            Scrim(scrimColor = style.colors.shadow, enter = fadeIn(tween(180)), exit = fadeOut(tween(140)))
        },
    ) {
        CompositionLocalProvider(LocalAppDialogScope provides this) {
            displayedValue?.let { content(it) }
        }
    }
}
