package com.avdbuddy

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.test.*

class DeviceCreationTest {
    @TempDir lateinit var root: File

    private fun request() = HardwareProfiles.all.single { it.id == "pixel-10" }.createSpec("Pixel 10",
        SystemImage("system-images;android-37;google_apis_playstore;${nativeImageAbi()!!}", "Android 17", false), emptyList())

    @Test fun `workspace stays blocked while loading a newly created emulator`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val entered = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val executor = CommandExecutor { command, cancelled, output ->
            if (command.args == listOf("devices") && !entered.isCompleted) {
                entered.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
            }
            fake.run(command, cancelled, output)
        }
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, executor) { spec, settings ->
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 512 * GIB) }
        }
        state.create(request())
        try {
            withTimeout(3000) { entered.await() }
            assertTrue(state.busy)
        } finally { release.countDown() }
        coroutineContext.job.children.toList().joinAll()
    }

    @Test fun `creation closes dialog and keeps selected preview through refresh until automatic launch`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake) { spec, settings ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 512 * GIB) }
        }
        state.showCreate = true
        state.create(request())
        try {
            assertFalse(state.showCreate)
            assertTrue(state.creating)
            assertEquals("Pixel 10", state.previewDevice?.displayName)
            assertTrue(state.devices.isEmpty())
            withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
            state.refresh()
            assertEquals(setOf("Pixel_10"), state.selected)
            assertEquals("Pixel_10", state.previewDevice?.name)
            assertFalse(File(fake.avds, "Pixel_10.avd").exists())
        } finally { release.countDown() }
        withTimeout(5000) { while (state.creating || state.starting.isNotEmpty()) delay(20) }
        assertNull(state.pendingCreation)
        assertFalse(state.showCreate)
        assertEquals("Pixel 10", state.previewDevice?.displayName)
        assertTrue("Pixel_10" in state.running)
        assertEquals(1, fake.commands.count { it.detached })
    }

    @Test fun `failed setup remains in preview and retry starts device without reopening dialog`(): Unit = runBlocking {
        val fake = FakeAndroid(root).apply { installFailure = "Connection interrupted" }
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake) { spec, settings ->
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 512 * GIB) }
        }
        state.create(request())
        withTimeout(5000) { while (state.creating) delay(20) }
        assertIs<CreationState.Failed>(state.creation)
        assertEquals("Pixel 10", state.previewDevice?.displayName)
        assertNotNull(state.pendingCreation)
        assertFalse(state.showCreate)
        assertFalse(fake.commands.any { it.detached })
        fake.installFailure = null
        state.retryCreation()
        withTimeout(5000) { while (state.creating || state.starting.isNotEmpty()) delay(20) }
        assertEquals(CreationState.Created, state.creation)
        assertNull(state.pendingCreation)
        assertTrue("Pixel_10" in state.running)
    }

    @Test fun `cancelled setup removes pending preview without starting a device`(): Unit = runBlocking {
        val fake = FakeAndroid(root)
        val state = AppState(this, SettingsStore(File(root, "settings")), fake.settings, fake) { spec, settings ->
            calculateStoragePlan(settings, spec, ImageFootprint(0, 0)) { DiskSpace("disk", 512 * GIB) }
        }
        state.create(request())
        state.cancelCreate()
        withTimeout(5000) { while (state.creating) delay(20) }
        assertNull(state.pendingCreation)
        assertTrue(state.visibleDevices.isEmpty())
        assertFalse(state.showCreate)
        assertFalse(fake.commands.any { it.detached || it.args.take(2) == listOf("create", "avd") })
    }
}
