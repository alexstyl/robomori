@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package com.avdbuddy

import java.io.File
import com.avdbuddy.components.*

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import com.avdbuddy.theme.AvdBuddyTheme
import com.avdbuddy.theme.LazyColumn
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import com.avdbuddy.iconography.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import avdbuddy.generated.resources.Res
import avdbuddy.generated.resources.app_icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.*
import dev.nucleusframework.aot.runtime.AotRuntime
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.compose.resources.painterResource
import com.composeunstyled.UnstyledDrawerState
import com.composeunstyled.DialogHost
import com.composeunstyled.FocusVisibilityProvider

fun main(args: Array<String>) {
    if ("--diagnostics" in args) {
        println(AndroidService(SettingsStore().load()).diagnostics())
        return
    }
    if (HostOs.detect() == HostOs.MAC) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        System.setProperty("apple.awt.application.name", APP_NAME)
        System.setProperty("apple.awt.application.appearance", "NSAppearanceNameAqua")
    }
    application {
        if (AotRuntime.isTraining()) {
            // Packaging records startup in a training run, which must exit on its own once the app has settled.
            LaunchedEffect(Unit) { delay(20_000); exitApplication() }
        }
        val scope = rememberCoroutineScope()
        val state = remember {
            AppState(scope, catalogCache = File(HostPlatform().configDirectory, "sdk-catalog.txt"))
                .also { it.keepCatalogFresh() }
        }
        val drawerState = rememberAppSidebarState()
        var alwaysOnTop by remember { mutableStateOf(false) }
        val windowState = rememberWindowState(width = 1040.dp, height = 720.dp)
        val focusMode = remember(windowState) { EmulatorFocusState(windowState) }
        val focusedEmulator = focusMode.active
        val windowIcon = if (HostOs.detect() == HostOs.MAC) null else painterResource(Res.drawable.app_icon)
        Window(
            onCloseRequest = ::exitApplication,
            title = APP_NAME,
            icon = windowIcon,
            alwaysOnTop = alwaysOnTop,
            state = windowState,
            onPreviewKeyEvent = { event ->
                handleMainWindowShortcut(event, state) { if (!focusedEmulator) drawerState.targetValue = !drawerState.targetValue }
            },
        ) {
            MenuBar {
                Menu("Keyboard") {
                    Item("Toggle Software Keyboard",
                        shortcut = KeyShortcut(Key.K, alt = true, meta = HostOs.detect() == HostOs.MAC, ctrl = HostOs.detect() != HostOs.MAC),
                        onClick = state::toggleSoftwareKeyboard)
                }
            }
            window.minimumSize = if (focusedEmulator) java.awt.Dimension(320, 360) else java.awt.Dimension(760, 540)
            val setFocusMode: (Boolean) -> Unit = { focused ->
                // Entering focus mode is a licensed feature; leaving it never is.
                if (focused != focusedEmulator && (!focused || state.unlocked())) {
                    if (focused) {
                        val device = state.previewDevice
                        val width = device?.config?.get("hw.lcd.width")?.toFloatOrNull() ?: 1080f
                        val height = device?.config?.get("hw.lcd.height")?.toFloatOrNull() ?: 2400f
                        val available = window.graphicsConfiguration.bounds
                        val insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(window.graphicsConfiguration)
                        val compactHeight = minOf(800f, (available.height - insets.top - insets.bottom).toFloat())
                        val compactWidth = ((compactHeight - 120f) * width / height.coerceAtLeast(1f) + 60f)
                            .coerceIn(320f, (available.width - insets.left - insets.right).toFloat().coerceAtLeast(320f))
                        window.minimumSize = java.awt.Dimension(320, 360)
                        focusMode.enter(DpSize(compactWidth.dp, compactHeight.dp))
                    } else {
                        focusMode.exit()
                    }
                }
            }
            LaunchedEffect(state.showSettings, state.previewDevice?.name, state.selected.size) {
                if (state.showSettings || state.previewDevice == null || state.selected.size > 1) setFocusMode(false)
            }
            DisposableEffect(window, state) {
                val listener = object : java.awt.event.WindowAdapter() {
                    override fun windowGainedFocus(event: java.awt.event.WindowEvent) = state.windowFocusChanged(true)
                    override fun windowLostFocus(event: java.awt.event.WindowEvent) = state.windowFocusChanged(false)
                }
                window.addWindowFocusListener(listener)
                state.windowFocusChanged(window.isFocused)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            val isMacOS = System.getProperty("os.name").startsWith("Mac")
            if (isMacOS) {
                // Keep the native decorated window, with content behind a transparent title bar.
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            }
            AvdBuddyTheme {
                FocusVisibilityProvider {
                    DialogHost {
                        CompositionLocalProvider(LocalContextMenuRepresentation provides FadingContextMenuRepresentation) {
                            App(state, topInset = if (isMacOS) 28.dp else 0.dp, drawerState = drawerState,
                                alwaysOnTop = alwaysOnTop, onAlwaysOnTopChange = { alwaysOnTop = it },
                                focusedEmulator = focusedEmulator, onFocusModeChange = setFocusMode)
                        }
                    }
                }
            }
        }
    }
}

