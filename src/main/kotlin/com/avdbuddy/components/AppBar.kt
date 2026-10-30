package com.avdbuddy.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme
import org.jetbrains.jewel.ui.component.Text

@Composable
fun AppBar(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.heightIn(min = 64.dp).padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, modifier = Modifier.testTag("PreviewDeviceName"),
                style = AvdBuddyTheme.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Label(subtitle, color = AvdBuddyTheme.colors.textSecondary, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun IconButtonGroup(modifier: Modifier = Modifier, contentPadding: Dp = 4.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.background(AvdBuddyTheme.colors.shelfBackground, CircleShape)
            .border(1.dp, AvdBuddyTheme.colors.shelfBorder, CircleShape).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
