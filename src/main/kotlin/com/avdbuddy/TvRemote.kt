package com.avdbuddy

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.IconButton
import com.avdbuddy.components.IconButtonGroup
import com.avdbuddy.components.Label
import com.avdbuddy.iconography.*

@Composable
internal fun TvRemote(state: AppState, device: Device) {
    TvRemote { key -> state.sendInput(device, listOf("keyevent", key)) }
}

@Composable
internal fun TvRemote(press: (String) -> Unit) {
    Row(Modifier.testTag("TvRemote"), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButtonGroup(contentPadding = 0.dp) {
            IconButton(Icons.ArrowLeft, "Back", tag = "RemoteBack", ghost = true) { press("KEYCODE_BACK") }
        }
        IconButtonGroup(contentPadding = 0.dp) {
            IconButton(Icons.RemoteHome, "Home", tag = "RemoteHome", ghost = true) { press("KEYCODE_HOME") }
        }
        IconButtonGroup(contentPadding = 0.dp) {
            IconButton(Icons.RemotePlayPause, "Play or pause", tag = "RemotePlayPause", ghost = true) { press("KEYCODE_MEDIA_PLAY_PAUSE") }
        }
        listOf("Left" to 90f, "Up" to 180f).forEach { (direction, turn) ->
            IconButtonGroup(contentPadding = 0.dp) {
                IconButton(Icons.ChevronDown, direction, tag = "Remote$direction",
                    iconModifier = Modifier.size(18.dp).rotate(turn), ghost = true) {
                    press("KEYCODE_DPAD_${direction.uppercase()}")
                }
            }
        }
        IconButtonGroup(contentPadding = 0.dp) {
            org.jetbrains.jewel.ui.component.IconButton(
                onClick = { press("KEYCODE_DPAD_CENTER") },
                modifier = Modifier.testTag("RemoteOk").semantics { contentDescription = "OK" },
            ) {
                Label("OK", modifier = Modifier.padding(8.dp))
            }
        }
        listOf("Down" to 0f, "Right" to -90f).forEach { (direction, turn) ->
            IconButtonGroup(contentPadding = 0.dp) {
                IconButton(Icons.ChevronDown, direction, tag = "Remote$direction",
                    iconModifier = Modifier.size(18.dp).rotate(turn), ghost = true) {
                    press("KEYCODE_DPAD_${direction.uppercase()}")
                }
            }
        }
    }
}
