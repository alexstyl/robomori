package com.avdbuddy

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmulatorFocusStateTest {
    @Test fun `exiting focus restores workspace even after compact window is moved and resized`() {
        val originalSize = DpSize(1040.dp, 720.dp)
        val originalPosition = WindowPosition(120.dp, 80.dp)
        val window = WindowState(size = originalSize, position = originalPosition, placement = WindowPlacement.Maximized)
        val focus = EmulatorFocusState(window)
        val compactSize = DpSize(365.dp, 800.dp)

        focus.enter(compactSize)
        assertTrue(focus.active)
        assertEquals(compactSize, window.size)
        assertEquals(WindowPlacement.Floating, window.placement)

        window.size = DpSize(400.dp, 700.dp)
        window.position = WindowPosition(500.dp, 200.dp)
        focus.enter(compactSize)
        focus.exit()
        assertFalse(focus.active)
        assertEquals(originalSize, window.size)
        assertEquals(originalPosition, window.position)
        assertEquals(WindowPlacement.Maximized, window.placement)
    }

    @Test fun `each focus session captures the latest workspace and repeated exits do nothing`() {
        val window = WindowState(size = DpSize(1040.dp, 720.dp))
        val focus = EmulatorFocusState(window)
        val compact = DpSize(365.dp, 800.dp)
        focus.enter(compact)
        focus.exit()
        val resized = DpSize(1200.dp, 900.dp)
        window.size = resized
        focus.exit()
        assertEquals(resized, window.size)
        focus.enter(compact)
        focus.exit()
        assertEquals(resized, window.size)
    }
}