// Handle shortcuts before focused content, including the emulator and text fields.
private fun handleMainWindowShortcut(event: KeyEvent, state: AppState, toggleSidebar: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || state.hasOpenDialog) return false
    if (if (HostOs.detect() == HostOs.MAC) event.isMetaPressed else event.isCtrlPressed) when (event.key) {
        Key.DirectionRight -> return state.rotateCurrent(clockwise = true)
        Key.DirectionLeft -> return state.rotateCurrent(clockwise = false)
        Key.B -> { toggleSidebar(); return true }
    }
    if (!event.isMetaPressed && !event.isCtrlPressed) return false
    return when (event.key) {
        Key.N -> { state.resetCreate(); true }
        Key.Comma -> { state.openSettings(); true }
        Key.Backspace -> { if (!state.busy && state.selectedDevices.none { it.name in state.starting }) state.deleteDevices = state.selectedDevices; true }
        else -> false
    }
}

@Composable fun App(state: AppState, alwaysOnTop: Boolean, onAlwaysOnTopChange: (Boolean) -> Unit,
                    autoRefresh: Boolean = true, topInset: Dp = 0.dp,
                    focusedEmulator: Boolean = false, onFocusModeChange: (Boolean) -> Unit = {},
                    drawerState: UnstyledDrawerState<Boolean> = rememberAppSidebarState()) {
    LaunchedEffect(state) { state.observeDisplays() }
    LaunchedEffect(state, autoRefresh) {
        if (autoRefresh) {
            while (isActive) {
                state.refresh()
                runCatching { state.connected.refresh() }
                delay(3000)
            }
        }
    }
    LaunchedEffect(state, autoRefresh) { if (autoRefresh) state.updates.monitor() }
    Box(Modifier.fillMaxSize()) {
        // Failures show over both the workspace and Settings, below the title bar.
        val toasts = rememberToastState()
        LaunchedEffect(state, toasts) {
            state.failures.collect { failure ->
                launch { toasts.show(ToastMessage(failure.message, ToastTone.Error), ToastPresentation.Top) }
            }
        }
        ToastHost(toasts, Modifier.fillMaxSize(),
            insets = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 12.dp, bottom = 16.dp)) {
            if (state.showSettings) SettingsScreen(state, topInset)
            else EmulatorWorkspace(state, alwaysOnTop, onAlwaysOnTopChange, topInset, drawerState, focusedEmulator, onFocusModeChange)
        }
        AppDialog(state.showDemoDialog.takeIf { it }, onClose = { state.showDemoDialog = false }) {
            DemoDialog(state)
        }
        AppDialog(state.renameDevice, onClose = { if (!state.busy) state.renameDevice = null }) { device ->
            RenameDeviceDialog(state, device)
        }
        AppDialog(state.wipeDataDevice, onClose = { state.wipeDataDevice = null }) { device ->
            WipeDataDialog(state, device)
        }
        // A stray click must not abort a pairing in progress.
        AppDialog(state.connected.showConnect.takeIf { it }, onClose = { state.connected.cancel(); state.connected.showConnect = false },
            dismissOnClickOutside = false) {
            ConnectDeviceDialog(state.connected)
        }
        AppDialog(state.connected.renameTarget, onClose = { if (!state.connected.working) state.connected.dismissRename() }) {
            RenameConnectedDeviceDialog(state.connected, it)
        }
        AppDialog(state.connected.unpairTarget, onClose = { if (!state.connected.working) state.connected.dismissUnpair() }) {
            UnpairDeviceDialog(state.connected, it)
        }
    }
}

