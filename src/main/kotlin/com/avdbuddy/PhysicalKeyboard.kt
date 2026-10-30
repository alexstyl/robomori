package com.avdbuddy

import java.awt.event.KeyEvent
import java.nio.ByteBuffer

private const val keyboardId = 1

internal fun scrcpyKeyboardCreatePacket(): ByteArray {
    // USB HID boot keyboard: eight modifiers, one reserved byte, six held keys.
    val descriptor = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xa1, 0x01, 0x05, 0x07,
        0x19, 0xe0, 0x29, 0xe7, 0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x75, 0x08, 0x95, 0x01, 0x81, 0x01,
        0x19, 0x00, 0x29, 0x65, 0x15, 0x00, 0x25, 0x65,
        0x75, 0x08, 0x95, 0x06, 0x81, 0x00, 0xc0,
    ).map(Int::toByte).toByteArray()
    val name = "AvdBuddy Keyboard".toByteArray(Charsets.UTF_8)
    return ByteBuffer.allocate(10 + name.size + descriptor.size).put(12).putShort(keyboardId.toShort())
        .putShort(0).putShort(0).put(name.size.toByte()).put(name).putShort(descriptor.size.toShort()).put(descriptor).array()
}

internal fun physicalKeyUsage(code: Int, location: Int = KeyEvent.KEY_LOCATION_STANDARD): Int? = when (code) {
    in KeyEvent.VK_A..KeyEvent.VK_Z -> code - KeyEvent.VK_A + 4
    in KeyEvent.VK_1..KeyEvent.VK_9 -> code - KeyEvent.VK_1 + 30
    KeyEvent.VK_0 -> 39
    in KeyEvent.VK_F1..KeyEvent.VK_F12 -> code - KeyEvent.VK_F1 + 58
    KeyEvent.VK_ENTER -> if (location == KeyEvent.KEY_LOCATION_NUMPAD) 88 else 40
    KeyEvent.VK_ESCAPE -> 41
    KeyEvent.VK_BACK_SPACE -> 42
    KeyEvent.VK_TAB -> 43
    KeyEvent.VK_SPACE -> 44
    KeyEvent.VK_MINUS -> 45
    KeyEvent.VK_EQUALS -> 46
    KeyEvent.VK_OPEN_BRACKET -> 47
    KeyEvent.VK_CLOSE_BRACKET -> 48
    KeyEvent.VK_BACK_SLASH -> 49
    KeyEvent.VK_SEMICOLON -> 51
    KeyEvent.VK_QUOTE -> 52
    KeyEvent.VK_BACK_QUOTE -> 53
    KeyEvent.VK_COMMA -> 54
    KeyEvent.VK_PERIOD -> 55
    KeyEvent.VK_SLASH -> 56
    KeyEvent.VK_CAPS_LOCK -> 57
    KeyEvent.VK_INSERT -> 73
    KeyEvent.VK_HOME -> 74
    KeyEvent.VK_PAGE_UP -> 75
    KeyEvent.VK_DELETE -> 76
    KeyEvent.VK_END -> 77
    KeyEvent.VK_PAGE_DOWN -> 78
    KeyEvent.VK_RIGHT -> 79
    KeyEvent.VK_LEFT -> 80
    KeyEvent.VK_DOWN -> 81
    KeyEvent.VK_UP -> 82
    KeyEvent.VK_NUM_LOCK -> 83
    KeyEvent.VK_DIVIDE -> 84
    KeyEvent.VK_MULTIPLY -> 85
    KeyEvent.VK_SUBTRACT -> 86
    KeyEvent.VK_ADD -> 87
    in KeyEvent.VK_NUMPAD1..KeyEvent.VK_NUMPAD9 -> code - KeyEvent.VK_NUMPAD1 + 89
    KeyEvent.VK_NUMPAD0 -> 98
    KeyEvent.VK_DECIMAL -> 99
    KeyEvent.VK_CONTROL -> if (location == KeyEvent.KEY_LOCATION_RIGHT) 228 else 224
    KeyEvent.VK_SHIFT -> if (location == KeyEvent.KEY_LOCATION_RIGHT) 229 else 225
    KeyEvent.VK_ALT -> if (location == KeyEvent.KEY_LOCATION_RIGHT) 230 else 226
    KeyEvent.VK_ALT_GRAPH -> 230
    KeyEvent.VK_META, KeyEvent.VK_WINDOWS -> if (location == KeyEvent.KEY_LOCATION_RIGHT) 231 else 227
    else -> null
}

internal class PhysicalKeyboard {
    private val held = sortedSetOf<Int>()

    fun key(usage: Int, down: Boolean): ByteArray? {
        require(usage in 4..101 || usage in 224..231)
        val changed = if (down) held.add(usage) else held.remove(usage)
        return if (changed) packet() else null
    }

    fun releaseAll(): ByteArray {
        held.clear()
        return packet()
    }

    private fun packet(): ByteArray {
        val keys = held.filter { it < 224 }
        val modifiers = held.filter { it >= 224 }.fold(0) { bits, key -> bits or (1 shl (key - 224)) }
        val report = ByteArray(8)
        report[0] = modifiers.toByte()
        if (keys.size > 6) report.fill(1, 2) else keys.forEachIndexed { index, key -> report[index + 2] = key.toByte() }
        return ByteBuffer.allocate(13).put(13).putShort(keyboardId.toShort()).putShort(8).put(report).array()
    }
}
