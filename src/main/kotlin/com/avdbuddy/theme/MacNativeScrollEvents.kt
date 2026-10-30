package com.avdbuddy.theme

import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.EventQueue

/** Observes only this application's NSEvents, before AWT drops their gesture phases. */
internal object MacNativeScrollEvents {
    data class Packet(val phase: Int, val momentum: Int, val timeMillis: Long = 0) {
        val input: MacWheelInput get() = when {
            momentum != 0 -> MacWheelInput.Momentum
            phase and 1 != 0 -> MacWheelInput.TrackingStart
            phase and (2 or 4) != 0 -> MacWheelInput.Tracking
            else -> MacWheelInput.Unphased
        }
        val isRelease: Boolean get() = phase and (8 or 16) != 0
        fun eventTime(nowMillis: Long): Long =
            if (kotlin.math.abs(timeMillis - nowMillis) < 1000) timeMillis else nowMillis
    }

    var current: Packet? = null
        private set
    var active: ((Packet) -> Unit)? = null

    // Application-lifetime ownership keeps native blocks and JNA callbacks alive.
    private val monitor by lazy { Monitor() }
    fun install() { monitor }

    private interface EventCallback : Callback {
        fun invoke(block: Pointer, event: Pointer): Pointer
    }
    private interface MainCallback : Callback { fun invoke(block: Pointer) }

    private class Monitor {
        private val objc = NativeLibrary.getInstance("objc")
        private val system = NativeLibrary.getInstance("System")
        private val message = objc.getFunction("objc_msgSend")
        private val eventClass = objc.getFunction("objc_getClass").invokePointer(arrayOf("NSEvent"))
        private fun selector(name: String): Pointer =
            objc.getFunction("sel_registerName").invokePointer(arrayOf(name))
        private val phase = selector("phase")
        private val momentum = selector("momentumPhase")
        private val timestamp = selector("timestamp")
        private var token: Pointer? = null
        private val handler = object : EventCallback {
            override fun invoke(block: Pointer, event: Pointer): Pointer {
                val packet = Packet(
                    message.invokeLong(arrayOf(event, phase)).toInt(),
                    message.invokeLong(arrayOf(event, momentum)).toInt(),
                    (message.invokeDouble(arrayOf(event, timestamp)) * 1000).toLong(),
                )
                // This marker is queued before AWT dispatches the corresponding wheel event.
                EventQueue.invokeLater {
                    current = packet
                    if (packet.isRelease) active?.invoke(packet)
                }
                return event
            }
        }
        private val eventBlock = Block(system, handler)
        private val setup = object : MainCallback {
            override fun invoke(block: Pointer) {
                token = message.invokePointer(arrayOf(
                    eventClass, selector("addLocalMonitorForEventsMatchingMask:handler:"),
                    1L shl 22, eventBlock.memory,
                ))
            }
        }
        private val setupBlock = Block(system, setup)

        init {
            system.getFunction("dispatch_async").invokeVoid(arrayOf(
                system.getGlobalVariableAddress("_dispatch_main_q"), setupBlock.memory,
            ))
        }
    }

    /** Global block layout defined by Clang's Apple Block ABI; no captured native storage. */
    private class Block(system: NativeLibrary, private val callback: Callback) {
        private val descriptor = Memory(16).apply {
            setLong(0, 0)
            setLong(8, 32)
        }
        val memory = Memory(32).apply {
            clear()
            setPointer(0, system.getGlobalVariableAddress("_NSConcreteGlobalBlock"))
            setInt(8, 1 shl 28)
            setPointer(16, CallbackReference.getFunctionPointer(callback))
            setPointer(24, descriptor)
        }
    }
}
