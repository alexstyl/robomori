package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.avdbuddy.iconography.Icons
import com.avdbuddy.iconography.Cog6Tooth
import com.avdbuddy.theme.AvdBuddyTheme

@Composable fun Section(title: String, icon: ImageVector = Icons.Cog6Tooth, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(icon,modifier=Modifier.size(15.dp));Spacer(Modifier.width(9.dp));Label(title)
            if(subtitle!=null){Spacer(Modifier.weight(1f));Label(subtitle,color=AvdBuddyTheme.colors.textSecondary)}
        }
        Rule();Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
