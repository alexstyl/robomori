package com.avdbuddy

import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class ScrcpyControlTest {
    @Test fun `shared Android navigation controls map to device keycodes`() {
        assertEquals(4, scrcpyKeyCode("KEYCODE_BACK"))
        assertEquals(3, scrcpyKeyCode("KEYCODE_HOME"))
        assertEquals(26, scrcpyKeyCode("KEYCODE_POWER"))
        assertEquals(187, scrcpyKeyCode("KEYCODE_APP_SWITCH"))
    }

    @Test fun `touch packets use scrcpy coordinates and release pressure`() {
        val packet = ByteBuffer.wrap(scrcpyTouchPacket(0, 100, 200, 720, 1280))
        assertEquals(32, packet.remaining())
        assertEquals(2, packet.get().toInt())
        assertEquals(0, packet.get().toInt())
        assertEquals(0, packet.long)
        assertEquals(100, packet.int)
        assertEquals(200, packet.int)
        assertEquals(720, packet.short.toInt())
        assertEquals(1280, packet.short.toInt())
        assertEquals(-1, packet.short.toInt())
        assertEquals(0, ByteBuffer.wrap(scrcpyTouchPacket(1, 100, 200, 720, 1280)).getShort(22).toInt())
    }

    @Test fun `slow network coalesces movement without delaying presses or dropping release`() {
        val firstWrite = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val completed = CountDownLatch(3)
        val bytes = ByteArrayOutputStream()
        val control = ScrcpyControl(object : OutputStream() {
            override fun write(value: Int) = error("Expected packet write")
            override fun write(packet: ByteArray) {
                firstWrite.countDown()
                check(releaseWrite.await(3, TimeUnit.SECONDS))
                bytes.write(packet)
                completed.countDown()
            }
        }) { throw AssertionError(it) }
        try {
            control.touch(0, 0, 0, 720, 1280)
            assertTrue(firstWrite.await(1, TimeUnit.SECONDS))
            repeat(10000) { control.touch(2, it % 720, 200, 720, 1280) }
            control.touch(1, 719, 200, 720, 1280)
            releaseWrite.countDown()
            assertTrue(completed.await(1, TimeUnit.SECONDS))
            val packets = bytes.toByteArray()
            assertEquals(96, packets.size)
            assertEquals(listOf(0, 2, 1), listOf(packets[1].toInt(), packets[33].toInt(), packets[65].toInt()))
        } finally { releaseWrite.countDown(); control.close() }
    }
}
