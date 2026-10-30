package com.avdbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.*

@Composable internal fun RenameConnectedDeviceDialog(state: ConnectedDevicesState, device: ConnectedDevice) {
    val name = rememberTextFieldState(device.displayName)
    AppDialogPanel("Rename device", "RenameConnectedDeviceDialog", Modifier.width(400.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label("Rename device")
            Field(name, "ConnectedDeviceName", Modifier.fillMaxWidth())
            state.actionFor(device).deviceActionMessage?.let { Label(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                ActionButton("Cancel", enabled = !state.working) { state.dismissRename() }
                ActionButton("Rename", primary = true, enabled = !state.working && name.text.isNotBlank()) { state.rename(device, name.text.toString()) }
            }
        }
    }
}

@Composable internal fun UnpairDeviceDialog(state: ConnectedDevicesState, device: ConnectedDevice) {
    AppDialogPanel("Unpair device", "UnpairDeviceDialog", Modifier.width(420.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label("Unpair ${device.displayName}?")
            Label("You will not be able to screen share to this device without pairing it again.")
            state.actionFor(device).deviceActionMessage?.let { Label(it) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                ActionButton("Cancel", enabled = !state.working) { state.dismissUnpair() }
                ActionButton(if (state.working) "Unpairing…" else "Unpair", dangerous = true, enabled = !state.working) { state.unpair(device) }
            }
        }
    }
}
