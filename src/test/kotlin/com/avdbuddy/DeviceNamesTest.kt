package com.avdbuddy

import kotlin.test.*
import org.junit.jupiter.api.Test

class DeviceNamesTest {
    @Test fun `duplicate names are rejected without case sensitivity`() {
        assertEquals(DeviceNameIssue.Duplicate("pixel_10"), deviceNameError("pixel_10", listOf("Pixel_10")))
    }
    @Test fun `different names remain valid`() {
        assertNull(deviceNameError("Pixel_11", listOf("Pixel_10")))
    }
    @Test fun `blank name produces empty name issue`() {
        assertEquals(DeviceNameIssue.Empty, deviceNameError("  ", emptyList()))
    }
    @Test fun `spaces are rejected in storage names`() {
        assertEquals(DeviceNameIssue.UnsupportedCharacters, deviceNameError("Pixel 10", emptyList()))
    }
    @Test fun `path traversal is rejected in storage names`() {
        assertEquals(DeviceNameIssue.UnsupportedCharacters, deviceNameError("../Pixel", emptyList()))
    }
    @Test fun `reserved Windows names are rejected`() {
        assertEquals(DeviceNameIssue.Reserved, deviceNameError("CON", emptyList()))
    }
    @Test fun `portable identifiers accept surrounding whitespace`() {
        assertNull(deviceNameError(" Pixel_10-Pro.2 ",emptyList()))
    }
    @Test fun `creation names reject blank input`() {
        assertEquals(CreationNameIssue.Empty, creationNameError("  "))
    }
    @Test fun `creation names reject control characters`() {
        assertEquals(CreationNameIssue.ControlCharacters, creationNameError("Name\nInjected=value"))
    }
    @Test fun `creation names reject more than one hundred characters`() {
        assertEquals(CreationNameIssue.TooLong, creationNameError("a".repeat(101)))
    }
    @Test fun `creation names reject duplicate display names`() {
        assertEquals(CreationNameIssue.Duplicate, creationNameError("My Pixel", listOf("my pixel")))
    }
}
