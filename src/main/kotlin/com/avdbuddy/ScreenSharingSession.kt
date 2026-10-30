package com.avdbuddy

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*

internal sealed interface ScreenSharingState {
    data object Starting : ScreenSharingState
    data class Streaming(val frame: ImageBitmap) : ScreenSharingState
    data class Failed(val cause: Throwable) : ScreenSharingState
    data object Stopped : ScreenSharingState
}

internal class ScreenSharingSession(
    val serial: String,
    val settings: AppSettings,
    val mirror: DeviceMirror,
    scope: CoroutineScope,
) {
    var state by mutableStateOf<ScreenSharingState>(ScreenSharingState.Starting); private set
    val frame get() = (state as? ScreenSharingState.Streaming)?.frame
    val failure get() = (state as? ScreenSharingState.Failed)?.cause
    private val job = scope.launch {
        try { mirror.frames(settings, serial).collect { state = ScreenSharingState.Streaming(it) } }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            state = ScreenSharingState.Failed(e)
        }
    }

    fun stop() {
        job.cancel()
        state = ScreenSharingState.Stopped
    }
}
