package com.avdbuddy.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation

internal data class MacOverscrollFactory(
    private val density: Float,
) {
    init { MacNativeScrollEvents.install() }

    fun createOverscrollEffect(orientation: Orientation): OverscrollEffect =
        MacOverscrollEffect(density, orientation)
}