@Composable private fun EmulatorWorkspace(state: AppState, alwaysOnTop: Boolean, onAlwaysOnTopChange: (Boolean) -> Unit,
                                          topInset: Dp, drawerState: UnstyledDrawerState<Boolean>,
                                          focusedEmulator: Boolean, onFocusModeChange: (Boolean) -> Unit) {
    val controlsState = rememberAppSidebarState(initialValue = false)
    val deviceControls = rememberDeviceControls(state, controlsState.targetValue && !focusedEmulator)
    var createMenuExpanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AppScaffold(
            topInset = topInset,
            title = state.connected.selected?.displayName ?: if (state.selected.size > 1) "Multiple emulators selected" else state.previewDevice?.displayName ?: APP_NAME,
            subtitle = state.previewDevice?.takeIf { state.selected.size <= 1 }?.let { it.platformTitle },
            modifier = Modifier.testTag("DeviceShelf"),
            sidebarState = drawerState,
            showSidebars = !focusedEmulator,
            sidebar = { DeviceSidebar(state, createMenuExpanded, { createMenuExpanded = it }, Modifier.fillMaxWidth()) },
            trailingSidebarState = controlsState,
            trailingSidebarWidth = deviceControlsPanelWidth,
            trailingSidebar = {
                DeviceControlsPanel(state, deviceControls)
            },
            topStartActions = {
                if (!focusedEmulator) {
                    IconButton(Icons.ViewColumns, if (drawerState.targetValue) "Hide sidebar" else "Show sidebar", "ToggleSidebar", ghost = true) {
                        drawerState.targetValue = !drawerState.targetValue
                    }
                    Spacer(Modifier.width(34.dp))
                }
            },
            topEndActions = {
                IconButtonGroup {
                    if (!focusedEmulator) {
                        val controlsDescription = if (controlsState.targetValue) "Hide device controls" else "Show device controls"
                        Tooltip(tooltip = { Text(controlsDescription) }) {
                            ToggleableIconButton(value = controlsState.targetValue,
                                onValueChange = { controlsState.targetValue = it },
                                modifier = Modifier.testTag("ToggleDeviceControls").semantics { contentDescription = controlsDescription }) {
                                Icon(Icons.AdjustmentsHorizontal)
                            }
                        }
                    }
                    val description = if (alwaysOnTop) "Stop keeping window on top" else "Keep window on top"
                    Tooltip(tooltip = { Text(description) }) {
                        ToggleableIconButton(value = alwaysOnTop, onValueChange = onAlwaysOnTopChange,
                            modifier = Modifier.testTag("ToggleAlwaysOnTop").semantics { contentDescription = description }) {
                            Icon(Icons.Pin)
                        }
                    }
                }
                IconButtonGroup {
                    IconButton(if (focusedEmulator) Icons.Maximize2 else Icons.Minimize2,
                        if (focusedEmulator) "Exit emulator focus" else "Focus on emulator", "ToggleEmulatorFocus",
                        state.previewDevice != null && state.selected.size <= 1, ghost = true) {
                        onFocusModeChange(!focusedEmulator)
                    }
                    DeviceOverflowMenu(state)
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                val device = state.previewDevice
                if (state.connected.selected != null) {
                    ConnectedDevicePanel(state.connected.selected!!, state)
                } else if (state.selected.size > 1) {
                    Box(Modifier.fillMaxSize().testTag("MultipleDevicesSelected"), contentAlignment = Alignment.Center) {
                        DeviceStateMessage("Multiple emulators selected")
                    }
                } else if (device != null) EmulatorPanel(device, state)
                else Box(Modifier.fillMaxSize().testTag("EmptyShelf"), contentAlignment = Alignment.Center) {
                    Text("No Selection", color = AvdBuddyTheme.colors.textSecondary)
                }
            }
        }
        // A stray click must not discard a configured emulator.
        AppDialog(state.showCreate.takeIf { it }, onClose = { state.showCreate = false }, dismissOnClickOutside = false) { CreateDeviceDialog(state) }
        AppDialog(state.deleteDevices.takeIf { it.isNotEmpty() }, onClose = { state.deleteDevices = emptyList() }) { devices ->
            DeleteDevicesDialog(state, devices)
        }
    }
}

