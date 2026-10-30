package com.avdbuddy.theme

import kotlin.math.abs

internal enum class MacWheelInput { TrackingStart, Tracking, Momentum, Unphased }

/** Native tracking holds the edge; release and momentum run on an independent return clock. */
internal class MacWheelBounce {
    private var lastInput: Long? = null
    private var lastDelta = 0f
    private var canStart = true
    private var started: Long? = null
    private var travel = 0f
    private var amplitude = 0f
    private var returnVelocity = 0f
    private var extent = 0f
    private var input = MacWheelInput.Unphased
    private val samples = ArrayDeque<Pair<Long, Float>>()
    var isTracking = false
        private set

    fun recordInput(delta: Float, nowMillis: Long, input: MacWheelInput = MacWheelInput.Unphased) {
        if (delta == 0f) return
        this.input = input
        if (input == MacWheelInput.TrackingStart || (input == MacWheelInput.Tracking && !isTracking)) {
            val current = if (isFinished(nowMillis)) 0f else offsetAt(nowMillis)
            travel = MacScrollPhysics.travel(current, extent)
            amplitude = current
            isTracking = true
            started = null
            canStart = true
            samples.clear()
        } else if (input == MacWheelInput.Momentum && isTracking) {
            release(nowMillis)
        }
        val previous = lastInput
        if (input == MacWheelInput.Unphased &&
            (previous == null || nowMillis - previous >= 180 || delta * lastDelta < 0f)) canStart = true
        lastInput = nowMillis
        lastDelta = delta
        samples.addLast(nowMillis to delta)
        while (samples.size > 2 && samples[1].first < nowMillis - 50) samples.removeFirst()
    }

    fun pull(delta: Float, extent: Float, nowMillis: Long, currentOffset: Float): Boolean {
        this.extent = extent
        if (isTracking) {
            if (canStart) {
                travel = MacScrollPhysics.travel(currentOffset, extent)
                canStart = false
            }
            travel += delta
            amplitude = MacScrollPhysics.stretch(travel, extent)
            return true
        }
        if (input == MacWheelInput.Momentum) {
            if (!canStart) return false
            amplitude = currentOffset
            returnVelocity = sampledVelocity(nowMillis)
            started = nowMillis
            canStart = false
            return true
        }
        if (canStart) {
            started = nowMillis
            travel = MacScrollPhysics.travel(currentOffset, extent)
            returnVelocity = 0f
            canStart = false
        } else {
            val start = started ?: return false
            // Capture the onset of a strong swipe without stretching throughout its momentum tail.
            if (nowMillis - start > 50) return false
        }
        travel += delta
        amplitude = MacScrollPhysics.stretch(travel, extent)
        return true
    }

    fun release(nowMillis: Long): Boolean {
        if (!isTracking) return false
        isTracking = false
        if (amplitude == 0f) return false
        started = nowMillis
        returnVelocity = sampledVelocity(nowMillis)
        canStart = false
        return true
    }

    private fun sampledVelocity(nowMillis: Long): Float {
        if (lastInput == null || nowMillis - lastInput!! > 50 || samples.isEmpty()) return 0f
        val seconds = (samples.last().first - samples.first().first) / 1000f
        if (seconds <= 0f) return samples.last().second * 60f
        val average = samples.drop(1).sumOf { it.second.toDouble() }.toFloat() / seconds
        val previous = samples.elementAtOrNull(samples.size - 2) ?: return average
        val interval = (samples.last().first - previous.first) / 1000f
        // Include the most recent acceleration instead of flattening a flick over the whole window.
        val recent = if (interval >= 0.005f) samples.last().second / interval else average
        return (average + recent) * 0.5f
    }

    fun offsetAt(nowMillis: Long): Float =
        if (isTracking) amplitude else MacScrollPhysics.returningOffset(amplitude, returnVelocity, elapsed(nowMillis))

    fun isFinished(nowMillis: Long): Boolean {
        if (isTracking) return false
        val seconds = elapsed(nowMillis)
        return seconds >= 1f ||
            (abs(offsetAt(nowMillis)) < 0.1f &&
                abs(MacScrollPhysics.returningVelocity(amplitude, returnVelocity, seconds)) < 1f)
    }

    private fun elapsed(nowMillis: Long): Float =
        started?.let { (nowMillis - it).coerceAtLeast(0) / 1000f } ?: 0f

    fun reset() {
        lastInput = null
        lastDelta = 0f
        canStart = true
        started = null
        travel = 0f
        amplitude = 0f
        returnVelocity = 0f
        extent = 0f
        isTracking = false
        input = MacWheelInput.Unphased
        samples.clear()
    }
}
