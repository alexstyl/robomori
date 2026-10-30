package com.avdbuddy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState

internal class EmulatorFocusState(private val window: WindowState) {
    private var workspace by mutableStateOf<WorkspaceWindow?>(null)
    val active: Boolean get() = workspace != null

    fun enter(size: DpSize) {
        if (active) return
        workspace = WorkspaceWindow(window.size, window.position, window.placement)
        window.placement = WindowPlacement.Floating
        window.size = size
    }

    fun exit() {
        val saved = workspace ?: return
        window.size = saved.size
        window.position = saved.position
        window.placement = saved.placement
        workspace = null
    }

    private data class WorkspaceWindow(
        val size: DpSize,
        val position: WindowPosition,
        val placement: WindowPlacement,
    )
}
