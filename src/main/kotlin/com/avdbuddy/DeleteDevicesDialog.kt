package com.avdbuddy

import com.avdbuddy.components.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.avdbuddy.theme.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable fun DeleteDevicesDialog(state: AppState, devices: List<Device>) {
    val title = "Delete ${if (devices.size == 1) "emulator" else "${devices.size} emulators"}?"
    val cancelFocus = remember { FocusRequester() }
    fun close() { state.deleteDevices = emptyList() }

    AppDialogPanel(title, "DeleteDevicesDialog", Modifier.width(460.dp).heightIn(max = 420.dp)) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Label(title)
            Column(
                Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(rememberScrollState())
                    .testTag("DeleteDeviceNames"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                devices.forEach { Label(it.displayName) }
            }
            Label(
                "The emulators and their saved data will be removed. Running emulators will be stopped first. This cannot be undone.",
                color = AvdBuddyTheme.colors.textSecondary,
            )
            if (state.busy) {
                Label("Wait for the current emulator operation to finish.", color = AvdBuddyTheme.colors.textSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(::close, Modifier.focusRequester(cancelFocus).testTag("CancelDelete")) {
                    Label("Cancel")
                }
                ActionButton("Delete", primary = true, enabled = !state.busy && devices.none { it.name in state.deviceOperations }, tag = "ConfirmDelete") {
                    state.delete(devices)
                    close()
                }
            }
        }
    }
}
