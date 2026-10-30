package com.avdbuddy

import java.io.File
import java.time.Clock
import java.time.Duration

/** Decides when to remind an unlicensed user about buying, after they return to the app from a break. */
internal class LicenseReminder(
    private val file: File = File(HostPlatform().configDirectory, "license-reminder"),
    private val clock: Clock = Clock.systemUTC(),
) {
    private var focused = false
    private var awaySince: Long? = null
    private var lastShown = runCatching { file.readText().trim().toLong() }.getOrNull()

    /** Returns whether to remind now. A reminder starts a 24 hour cooldown that survives restarts. */
    fun focusChanged(focused: Boolean, licensed: Boolean, canShow: Boolean): Boolean {
        if (this.focused == focused) return false
        this.focused = focused
        val now = clock.millis()
        if (!focused) {
            awaySince = now
            return false
        }
        val away = awaySince
        awaySince = null
        if (away == null || licensed || !canShow) return false
        if (now - away < Duration.ofMinutes(15).toMillis()) return false
        if (lastShown?.let { now - it < Duration.ofHours(24).toMillis() } == true) return false
        recordShown(now)
        return true
    }

    private fun recordShown(now: Long) {
        lastShown = now
        runCatching { atomicWrite(file, now.toString()) }
    }
}
