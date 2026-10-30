package com.avdbuddy

import org.junit.jupiter.api.Test
import kotlin.test.*

class FailedOperationMessageTest {
    @Test fun `notices name one or two devices and count larger batches`() {
        assertEquals("Pixel 10 did not start. Try again.", FailedOperation.Start(listOf("Pixel 10")).message)
        assertEquals("Could not stop Pixel 10 and Pixel Tablet. Try again.",
            FailedOperation.Stop(listOf("Pixel 10", "Pixel Tablet")).message)
        assertEquals("Could not remove 3 emulators. Try again.", FailedOperation.Remove(listOf("A", "B", "C")).message)
    }
}
