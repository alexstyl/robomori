package com.avdbuddy

import kotlinx.coroutines.job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

// Headless state and command checks only; no windows or UI interaction.
class DeviceSelectionTest {
    @TempDir lateinit var root: File

    @Test fun `toggle range and context selection preserve the intended targets`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "C", "D").map { fake.device(it) }
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.selectDevice(devices[0])
        state.selectDevice(devices[2], toggle = true)
        assertEquals(setOf("A", "C"), state.selected)
        assertEquals("C", state.previewDevice?.name)
        state.selectForContextMenu(devices[0])
        assertEquals(setOf("A", "C"), state.selected)
        state.selectDevice(devices[3], extend = true)
        assertEquals(setOf("C", "D"), state.selected)
        state.selectDevice(devices[0], extend = true)
        assertEquals(setOf("A", "B", "C"), state.selected)
        state.selectDevice(devices[3], toggle = true, extend = true)
        assertEquals(setOf("A", "B", "C", "D"), state.selected)
        state.selectDevice(devices[2], toggle = true)
        assertEquals(setOf("A", "B", "D"), state.selected)
        assertTrue(state.previewDevice?.name in state.selected)
        state.selectForContextMenu(devices[2])
        assertEquals(setOf("C"), state.selected)
    }

    @Test fun `batch start skips running and missing devices and preserves selection`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "Running", "Missing").map { fake.device(it) }
        devices.last().directory.deleteRecursively()
        fake.active["Running"] = "emulator-5560"
        val executor = CommandExecutor { command, cancelled, output ->
            fake.run(command, cancelled, output).also {
                if (command.detached) fake.active[command.args[1]] = if (command.args[1] == "A") "emulator-5554" else "emulator-5556"
            }
        }
        val state = AppState(this, initial = fake.settings, executor = executor, license = licensedForTest(this, File(root, "license")))
        state.refresh()
        state.selected = devices.map { it.name }.toSet()
        state.previewName = "Running"
        state.launch(state.devices)
        assertTrue(state.busy)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("A", "B"), fake.commands.filter { it.detached }.map { it.args[1] }.toSet())
        assertEquals(devices.map { it.name }.toSet(), state.selected)
        assertEquals("Running", state.previewDevice?.name)
        assertTrue(state.starting.isEmpty())
        assertNull(state.failureEvents.tryReceive().getOrNull())
    }

    @Test fun `one failed launch does not skip the other selected devices`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("Broken", "Healthy").map { fake.device(it) }
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.detached && command.args[1] == "Broken") error("Cannot start")
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor, license = licensedForTest(this, File(root, "license")))
        state.refresh()
        state.launch(devices)
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Healthy"), state.running.keys)
        assertEquals(FailedOperation.Start(listOf(devices[0].displayName)), state.failureEvents.tryReceive().getOrNull())
        assertTrue(state.starting.isEmpty())
    }

    @Test fun `batch stop and restart only address selected running devices`() = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("A", "B", "Other", "Stopped").map { fake.device(it) }
        fake.active.putAll(mapOf("A" to "emulator-5554", "B" to "emulator-5556", "Other" to "emulator-5558"))
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.restart(listOf(devices[0], devices[1], devices[3]))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("emulator-5554", "emulator-5556"), fake.commands.filter { "reboot" in it.args }.map { it.args[1] }.toSet())
        state.stop(listOf(devices[0], devices[1], devices[3]))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Other"), state.running.keys)
        assertEquals(setOf("emulator-5554", "emulator-5556"), fake.commands.filter { "kill" in it.args }.map { it.args[1] }.toSet())
    }

    @Test fun `delete tracks its device without blocking the workspace`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Existing")
        val state = AppState(this, initial = fake.settings, executor = fake)
        state.refresh()
        state.delete(listOf(device))
        assertFalse(state.busy)
        assertEquals(EmulatorOperation.Deleting, state.deviceOperations[device.name])
        state.resetCreate()
        assertTrue(state.showCreate)
        assertFalse(state.busy)
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `another emulator can start while a device is being deleted`() = runBlocking {
        val fake = FakeAndroid(root)
        val deleting = fake.device("Deleting")
        val other = fake.device("Other")
        val deletionStarted = CompletableDeferred<Unit>()
        val finishDeletion = CompletableDeferred<Unit>()
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args.take(2) == listOf("delete", "avd")) {
                deletionStarted.complete(Unit)
                runBlocking { finishDeletion.await() }
            }
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.delete(listOf(deleting))
        try {
            deletionStarted.await()
            assertFalse(state.busy)
            assertEquals(EmulatorOperation.Deleting, state.deviceOperations[deleting.name])
            state.duplicate(deleting)
            state.rename(deleting, "Renamed")
            assertFalse(state.busy)
            state.launch(deleting)
            state.launch(other)
            assertTrue(other.name in state.starting)
            assertFalse(deleting.name in state.starting)
        } finally {
            finishDeletion.complete(Unit)
            coroutineContext.job.children.toList().joinAll()
        }
        assertTrue(fake.commands.any { it.detached && it.args[1] == other.name })
        assertFalse(fake.commands.any { it.detached && it.args[1] == deleting.name })
        assertFalse(state.busy)
        assertTrue(state.deviceOperations.isEmpty())
    }

    @Test fun `creation stays available while an emulator is stopping`() = runBlocking {
        val fake = FakeAndroid(root)
        val device = fake.device("Running")
        fake.active[device.name] = "emulator-5554"
        val stopStarted = CompletableDeferred<Unit>()
        val finishStop = CompletableDeferred<Unit>()
        val executor = CommandExecutor { command, cancelled, output ->
            if ("kill" in command.args) {
                stopStarted.complete(Unit)
                runBlocking { finishStop.await() }
            }
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.stop(device)
        try {
            stopStarted.await()
            assertTrue(device.name in state.stopping)
            assertFalse(state.busy)
            state.resetCreate()
            assertTrue(state.showCreate)
        } finally {
            finishStop.complete(Unit)
            coroutineContext.job.children.toList().joinAll()
        }
        assertNull(state.failureEvents.tryReceive().getOrNull())
        assertTrue(state.stopping.isEmpty())
    }

    @Test fun `batch remove continues after failure and refreshes remaining selection`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val devices = listOf("Broken", "Healthy", "Other").map { fake.device(it) }
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args.take(2) == listOf("delete", "avd") && command.args.last() == "Broken") error("Cannot remove")
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, initial = fake.settings, executor = executor)
        state.refresh()
        state.selected = setOf("Broken", "Healthy")
        state.delete(devices.take(2))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(setOf("Broken", "Other"), state.devices.map { it.name }.toSet())
        assertEquals(setOf("Broken"), state.selected)
        assertEquals(FailedOperation.Remove(listOf(devices[0].displayName)), state.failureEvents.tryReceive().getOrNull())
    }
}
