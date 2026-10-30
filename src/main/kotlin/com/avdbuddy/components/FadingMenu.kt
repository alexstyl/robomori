package com.avdbuddy.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun FadingMenu(visible: Boolean, content: @Composable (Modifier) -> Unit) {
    val transition = updateTransition(visible, label = "Menu visibility")
    val opacity by transition.animateFloat(
        transitionSpec = { if (targetState) snap() else tween(140) },
        label = "Menu opacity",
    ) { if (it) 1f else 0f }

    if (transition.currentState || transition.targetState) {
        content(Modifier.graphicsLayer { alpha = opacity })
    }
}
