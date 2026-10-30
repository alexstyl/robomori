package com.avdbuddy.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogPanel
import com.avdbuddy.theme.AvdBuddyTheme

@Composable
fun AppDialogPanel(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = AvdBuddyTheme.styles.popupContainer
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    with(LocalAppDialogScope.current) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            DialogPanel(
                modifier = modifier.clip(shape).background(style.colors.background)
                    .border(style.metrics.borderWidth, style.colors.border, shape)
                    .semantics { dialog() }.testTag(tag),
                paneTitle = title,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.96f),
                exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.98f),
                content = content,
            )
        }
    }
}
