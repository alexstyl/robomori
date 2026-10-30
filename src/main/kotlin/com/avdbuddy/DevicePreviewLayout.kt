package com.avdbuddy

internal val DeviceType.previewViewportFraction: Float
    get() = when (this) {
        DeviceType.WEAR -> .24f
        DeviceType.TABLET -> .65f
        else -> .82f
    }
