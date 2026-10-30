package com.avdbuddy.theme

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacNativeScrollPacketTest {
    @Test fun `AppKit changed and stationary phases continue tracking`() {
        for (phase in listOf(2, 4)) {
            val packet = MacNativeScrollEvents.Packet(phase, 0)
            assertEquals(MacWheelInput.Tracking, packet.input)
            assertFalse(packet.isRelease)
        }
    }

    @Test fun `AppKit ended and cancelled phases release the edge`() {
        for (phase in listOf(8, 16)) assertTrue(MacNativeScrollEvents.Packet(phase, 0).isRelease)
        assertEquals(MacWheelInput.TrackingStart, MacNativeScrollEvents.Packet(1, 0).input)
        assertEquals(MacWheelInput.Momentum, MacNativeScrollEvents.Packet(0, 4).input)
    }

    @Test fun `source timestamps are used only when they share the animation clock`() {
        assertEquals(9950L, MacNativeScrollEvents.Packet(4, 0, 9950).eventTime(10000))
        assertEquals(10000L, MacNativeScrollEvents.Packet(4, 0, 0).eventTime(10000))
        assertEquals(10000L, MacNativeScrollEvents.Packet(4, 0, 20000).eventTime(10000))
    }
}
