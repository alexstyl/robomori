package com.avdbuddy

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class LicenseReminderTest {
    @TempDir lateinit var root: File
    private val clock = TestClock()
    private fun reminder() = LicenseReminder(File(root, "reminder"), clock)

    /** Leaves the app for [minutes] and returns, reporting whether that reminded the user. */
    private fun LicenseReminder.returnAfter(minutes: Long, licensed: Boolean = false, canShow: Boolean = true): Boolean {
        focusChanged(false, licensed, canShow)
        clock.advanceMinutes(minutes)
        return focusChanged(true, licensed, canShow)
    }

    @Test fun `startup and short app switches do not remind`() {
        val reminder = reminder()
        clock.advanceMinutes(30)
        assertFalse(reminder.focusChanged(true, false, true))
        assertFalse(reminder.returnAfter(14))
    }

    @Test fun `return after exactly fifteen minutes reminds once`() {
        val reminder = reminder()
        reminder.focusChanged(true, false, true)
        reminder.focusChanged(false, false, true)
        clock.advanceMinutes(15)
        assertFalse(reminder.focusChanged(false, false, true)) // Duplicate notifications must not reset the absence.
        assertTrue(reminder.focusChanged(true, false, true))
        assertFalse(reminder.returnAfter(15))
    }

    @Test fun `licensed users and open dialogs suppress the reminder without delaying it`() {
        for ((licensed, canShow) in listOf(true to true, false to false)) {
            val reminder = reminder()
            reminder.focusChanged(true, licensed, canShow)
            assertFalse(reminder.returnAfter(15, licensed, canShow))
            assertTrue(reminder.returnAfter(15))
            File(root, "reminder").delete()
        }
    }

    @Test fun `a reminder starts a persistent twenty four hour cooldown`() {
        var reminder = reminder()
        reminder.focusChanged(true, false, true)
        assertTrue(reminder.returnAfter(15))
        reminder = reminder() // Restarting preserves the cooldown, but does not count as returning.
        assertFalse(reminder.focusChanged(true, false, true))
        assertFalse(reminder.returnAfter(24 * 60 - 1))
        assertTrue(reminder.returnAfter(15))
    }

    private class TestClock : Clock() {
        private var time = Instant.parse("2026-10-24T00:00:00Z")
        fun advanceMinutes(minutes: Long) { time = time.plusSeconds(minutes * 60) }
        override fun instant(): Instant = time
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(time, zone)
    }
}
