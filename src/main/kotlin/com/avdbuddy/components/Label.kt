package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.jewel.ui.component.Text

@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
