package com.avdbuddy

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.avdbuddy.components.FadingMenu
import com.avdbuddy.components.IconButton
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.EllipsisVertical
import com.avdbuddy.iconography.Plus
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.component.Text

internal fun deviceActions(state: AppState, device: Device, targets: List<Device>): List<ContextMenuItem> {
    val pending = state.pendingCreation?.name == device.name
    val startable = targets.filter { it.name !in state.running && it.name !in state.starting && !it.missingStorage }
    val stoppable = targets.filter { it.name in state.running && it.name !in state.starting }
    return buildList {
        if (pending) {
            if (state.creating) add(ContextMenuItem("Cancel setup") { state.cancelCreate() })
            else {
                add(ContextMenuItem("Try again") { state.retryCreation() })
                add(ContextMenuItem("Remove") { state.discardPendingCreation() })
            }
        } else if (!state.busy && targets.isNotEmpty() && targets.none { it.name in state.deviceOperations }) {
            if (startable.isNotEmpty()) {
                add(ContextMenuItem("Start") { state.launch(startable) })
                add(ContextMenuItem("Cold boot") { state.launch(startable, coldBoot = true) })
            }
            if (stoppable.isNotEmpty()) {
                add(ContextMenuItem("Stop") { state.stop(stoppable) })
                add(ContextMenuItem("Restart") { state.restart(stoppable) })
            }
            if (targets.size == 1 && device.name !in state.running && device.name !in state.starting) {
                if (!device.missingStorage) add(ContextMenuItem("Wipe data…") { state.openWipeData(device) })
                add(ContextMenuItem("Duplicate") { state.duplicate(device) })
                add(ContextMenuItem("Rename…") { state.openRename(device) })
            }
            add(ContextMenuItem("Show in ${HostPlatform().fileManagerName}") { state.reveal(targets) })
            if (targets.none { it.name in state.starting }) {
                add(ContextMenuItem("Remove…") { state.deleteDevices = targets })
            }
        }
    }
}

internal fun connectedDeviceActions(state: AppState, device: ConnectedDevice): List<ContextMenuItem> {
    if (state.connected.working) return emptyList()
    fun select() { state.selected = emptySet(); state.connected.selected = device }
    return buildList {
        // USB devices share for as long as they are plugged in.
        if (device.wireless) {
            if (state.connected.sharingRequested(device)) add(ContextMenuItem("Stop Screen Sharing") { state.connected.stopSharing(device) })
            else if (device.available || state.connected.canReconnect(device)) add(ContextMenuItem("Start Screen Sharing") { select(); state.connected.startSharing(device) })
        }
        add(ContextMenuItem("Rename") { select(); state.connected.openRename(device) })
        if (device.available) add(ContextMenuItem("Restart") { select(); state.connected.restart(device) })
        if (state.connected.isPaired(device)) add(ContextMenuItem("Unpair...") { select(); state.connected.clearActionError(); state.connected.unpairTarget = device })
    }
}

@Composable
internal fun DeviceOverflowMenu(state: AppState) {
    val device = state.previewDevice
    val targets = device?.let { if (it.name in state.selected) state.selectedDevices else listOf(it) }.orEmpty()
    val actions = state.connected.selected?.let { connectedDeviceActions(state, it) }
        ?: device?.let { deviceActions(state, it, targets) }.orEmpty()
    // Reset an open menu if its selection changes or no actions remain available.
    var expanded by remember(device?.name, state.connected.selected?.identity, state.selected, actions.isEmpty()) { mutableStateOf(false) }
    var lastActions by remember { mutableStateOf(actions) }
    LaunchedEffect(expanded) { if (expanded) lastActions = actions }
    val displayedActions = if (expanded) actions else lastActions
    Box {
        IconButton(Icons.EllipsisVertical, "Device actions", "DeviceActions", enabled = actions.isNotEmpty(), ghost = true) {
            expanded = !expanded
        }
        FadingMenu(expanded && displayedActions.isNotEmpty()) { fade ->
            PopupMenu(onDismissRequest = { expanded = false; true }, horizontalAlignment = Alignment.End, modifier = fade) {
                displayedActions.forEach { action ->
                    selectableItem(selected = false, onClick = { expanded = false; action.onClick() }) {
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CreateDeviceMenu(state: AppState, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    Box {
        IconButton(Icons.Plus, "Add a device", "CreateDevice", ghost = true) { onExpandedChange(!expanded) }
        FadingMenu(expanded) { fade ->
            PopupMenu(onDismissRequest = { onExpandedChange(false); true }, horizontalAlignment = Alignment.Start, modifier = fade) {
                listOf(DeviceType.PHONE, DeviceType.TABLET, DeviceType.FOLDABLE, DeviceType.WEAR, DeviceType.TV).forEach { type ->
                    selectableItem(selected = false, onClick = {
                        onExpandedChange(false)
                        if (state.ready) state.resetCreate(type) else state.openSettings(SettingsSection.Storage)
                    }) {
                        Text("${if (type == DeviceType.WEAR) "Watch" else type.label}…")
                    }
                }
                selectableItem(selected = false, onClick = {
                    onExpandedChange(false)
                    state.pairDeviceWirelessly()
                }) {
                    Text("Pair device wirelessly...")
                }
            }
        }
    }
}
