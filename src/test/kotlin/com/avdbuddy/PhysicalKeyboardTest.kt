package com.avdbuddy

import java.awt.event.KeyEvent
import java.nio.ByteBuffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.*

class PhysicalKeyboardTest {
    @Test fun `keyboard registration declares a HID keyboard`() {
        val packet = ByteBuffer.wrap(scrcpyKeyboardCreatePacket())
        assertEquals(12, packet.get().toInt())
        assertEquals(1, packet.short.toInt())
        packet.short
        packet.short
        val nameLength = packet.get().toInt()
        packet.position(packet.position() + nameLength)
        val size = packet.short.toInt()
        val descriptor = ByteArray(size).also(packet::get)
        assertContentEquals(byteArrayOf(5, 1, 9, 6), descriptor.take(4).toByteArray())
        assertEquals(0, packet.remaining())
    }

    @ParameterizedTest
    @CsvSource("65,4", "90,29", "49,30", "48,39", "10,40", "8,42", "32,44", "37,80", "112,58")
    fun `host keys map to USB keyboard usages`(host: Int, usage: Int) {
        assertEquals(usage, physicalKeyUsage(host))
    }

    @Test fun `right shift keeps its physical side`() {
        assertEquals(229, physicalKeyUsage(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT))
    }

    @Test fun `key presses include the held modifier`() {
        val keyboard = PhysicalKeyboard()
        keyboard.key(225, true)
        assertContentEquals(byteArrayOf(2, 0, 4, 0, 0, 0, 0, 0), keyboard.key(4, true)!!.takeLast(8).toByteArray())
    }

    @Test fun `releasing a key preserves other held keys`() {
        val keyboard = PhysicalKeyboard()
        keyboard.key(4, true)
        keyboard.key(5, true)
        assertContentEquals(byteArrayOf(0, 0, 5, 0, 0, 0, 0, 0), keyboard.key(4, false)!!.takeLast(8).toByteArray())
    }

    @Test fun `host auto repeat is ignored because Android generates repeat`() {
        val keyboard = PhysicalKeyboard()
        keyboard.key(4, true)
        assertNull(keyboard.key(4, true))
    }

    @Test fun `losing focus releases every key and modifier`() {
        val keyboard = PhysicalKeyboard()
        keyboard.key(224, true)
        keyboard.key(4, true)
        assertContentEquals(ByteArray(8), keyboard.releaseAll().takeLast(8).toByteArray())
    }

    @Test fun `more than six held keys reports USB rollover`() {
        val keyboard = PhysicalKeyboard()
        (4..9).forEach { keyboard.key(it, true) }
        assertContentEquals(ByteArray(6) { 1 }, keyboard.key(10, true)!!.takeLast(6).toByteArray())
    }
}
