package com.avdbuddy

import com.avdbuddy.components.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import com.avdbuddy.theme.AvdBuddyTheme
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable fun EmulatorPanel(device: Device, state: AppState) {
    val running = device.name in state.running
    val starting = device.name in state.starting && !running
    val stopping = device.name in state.stopping
    val pending = state.pendingCreation?.name == device.name
    if ((starting || stopping) && !pending) {
        Box(Modifier.fillMaxSize().testTag("EmulatorPanel"), contentAlignment = Alignment.Center) {
            org.jetbrains.jewel.ui.component.CircularProgressIndicatorBig(
                Modifier.testTag(if (stopping) "StoppingEmulatorSpinner" else "StartingEmulatorSpinner").semantics {
                    contentDescription = if (stopping) "Shutting down ${device.displayName}" else "Starting ${device.displayName}"
                },
            )
        }
        return
    }
    if (!running && !starting && !pending) {
        InactiveDevicePanel(
            type = device.type,
            name = device.displayName,
            description = "${device.platformTitle} emulator",
            modifier = Modifier.testTag("EmulatorPanel"),
        ) {
            ActionButton("Start", primary = true,
                enabled = !state.busy && device.name !in state.deviceOperations && !device.missingStorage,
                tag = "Launch-${device.name}") { state.launch(device) }
            val unavailableReason = when {
                device.name in state.deviceOperations -> state.deviceOperations[device.name]?.label
                device.missingStorage -> "Storage unavailable"
                state.busy -> "Another emulator operation is in progress."
                else -> null
            }
            if (unavailableReason != null) {
                Spacer(Modifier.height(10.dp))
                DeviceStateMessage(unavailableReason)
            }
        }
        return
    }
    val liveFrame = if (pending) null else state.frames[device.directory.absolutePath]
    val displayError = state.displayErrors[device.name]
    if (pending || displayError != null) {
        Column(
            Modifier.fillMaxSize().padding(30.dp).testTag("EmulatorPanel"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            when {
                pending && state.createError.isNotBlank() -> {
                    DeviceStateMessage(state.createError, modifier = Modifier.testTag("CreateError"))
                    ActionButton("Try again", primary = true, tag = "RetrySetup") { state.retryCreation() }
                    ActionButton("Remove", tag = "RemoveSetup") { state.discardPendingCreation() }
                }
                !pending && displayError != null -> DeviceStateMessage(displayError.message)
                else -> {
                    org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(30.dp))
                    DeviceStateMessage(if (state.createCancelling) "Cancelling…" else "Setting up…",
                        modifier = Modifier.testTag("DeviceSetupStatus"))
                    if (pending) ActionButton("Cancel", enabled = !state.createCancelling, tag = "CancelSetup") { state.cancelCreate() }
                }
            }
        }
        return
    }
    val frameRotation = state.frameRotations[device.directory.absolutePath] ?: 0
    val request = state.rotations[device.name]?.takeIf { it.serial == state.running[device.name] }
    val targetAngle = -(request?.initialRotation ?: frameRotation) * 90f + (request?.turns ?: 0) * 90f
    val rotation = remember(device.name, state.running[device.name]) {
        Animatable(targetAngle)
    }
    LaunchedEffect(rotation, targetAngle, request != null) {
        if (request == null) {
            // A newly connected preview reports its existing orientation, not a user rotation.
            rotation.snapTo(targetAngle)
        } else {
            // Cancellation preserves the current angle. Drop old momentum so a
            // direction change responds immediately instead of continuing the old turn.
            rotation.animateTo(
                targetValue = targetAngle,
                animationSpec = deviceRotationAnimationSpec(),
                initialVelocity = 0f,
            )
        }
    }
    val angle = if (request == null) targetAngle else rotation.value
    val rotating = kotlin.math.abs(angle - targetAngle) > .1f
    val frame = liveFrame
    val focus = remember(device.name) { FocusRequester() }
    val landscape = device.type == DeviceType.TV
    val hardware = remember(device.config) {
        HardwareProfiles.all.firstOrNull { profile ->
            device.config["showDeviceFrame"] != "no" &&
                (device.config["avdbuddy.profile"] == profile.id || device.config["hw.device.name"] == profile.id.replace('-', '_'))
        }
    }
    // Until a foldable's stream starts, show the screen it was last seen on: its config only knows the open one.
    val postureKey = device.directory.absolutePath
    val lastFolded = state.foldedDevices[postureKey]
    val cover = hardware?.folded?.takeIf { frame == null && lastFolded == true }
    val postureUnknown = frame == null && running && hardware?.folded != null &&
        (lastFolded == null || state.frameRotations[postureKey] == null)
    // The touch protocol addresses the unrotated physical framebuffer.
    val nativeSize = frame?.let { nativeScreenSize(it.width, it.height, frameRotation) }
    val width = nativeSize?.first ?: cover?.width ?: device.config["hw.lcd.width"]?.toIntOrNull() ?: if(landscape) 1920 else if(device.type == DeviceType.TABLET) 1600 else 1080
    val height = nativeSize?.second ?: cover?.height ?: device.config["hw.lcd.height"]?.toIntOrNull() ?: if(landscape) 1080 else if(device.type == DeviceType.TABLET) 2560 else if(device.type == DeviceType.WEAR) 1080 else 2400
    // A closed foldable streams its cover screen, so the frame follows whichever screen is showing.
    val folded = hardware?.folded?.let { width == it.width && height == it.height } == true
    // The emulator swaps screens instantly when the hinge moves. Keep the last picture of each screen
    // and play the fold over the top: the open device closes like a book onto its cover.
    val foldProgress = remember(device.name) { Animatable(0f) }
    var shownFolded by remember(device.name) { mutableStateOf<Boolean?>(null) }
    var openPicture by remember(device.name) { mutableStateOf<FoldPicture?>(null) }
    var coverPicture by remember(device.name) { mutableStateOf<FoldPicture?>(null) }
    LaunchedEffect(folded, frame != null) {
        if (frame == null) return@LaunchedEffect
        val previous = shownFolded
        shownFolded = folded
        val target = if (folded) 1f else 0f
        if (previous == null || hardware?.folded == null) foldProgress.snapTo(target)
        else foldProgress.animateTo(target, tween(650, easing = LinearEasing))
    }
    // Also covers the frame between the screens swapping and the animation starting.
    val folding = foldProgress.isRunning ||
        (shownFolded != null && shownFolded != folded && hardware?.folded != null)
    // Keep the last picture of each screen for the animation. Not while it plays: just after the hinge
    // moves the emulator sends a few stretched or sideways frames.
    SideEffect {
        if (frame != null && hardware?.folded != null) state.foldedDevices[postureKey] = folded
        if (frame != null && !folding && shownFolded == folded) {
            if (folded) coverPicture = FoldPicture(frame, frameRotation) else openPicture = FoldPicture(frame, frameRotation)
        }
    }
    val shell = when {
        hardware == null -> null
        folded -> hardware.folded?.frame
        width == hardware.display.width && height == hardware.display.height -> hardware.frame
        else -> null
    }
    val artwork = remember(shell) { shell?.let(::frameArtwork) }
    val frameTint = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }

    Column(Modifier.fillMaxSize().testTag("EmulatorPanel")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal=30.dp, vertical=10.dp), contentAlignment=Alignment.Center) {
            val bezel = 9.dp
            val viewportWidth = maxWidth * device.type.previewViewportFraction
            val viewportHeight = maxHeight * device.type.previewViewportFraction
            // Keep one physical surface; its scale and touch coordinates never
            // change when it turns. The parent transform maps pointer positions.
            val naturalRatio = width.toFloat() / height.coerceAtLeast(1)
            val shellWidth = if (shell == null) null else minOf(viewportWidth, viewportHeight * shell.width / shell.height)
            val screenWidth = if (shell != null && shellWidth != null) shellWidth * width / shell.width else minOf(viewportWidth - bezel*2, (viewportHeight-bezel*2)*naturalRatio).coerceAtLeast(1.dp)
            val screenHeight = screenWidth / naturalRatio
            val outerShape = RoundedCornerShape(if(device.type == DeviceType.WEAR) 100.dp else 34.dp)
            val innerShape = RoundedCornerShape(if (shell != null) screenWidth * shell.cornerRadius / width else if(device.type == DeviceType.WEAR) 100.dp else 26.dp)
            val surface = if (shell != null && shellWidth != null) Modifier.requiredSize(shellWidth, shellWidth * shell.height / shell.width)
                else Modifier.requiredSize(screenWidth+bezel*2,screenHeight+bezel*2)
            Box(surface
                .graphicsLayer {
                    rotationZ = angle
                    if (folding || postureUnknown) alpha = 0f
                }
                .then(if (shell == null) Modifier.background(AvdBuddyTheme.colors.deviceFrame, outerShape).border(2.dp, AvdBuddyTheme.colors.deviceFrameBorder, outerShape).padding(bezel) else Modifier)
                .clipToBounds().testTag("EmulatorBezel")) {
                val screen = if (shell != null && shellWidth != null) Modifier.offset(shellWidth * shell.screenX / shell.width, shellWidth * shell.screenY / shell.width).size(screenWidth, screenHeight) else Modifier.fillMaxSize()
                Box(screen
                    .then(if (artwork != null) Modifier.drawBehind {
                        // Rounding can leave a sub-pixel gap between the screen and the frame's opening;
                        // back it with black so the window never shows through.
                        val bleed = 2.dp.toPx()
                        val radius = innerShape.topStart.toPx(size, this)
                        drawRoundRect(Color.Black, Offset(-bleed, -bleed), Size(size.width + bleed * 2, size.height + bleed * 2),
                            CornerRadius(radius + bleed))
                    } else Modifier)
                    .clip(innerShape).background(AvdBuddyTheme.colors.deviceScreen)
                    .focusRequester(focus).onPreviewKeyEvent { event ->
                        if (!running || rotating || event.type != KeyEventType.KeyDown) false else {
                            val backShortcut = when (HostOs.detect()) {
                                HostOs.MAC -> event.key == Key.LeftBracket && event.isMetaPressed &&
                                    !event.isAltPressed && !event.isCtrlPressed && !event.isShiftPressed
                                HostOs.WINDOWS, HostOs.LINUX -> event.key == Key.DirectionLeft && event.isAltPressed &&
                                    !event.isMetaPressed && !event.isCtrlPressed && !event.isShiftPressed
                            }
                            val pasteShortcut = event.key == Key.V && !event.isAltPressed && !event.isShiftPressed &&
                                when (HostOs.detect()) {
                                    HostOs.MAC -> event.isMetaPressed && !event.isCtrlPressed
                                    HostOs.WINDOWS, HostOs.LINUX -> event.isCtrlPressed && !event.isMetaPressed
                                }
                            if (pasteShortcut) {
                                state.paste(device)
                                true
                            } else {
                                val code = if (backShortcut) "4" else when (event.key) {
                                    Key.Enter -> "66"
                                    Key.Backspace -> "67"
                                    Key.DirectionLeft -> "21"
                                    Key.DirectionRight -> "22"
                                    Key.DirectionUp -> "19"
                                    Key.DirectionDown -> "20"
                                    else -> null
                                }
                                if (code != null) {
                                    state.sendInput(device, listOf("keyevent", code))
                                    true
                                } else {
                                    val character = event.utf16CodePoint
                                    if (!event.isMetaPressed && !event.isCtrlPressed && character in 32..126) {
                                        state.type(device, character.toChar().toString())
                                        true
                                    } else false
                                }
                            }
                        }
                    }.focusable().pointerInput(device.name, running, rotating, width, height) {
                        if(running && !rotating) awaitEachGesture {
                            val down = awaitFirstDown(); focus.requestFocus(); down.consume()
                            var last = screenPoint(down.position.x, down.position.y, size.width, size.height, width, height)
                            state.sendTouch(device, last.first, last.second, true)
                            try {
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    last = screenPoint(change.position.x, change.position.y, size.width, size.height, width, height)
                                    change.consume()
                                    if (change.pressed) state.sendTouch(device, last.first, last.second, true)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                state.sendTouch(device, last.first, last.second, false)
                            }
                        }
                    }.testTag("LiveDeviceScreen")) {
                    if (frame != null) Image(frame, "${device.displayName} screen",
                        Modifier.align(Alignment.Center)
                            .requiredSize(
                                if (frameRotation % 2 == 0) screenWidth else screenHeight,
                                if (frameRotation % 2 == 0) screenHeight else screenWidth,
                            )
                            // Undo the stream's orientation inside the physical surface.
                            .graphicsLayer { rotationZ = frameRotation * 90f },
                        contentScale=ContentScale.FillBounds)

                }
                // One continuous foreground contains the bezel, rounded aperture and camera.
                // Paint it over the screen so screen pixels can never cut into the frame.
                artwork?.let {
                    Image(it, "${hardware?.name} frame", Modifier.matchParentSize(),
                        contentScale = ContentScale.FillBounds, colorFilter = frameTint)
                }
            }
            val closed = hardware?.folded
            val openFrame = hardware?.frame
            if (folding && closed != null && openFrame != null) {
                val openArtwork = remember(openFrame) { frameArtwork(openFrame) }
                val closedArtwork = remember(closed.frame) { frameArtwork(closed.frame) }
                val closing = folded
                FoldScene(
                    progress = foldProgress.value,
                    inner = FoldFace(openFrame, IntSize(hardware.display.width, hardware.display.height), openArtwork,
                        // The open screen's last good picture: its first frames after unfolding are garbled.
                        openPicture),
                    cover = FoldFace(closed.frame, IntSize(closed.width, closed.height), closedArtwork,
                        if (closing) frame?.let { FoldPicture(it, frameRotation) } else coverPicture),
                    tint = frameTint,
                    modifier = Modifier.requiredSize(viewportWidth, viewportHeight).graphicsLayer { rotationZ = angle },
                )
            }
        }
        DeviceToolbar(device, state, sendInput = { state.sendInput(device, it) },
            rotate = { state.rotateCurrent(clockwise = true) },
            fold = if (hardware?.folded != null) ({ state.fold(device, !folded) }) else null,
            folded = folded)
    }
}

private fun frameArtwork(frame: ProfileFrame): ImageBitmap =
    profileResource("${frame.directory}/back.webp").use {
        org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()).toComposeImageBitmap()
    }
