package com.avdbuddy

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

/** Without a license, only creating and running one emulator and USB screen sharing are free. */
class LicensedFeaturesTest {
    @TempDir lateinit var root: File

    private fun launching(fake: FakeAndroid) = CommandExecutor { command, cancelled, output ->
        fake.run(command, cancelled, output).also {
            if (command.detached) fake.active[command.args[1]] = "emulator-${5554 + 2 * fake.active.size}"
        }
    }

    private fun CoroutineScope.demo(fake: FakeAndroid, executor: CommandExecutor = launching(fake)) =
        AppState(this, initial = fake.settings, executor = executor, license = AppLicense(this, File(root, "no-license")))

    @Test fun `one emulator runs without a license`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A")
        val state = demo(fake)
        state.refresh()
        state.launch(state.devices)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("A"), state.running.keys)
        assertFalse(state.showDemoDialog)
    }

    @Test fun `a second emulator asks for a license instead of starting`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A"); fake.device("B")
        val state = demo(fake)
        state.refresh()
        state.launch(state.devices.filter { it.name == "A" })
        coroutineContext.job.children.toList().joinAll()
        state.launch(state.devices.filter { it.name == "B" })
        assertTrue(state.showDemoDialog)
        assertTrue(state.starting.isEmpty())
        assertEquals(setOf("A"), state.running.keys)
    }

    @Test fun `starting several emulators at once asks for a license`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A"); fake.device("B")
        val state = demo(fake)
        state.refresh()
        state.launch(state.devices)
        assertTrue(state.showDemoDialog)
        assertTrue(fake.commands.none { it.detached })
    }

    @Test fun `emulators started outside AvdBuddy do not count toward the limit`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A"); fake.device("Elsewhere")
        fake.active["Elsewhere"] = "emulator-5560"
        val state = demo(fake)
        state.refresh()
        state.launch(state.devices.filter { it.name == "A" })
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("A", "Elsewhere"), state.running.keys)
        assertFalse(state.showDemoDialog)
    }

    @Test fun `a stopped emulator frees the slot for another`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A"); fake.device("B")
        val state = demo(fake)
        state.refresh()
        state.launch(state.devices.filter { it.name == "A" })
        coroutineContext.job.children.toList().joinAll()
        fake.active.remove("A")
        state.refresh()
        state.launch(state.devices.filter { it.name == "B" })
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("B"), state.running.keys)
        assertFalse(state.showDemoDialog)
    }

    @Test fun `emulator editing actions ask for a license`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A")
        val state = demo(fake)
        state.refresh()
        val device = state.devices.single()

        state.openRename(device)
        assertNull(state.renameDevice)
        assertTrue(state.showDemoDialog)

        state.showDemoDialog = false
        state.openWipeData(device)
        assertNull(state.wipeDataDevice)
        assertTrue(state.showDemoDialog)

        state.showDemoDialog = false
        state.duplicate(device)
        assertTrue(state.showDemoDialog)
        assertTrue(state.deviceOperations.isEmpty())
    }

    @Test fun `screenshots and the keyboard toggle ask for a license`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("A")
        fake.active["A"] = "emulator-5554"
        val state = demo(fake)
        state.refresh()
        val device = state.devices.single()
        state.selectPreview(device)

        state.captureScreenshot(device)
        assertTrue(state.showDemoDialog)
        assertTrue(state.savingCaptures.isEmpty())

        state.showDemoDialog = false
        state.toggleSoftwareKeyboard()
        assertTrue(state.showDemoDialog)
        assertTrue(fake.commands.none { "input_method" in it.args })
    }

    @Test fun `renaming and restarting a connected device ask for a license`() = runBlocking {
        var locked = 0
        val connected = ConnectedDevicesState(this, { AppSettings(root.path, root.path) },
            DeviceNamesStore(File(root, "names")), PairedDevicesStore(File(root, "paired.json")), { TestDeviceMirror() },
            { AndroidService(it, CommandExecutor { _, _, _ -> error("No device commands without a license") }) },
            licensed = { false }, onLocked = { locked++ })
        val phone = ConnectedDevice("USB123", "Pixel", "device")
        connected.openRename(phone)
        connected.restart(phone)
        assertEquals(2, locked)
        assertNull(connected.renameTarget)
        assertEquals(OperationState.Idle, connected.action)
    }
}
