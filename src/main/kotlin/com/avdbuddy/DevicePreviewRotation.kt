package com.avdbuddy

import androidx.compose.animation.core.spring

internal fun deviceRotationAnimationSpec() = spring<Float>(dampingRatio = .75f, stiffness = 700f)
