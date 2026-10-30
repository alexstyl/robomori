package com.avdbuddy

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class DeviceActionsTest {
    @TempDir lateinit var root: File

    @Test fun `single stopped device offers editing and removal targets that device`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("Phone")
        val state = AppState(this, initial = fake.settings, executor = fake, license = licensedForTest(this, File(root, "license")))
        state.refresh()
        val device = state.devices.single()
        val actions = deviceActions(state, device, listOf(device))
        assertEquals(listOf("Start", "Cold boot", "Wipe data…", "Duplicate", "Rename…", "Show in ${HostPlatform().fileManagerName}", "Remove…"), actions.map { it.label })
        actions.first { it.label == "Wipe data…" }.onClick()
        assertEquals(device, state.wipeDataDevice)
        actions.first { it.label == "Rename…" }.onClick()
        assertEquals(device, state.renameDevice)
        actions.last().onClick()
        assertEquals(listOf(device), state.deleteDevices)
    }

    @Test fun `mixed selection offers batch actions and removes every selected device`() = runBlocking {
        val fake = FakeAndroid(root)
        fake.device("Stopped")
        fake.device("Running")
        fake.active["Running"] = "emulator-5554"
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        val actions = deviceActions(state, state.devices.first(), state.devices)
        assertEquals(listOf("Start", "Cold boot", "Stop", "Restart", "Show in ${HostPlatform().fileManagerName}", "Remove…"), actions.map { it.label })
        actions.last().onClick()
        assertEquals(state.devices, state.deleteDevices)
    }

    @Test fun `cold boot action starts a stopped emulator without loading a snapshot`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Phone")
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()

        deviceActions(state, device, listOf(device)).first { it.label == "Cold boot" }.onClick()
        coroutineContext.job.children.toList().joinAll()

        val args = fake.commands.single { it.detached }.args
        assertContains(args, "-no-snapshot-load")
        assertFalse("-no-snapshot" in args)
    }

    @Test fun `unavailable storage cannot start and empty targets have no actions`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Missing")
        device.directory.deleteRecursively()
        val state = AppState(this, initial = fake.settings, executor = fake)
        assertFalse(deviceActions(state, device, listOf(device)).any { it.label == "Start" || it.label == "Cold boot" })
        assertFalse(deviceActions(state, device, listOf(device)).any { it.label == "Wipe data…" })
        assertTrue(deviceActions(state, device, emptyList()).isEmpty())
    }
}
