package com.avdbuddy

import org.junit.jupiter.api.Test
import kotlin.test.*

class DeviceSettingsTest {
    private val settings = AppSettings("/sdk", "/avds", adb = "/custom tools/adb")

    @Test fun `reads effective appearance and independent scales from the selected device`() {
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
            commands += command
            when (command.args.last()) {
                "get-config" -> "config: en-rUS-sw411dp-port-night-420dpi-v36\nabi: arm64-v8a"
                "window_animation_scale" -> "0\n"
                "transition_animation_scale" -> "0.5\n"
                "animator_duration_scale" -> "2.0\n"
                "font_scale" -> "1.3\n"
                "android.accessibilityservice.AccessibilityService" -> "No services found"
                else -> error("Unexpected command")
            }
        })
        val actual = service.read("emulator-5556")
        assertTrue(actual.darkMode)
        assertEquals(mapOf(AnimationScaleSetting.WINDOW to 0f, AnimationScaleSetting.TRANSITION to .5f,
            AnimationScaleSetting.ANIMATOR to 2f), actual.animationScales)
        assertTrue(commands.all { it.executable == expandPath(settings.adb) })
        assertTrue(commands.all { it.args.take(3) == listOf("-s", "emulator-5556", "shell") })
        assertEquals(1.3f, actual.fontScale)
        assertEquals(listOf("-s", "emulator-5556", "shell", "settings", "--user", "current", "get", "system", "font_scale"), commands[4].args)
        assertFalse(actual.talkBackInstalled)
        assertFalse(actual.talkBackEnabled)
        assertEquals(6, commands.size)
    }

    @Test fun `light configuration and unset scales use Android defaults`() {
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
            if (command.args.last() == "get-config") "config: en-rUS-port-notnight-v29" else "null\n"
        })
        val actual = service.read("emulator-5554")
        assertFalse(actual.darkMode)
        assertTrue(actual.animationScales.values.all { it == 1f })
        assertEquals(1f, actual.fontScale)
    }

    @Test fun `unknown configuration fails instead of pretending the device is light`() {
        val service = DeviceSettingsService(settings, CommandExecutor { _, _, _ -> "Error: device offline" })
        assertFailsWith<IllegalStateException> { service.read("emulator-5554") }
    }

    @Test fun `invalid animation values fail instead of displaying invented defaults`() {
        for (value in listOf("", "NaN", "Infinity", "-1", "Permission denied")) {
            val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
                if (command.args.last() == "get-config") "config: en-rUS-notnight-v36" else value
            })
            assertFailsWith<IllegalStateException> { service.read("emulator-5554") }
        }
    }

    @Test fun `writes target the requested serial and native settings keys`() {
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
            commands += command
            if ("uimode" in command.args) "Night mode: ${command.args.last()}\n" else ""
        })
        service.setDarkMode("emulator-5556", true)
        service.setDarkMode("emulator-5556", false)
        AnimationScaleSetting.entries.forEach { service.setAnimationScale("emulator-5556", it, .5f) }
        assertEquals(listOf("-s", "emulator-5556", "shell", "cmd", "uimode", "night", "yes"), commands[0].args)
        assertEquals("no", commands[1].args.last())
        AnimationScaleSetting.entries.forEachIndexed { index, setting ->
            assertEquals(listOf("-s", "emulator-5556", "shell", "settings", "put", "global", setting.key, "0.5"),
                commands[index + 2].args)
        }
        assertFailsWith<IllegalArgumentException> { service.setAnimationScale("emulator-5556", AnimationScaleSetting.WINDOW, Float.NaN) }
        assertFailsWith<IllegalArgumentException> { service.setDarkMode("", true) }
        assertEquals(5, commands.size)
    }

    @Test fun `rejected night mode command reports failure even when shell succeeds`() {
        val service = DeviceSettingsService(settings, CommandExecutor { _, _, _ -> "Error: Permission denied" })
        assertFailsWith<IllegalStateException> { service.setDarkMode("emulator-5554", true) }
    }

    @Test fun `text size writes use current user system settings and reject invalid scales`() {
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ -> commands += command; "" })
        service.setFontScale("emulator-5556", 2f)
        assertEquals(listOf("-s", "emulator-5556", "shell", "settings", "--user", "current", "put", "system", "font_scale", "2.0"), commands.single().args)
        for (value in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, 3f)) {
            assertFailsWith<IllegalArgumentException> { service.setFontScale("emulator-5556", value) }
        }
        assertEquals(1, commands.size)
    }

    @Test fun `invalid font scale does not masquerade as default text size`() {
        for (value in listOf("0", "-1", "NaN", "Infinity", "Error")) {
            val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
                when (command.args.last()) {
                    "get-config" -> "config: en-notnight-v36"
                    "font_scale" -> value
                    else -> "1.0"
                }
            })
            assertFailsWith<IllegalStateException> { service.read("emulator-5554") }
        }
    }

    @Test fun `talkback changes preserve other services and normalize component names`() {
        val talkBack = "com.google.android.marvin.talkback/.TalkBackService"
        val other = "example.reader/.ReaderService"
        var services = other
        var accessibility = "1"
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
            commands += command
            val args = command.args
            when {
                "query-services" in args -> "$talkBack\ncom.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
                "put" in args -> {
                    when (args[args.lastIndex - 1]) {
                        "enabled_accessibility_services" -> services = args.last()
                        "accessibility_enabled" -> accessibility = args.last()
                    }
                    ""
                }
                "delete" in args -> { services = "null"; "Deleted 1 rows" }
                args.last() == "enabled_accessibility_services" -> services
                args.last() == "accessibility_enabled" -> accessibility
                args.last() == "get-config" -> "config: en-notnight-v36"
                else -> "1.0"
            }
        })
        assertFalse(service.read("emulator-5556").talkBackEnabled)
        service.setTalkBack("emulator-5556", true)
        assertEquals("$other:$talkBack", services)
        assertTrue(service.read("emulator-5556").talkBackEnabled)
        services = "$other:com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        commands.clear()
        service.setTalkBack("emulator-5556", false)
        assertEquals(other, services)
        assertFalse(commands.any { "put" in it.args && "accessibility_enabled" in it.args })
        services = talkBack
        service.setTalkBack("emulator-5556", false)
        assertEquals("null", services)
        assertEquals("0", accessibility)
        assertTrue(commands.all { it.args.take(3) == listOf("-s", "emulator-5556", "shell") })
    }

    @Test fun `missing talkback cannot modify accessibility settings`() {
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(settings, CommandExecutor { command, _, _ ->
            commands += command
            "com.google.android.marvin.talkback/com.google.android.accessibility.selecttospeak.SelectToSpeakService"
        })
        assertFailsWith<IllegalStateException> { service.setTalkBack("emulator-5554", true) }
        assertEquals(1, commands.size)
    }
}
