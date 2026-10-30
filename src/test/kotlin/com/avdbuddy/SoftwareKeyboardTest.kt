package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class SoftwareKeyboardTest {
    @TempDir lateinit var root: File

    private class Keyboard(var visible: Boolean, var preference: String, val hasEditor: Boolean = true, val appliesPreferenceImmediately: Boolean = false) {
        val commands = mutableListOf<Command>()
        val executor = CommandExecutor { command, _, _ ->
            commands += command
            when {
                "dumpsys" in command.args -> "curEditorInfo:\n inputType=0x${if (hasEditor) "1" else "0"}\n mInputShown=$visible"
                "put" in command.args -> {
                    preference = command.args.last()
                    if (appliesPreferenceImmediately) visible = preference == "1"
                    ""
                }
                "keyevent" in command.args -> { visible = command.args.last() == "KEYCODE_DPAD_CENTER"; "" }
                else -> error("Unexpected keyboard command")
            }
        }
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), executor)
    }

    @Test fun `visible keyboard is hidden even when its preference already says hidden`() {
        val keyboard = Keyboard(visible = true, preference = "0")
        keyboard.service.toggleSoftwareKeyboard("emulator-5554")
        assertFalse(keyboard.visible)
    }

    @Test fun `hidden keyboard is shown even when its preference already says shown`() {
        val keyboard = Keyboard(visible = false, preference = "1")
        keyboard.service.toggleSoftwareKeyboard("emulator-5554")
        assertTrue(keyboard.visible)
    }

    @Test fun `showing keyboard allows it alongside hardware input`() {
        val keyboard = Keyboard(visible = false, preference = "0")
        keyboard.service.toggleSoftwareKeyboard("emulator-5554")
        assertEquals("1", keyboard.preference)
    }

    @Test fun `show request is forwarded even without a focused editor`() {
        val keyboard = Keyboard(visible = false, preference = "0", hasEditor = false)
        keyboard.service.toggleSoftwareKeyboard("emulator-5554")
        assertTrue(keyboard.commands.any { it.args.takeLast(4) == listOf("input", "dpad", "keyevent", "KEYCODE_DPAD_CENTER") })
    }

    @Test fun `Android ignoring a show request is not an app error`() {
        val keyboard = Keyboard(visible = false, preference = "0", hasEditor = false)
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, token, output ->
            val result = keyboard.executor.run(command, token, output)
            keyboard.visible = false
            result
        })
        assertTrue(service.toggleSoftwareKeyboard("emulator-5554"))
    }

    @Test fun `an IME that immediately hides does not receive a back key that could navigate away`() {
        val keyboard = Keyboard(visible = true, preference = "1", appliesPreferenceImmediately = true)
        keyboard.service.toggleSoftwareKeyboard("emulator-5554")
        assertTrue(keyboard.commands.none { "keyevent" in it.args })
    }

    @Test fun `keyboard commands address the selected emulator`() {
        val keyboard = Keyboard(visible = false, preference = "0")
        keyboard.service.toggleSoftwareKeyboard("emulator-5556")
        assertTrue(keyboard.commands.all { it.args.take(2) == listOf("-s", "emulator-5556") })
    }

    @Test fun `unknown visibility does not send a key`() {
        val commands = mutableListOf<Command>()
        val service = DeviceSettingsService(AppSettings("/sdk", "/avds"), CommandExecutor { command, _, _ -> commands += command; "" })
        runCatching { service.toggleSoftwareKeyboard("emulator-5554") }
        assertTrue(commands.none { "keyevent" in it.args })
    }

    @Test fun `a keyboard request without a running emulator has no effect`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Phone")
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.selectPreview(device)
        state.toggleSoftwareKeyboard()
        coroutineContext.job.children.toList().joinAll()
        assertTrue(fake.commands.none { "input_method" in it.args || "show_ime_with_hard_keyboard" in it.args })
        assertFalse(state.showDemoDialog)
    }

    @Test fun `selected emulator receives the keyboard toggle without restarting`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Phone")
        writeIni(File(device.directory, "config.ini"), device.config + ("hw.keyboard" to "no"))
        fake.active[device.name] = "emulator-5556"
        val keyboard = Keyboard(visible = false, preference = "0")
        val executor = CommandExecutor { command, token, output ->
            if ("show_ime_with_hard_keyboard" in command.args || "input_method" in command.args || "keyevent" in command.args) {
                keyboard.executor.run(command, token, output)
            } else fake.run(command, token, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor, license = licensedForTest(this, File(root, "license")))
        state.refresh()
        state.selectPreview(state.devices.single())
        state.toggleSoftwareKeyboard()
        coroutineContext.job.children.toList().joinAll()
        assertTrue(keyboard.visible)
        assertTrue(keyboard.commands.all { it.args.take(2) == listOf("-s", "emulator-5556") })
    }

    @Test fun `selected USB phone never receives the emulator back key`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val keyboard = Keyboard(visible = true, preference = "0")
        val state = AppState(this, initial = fake.settings, executor = keyboard.executor)
        state.connected.selected = ConnectedDevice("USB123", "Pixel", "device")
        state.toggleSoftwareKeyboard()
        coroutineContext.job.children.toList().joinAll()
        assertTrue(keyboard.commands.none { "keyevent" in it.args })
    }
}
