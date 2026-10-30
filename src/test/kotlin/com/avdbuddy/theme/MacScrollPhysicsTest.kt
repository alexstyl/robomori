package com.avdbuddy.theme

import kotlin.math.abs
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacScrollPhysicsTest {
    @Test fun `resistance matches sampled AppKit values in points`() {
        val samples = listOf(10f to 0.74812968f, 50f to 3.70370370f, 100f to 7.31707317f,
            200f to 14.28571429f, 500f to 33.33333333f)
        for ((travel, expected) in samples) {
            assertEquals(expected, MacScrollPhysics.nativeStretch(travel, 300f), 0.00001f)
            assertEquals(-expected, MacScrollPhysics.nativeStretch(-travel, 300f), 0.00001f)
        }
    }

    @Test fun `still release matches sampled AppKit return`() {
        val samples = listOf(0.016f to 16.37461506f, 0.055f to 10.05663156f,
            0.1f to 5.73009594f, 0.2f to 1.64169997f, 0.3f to 0.47035492f, 0.4f to 0.13475894f)
        for ((seconds, expected) in samples) {
            assertEquals(expected, MacScrollPhysics.returningOffset(20f, 0f, seconds), 0.00001f)
        }
    }

    @Test fun `edge fling carries velocity once and returns without oscillation`() {
        assertEquals(13.68511521f, MacScrollPhysics.returningOffset(0f, 1500f, 0.08f), 0.00001f)
        assertTrue(MacScrollPhysics.returningVelocity(0f, 1500f, 0.016f) > 0f)
        assertTrue(MacScrollPhysics.returningVelocity(0f, 1500f, 0.2f) < 0f)
        assertTrue(MacScrollPhysics.returningOffset(0f, 1500f, 1f) < 0.01f)
    }

    @Test fun `catching a returning edge preserves its visible position`() {
        for (extent in listOf(50f, 300f, 1000f)) {
            for (initialTravel in listOf(-1000f, -100f, 0f, 100f, 1000f)) {
                val offset = MacScrollPhysics.stretch(initialTravel, extent)
                val caught = MacScrollPhysics.travel(offset, extent)
                assertEquals(offset, MacScrollPhysics.stretch(caught, extent), 0.0001f)
            }
        }
    }

    @Test fun `large gestures stay bounded and missing viewport does not stretch`() {
        assertEquals(0f, MacScrollPhysics.stretch(100f, 0f))
        assertEquals(0f, MacScrollPhysics.travel(20f, 0f))
        for (direction in listOf(-1f, 1f)) {
            assertTrue(abs(MacScrollPhysics.stretch(direction * 100000f, 1000f)) <= 80f)
            assertTrue(abs(MacScrollPhysics.returningOffset(0f, direction * 100000f, 0.08f)) <= 80f)
        }
    }

    @Test fun `gentle pull matches the displacement recorded in Finder`() {
        assertTrue(MacScrollPhysics.stretch(100f, 590f) in 7f..7.5f)
    }

    @Test fun `extreme release velocity cannot create a floating plateau at the limit`() {
        val before = MacScrollPhysics.returningOffset(20f, 100000f, 0.08f)
        val after = MacScrollPhysics.returningOffset(20f, 100000f, 0.1f)
        assertTrue(before < 80f)
        assertTrue(after < before)
        assertTrue(MacScrollPhysics.returningVelocity(80f, 100000f, 0f) < 0f)
    }
}
