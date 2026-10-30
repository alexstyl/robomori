package com.avdbuddy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.components.IconButton
import com.avdbuddy.components.Label
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.ChevronDown
import com.avdbuddy.theme.AvdBuddyTheme
import java.io.File

@Composable internal fun CaptureToast(message: String, file: File?, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.padding(horizontal = 16.dp)
        .widthIn(max = 500.dp)
        .background(AvdBuddyTheme.colors.panelBackground, shape)
        .border(1.dp, AvdBuddyTheme.colors.shelfBorder, shape)
        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(message, modifier = Modifier.widthIn(max = 420.dp).testTag("CaptureNotice"), maxLines = 2,
            color = if (file == null) AvdBuddyTheme.colors.danger else AvdBuddyTheme.colors.text)
        if (file != null) {
            IconButton(Icons.ChevronDown, "Open in ${HostPlatform().fileManagerName}", tag = "OpenCaptureLocation",
                iconModifier = Modifier.size(18.dp).rotate(-90f), ghost = true, onClick = onOpen)
        }
    }
}
