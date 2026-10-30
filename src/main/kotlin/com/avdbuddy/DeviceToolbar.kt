package com.avdbuddy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.Layout
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.IconButton
import com.avdbuddy.iconography.*

@Composable internal fun DeviceToolbar(device: DeviceTarget, state: AppState, sendInput: (List<String>) -> Unit,
    rotate: (() -> Unit)? = null, fold: (() -> Unit)? = null, folded: Boolean = false, enabled: Boolean = true) {
    CaptureNoticeAbove(device, state) {
        DeviceToolbarButtons(device, state, sendInput, rotate, fold, folded, enabled)
    }
}

/**
 * Shows the device's latest capture result as a bubble centred above [toolbar], where the capture started.
 * The bubble takes no space, so the device screen above it never moves.
 */
@Composable private fun CaptureNoticeAbove(device: DeviceTarget, state: AppState, toolbar: @Composable () -> Unit) {
    val notice = state.captureNotices[device.name]
    // The last result stays drawn while the bubble animates away.
    var lastNotice by remember(device.name) { mutableStateOf(notice) }
    SideEffect { if (notice != null) lastNotice = notice }
    LaunchedEffect(device.name, notice) {
        if (notice != null) {
            delay(3.seconds)
            state.dismissCaptureNotice(device.name, notice)
        }
    }
    Layout(content = {
        toolbar()
        AnimatedVisibility(notice != null,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { 16 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { 16 }) {
            (notice ?: lastNotice)?.let { shown ->
                CaptureToast(shown.message, shown.file) { shown.file?.let { state.openCapture(device, it) } }
            }
        }
    }) { measurables, constraints ->
        val bar = measurables[0].measure(constraints)
        // The bubble is absent from the layout entirely while there is no result to show.
        val bubble = measurables.getOrNull(1)?.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(bar.width, bar.height) {
            bar.place(0, 0)
            bubble?.place((bar.width - bubble.width) / 2, -bubble.height - 12.dp.roundToPx())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DeviceToolbarButtons(device: DeviceTarget, state: AppState, sendInput: (List<String>) -> Unit,
    rotate: (() -> Unit)?, fold: (() -> Unit)?, folded: Boolean, enabled: Boolean) {
    FlowRow(Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (device.type == DeviceType.TV) {
            TvRemote { sendInput(listOf("keyevent", it)) }
        } else if (device.type == DeviceType.WEAR) {
            IconButton(Icons.WatchCrown, "Press crown", tag = "PressCrown", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_HOME")) }
            IconButton(Icons.WatchSideButton, "Press side button", tag = "PressSideButton", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_STEM_PRIMARY")) }
        } else if (device.type.supportsRotation) {
            IconButton(Icons.AndroidBack, "Back", tag = "PhoneBack", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_BACK")) }
            IconButton(Icons.AndroidHome, "Home", tag = "PhoneHome", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_HOME")) }
            IconButton(Icons.AndroidRecents, "Recents", tag = "PhoneRecents", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_APP_SWITCH")) }
            if (rotate != null) {
                IconButton(Icons.ArrowPath, "Rotate clockwise", tag = "RotateClockwise", enabled = enabled, onClick = rotate)
            }
            if (fold != null) {
                IconButton(if (folded) Icons.DeviceUnfolded else Icons.DeviceFolded, if (folded) "Unfold" else "Fold", tag = "ToggleFold", onClick = fold)
            }
        }
        IconButton(Icons.Power, "Press power button", tag = "DevicePower", enabled = enabled) { sendInput(listOf("keyevent", "KEYCODE_POWER")) }
        val saving = state.savingCaptures[device.name] == true
        IconButton(Icons.ViewfinderCircle, "Take screenshot", tag = "CaptureScreenshot", enabled = enabled && !saving) { state.captureScreenshot(device) }
        RecordingButton(device, state, enabled = enabled && !saving)
    }
}
