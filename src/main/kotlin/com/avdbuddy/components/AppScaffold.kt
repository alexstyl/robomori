package com.avdbuddy.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.*

@Composable
fun rememberAppSidebarState(initialValue: Boolean = true): UnstyledDrawerState<Boolean> = remember {
    UnstyledDrawerState(initialValue = initialValue, snapPoints = DrawerSnapPoints {
        false at DrawerSnapPoint.Zero
        true at DrawerSnapPoint.ContentSize
    })
}

@Composable
fun AppScaffold(
    topInset: Dp,
    modifier: Modifier = Modifier,
    sidebarState: UnstyledDrawerState<Boolean> = rememberAppSidebarState(),
    sidebarPinned: Boolean = false,
    showSidebars: Boolean = true,
    contentTopInset: Dp = maxOf(topInset, 40.dp),
    title: String? = null,
    subtitle: String? = null,
    sidebar: @Composable (topInset: Dp) -> Unit,
    topStartActions: @Composable RowScope.() -> Unit = {},
    topEndActions: @Composable RowScope.() -> Unit = {},
    trailingSidebarState: UnstyledDrawerState<Boolean>? = null,
    trailingSidebarWidth: Dp = 270.dp,
    trailingSidebar: @Composable (topInset: Dp) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val headerHeight = maxOf(topInset, if (title != null) 64.dp else 40.dp)
    val fullSidebarWidth = 270.dp
    val sidebarWidth = if (!showSidebars) 0.dp else if (sidebarPinned) fullSidebarWidth else with(LocalDensity.current) { sidebarState.offset.toDp() }
    val trailingWidth = if (!showSidebars) 0.dp else with(LocalDensity.current) { trailingSidebarState?.offset?.toDp() ?: 0.dp }
    val startActionsWidth = maxOf(sidebarWidth, if (topInset > 0.dp) 152.dp else 80.dp)
    val titleClearanceWidth = maxOf(sidebarWidth, if (topInset > 0.dp) 136.dp else 80.dp)
    Box(modifier.fillMaxSize().background(Color(0xFFF1F1F1))) {
        Box(Modifier.fillMaxSize().padding(start = sidebarWidth, end = trailingWidth)
            .then(if (sidebarWidth > 0.dp) Modifier.border(1.dp, Color(0xFFD6D6D6)) else Modifier)
            .background(Color.White)
            .clipToBounds()
            .padding(top = if (title != null) 0.dp else contentTopInset)) {
            Column(Modifier.fillMaxSize()) {
                if (title != null) AppBar(title, subtitle, Modifier.fillMaxWidth().padding(
                    start = when {
                        showSidebars -> titleClearanceWidth - sidebarWidth
                        topInset > 0.dp -> 80.dp
                        else -> 0.dp
                    },
                ), topEndActions)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
        if (showSidebars && sidebarPinned) {
            Box(Modifier.width(fullSidebarWidth).fillMaxHeight()) { sidebar(headerHeight) }
        } else if (showSidebars) UnstyledDrawer(
            state = sidebarState,
            placement = DrawerPlacement.Start,
            presentation = DrawerPresentation.InPlace,
            dismissOnClickOutside = false,
            dismissOnNavigateBack = false,
            gesturesEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            Viewport<Boolean>(Modifier.fillMaxSize()) {
                Panel(Modifier.width(fullSidebarWidth).fillMaxHeight()) { sidebar(headerHeight) }
            }
        }
        if (showSidebars && trailingSidebarState != null) UnstyledDrawer(
            state = trailingSidebarState,
            placement = DrawerPlacement.End,
            presentation = DrawerPresentation.InPlace,
            dismissOnClickOutside = false,
            dismissOnNavigateBack = false,
            gesturesEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) {
            Viewport<Boolean>(Modifier.fillMaxSize()) {
                Panel(Modifier.width(trailingSidebarWidth).fillMaxHeight()) {
                    Box(Modifier.fillMaxSize()) {
                        trailingSidebar(headerHeight)
                        Box(Modifier.align(Alignment.CenterStart).width(1.dp).fillMaxHeight()
                            .background(Color(0xFFD6D6D6)))
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopStart)
            .width(startActionsWidth)
            .height(headerHeight).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.Top,
            content = topStartActions)
        if (title == null) Row(Modifier.align(Alignment.TopEnd).height(headerHeight).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, content = topEndActions)
    }
}
