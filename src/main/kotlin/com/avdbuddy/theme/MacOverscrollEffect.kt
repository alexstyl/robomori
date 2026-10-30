package com.avdbuddy.theme

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import java.awt.event.MouseWheelEvent
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Single-axis macOS motion; all distances are points until content placement. */
internal class MacOverscrollEffect(
    private val density: Float,
    private val orientation: Orientation,
) : OverscrollEffect {
    private var extent = 0f
    private var travel = 0f
    private var offset by mutableFloatStateOf(0f)
    private var returnJob: Job? = null
    private val wheelBounce = MacWheelBounce()
    private var isWheelReturning = false
    private val bounceNode = BounceNode()
    override val node: DelegatableNode get() = bounceNode
    override val isInProgress: Boolean get() = abs(offset) * density >= 0.5f

    private fun Offset.onAxis(): Float = if (orientation == Orientation.Vertical) y else x
    private fun Velocity.onAxis(): Float = if (orientation == Orientation.Vertical) y else x
    private fun Float.asOffset(): Offset =
        if (orientation == Orientation.Vertical) Offset(0f, this) else Offset(this, 0f)

    private fun catchBounce() {
        returnJob?.cancel()
        returnJob = null
        isWheelReturning = false
        travel = MacScrollPhysics.travel(offset, extent)
    }

    private fun pull(delta: Float) {
        if (extent <= 0f) return
        travel += delta
        offset = MacScrollPhysics.stretch(travel, extent)
    }

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        catchBounce()
        val input = delta.onAxis() / density
        var available = input
        if (source == NestedScrollSource.UserInput && travel != 0f) {
            val next = travel + input
            available = if (travel * next < 0f) next else 0f
            travel = if (available != 0f) 0f else next
            offset = MacScrollPhysics.stretch(travel, extent)
        }
        val consumed = performScroll((available * density).asOffset()).onAxis() / density
        pull(available - consumed)
        // Unconsumed fling input stops Foundation's fling; this effect owns the return.
        return ((if (source == NestedScrollSource.UserInput && extent > 0f) input else consumed) * density).asOffset()
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val axisVelocity = velocity.onAxis().asOffset().let { Velocity(it.x, it.y) }
        val available = if (isInProgress) Velocity.Zero else axisVelocity
        val consumed = performFling(available)
        returnToRest((axisVelocity - consumed).onAxis() / density)
    }

    private fun returnToRest(velocity: Float = 0f) {
        if (velocity == 0f && returnJob?.isActive == true) return
        returnJob?.cancel()
        returnJob = null
        isWheelReturning = false
        if (!bounceNode.isAttached || (offset == 0f && velocity == 0f)) return
        val initial = offset
        returnJob = bounceNode.coroutineScope.launch {
            val start = withFrameNanos { it }
            do {
                val seconds = withFrameNanos { (it - start) / 1_000_000_000f }
                offset = MacScrollPhysics.returningOffset(initial, velocity, seconds)
                val speed = MacScrollPhysics.returningVelocity(initial, velocity, seconds)
            } while (seconds < 1f && (abs(offset) >= 0.1f || abs(speed) >= 1f))
            offset = 0f
            travel = 0f
            returnJob = null
        }
    }

    private fun returnWheelToRest() {
        if (isWheelReturning && returnJob?.isActive == true) return
        returnJob?.cancel()
        isWheelReturning = true
        returnJob = bounceNode.coroutineScope.launch {
            do {
                withFrameNanos { }
                val now = System.nanoTime() / 1_000_000
                offset = wheelBounce.offsetAt(now)
            } while (!wheelBounce.isFinished(now))
            offset = 0f
            travel = 0f
            returnJob = null
            isWheelReturning = false
        }
    }

    private inner class BounceNode : Modifier.Node(), LayoutModifierNode,
        LayoutAwareModifierNode, PointerInputModifierNode {
        private val onNativeRelease: (MacNativeScrollEvents.Packet) -> Unit = { packet ->
            if (wheelBounce.release(packet.eventTime(System.nanoTime() / 1_000_000))) returnWheelToRest()
        }
        override fun onRemeasured(size: IntSize) {
            extent = (if (orientation == Orientation.Vertical) size.height else size.width) / density
        }

        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                val pixels = (offset * density).roundToInt()
                placeable.placeWithLayer(
                    if (orientation == Orientation.Vertical) IntOffset(0, pixels) else IntOffset(pixels, 0)
                )
            }
        }

        override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
            if (pointerEvent.type != PointerEventType.Scroll) return
            val delta = pointerEvent.changes.sumOf { it.scrollDelta.onAxis().toDouble() }.toFloat()
            if (delta == 0f) return
            val wheel = pointerEvent.awtEventOrNull as? MouseWheelEvent
            val points = -delta * 10f * (wheel?.scrollAmount ?: 1)
            if (pass == PointerEventPass.Initial) {
                val packet = MacNativeScrollEvents.current
                MacNativeScrollEvents.active = onNativeRelease
                val now = System.nanoTime() / 1_000_000
                wheelBounce.recordInput(points, packet?.eventTime(now) ?: now,
                    packet?.input ?: MacWheelInput.Unphased)
            }
            // Foundation's scroll node is delegated at the same level: wait until it has acted.
            if (pass == PointerEventPass.Final) {
                if (pointerEvent.changes.none { it.isConsumed }) {
                    // Foundation's MacOSCocoaConfig converts each wheel unit to 10 points.
                    if (wheelBounce.pull(
                        points, extent,
                        System.nanoTime() / 1_000_000, offset,
                    )) {
                        if (wheelBounce.isTracking) {
                            returnJob?.cancel()
                            returnJob = null
                            isWheelReturning = false
                            offset = wheelBounce.offsetAt(System.nanoTime() / 1_000_000)
                        } else returnWheelToRest()
                    }
                    pointerEvent.changes.forEach { it.consume() }
                } else if (isInProgress) {
                    returnToRest()
                }
            }
        }

        override fun onCancelPointerInput() {
            wheelBounce.release(System.nanoTime() / 1_000_000)
            returnToRest()
        }

        override fun onDetach() {
            if (MacNativeScrollEvents.active === onNativeRelease) MacNativeScrollEvents.active = null
            returnJob?.cancel()
            wheelBounce.reset()
            isWheelReturning = false
            offset = 0f
            travel = 0f
        }
    }
}
