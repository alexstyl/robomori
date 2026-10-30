package com.avdbuddy.theme

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacWheelBounceTest {
    @Test fun `a forceful swipe returns while its momentum packets keep arriving`() {
        val bounce = MacWheelBounce()
        for (time in 0L..1000L step 16) {
            bounce.recordInput(100f, time)
            bounce.pull(100f, 600f, time, bounce.offsetAt(time))
            if (time >= 400) assertTrue(bounce.offsetAt(time) < 0.25f)
        }
        assertTrue(bounce.isFinished(1000))
    }

    @Test fun `early packets strengthen the initial response without restarting its return`() {
        val bounce = MacWheelBounce()
        bounce.recordInput(1f, 0)
        bounce.pull(1f, 600f, 0, 0f)
        val first = bounce.offsetAt(0)
        bounce.recordInput(200f, 16)
        assertTrue(bounce.pull(200f, 600f, 16, bounce.offsetAt(16)))
        assertTrue(bounce.offsetAt(16) > first)
        bounce.recordInput(500f, 64)
        assertFalse(bounce.pull(500f, 600f, 64, bounce.offsetAt(64)))
        assertTrue(bounce.offsetAt(400) < 0.15f)
    }

    @Test fun `momentum cannot start another bounce after the edge reaches rest`() {
        val bounce = MacWheelBounce()
        for (time in 0L..2000L step 16) {
            bounce.recordInput(100f, time)
            val accepted = bounce.pull(100f, 600f, time, bounce.offsetAt(time))
            if (time > 50) assertFalse(accepted)
        }
        assertEquals(0f, bounce.offsetAt(2000), 0.00001f)
    }

    @Test fun `a new swipe or reversed input can respond again`() {
        val bounce = MacWheelBounce()
        bounce.recordInput(100f, 0)
        assertTrue(bounce.pull(100f, 600f, 0, 0f))
        bounce.recordInput(100f, 100)
        assertFalse(bounce.pull(100f, 600f, 100, 0f))
        bounce.recordInput(100f, 400)
        assertTrue(bounce.pull(100f, 600f, 400, 0f))
        bounce.recordInput(-100f, 416)
        assertTrue(bounce.pull(-100f, 600f, 416, bounce.offsetAt(416)))
    }

    @Test fun `native gentle pull stays visible while fingers rest and releases immediately`() {
        val bounce = MacWheelBounce()
        for (i in 0..19) {
            val now = i * 16L
            bounce.recordInput(5f, now, if (i == 0) MacWheelInput.TrackingStart else MacWheelInput.Tracking)
            assertTrue(bounce.pull(5f, 590f, now, bounce.offsetAt(now)))
        }
        val held = bounce.offsetAt(304)
        assertTrue(held in 7f..7.5f)
        assertEquals(held, bounce.offsetAt(600))
        assertFalse(bounce.isFinished(600))
        assertTrue(bounce.release(620))
        assertEquals(held, bounce.offsetAt(620))
        assertTrue(bounce.offsetAt(675) in held * 0.49f..held * 0.51f)
    }

    @Test fun `native momentum does not catch a released edge`() {
        val bounce = MacWheelBounce()
        for (i in 0..7) {
            val now = i * 16L
            bounce.recordInput(20f, now, if (i == 0) MacWheelInput.TrackingStart else MacWheelInput.Tracking)
            bounce.pull(20f, 590f, now, bounce.offsetAt(now))
        }
        assertTrue(bounce.release(128))
        for (i in 0..60) {
            val now = 144L + i * 16L
            bounce.recordInput(20f, now, MacWheelInput.Momentum)
            assertFalse(bounce.pull(20f, 590f, now, bounce.offsetAt(now)))
        }
        assertTrue(bounce.isFinished(1200))
    }

    @Test fun `native repeated pulls rearm even during a momentum tail`() {
        val bounce = MacWheelBounce()
        for (start in listOf(0L, 350L, 700L)) {
            bounce.recordInput(100f, start, MacWheelInput.TrackingStart)
            assertTrue(bounce.pull(100f, 590f, start, bounce.offsetAt(start)))
            assertTrue(bounce.offsetAt(start) >= 7f)
            assertTrue(bounce.release(start + 100))
            bounce.recordInput(10f, start + 200, MacWheelInput.Momentum)
            assertFalse(bounce.pull(10f, 590f, start + 200, bounce.offsetAt(start + 200)))
        }
    }

    @Test fun `recorded hard swipe produces a Finder sized response`() {
        val bounce = MacWheelBounce()
        for ((i, delta) in listOf(2f, 4f, 8f, 16f, 32f, 48f, 64f, 80f).withIndex()) {
            val now = i * 17L
            bounce.recordInput(delta, now, if (i == 0) MacWheelInput.TrackingStart else MacWheelInput.Tracking)
            bounce.pull(delta, 590f, now, bounce.offsetAt(now))
        }
        bounce.release(136)
        val peak = (136L..1000L step 4).maxOf { bounce.offsetAt(it) }
        // Native video peaked at 46 points for this input profile.
        assertTrue(peak in 42f..48f, "Peak was $peak")
        assertTrue(bounce.offsetAt(850) < 0.5f)
    }

    @Test fun `a completed response does not prevent a later fling from hitting an edge`() {
        val bounce = MacWheelBounce()
        bounce.recordInput(100f, 0, MacWheelInput.TrackingStart)
        bounce.pull(100f, 590f, 0, 0f)
        bounce.release(100)
        assertTrue(bounce.isFinished(1000))
        // This gesture scrolls normally until its momentum reaches the boundary.
        bounce.recordInput(100f, 2000, MacWheelInput.TrackingStart)
        bounce.recordInput(100f, 2016, MacWheelInput.Tracking)
        assertFalse(bounce.release(2032))
        bounce.recordInput(100f, 2048, MacWheelInput.Momentum)
        assertTrue(bounce.pull(100f, 590f, 2048, 0f))
        assertTrue(bounce.offsetAt(2128) > 0f)
    }
}