@Composable private fun DeviceSidebar(state: AppState, createMenuExpanded: Boolean,
                                      onCreateMenuExpandedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val searchField = rememberTextFieldState()
    val query = searchField.text.toString().trim()
    Column(modifier.fillMaxHeight().testTag("DeviceSidebar")) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
            CreateDeviceMenu(state, createMenuExpanded, onCreateMenuExpandedChange)
        }
        TextField(
            state = searchField,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 8.dp, bottom = 4.dp)
                .testTag("DeviceSearch").semantics { contentDescription = "Search devices" },
            placeholder = { Text("Search devices") },
            leadingIcon = { Icon(Icons.MagnifyingGlass, tint = AvdBuddyTheme.colors.textSecondary, modifier = Modifier.size(18.dp).testTag("DeviceSearchIcon")) },
            trailingIcon = if (searchField.text.isNotEmpty()) {
                { IconButton(Icons.XMark, "Clear search", "ClearDeviceSearch", ghost = true) { searchField.setTextAndPlaceCursorAtEnd("") } }
            } else null,
        )
        val allDevices: List<DeviceTarget> = state.visibleDevices + state.connected.devices
        fun active(device: DeviceTarget) = when (device) {
            is ConnectedDevice -> state.connected.sharing(device)
            else -> device.name in state.running
        }
        val sidebarDevices = allDevices.filter { it.displayName.contains(query, ignoreCase = true) }.sortedWith(
            compareBy<DeviceTarget> { !active(it) }
                .thenBy { it !is ConnectedDevice }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
                .thenBy { it.name }
        )
        val orderedNames = sidebarDevices.filterIsInstance<Device>().map { it.name }
        LazyColumn(Modifier.weight(1f).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false
            else if ((event.isMetaPressed || event.isCtrlPressed) && event.key == Key.A) {
                state.selected = orderedNames.toSet()
                state.connected.selected = null
                true
            } else if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                val current = sidebarDevices.indexOfFirst { it.name == (state.connected.selected?.name ?: state.previewDevice?.name) }
                val next = (current + if (event.key == Key.DirectionDown) 1 else -1).coerceIn(0, (sidebarDevices.size - 1).coerceAtLeast(0))
                when (val target = sidebarDevices.getOrNull(next)) {
                    is ConnectedDevice -> { state.selected = emptySet(); state.connected.selected = target }
                    is Device -> state.selectDevice(target, extend = event.isShiftPressed, orderedNames = orderedNames)
                }
                true
            } else false
        }, contentPadding=PaddingValues(horizontal=8.dp, vertical=4.dp)) {
            if (sidebarDevices.isEmpty() && query.isNotEmpty()) {
                item {
                    Label("No matching devices", modifier = Modifier.padding(8.dp).testTag("NoDeviceMatches"),
                        color = AvdBuddyTheme.colors.textSecondary)
                }
            } else if (sidebarDevices.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize().testTag("NoDevices"), contentAlignment = Alignment.Center) {
                        Text("No Devices", color = AvdBuddyTheme.colors.textSecondary)
                    }
                }
            }
            items(sidebarDevices.size, key={sidebarDevices[it].name}) { index ->
                Box(Modifier.animateItem()) {
                    when (val device = sidebarDevices[index]) {
                        is ConnectedDevice -> ConnectedDeviceRow(device, state)
                        is Device -> DeviceRow(device, state, orderedNames)
                    }
                }
            }
        }
        if (!state.license.licensed) {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                PurchaseLicenseButton {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(purchaseLicenseUrl))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AvdBuddyTheme.colors.shelfBorder))
        Column(Modifier.fillMaxWidth().padding(12.dp).testTag("SidebarFooter"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(APP_NAME, style = AvdBuddyTheme.typography.body, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    modifier = Modifier.testTag("SidebarAppName"))
                Spacer(Modifier.width(6.dp))
                Text(APP_VERSION, style = AvdBuddyTheme.typography.small,
                    color = AvdBuddyTheme.colors.textSecondary, modifier = Modifier.testTag("SidebarAppVersion"))
                Spacer(Modifier.weight(1f))
                SettingsButton(state)
            }
            ActionButton("Send feedback", tag = "SendFeedback") {
                java.awt.Desktop.getDesktop().mail(java.net.URI("mailto", "$FEEDBACK_EMAIL?subject=$APP_NAME $APP_VERSION feedback", null))
            }
        }
    }
}

