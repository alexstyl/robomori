@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.avdbuddy

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.Icon
import com.avdbuddy.components.iconButtonSurface
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.RecordCircle
import com.avdbuddy.iconography.StopCircle
import com.avdbuddy.theme.AvdBuddyTheme
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

@Composable internal fun RecordingButton(device: DeviceTarget, state: AppState, enabled: Boolean) {
    val recording = state.recordingDevices[device.name] == true
    val startedAt = state.recordingStartedAt[device.name]
    var elapsedSeconds by remember(device.name) { mutableLongStateOf(0) }
    LaunchedEffect(startedAt) {
        if (startedAt == null) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        while (true) {
            elapsedSeconds = ((System.nanoTime() - startedAt) / 1_000_000_000L).coerceAtLeast(0)
            delay(250)
        }
    }
    val timer = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
    val description = if (recording) "Stop recording" else "Record video"
    Tooltip(tooltip = { Text(description) }) {
        org.jetbrains.jewel.ui.component.IconButton(
            onClick = { state.toggleRecording(device) }, enabled = enabled,
            modifier = Modifier.animateContentSize(tween(220)).iconButtonSurface().testTag("CaptureRecording").semantics {
                contentDescription = if (recording) "$description, $timer elapsed" else description
            },
        ) {
            val tint = if (enabled) AvdBuddyTheme.contentColor else AvdBuddyTheme.colors.textDisabled
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (recording) Icons.StopCircle else Icons.RecordCircle, tint = tint,
                    modifier = Modifier.padding(8.dp).size(18.dp))
                if (recording) Text(timer, modifier = Modifier.padding(end = 10.dp), color = tint,
                    style = AvdBuddyTheme.typography.body.copy(fontFeatureSettings = "tnum"))
            }
        }
    }
}
