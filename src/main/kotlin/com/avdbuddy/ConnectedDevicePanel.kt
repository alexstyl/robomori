package com.avdbuddy

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.*
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.*
import com.avdbuddy.theme.AvdBuddyTheme
import kotlin.math.abs

@Composable fun ConnectedDevicePanel(device: ConnectedDevice, state: AppState) {
    val session = state.connected.sharingSession(device)
    val frame = session?.frame
    val failure = session?.failure
    val keyboardFocus = remember(device.identity) { FocusRequester() }
    DisposableEffect(session) { onDispose { session?.mirror?.releaseKeyboard() } }
    fun input(vararg args: String) {
        if (args.firstOrNull() == "keyevent") args.getOrNull(1)?.let(::scrcpyKeyCode)?.let { session?.mirror?.key(it) }
    }
    val sharing = state.connected.sharing(device)
    if (!sharing || failure != null) {
        val connectionMessage = if (state.connected.canReconnect(device)) null else when (device.connectionState(sharingFailed = failure != null)) {
            DeviceConnectionState.Ready -> null
            DeviceConnectionState.AuthorizationRequired -> "Allow USB debugging on your device to continue."
            DeviceConnectionState.WirelessDisconnected -> "Connect the device to the same Wi-Fi as this computer and then enable Wireless debugging to continue."
            DeviceConnectionState.UsbDisconnected -> "Connect your device over USB and enable USB debugging to resume screen sharing."
        }
        InactiveDevicePanel(
            type = device.type,
            name = device.displayName,
            description = device.manufacturerAndModel,
            modifier = Modifier.testTag("ConnectedDevicePanel"),
        ) {
            key(device.identity) {
                Crossfade(connectionMessage, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                    animationSpec = tween(180), label = "Device connection state") { message ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        // USB devices start sharing on their own as soon as they are ready.
                        if (message == null && device.wireless) {
                            ActionButton("Start Screen Sharing", primary = true, enabled = !state.connected.working) {
                                state.connected.startSharing(device)
                            }
                        } else if (message != null) {
                            DeviceStateMessage(message)
                        }
                    }
                }
            }
            state.connected.actionFor(device).deviceActionMessage?.let { Spacer(Modifier.height(12.dp)); DeviceStateMessage(it) }
        }
        return
    }
    if (frame == null) {
        Box(Modifier.fillMaxSize().testTag("ConnectedDevicePanel"), contentAlignment = Alignment.Center) {
            org.jetbrains.jewel.ui.component.CircularProgressIndicatorBig(
                Modifier.testTag("StartingScreenSharingSpinner").semantics {
                    contentDescription = "Starting screen sharing for ${device.displayName}"
                },
            )
        }
        return
    }
    val frameRotation = if (frame.width > frame.height) 1 else 0
    val targetAngle = frameRotation * 90f
    val rotation = remember(device.identity, session) { Animatable(targetAngle) }
    LaunchedEffect(rotation, targetAngle) {
        rotation.animateTo(targetAngle, deviceRotationAnimationSpec(), initialVelocity = 0f)
    }
    val rotating by remember(rotation, targetAngle) {
        derivedStateOf { rotation.isRunning || abs(rotation.value - targetAngle) > .1f }
    }
    Column(Modifier.fillMaxSize().testTag("ConnectedDevicePanel"), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 30.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            val picture = frame
            val (nativeWidth, nativeHeight) = nativeScreenSize(picture.width, picture.height, frameRotation)
            val ratio = nativeWidth.toFloat() / nativeHeight
            val viewportWidth = maxWidth * device.type.previewViewportFraction
            val viewportHeight = maxHeight * device.type.previewViewportFraction
            val width = minOf(viewportWidth - 18.dp, (viewportHeight - 18.dp) * ratio).coerceAtLeast(1.dp)
            val height = width / ratio
            val corner = when (device.type) { DeviceType.WEAR -> 100.dp; DeviceType.TV -> 8.dp; else -> 16.dp }
            Box(Modifier.requiredSize(width + 18.dp, height + 18.dp).testTag("ConnectedDeviceFrame")
                .graphicsLayer { rotationZ = rotation.value }
                .background(AvdBuddyTheme.colors.deviceFrame, RoundedCornerShape(corner))
                .border(2.dp, AvdBuddyTheme.colors.deviceFrameBorder, RoundedCornerShape(corner)).padding(9.dp)) {
                Image(picture, "${device.displayName} screen", Modifier.align(Alignment.Center)
                    .requiredSize(if (frameRotation == 0) width else height, if (frameRotation == 0) height else width)
                    .graphicsLayer { rotationZ = -targetAngle }
                    .clip(RoundedCornerShape(if (device.type == DeviceType.WEAR) corner - 8.dp else 6.dp))
                    .focusRequester(keyboardFocus)
                    .onFocusChanged { if (!it.isFocused) session.mirror.releaseKeyboard() }
                    .onPreviewKeyEvent { event ->
                        val native = event.awtEventOrNull
                        val usage = native?.let { physicalKeyUsage(it.keyCode, it.keyLocation) }
                        if (event.isMetaPressed) {
                            session.mirror.releaseKeyboard()
                            false
                        } else if (usage == null || rotating || !device.available ||
                            event.type !in listOf(KeyEventType.KeyDown, KeyEventType.KeyUp)) false else {
                            session.mirror.keyboardKey(usage, event.type == KeyEventType.KeyDown)
                            true
                        }
                    }.focusable()
                    .pointerInput(device.serial, picture.width, picture.height, rotating) {
                        if (!rotating) awaitEachGesture {
                            val down = awaitFirstDown()
                            keyboardFocus.requestFocus()
                            var last = down.position
                            fun send(action: Int) = session.mirror.touch(action,
                                (last.x / size.width * picture.width).toInt(),
                                (last.y / size.height * picture.height).toInt(), picture.width, picture.height)
                            send(0)
                            down.consume()
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    last = change.position
                                    if (change.pressed) send(2)
                                    change.consume()
                                } while (event.changes.any { it.pressed })
                            } finally { send(1) }
                        }
                    }, contentScale = ContentScale.FillBounds)
            }
        }
        DeviceToolbar(device, state, sendInput = { input(*it.toTypedArray()) }, enabled = device.available)
    }
}
