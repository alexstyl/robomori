package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeviceControlsStateTest {
    @Test fun `demo controls show the paywall without changing device settings`() = runBlocking {
        val writes = mutableListOf<List<String>>()
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ ->
            val args = command.args
            if ("put" in args || "delete" in args || "uimode" in args) writes += args
            when {
                args.takeLast(2) == listOf("getprop", "sys.boot_completed") -> "1"
                "get-config" in args -> "config: en-notnight-v36"
                else -> "1.0"
            }
        })
        var paywallCount = 0
        val state = DeviceControlsState(service, "emulator-5554", this,
            canEdit = { false }, onLocked = { paywallCount++ })
        state.refresh()
        val original = state.settings

        state.setDarkMode(true)
        state.setFontScale(1.5f)
        state.setTalkBack(true)
        AnimationScaleSetting.entries.forEach { state.setAnimationScale(it, .5f) }
        state.resetAnimations()

        assertEquals(7, paywallCount)
        assertEquals(original, state.settings)
        assertTrue(writes.isEmpty())
    }

    @Test fun `settings remain hidden until Android reports boot completion`() = runBlocking {
        var booted = false
        var settingsReads = 0
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ ->
            when {
                command.args.takeLast(2) == listOf("getprop", "sys.boot_completed") -> if (booted) "1\n" else "\n"
                "get-config" in command.args -> { settingsReads++; "config: en-notnight-v36" }
                else -> "1.0"
            }
        })
        val state = DeviceControlsState(service, "emulator-5554", this)
        state.refresh()
        assertFalse(state.booted)
        assertNull(state.settings)
        assertEquals(DeviceControlsSnapshot.WaitingForBoot, state.snapshot)
        assertEquals(0, settingsReads)

        booted = true
        state.refresh()
        assertTrue(state.booted)
        assertNotNull(state.settings)
        assertEquals(1, settingsReads)
    }

    @Test fun `edits update immediately and obsolete queued values are skipped per setting`(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val writes = mutableListOf<String>()
        var dark = false
        var scale = "1.0"
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ ->
            val args = command.args
            when {
                args.takeLast(2) == listOf("getprop", "sys.boot_completed") -> "1"
                "uimode" in args -> {
                    if (writes.isEmpty()) {
                        entered.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                    }
                    writes += args.last()
                    dark = args.last() == "yes"
                    "Night mode: ${args.last()}"
                }
                "put" in args -> { scale = args.last(); writes += scale; "" }
                "get-config" in args -> "config: en-${if (dark) "night" else "notnight"}-v36"
                args.last() == "window_animation_scale" -> scale
                else -> "1.0"
            }
        })
        val state = DeviceControlsState(service, "emulator-5554", this)
        state.refresh()
        try {
            state.setDarkMode(true)
            withTimeout(5000) { entered.await() }
            state.setAnimationScale(AnimationScaleSetting.WINDOW, .5f)
            state.setDarkMode(false)
            state.setAnimationScale(AnimationScaleSetting.WINDOW, 2f)
            assertFalse(state.settings!!.darkMode)
            assertEquals(2f, state.settings!!.animationScales[AnimationScaleSetting.WINDOW])
            state.refresh()
        } finally {
            release.countDown()
        }
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf("yes", "no", "2.0"), writes)
        assertFalse(state.settings!!.darkMode)
        assertEquals(2f, state.settings!!.animationScales[AnimationScaleSetting.WINDOW])
        assertIs<DeviceControlsSnapshot.Ready>(state.snapshot)
    }

    @Test fun `failed writes reconcile actual state and do not prevent subsequent edits`(): Unit = runBlocking {
        var reject = true
        var dark = false
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ ->
            when {
                command.args.takeLast(2) == listOf("getprop", "sys.boot_completed") -> "1"
                "uimode" in command.args -> {
                    check(!reject) { "Device unavailable" }
                    dark = command.args.last() == "yes"
                    "Night mode: ${command.args.last()}"
                }
                "get-config" in command.args -> "config: en-${if (dark) "night" else "notnight"}-v36"
                else -> "1.0"
            }
        })
        val state = DeviceControlsState(service, "emulator-5554", this)
        state.refresh()
        state.setDarkMode(true)
        coroutineContext.job.children.toList().joinAll()
        assertFalse(state.settings!!.darkMode)
        assertIs<DeviceControlsSnapshot.WriteFailed>(state.snapshot)
        reject = false
        state.setDarkMode(true)
        assertTrue(state.settings!!.darkMode)
        coroutineContext.job.children.toList().joinAll()
        assertTrue(state.settings!!.darkMode)
        assertIs<DeviceControlsSnapshot.Ready>(state.snapshot)
    }
}