@Composable private fun ConnectedDeviceRow(device: ConnectedDevice, state: AppState) {
    val selected = state.connected.selected?.identity == device.identity
    val subtitle = device.manufacturerAndModel
    ContextMenuArea(items = { connectedDeviceActions(state, device) }) {
        SidebarListItem(selected = selected, contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).testTag("Connected-${device.serial}")
                .semantics { contentDescription = "${device.displayName}, $subtitle"; this.selected = selected }
                .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { event ->
                    val modifiers = event.keyboardModifiers
                    if (event.buttons.isPrimaryPressed && event.awtEventOrNull?.clickCount == 2 &&
                        !modifiers.isMetaPressed && !modifiers.isCtrlPressed && !modifiers.isShiftPressed) {
                        state.selected = emptySet()
                        state.connected.selected = device
                        state.connected.startSharing(device)
                    }
                }
                .clickable { state.selected = emptySet(); state.connected.selected = device }) {
            DeviceRowContent(device.type, state.connected.sharing(device), device.displayName, subtitle)
        }
    }
}

@Composable private fun DeviceRow(device: Device, state: AppState, orderedNames: List<String>) {
    val selected = device.name in state.selected
    val running = device.name in state.running
    val pending = state.pendingCreation?.name == device.name
    val operation = if (pending) {
        if (state.creating) "Setting up…" else "Setup failed"
    } else if (device.name in state.stopping) "Shutting down…" else state.deviceOperations[device.name]?.label
        ?: if (device.name in state.starting) "Starting…" else null
    val subtitle = operation ?: if (device.missingStorage) "Storage unavailable" else device.platformTitle
    var clickModifiers by remember { mutableStateOf(PointerKeyboardModifiers()) }
    val menuState = remember { ContextMenuState() }
    LaunchedEffect(menuState.status) {
        if (menuState.status is ContextMenuState.Status.Open) state.selectForContextMenu(device)
    }
    ContextMenuArea(state = menuState, items = {
        val targets = if (device.name in state.selected) state.selectedDevices else listOf(device)
        deviceActions(state, device, targets)
    }) {
        SidebarListItem(selected = selected, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).testTag("Device-${device.name}")
                .semantics { contentDescription="${device.displayName}, $subtitle"; this.selected = selected }
                .onPointerEvent(PointerEventType.Press, pass = PointerEventPass.Initial) { event ->
                    if (event.buttons.isPrimaryPressed) {
                        clickModifiers = event.keyboardModifiers
                        // Detect double clicks from the OS click count so single clicks select
                        // immediately instead of waiting out a double-tap timeout.
                        if (event.awtEventOrNull?.clickCount == 2 && !clickModifiers.isMetaPressed &&
                            !clickModifiers.isCtrlPressed && !clickModifiers.isShiftPressed) {
                            state.selectDevice(device)
                            if (!pending && device.name !in state.stopping) state.launch(device)
                        }
                    }
                }
                .clickable(onClick = {
                    state.selectDevice(device, toggle = clickModifiers.isMetaPressed || clickModifiers.isCtrlPressed,
                        extend = clickModifiers.isShiftPressed, orderedNames = orderedNames)
                    clickModifiers = PointerKeyboardModifiers()
                })) {
            DeviceRowContent(device.type, running, device.displayName, subtitle,
                busy = operation != null && (!pending || state.creating))
        }
    }
}

@Composable private fun DeviceRowContent(type: DeviceType, running: Boolean, name: String, subtitle: String, busy: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).background(if (running) AvdBuddyTheme.colors.runningIconBackground else AvdBuddyTheme.colors.deviceIconBackground, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            DeviceGlyph(type, running, size = 20.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Label(name, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = AvdBuddyTheme.colors.textSecondary, style = AvdBuddyTheme.typography.small,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (busy) {
            Spacer(Modifier.width(10.dp))
            org.jetbrains.jewel.ui.component.CircularProgressIndicator(Modifier.size(16.dp))
        }
    }
}
