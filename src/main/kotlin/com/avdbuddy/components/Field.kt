package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.jewel.ui.component.TextField

@Composable fun Field(state: androidx.compose.foundation.text.input.TextFieldState, tag:String, modifier:Modifier=Modifier) {
    TextField(state=state,modifier=modifier.testTag(tag))
}
