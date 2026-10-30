package com.avdbuddy.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

@Composable fun Rule() { Divider(Orientation.Horizontal, Modifier.fillMaxWidth()) }
