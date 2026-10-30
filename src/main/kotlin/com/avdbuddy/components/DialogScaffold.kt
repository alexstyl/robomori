package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avdbuddy.theme.AvdBuddyTheme
import com.avdbuddy.theme.verticalScroll
import org.jetbrains.jewel.ui.component.Text

@Composable fun DialogScaffold(title:String,tag:String,width:Int=560,height:Int=640,scrollableContent:Boolean=true,contentPadding:PaddingValues=PaddingValues(24.dp),footer:@Composable RowScope.()->Unit,content:@Composable ColumnScope.()->Unit) {
    AppDialogPanel(title, tag, Modifier.width(width.dp).height(height.dp)) {
        Column(Modifier.fillMaxSize().background(AvdBuddyTheme.colors.panelBackground)) {
            Column(Modifier.padding(start=24.dp,end=24.dp,top=22.dp,bottom=18.dp)) {
                Text(title, style = AvdBuddyTheme.typography.heading)
            }
            Rule()
            Box(Modifier.weight(1f).testTag("$tag-Content")) {
                if (scrollableContent) {
                    val scroll = rememberScrollState()
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(contentPadding),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
                    VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical=4.dp))
                } else {
                    Column(Modifier.fillMaxSize().padding(contentPadding),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
                }
            }
            Rule()
            Row(Modifier.fillMaxWidth().testTag("$tag-Footer").background(AvdBuddyTheme.colors.panelBackground).padding(horizontal=20.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically,content=footer)
        }
    }
}
