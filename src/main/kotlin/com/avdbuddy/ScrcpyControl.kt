package com.avdbuddy

import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal fun scrcpyKeyCode(value: String): Int? = value.toIntOrNull() ?: when (value) {
    "KEYCODE_HOME" -> 3
    "KEYCODE_BACK" -> 4
    "KEYCODE_DPAD_UP" -> 19
    "KEYCODE_DPAD_DOWN" -> 20
    "KEYCODE_DPAD_LEFT" -> 21
    "KEYCODE_DPAD_RIGHT" -> 22
    "KEYCODE_DPAD_CENTER" -> 23
    "KEYCODE_POWER" -> 26
    "KEYCODE_MEDIA_PLAY_PAUSE" -> 85
    "KEYCODE_APP_SWITCH" -> 187
    "KEYCODE_STEM_PRIMARY" -> 264
    else -> null
}

internal fun scrcpyTouchPacket(action: Int, x: Int, y: Int, width: Int, height: Int): ByteArray =
    ByteBuffer.allocate(32).put(2).put(action.toByte()).putLong(0)
        .putInt(x.coerceIn(0, width - 1)).putInt(y.coerceIn(0, height - 1))
        .putShort(width.toShort()).putShort(height.toShort())
        .putShort(if (action == 1) 0 else (-1).toShort()).putInt(0).putInt(0).array()

internal class ScrcpyControl(private val output: OutputStream, onFailure: (Exception) -> Unit) : AutoCloseable {
    private data class Message(val bytes: ByteArray, val move: Boolean)
    private val queue = LinkedBlockingDeque<Message>()
    private val active = AtomicBoolean(true)
    private val keyboard = PhysicalKeyboard()

    fun connectKeyboard() {
        if (active.get()) queue.offerLast(Message(scrcpyKeyboardCreatePacket(), false))
    }

    fun keyboardKey(usage: Int, down: Boolean) {
        if (!active.get()) return
        synchronized(queue) { keyboard.key(usage, down)?.let { queue.offerLast(Message(it, false)) } }
    }

    fun releaseKeyboard() {
        if (!active.get()) return
        synchronized(queue) { queue.offerLast(Message(keyboard.releaseAll(), false)) }
    }
    private val writer = thread(isDaemon = true, name = "scrcpy-input") {
        try {
            while (active.get()) {
                val message = queue.takeFirst()
                output.write(message.bytes)
                output.flush()
            }
        } catch (failure: Exception) { if (active.get()) onFailure(failure) }
    }

    fun touch(action: Int, x: Int, y: Int, width: Int, height: Int) {
        if (!active.get()) return
        synchronized(queue) {
            // Superseded movement can be skipped; presses and releases retain their order.
            if (action == 2 && queue.peekLast()?.move == true) queue.pollLast()
            queue.offerLast(Message(scrcpyTouchPacket(action, x, y, width, height), action == 2))
        }
    }

    fun key(code: Int) {
        if (!active.get()) return
        synchronized(queue) {
            for (action in 0..1) queue.offerLast(Message(ByteBuffer.allocate(14).put(0).put(action.toByte())
                .putInt(code).putInt(0).putInt(0).array(), false))
        }
    }

    override fun close() {
        active.set(false)
        writer.interrupt()
        runCatching { output.close() }
        queue.clear()
    }
}
