package com.avdbuddy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.IconButton
import com.avdbuddy.iconography.Cog6Tooth
import com.avdbuddy.iconography.Icons
import com.avdbuddy.theme.AvdBuddyTheme

@Composable
internal fun SettingsButton(state: AppState) {
    val active = state.downloads.activeCount
    val failed = state.downloads.tasks.values.count { it.state is AndroidDownloadState.Failed }
    val iconModifier = if (active > 0) {
        val transition = rememberInfiniteTransition(label = "Downloading Android images")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 720f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 2000
                    0f at 0 using FastOutSlowInEasing
                    -22f at 220
                    -22f at 320 using CubicBezierEasing(0.65f, 0f, 0.2f, 1f)
                    734f at 1150 using FastOutSlowInEasing
                    720f at 1370
                    720f at 2000
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "Settings rotation",
        )
        Modifier.graphicsLayer { rotationZ = angle }.testTag("DownloadingSettingsIcon")
    } else Modifier
    val description = when {
        active > 0 -> "Settings ($active active Android downloads). Show platforms"
        failed > 0 -> "Settings ($failed failed Android downloads). Show platforms"
        else -> "Settings"
    }
    Box {
        IconButton(Icons.Cog6Tooth, description, tag = "Settings", iconModifier = iconModifier.size(18.dp), ghost = true) {
            if (active > 0 || failed > 0) state.openSettings(SettingsSection.Images)
            else state.openSettings()
        }
        if (failed > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(5.dp)
                .background(AvdBuddyTheme.colors.danger, CircleShape).testTag("DownloadErrorIndicator"))
        }
    }
}
