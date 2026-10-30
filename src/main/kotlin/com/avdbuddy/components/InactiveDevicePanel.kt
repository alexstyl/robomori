package com.avdbuddy.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avdbuddy.DeviceType
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun InactiveDevicePanel(
    type: DeviceType,
    name: String,
    description: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().padding(30.dp)) {
        val iconSize = 64.dp
        val iconOffset = (-60).dp
        DeviceGlyph(type, running = false, size = iconSize,
            modifier = Modifier.align(Alignment.Center).offset(y = iconOffset))
        Column(
            Modifier.fillMaxWidth().padding(top = maxHeight / 2 + iconOffset + iconSize / 2 + 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, Modifier.widthIn(max = 360.dp), style = AvdBuddyTheme.typography.heading,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            DeviceStateMessage(description)
            Spacer(Modifier.height(16.dp))
            actions()
        }
    }
}
