package com.avdbuddy

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.*

class LivePreviewTest {
    @TempDir lateinit var root: File

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `intentional stop does not report a disconnected screen while adb still lists the device`(stopAll: Boolean): Unit = runBlocking {
        withTimeout(5_000) {
            val fake = FakeAndroid(root)
            val device = fake.device("Running")
            fake.active[device.name] = "emulator-5554"
            val subscribed = CompletableDeferred<Unit>()
            val disconnected = CompletableDeferred<Unit>()
            val streamClosed = CompletableDeferred<Unit>()
            val display = object : DeviceDisplay by AdbDeviceDisplay() {
                override fun stream(settings: AppSettings, device: Device, serial: String) = flow<EmulatorFrame> {
                    subscribed.complete(Unit)
                    try {
                        disconnected.await()
                        error("Lost connection to emulator")
                    } finally { streamClosed.complete(Unit) }
                }
            }
            val executor = CommandExecutor { command, cancelled, output ->
                if ("kill" in command.args) {
                    disconnected.complete(Unit)
                    "OK"
                } else fake.run(command, cancelled, output)
            }
            val state = AppState(this, initial = fake.settings, executor = executor, display = display)
            state.refresh()
            val observer = launch { state.observeDisplays() }
            try {
                subscribed.await()
                Snapshot.withMutableSnapshot { if (stopAll) state.stopAll() else state.stop(device) }
                Snapshot.sendApplyNotifications()
                streamClosed.await()
                while (state.busy) yield()
                yield()
                assertTrue(device.name in state.running, "ADB has not confirmed shutdown yet")
                assertTrue(device.name in state.stopping)
                assertTrue(state.displayErrors.isEmpty(), "Intentional shutdown must not show a connection error")
                fake.active.clear()
                state.refresh()
                assertTrue(state.frames.isEmpty())
                assertTrue(state.stopping.isEmpty())
            } finally { observer.cancelAndJoin() }
        }
    }

    @Test fun `failed stop restores the running preview state`(): Unit = runBlocking {
        withTimeout(5_000) {
            val fake = FakeAndroid(root)
            val device = fake.device("Running")
            fake.active[device.name] = "emulator-5554"
            val executor = CommandExecutor { command, cancelled, output ->
                if ("kill" in command.args) error("Could not stop emulator")
                fake.run(command, cancelled, output)
            }
            val state = AppState(this, initial = fake.settings, executor = executor)
            state.refresh()
            state.stop(device)
            while (state.stopping.isNotEmpty()) yield()
            assertTrue(state.stopping.isEmpty())
            assertTrue(device.name in state.running)
            assertEquals(FailedOperation.Stop(listOf(device.displayName)), state.failureEvents.tryReceive().getOrNull())
        }
    }

    @Test fun `unexpected stream failures still report a connection error`(): Unit = runBlocking {
        withTimeout(5_000) {
            val fake = FakeAndroid(root)
            val device = fake.device("Running")
            fake.active[device.name] = "emulator-5554"
            val display = object : DeviceDisplay by AdbDeviceDisplay() {
                override fun stream(settings: AppSettings, device: Device, serial: String) = flow<EmulatorFrame> {
                    error("Lost connection to emulator")
                }
            }
            val state = AppState(this, initial = fake.settings, executor = fake, display = display)
            state.refresh()
            val observer = launch { state.observeDisplays() }
            try {
                while (state.displayErrors.isEmpty()) yield()
                assertIs<DisplayFailure.Connection>(state.displayErrors[device.name])
            } finally { observer.cancelAndJoin() }
        }
    }

    @Test fun `switching devices waits for fresh frames and stopping discards the screen`(): Unit = runBlocking {
        withTimeout(5_000) {
            val fake = FakeAndroid(root)
            val first = fake.device("First")
            val second = fake.device("Second")
            File(first.directory, ".avdbuddy-preview.png").writeText("An obsolete cached screenshot")
            fake.active[first.name] = "emulator-5554"
            fake.active[second.name] = "emulator-5556"
            val subscriptions = Channel<String>(Channel.UNLIMITED)
            val incoming = Channel<EmulatorFrame>(Channel.UNLIMITED)
            val delivered = Channel<Unit>(Channel.UNLIMITED)
            val display = object : DeviceDisplay by AdbDeviceDisplay() {
                override fun stream(settings: AppSettings, device: Device, serial: String) = flow {
                    subscriptions.send(device.name)
                    for (frame in incoming) {
                        emit(frame)
                        delivered.send(Unit)
                    }
                }
            }
            val state = AppState(this, initial = fake.settings, executor = fake, display = display)
            state.refresh()
            state.selectPreview(first)
            val observer = launch { state.observeDisplays() }
            try {
                assertEquals(first.name, subscriptions.receive())
                assertTrue(state.frames.isEmpty())
                val frame = EmulatorFrame(ImageBitmap(2, 2), 1)
                incoming.send(frame)
                delivered.receive()
                assertSame(frame.bitmap, state.frames[first.directory.absolutePath])

                Snapshot.withMutableSnapshot { state.selectPreview(second) }
                Snapshot.sendApplyNotifications()
                assertEquals(second.name, subscriptions.receive())
                assertTrue(state.frames.isEmpty())
                assertEquals(1, state.frameRotations[first.directory.absolutePath])
                incoming.send(frame)
                delivered.receive()
                assertSame(frame.bitmap, state.frames[second.directory.absolutePath])

                fake.active.remove(second.name)
                state.refresh()
                assertTrue(state.frames.isEmpty())
                assertEquals(1, state.frameRotations[first.directory.absolutePath])
                assertTrue(second.directory.absolutePath !in state.frameRotations)
            } finally {
                observer.cancelAndJoin()
            }
        }
    }
}
