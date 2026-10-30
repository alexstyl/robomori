package com.avdbuddy.theme

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.E

/** Pure motion in points, fitted to sampled AppKit responses. */
internal object MacScrollPhysics {
    private const val resistance = 0.075f
    private const val decay = 12.5f
    private const val velocityCarry = 0.31f
    private const val stretchLimit = 80f

    fun nativeStretch(travel: Float, extent: Float): Float =
        if (extent <= 0f) 0f else travel * resistance / (1f + abs(travel) * resistance / extent)

    fun stretch(travel: Float, extent: Float): Float =
        nativeStretch(travel, extent).coerceIn(-stretchLimit, stretchLimit)

    fun travel(stretch: Float, extent: Float): Float {
        if (extent <= 0f) return 0f
        return stretch / (resistance * (1f - abs(stretch) / extent).coerceAtLeast(0.00001f))
    }

    fun returningOffset(initial: Float, outwardVelocity: Float, seconds: Float): Float =
        ((initial + carriedVelocity(initial, outwardVelocity) * seconds) * exp(-decay * seconds))
            .coerceIn(-stretchLimit, stretchLimit)

    fun returningVelocity(initial: Float, outwardVelocity: Float, seconds: Float): Float {
        val carried = carriedVelocity(initial, outwardVelocity)
        return (carried - decay * (initial + carried * seconds)) * exp(-decay * seconds)
    }

    private fun carriedVelocity(initial: Float, velocity: Float): Float {
        // Bound the impulse rather than flattening the curve against the displacement limit.
        // max(t * exp(-decay * t)) = 1 / (decay * e).
        val maximum = (stretchLimit - abs(initial)).coerceAtLeast(0f) * decay * E.toFloat()
        return (velocityCarry * velocity).coerceIn(-maximum, maximum)
    }
}
