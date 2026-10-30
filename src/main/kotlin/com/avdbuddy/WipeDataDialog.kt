package com.avdbuddy

import com.avdbuddy.components.ActionButton
import com.avdbuddy.components.AppDialogPanel
import com.avdbuddy.components.Label
import com.avdbuddy.theme.AvdBuddyTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.OutlinedButton

@Composable
fun WipeDataDialog(state: AppState, device: Device) {
    val cancelFocus = remember { FocusRequester() }
    fun close() { state.wipeDataDevice = null }

    AppDialogPanel("Wipe data?", "WipeDataDialog", Modifier.width(460.dp)) {
        LaunchedEffect(Unit) { cancelFocus.requestFocus() }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Label("Wipe data from ${device.displayName}?")
            Label("Installed apps, accounts, and settings on this emulator will be erased. The emulator will start with fresh data. This cannot be undone.",
                color = AvdBuddyTheme.colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                OutlinedButton(::close, Modifier.focusRequester(cancelFocus).testTag("CancelWipeData")) { Label("Cancel") }
                ActionButton("Wipe data and start", primary = true, enabled = !state.busy && device.name !in state.deviceOperations, tag = "ConfirmWipeData") {
                    close()
                    state.launch(device, wipeData = true)
                }
            }
        }
    }
}
