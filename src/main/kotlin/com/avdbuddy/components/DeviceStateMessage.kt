package com.avdbuddy.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DeviceStateMessage(text: String, modifier: Modifier = Modifier) {
    DeviceStateMessage(AnnotatedString(text), modifier)
}

@Composable
fun DeviceStateMessage(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.widthIn(max = 360.dp),
        color = AvdBuddyTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}
